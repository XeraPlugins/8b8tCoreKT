/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 *
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.coordinate

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.event.UserDisconnectEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRespawn
import me.gb8.core.Main
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.logging.Level

internal class CoordinateSpoofingService(private val plugin: Main) : Listener, CoordinateSpoofingBackend {
    private val generator = SpoofOffsetGenerator()
    private val border = SpoofWorldBorder()
    private val swapper = SpoofOffsetSwapper(plugin, border)
    private val states = ConcurrentHashMap<UUID, PlayerState>()
    private val joining = ConcurrentHashMap<UUID, CompletableFuture<PlayerState>>()
    private val lastErrors = ConcurrentHashMap<ErrorKey, Long>()
    private val packetListener = PacketListener()

    override fun enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        PacketEvents.getAPI().eventManager.registerListener(packetListener)
    }

    override fun disable() {
        HandlerList.unregisterAll(this)
        PacketEvents.getAPI().eventManager.unregisterListener(packetListener)
        swapper.cancelAll()
        states.clear()
        joining.values.forEach { it.cancel(false) }
        joining.clear()
        lastErrors.clear()
    }

    override fun isBedrock(player: Player): Boolean = BedrockDetector.isBedrock(player)

    override fun applyLoadedPreference(player: Player, enabled: Boolean) {
        if (isBedrock(player) || !enabled) {
            setEnabled(player, false)
        } else if (states[player.uniqueId]?.effectiveOffset?.isZero != false) {
            setEnabled(player, true)
        }
    }

    override fun setEnabled(player: Player, enabled: Boolean) {
        require(!enabled || !isBedrock(player)) { "Coordinate spoofing is unavailable for Bedrock players" }
        val desiredScalable = if (enabled) generator.generate(player.uniqueId, player.location) else ScalableSpoofOffset(0, 0)
        val desired = desiredScalable.inWorld(player.location)
        val context = worldContext(player.location)
        var changed = false
        states.compute(player.uniqueId) { _, old ->
            val current = old ?: PlayerState(
                ScalableSpoofOffset(0, 0),
                SpoofOffset.ZERO,
                worldContext(player.location),
                player.location.x,
                player.location.z
            )
            changed = current.effectiveOffset != desired
            if (!changed) current else current.copy(
                lastRealX = player.location.x,
                lastRealZ = player.location.z,
                nextScalable = desiredScalable,
                nextOffset = desired,
                nextWorld = context
            )
        }
        if (changed) swapper.swap(player)
    }

    fun isEnabled(player: Player): Boolean = states[player.uniqueId]?.effectiveOffset?.isZero == false

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val scalable = if (isBedrock(player)) ScalableSpoofOffset(0, 0) else generator.generate(player.uniqueId, player.location)
        val state = PlayerState(
            scalable,
            scalable.inWorld(player.location),
            worldContext(player.location),
            player.location.x,
            player.location.z
        )
        states[player.uniqueId] = state
        joining.remove(player.uniqueId)?.complete(state)
        border.update(player, player.location)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val current = states[player.uniqueId] ?: return
        val enabled = !current.effectiveOffset.isZero
        val scalable = when {
            !enabled -> ScalableSpoofOffset(0, 0)
            event.respawnReason == PlayerRespawnEvent.RespawnReason.END_PORTAL ->
                safeForDestination(player.uniqueId, current.effectiveScalable, event.respawnLocation)
            else -> generator.generate(player.uniqueId, event.respawnLocation)
        }
        setNext(player.uniqueId, scalable, event.respawnLocation)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        if (event.cause.name in IGNORED_TELEPORT_CAUSES) return
        val player = event.player
        val state = states[player.uniqueId] ?: return
        val from = event.from
        val to = event.to
        val worldChanged = from.world != to.world
        val regenerate = !worldChanged && from.distanceSquared(to) > REGENERATE_DISTANCE_SQUARED
        val scalable = when {
            state.effectiveOffset.isZero -> ScalableSpoofOffset(0, 0)
            regenerate -> generator.generate(player.uniqueId, to)
            worldChanged -> safeForDestination(player.uniqueId, state.effectiveScalable, to)
            else -> state.effectiveScalable
        }
        val previous = state.effectiveOffset
        val next = scalable.inWorld(to)
        setNext(player.uniqueId, scalable, to)

        if (!worldChanged && previous != next) {
            val viewBlocks = (maxOf(player.viewDistance, player.sendViewDistance) + 2) * 16.0
            if (from.distanceSquared(to) < viewBlocks * viewBlocks) {
                val chunks = swapper.unloadSentChunks(player)
                player.scheduler.runDelayed(plugin, { _ ->
                    if (!player.isOnline) return@runDelayed
                    PacketEvents.getAPI().playerManager.sendPacket(
                        player,
                        com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateViewPosition(
                            player.chunk.x,
                            player.chunk.z
                        )
                    )
                    swapper.refresh(player, chunks)
                }, null, 1L)
            }
        }
        border.update(player, to)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        if (event.from.blockX == event.to.blockX && event.from.blockZ == event.to.blockZ) return
        states.computeIfPresent(event.player.uniqueId) { _, state ->
            state.copy(lastRealX = event.to.x, lastRealZ = event.to.z)
        }
        rebaseIfNeeded(event.player, event.to)
        border.update(event.player, event.to)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        val playerId = event.player.uniqueId
        states.remove(playerId)
        joining.remove(playerId)?.cancel(false)
        border.remove(playerId)
        swapper.cancel(playerId)
        clearErrors(playerId)
    }

    private fun setNext(playerId: UUID, scalable: ScalableSpoofOffset, location: Location) {
        val offset = scalable.inWorld(location)
        val context = worldContext(location)
        states.computeIfPresent(playerId) { _, state ->
            if (state.currentOffset == offset) {
                state.copy(
                    currentScalable = scalable,
                    currentWorld = context,
                    lastRealX = location.x,
                    lastRealZ = location.z,
                    nextScalable = null,
                    nextOffset = null,
                    nextWorld = null
                )
            } else {
                state.copy(
                    lastRealX = location.x,
                    lastRealZ = location.z,
                    nextScalable = scalable,
                    nextOffset = offset,
                    nextWorld = context
                )
            }
        }
    }

    private fun rebaseIfNeeded(player: Player, location: Location) {
        val state = states[player.uniqueId] ?: return
        if (state.effectiveOffset.isZero || state.nextOffset != null) return

        val displayedX = location.x - state.currentOffset.x
        val displayedZ = location.z - state.currentOffset.z
        if (kotlin.math.abs(displayedX) < REBASE_DISPLAY_LIMIT &&
            kotlin.math.abs(displayedZ) < REBASE_DISPLAY_LIMIT
        ) return

        val scalable = generator.generate(player.uniqueId, location)
        val replacement = scalable.inWorld(location)
        if (replacement == state.currentOffset) return
        setNext(player.uniqueId, scalable, location)
        swapper.swap(player)
    }

    private fun safeForDestination(
        playerId: UUID,
        candidate: ScalableSpoofOffset,
        location: Location
    ): ScalableSpoofOffset {
        val fixed = candidate.inWorld(location)
        return if (isDisplayedPositionSafe(location, fixed)) candidate else generator.generate(playerId, location)
    }

    private fun isDisplayedPositionSafe(location: Location, offset: SpoofOffset): Boolean =
        isDisplayedPositionSafe(location.x, location.z, offset)

    private fun isDisplayedPositionSafe(realX: Double, realZ: Double, offset: SpoofOffset): Boolean =
        kotlin.math.abs(realX - offset.x) <= REBASE_DISPLAY_LIMIT &&
            kotlin.math.abs(realZ - offset.z) <= REBASE_DISPLAY_LIMIT

    private fun captureRespawnContext(playerId: UUID, event: PacketSendEvent) {
        val packet = WrapperPlayServerRespawn(event)
        val worldName = packet.worldName.orElse(null) ?: return
        val scale = packet.dimensionType.coordinateScale
        if (!scale.isFinite() || scale <= 0.0) return
        val context = WorldContext(worldName, scale)
        states.computeIfPresent(playerId) { _, state ->
            val scalable = state.effectiveScalable
            state.copy(nextScalable = scalable, nextOffset = scalable.atScale(scale), nextWorld = context)
        }
    }

    private fun promoteForPosition(player: Player, event: PacketSendEvent): PlayerState? {
        val packet = WrapperPlayServerPlayerPositionAndLook(event)
        var promoted: PlayerState? = null
        var refreshState: Pair<SpoofOffset, WorldContext>? = null
        states.computeIfPresent(player.uniqueId) { _, state ->
            val context = state.nextWorld ?: state.currentWorld
            val realX = if (packet.isRelativeFlag(RelativeFlag.X)) state.lastRealX + packet.x else packet.x
            val realZ = if (packet.isRelativeFlag(RelativeFlag.Z)) state.lastRealZ + packet.z else packet.z
            var scalable = state.effectiveScalable
            var offset = scalable.atScale(context.scale)
            if (!offset.isZero && !isDisplayedPositionSafe(realX, realZ, offset)) {
                scalable = generator.generate(player.uniqueId, context.name, context.scale, realX, realZ)
                offset = scalable.atScale(context.scale)
            }
            if (offset != state.currentOffset || context != state.currentWorld) {
                refreshState = offset to context
            }
            promoted = PlayerState(scalable, offset, context, realX, realZ)
            promoted
        }
        refreshState?.let { (expectedOffset, expectedWorld) ->
            player.scheduler.runDelayed(plugin, { _ ->
                val current = states[player.uniqueId]
                if (!player.isOnline || current?.currentOffset != expectedOffset || current.currentWorld != expectedWorld) {
                    return@runDelayed
                }
                swapper.refreshWithoutRespawn(player)
            }, null, 1L)
        }
        return promoted
    }

    private fun worldContext(location: Location): WorldContext {
        val world = requireNotNull(location.world)
        return WorldContext(world.name, world.coordinateScale)
    }

    private fun joiningState(playerId: UUID): PlayerState {
        states[playerId]?.let { return it }
        val future = joining.computeIfAbsent(playerId) { CompletableFuture() }
        states[playerId]?.let {
            future.complete(it)
            return it
        }
        return try {
            future.get(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            throw IllegalStateException("Timed out waiting for coordinate offset state for $playerId")
        } finally {
            joining.remove(playerId, future)
        }
    }

    private fun report(direction: String, player: Player, packetName: String, error: Throwable) {
        val key = ErrorKey(direction, player.uniqueId, packetName)
        val now = System.currentTimeMillis()
        val previous = lastErrors.put(key, now)
        if (previous != null && now - previous < ERROR_LOG_INTERVAL_MS) return
        plugin.logger.log(Level.SEVERE, "Failed to translate $direction packet $packetName for ${player.name}", error)
    }

    private fun clearErrors(playerId: UUID) {
        lastErrors.keys.removeIf { it.playerId == playerId }
    }

    private inner class PacketListener : PacketListenerAbstract(PacketListenerPriority.HIGH) {
        override fun onPacketSend(event: PacketSendEvent) {
            val player = event.getPlayer() as? Player ?: return
            val type = event.packetType
            if (type !is PacketType.Play.Server) return
            val relevant = CoordinatePacketTranslator.handlesOutgoing(type) || border.handles(type) ||
                type.name.startsWith("DEBUG")
            if (!relevant) return

            try {
                if (type == PacketType.Play.Server.RESPAWN) {
                    captureRespawnContext(player.uniqueId, event)
                }
                val state = when (type) {
                    PacketType.Play.Server.JOIN_GAME -> joiningState(player.uniqueId)
                    PacketType.Play.Server.PLAYER_POSITION_AND_LOOK -> promoteForPosition(player, event) ?: return
                    else -> states[player.uniqueId] ?: return
                }
                val offset = if (type == PacketType.Play.Server.RESPAWN) state.effectiveOffset else state.currentOffset
                if (offset.isZero) return
                if (type.name.startsWith("DEBUG")) {
                    event.isCancelled = true
                    return
                }
                if (border.handles(type)) {
                    border.translate(event, player.uniqueId, offset)
                } else {
                    CoordinatePacketTranslator.translateOutgoing(event, offset)
                }
            } catch (error: Exception) {
                if (type == PacketType.Play.Server.JOIN_GAME) event.isCancelled = true
                report("outgoing", player, type.name, error)
            }
        }

        override fun onPacketReceive(event: PacketReceiveEvent) {
            val player = event.getPlayer() as? Player ?: return
            val type = event.packetType
            if (type !is PacketType.Play.Client || !CoordinatePacketTranslator.handlesIncoming(type)) return
            val offset = states[player.uniqueId]?.currentOffset ?: return
            if (offset.isZero) return
            try {
                CoordinatePacketTranslator.translateIncoming(event, offset)
            } catch (error: Exception) {
                report("incoming", player, type.name, error)
            }
        }

        override fun onUserDisconnect(event: UserDisconnectEvent) {
            val playerId = event.user.uuid ?: return
            val online = Bukkit.getPlayer(playerId)
            if (online != null && PacketEvents.getAPI().playerManager.getUser(online) !== event.user) return
            states.remove(playerId)
            joining.remove(playerId)?.cancel(false)
            border.remove(playerId)
            swapper.cancel(playerId)
            clearErrors(playerId)
        }
    }

    private data class ErrorKey(val direction: String, val playerId: UUID, val packetName: String)

    private data class PlayerState(
        val currentScalable: ScalableSpoofOffset,
        val currentOffset: SpoofOffset,
        val currentWorld: WorldContext,
        val lastRealX: Double,
        val lastRealZ: Double,
        val nextScalable: ScalableSpoofOffset? = null,
        val nextOffset: SpoofOffset? = null,
        val nextWorld: WorldContext? = null
    ) {
        val effectiveScalable: ScalableSpoofOffset get() = nextScalable ?: currentScalable
        val effectiveOffset: SpoofOffset get() = nextOffset ?: currentOffset
    }

    private data class WorldContext(val name: String, val scale: Double)

    private object BedrockDetector {
        private val floodgateCheck by lazy {
            runCatching {
                val type = Class.forName("org.geysermc.floodgate.api.FloodgateApi")
                val instance = type.getMethod("getInstance").invoke(null)
                instance to type.getMethod("isFloodgatePlayer", UUID::class.java)
            }.getOrNull()
        }
        private val geyserCheck by lazy {
            runCatching {
                val type = Class.forName("org.geysermc.geyser.api.GeyserApi")
                val instance = type.getMethod("api").invoke(null)
                instance to type.getMethod("isBedrockPlayer", UUID::class.java)
            }.getOrNull()
        }

        fun isBedrock(player: Player): Boolean {
            if ('.' in player.name) return true
            floodgateCheck?.let { (instance, method) ->
                if (runCatching { method.invoke(instance, player.uniqueId) as Boolean }.getOrDefault(false)) return true
            }
            geyserCheck?.let { (instance, method) ->
                if (runCatching { method.invoke(instance, player.uniqueId) as Boolean }.getOrDefault(false)) return true
            }
            return false
        }
    }

    private companion object {
        val IGNORED_TELEPORT_CAUSES = setOf("DISMOUNT", "EXIT_BED", "UNKNOWN")
        const val REGENERATE_DISTANCE_SQUARED = 256.0 * 256.0
        const val REBASE_DISPLAY_LIMIT = 28_000_000.0
        const val JOIN_TIMEOUT_SECONDS = 5L
        const val ERROR_LOG_INTERVAL_MS = 2_500L
    }
}
