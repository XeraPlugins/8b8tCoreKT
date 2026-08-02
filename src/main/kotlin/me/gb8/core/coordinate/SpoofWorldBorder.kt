/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 *
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.coordinate

import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerInitializeWorldBorder
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWorldBorderCenter
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWorldBorderSize
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayWorldBorderLerpSize
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class SpoofWorldBorder {
    private val snapshots = ConcurrentHashMap<UUID, BorderSnapshot>()

    fun handles(type: PacketTypeCommon): Boolean = type in PACKET_TYPES

    fun update(player: Player, location: Location, force: Boolean = false) {
        val targetWorld = requireNotNull(location.world)
        val border = if (player.world == targetWorld) {
            player.worldBorder ?: targetWorld.worldBorder
        } else {
            targetWorld.worldBorder
        }
        val snapshot = BorderSnapshot(
            visibleAt(player, location, border.center, border.size),
            border.center.x,
            border.center.z,
            border.size
        )
        val previous = snapshots.put(player.uniqueId, snapshot)
        if (!force && previous == snapshot) return
        if (player.isOnline && player.world == targetWorld) player.setWorldBorder(player.worldBorder)
    }

    fun remove(playerId: UUID) {
        snapshots.remove(playerId)
    }

    fun translate(event: PacketSendEvent, playerId: UUID, offset: SpoofOffset) {
        val snapshot = snapshots[playerId] ?: HIDDEN_BORDER
        val walls = snapshot.walls
        if (hasOpposingWalls(walls)) {
            translateCompleteBorder(event, offset)
            return
        }

        var centerX = 0.0
        var centerZ = 0.0
        if (walls.isNotEmpty()) {
            val radius = snapshot.size / 2.0
            if (Wall.X_POSITIVE in walls) centerX = snapshot.centerX + radius - BASELINE_RADIUS - offset.x
            if (Wall.X_NEGATIVE in walls) centerX = snapshot.centerX - radius + BASELINE_RADIUS - offset.x
            if (Wall.Z_POSITIVE in walls) centerZ = snapshot.centerZ + radius - BASELINE_RADIUS - offset.z
            if (Wall.Z_NEGATIVE in walls) centerZ = snapshot.centerZ - radius + BASELINE_RADIUS - offset.z
        }

        when (event.packetType) {
            PacketType.Play.Server.INITIALIZE_WORLD_BORDER -> WrapperPlayServerInitializeWorldBorder(event).run {
                x = centerX
                z = centerZ
                oldDiameter = BASELINE_SIZE
                newDiameter = BASELINE_SIZE
                portalTeleportBoundary = WORLD_LIMIT
            }
            PacketType.Play.Server.WORLD_BORDER_CENTER -> WrapperPlayServerWorldBorderCenter(event).run {
                x = centerX
                z = centerZ
            }
            PacketType.Play.Server.WORLD_BORDER_LERP_SIZE -> WrapperPlayWorldBorderLerpSize(event).run {
                oldDiameter = BASELINE_SIZE
                newDiameter = BASELINE_SIZE
            }
            PacketType.Play.Server.WORLD_BORDER_SIZE -> WrapperPlayServerWorldBorderSize(event).run {
                diameter = BASELINE_SIZE
            }
        }
    }

    private fun translateCompleteBorder(event: PacketSendEvent, offset: SpoofOffset) {
        when (event.packetType) {
            PacketType.Play.Server.INITIALIZE_WORLD_BORDER -> WrapperPlayServerInitializeWorldBorder(event).run {
                x -= offset.x
                z -= offset.z
                portalTeleportBoundary = WORLD_LIMIT
            }
            PacketType.Play.Server.WORLD_BORDER_CENTER -> WrapperPlayServerWorldBorderCenter(event).run {
                x -= offset.x
                z -= offset.z
            }
        }
    }

    private fun visibleAt(
        player: Player,
        location: Location,
        center: Location,
        size: Double
    ): EnumSet<Wall> {
        val world = requireNotNull(location.world)
        val distance = maxOf(player.viewDistance, player.sendViewDistance, world.viewDistance) * 16.0
        val radius = size / 2.0
        return EnumSet.noneOf(Wall::class.java).apply {
            if (center.x + radius - location.x < distance) add(Wall.X_POSITIVE)
            if (location.x - (center.x - radius) < distance) add(Wall.X_NEGATIVE)
            if (center.z + radius - location.z < distance) add(Wall.Z_POSITIVE)
            if (location.z - (center.z - radius) < distance) add(Wall.Z_NEGATIVE)
        }
    }

    private fun hasOpposingWalls(walls: EnumSet<Wall>): Boolean =
        (Wall.X_POSITIVE in walls && Wall.X_NEGATIVE in walls) ||
            (Wall.Z_POSITIVE in walls && Wall.Z_NEGATIVE in walls)

    private enum class Wall { X_POSITIVE, X_NEGATIVE, Z_POSITIVE, Z_NEGATIVE }

    private data class BorderSnapshot(
        val walls: EnumSet<Wall>,
        val centerX: Double,
        val centerZ: Double,
        val size: Double
    )

    private companion object {
        const val BASELINE_SIZE = 60_000_000.0
        const val BASELINE_RADIUS = BASELINE_SIZE / 2.0
        const val WORLD_LIMIT = 60_000_000
        val HIDDEN_BORDER = BorderSnapshot(EnumSet.noneOf(Wall::class.java), 0.0, 0.0, BASELINE_SIZE)

        val PACKET_TYPES: Set<PacketTypeCommon> = setOf(
            PacketType.Play.Server.INITIALIZE_WORLD_BORDER,
            PacketType.Play.Server.WORLD_BORDER_CENTER,
            PacketType.Play.Server.WORLD_BORDER_LERP_SIZE,
            PacketType.Play.Server.WORLD_BORDER_SIZE,
            PacketType.Play.Server.WORLD_BORDER_WARNING_DELAY,
            PacketType.Play.Server.WORLD_BORDER_WARNING_REACH
        )
    }
}
