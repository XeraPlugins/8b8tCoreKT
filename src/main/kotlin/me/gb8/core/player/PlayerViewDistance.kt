/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.player

import me.gb8.core.util.GlobalUtils.log
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class PlayerViewDistance(private val plugin: JavaPlugin) : Listener {
    @Volatile
    private var defaultDistance = 6

    init {
        reloadConfig()
    }

    fun reloadConfig() {
        defaultDistance = plugin.config.getInt("viewdistance.default", 6)
    }

    fun handlePlayerJoin(player: Player) {
        setRenderDistance(player)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        setRenderDistance(event.player)
    }

    private fun setRenderDistance(player: Player) {
        try {
            val renderDistance = calculateRenderDistance(player).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
            player.setSendViewDistance(renderDistance)
            player.setViewDistance(renderDistance)
        } catch (e: Exception) {
            log(Level.WARNING, "Failed to set view distance for ${player.name}: ${e.message}")
        }
    }

    private fun calculateRenderDistance(player: Player): Int {
        if (player.isOp) return MAX_DISTANCE

        var maxDistance = defaultDistance

        for ((permission, distance) in RANK_DISTANCES) {
            if (player.hasPermission(permission)) {
                maxDistance = maxOf(maxDistance, distance)
            }
        }

        for (permInfo in player.effectivePermissions) {
            if (!permInfo.value) continue

            val permission = permInfo.permission
            if (permission.startsWith("8b8tcore.viewdistance.")) {
                try {
                    val chunksStr = permission.substring("8b8tcore.viewdistance.".length)
                    val chunks = chunksStr.toInt().coerceIn(MIN_DISTANCE, MAX_DISTANCE)
                    maxDistance = maxOf(maxDistance, chunks)
                } catch (ignored: NumberFormatException) {
                }
            }
        }

        return maxDistance
    }

    companion object {
        private const val MIN_DISTANCE = 4
        private const val MAX_DISTANCE = 32

        private val RANK_DISTANCES = mapOf(
            "8b8tcore.prefix.donator1" to 9,
            "8b8tcore.prefix.donator2" to 11,
            "8b8tcore.prefix.donator3" to 13,
            "8b8tcore.prefix.donator4" to 15,
            "8b8tcore.prefix.donator5" to 17,
            "8b8tcore.prefix.donator6" to 19
        )
    }
}
