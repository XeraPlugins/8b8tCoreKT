/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.patch.workers

import me.gb8.core.patch.PatchSection
import me.gb8.core.util.FoliaCompat
import me.gb8.core.util.GlobalUtils.removeElytra
import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ElytraWorker(private val main: PatchSection) : Runnable {
    private val positions = ConcurrentHashMap<UUID, Location>()

    
    override fun run() {
        val mainConfig = main.getConfig() ?: return
        val MAX_SPEED_DEFAULT = mainConfig.getDouble("DefaultElytraMaxSpeedBlocksPerSecond", 128.0)
        val MAX_SPEED_WITH_ENTITIES = mainConfig.getDouble("TileEntitiesElytraMaxSpeedBlocksPerSecond", 64.0)

        for (player in Bukkit.getOnlinePlayers()) {
            val plugin = main.plugin
            FoliaCompat.schedule(player, plugin) {
                if (!player.isOnline) return@schedule

                val uuid = player.uniqueId
                if (!player.isGliding) {
                    positions.remove(uuid)
                    return@schedule
                }

                val currentLoc = player.location
                val from = positions[uuid]

                if (from == null || from.world != currentLoc.world) {
                    positions[uuid] = currentLoc.clone()
                    return@schedule
                }

                val speed = calcSpeed(from, currentLoc)
                positions[uuid] = currentLoc.clone()

                if (speed > MAX_SPEED_WITH_ENTITIES) {
                    if (currentLoc.chunk.tileEntities.size > 128) {
                        handleViolation(player, "elytra_tile_entities_too_fast", MAX_SPEED_WITH_ENTITIES)
                        return@schedule
                    }
                }

                if (speed > MAX_SPEED_DEFAULT) {
                    handleViolation(player, "elytra_too_fast", MAX_SPEED_DEFAULT)
                }
            }
        }
    }

    private fun handleViolation(player: Player, messageKey: String, speed: Double) {
        removeElytra(player)
        sendPrefixedLocalizedMessage(player, messageKey, speed)
    }

    private fun calcSpeed(from: Location, to: Location): Double {
        return Math.hypot(to.x - from.x, to.z - from.z)
    }
}
