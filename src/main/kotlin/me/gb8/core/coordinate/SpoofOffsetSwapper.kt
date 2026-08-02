/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 *
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.coordinate

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.EntityPositionData
import com.github.retrooper.packetevents.protocol.player.User
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUnloadChunk
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateViewPosition
import me.gb8.core.Main
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.World
import org.bukkit.entity.Player
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class SpoofOffsetSwapper(
    private val plugin: Main,
    private val border: SpoofWorldBorder
) {
    private val jobs = ConcurrentHashMap<UUID, RefreshJob>()
    private val lastFailureTick = AtomicInteger(Int.MIN_VALUE)

    fun swap(player: Player) {
        val chunks = sentChunksClosestFirst(player)
        for (chunk in chunks.asReversed()) {
            PacketEvents.getAPI().playerManager.sendPacket(
                player,
                WrapperPlayServerUnloadChunk(chunk.x, chunk.z)
            )
        }

        player.setPlayerProfile(player.playerProfile)
        border.update(player, player.location, force = true)
        PacketEvents.getAPI().playerManager.sendPacket(
            player,
            WrapperPlayServerUpdateViewPosition(player.chunk.x, player.chunk.z)
        )
        refresh(player, chunks)
    }

    fun unloadSentChunks(player: Player): List<Chunk> {
        val chunks = sentChunksClosestFirst(player)
        chunks.asReversed().forEach {
            PacketEvents.getAPI().playerManager.sendPacket(player, WrapperPlayServerUnloadChunk(it.x, it.z))
        }
        return chunks
    }

    fun refreshWithoutRespawn(player: Player) {
        val chunks = sentChunksClosestFirst(player)
        border.update(player, player.location, force = true)
        PacketEvents.getAPI().playerManager.sendPacket(
            player,
            WrapperPlayServerUpdateViewPosition(player.chunk.x, player.chunk.z)
        )
        refresh(player, chunks)
    }

    fun refresh(player: Player, chunks: List<Chunk>) {
        val queue = ConcurrentLinkedQueue(chunks.map { ChunkRef(it.x, it.z) })
        val user = PacketEvents.getAPI().playerManager.getUser(player) ?: return
        val job = RefreshJob(player.uniqueId, player.world.uid, queue, user)
        jobs.put(player.uniqueId, job)?.cancelled?.set(true)
        scheduleBatch(player, player.world, job, 1L)
    }

    fun cancel(playerId: UUID) {
        jobs.remove(playerId)?.cancelled?.set(true)
    }

    fun cancelAll() {
        jobs.values.forEach { it.cancelled.set(true) }
        jobs.clear()
    }

    private fun sentChunksClosestFirst(player: Player): List<Chunk> {
        val centerX = player.chunk.x
        val centerZ = player.chunk.z
        return player.sentChunks.sortedBy {
            val dx = it.x - centerX
            val dz = it.z - centerZ
            dx * dx + dz * dz
        }
    }

    private fun scheduleBatch(player: Player, world: World, job: RefreshJob, delay: Long) {
        player.scheduler.runDelayed(plugin, { _ ->
            if (!isCurrent(player, job)) {
                finish(job)
                return@runDelayed
            }

            repeat(CHUNKS_PER_TICK) {
                val chunk = job.chunks.poll() ?: return@repeat
                if (!player.isChunkSent(Chunk.getChunkKey(chunk.x, chunk.z))) return@repeat

                job.pending.incrementAndGet()
                Bukkit.getRegionScheduler().execute(plugin, world, chunk.x, chunk.z) {
                    try {
                        if (jobs[job.playerId] === job && !job.cancelled.get()) {
                            refreshChunkAndEntities(player, world, chunk, job.user)
                        }
                    } finally {
                        if (job.pending.decrementAndGet() == 0 && job.dispatched.get()) finish(job)
                    }
                }
            }

            if (job.chunks.isEmpty()) {
                job.dispatched.set(true)
                if (job.pending.get() == 0) finish(job)
            } else {
                scheduleBatch(player, world, job, 1L)
            }
        }, { finish(job) }, delay.coerceAtLeast(1L))
    }

    private fun isCurrent(player: Player, job: RefreshJob): Boolean =
        jobs[job.playerId] === job && !job.cancelled.get() && player.isOnline && player.world.uid == job.worldId

    private fun refreshChunkAndEntities(player: Player, world: World, ref: ChunkRef, user: User) {
        if (!world.isChunkLoaded(ref.x, ref.z)) return
        val chunk = world.getChunkAt(ref.x, ref.z)
        try {
            ChunkRefreshBridge.refresh(player, chunk)
        } catch (error: ReflectiveOperationException) {
            logRefreshFailure(player, chunk, error)
            world.refreshChunk(ref.x, ref.z)
        } catch (error: LinkageError) {
            logRefreshFailure(player, chunk, error)
            world.refreshChunk(ref.x, ref.z)
        }

        for (entity in chunk.entities) {
            if (player !in entity.trackedBy) continue
            val location = entity.location
            val velocity = entity.velocity
            user.sendPacket(
                WrapperPlayServerEntityTeleport(
                    entity.entityId,
                    EntityPositionData(
                        Vector3d(location.x, location.y, location.z),
                        Vector3d(velocity.x, velocity.y, velocity.z),
                        location.yaw,
                        location.pitch
                    ),
                    RelativeFlag.NONE,
                    entity.isOnGround
                )
            )
        }
    }

    private fun logRefreshFailure(player: Player, chunk: Chunk, error: Throwable) {
        val tick = Bukkit.getCurrentTick()
        val previous = lastFailureTick.get()
        if (tick - previous <= 10 || !lastFailureTick.compareAndSet(previous, tick)) return
        plugin.logger.log(
            java.util.logging.Level.WARNING,
            "Per-player chunk refresh failed for ${player.name} at ${chunk.x},${chunk.z}; using world refresh",
            error
        )
    }

    private fun finish(job: RefreshJob) {
        job.cancelled.set(true)
        jobs.remove(job.playerId, job)
    }

    private data class ChunkRef(val x: Int, val z: Int)

    private data class RefreshJob(
        val playerId: UUID,
        val worldId: UUID,
        val chunks: ConcurrentLinkedQueue<ChunkRef>,
        val user: User,
        val pending: AtomicInteger = AtomicInteger(),
        val dispatched: AtomicBoolean = AtomicBoolean(),
        val cancelled: AtomicBoolean = AtomicBoolean()
    )

    private object ChunkRefreshBridge {
        private val craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer")
        private val craftChunk = Class.forName("org.bukkit.craftbukkit.CraftChunk")
        private val chunkStatus = Class.forName("net.minecraft.world.level.chunk.status.ChunkStatus")
        private val getPlayerHandle: Method = craftPlayer.getMethod("getHandle")
        private val getChunkHandle: Method = craftChunk.getMethod("getHandle", chunkStatus)
        private val fullStatus = chunkStatus.getField("FULL").get(null)
        private val refreshMethod: Method = Class.forName("io.papermc.paper.FeatureHooks").methods.first {
            it.name == "sendChunkRefreshPackets" && it.parameterCount == 2
        }

        fun refresh(player: Player, chunk: Chunk) {
            val serverPlayer = getPlayerHandle.invoke(player)
            val levelChunk = getChunkHandle.invoke(chunk, fullStatus)
            refreshMethod.invoke(null, listOf(serverPlayer), levelChunk)
        }
    }

    private companion object {
        const val CHUNKS_PER_TICK = 16
    }
}
