/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.player

import me.gb8.core.util.FoliaCompat
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.permissions.PermissionAttachmentInfo
import org.bukkit.plugin.java.JavaPlugin

import java.util.logging.Level
import me.gb8.core.util.GlobalUtils.log

class PlayerSimulationDistance(private val plugin: JavaPlugin) : Listener {
    private var defaultDistance: Int = 0

    init {
        reloadConfig()
    }

    fun reloadConfig() {
        defaultDistance = plugin.config.getInt("simulationdistance.default", 4)
    }

    fun handlePlayerJoin(player: Player) {
        FoliaCompat.scheduleDelayed(player, plugin, {
            if (player.isOnline) {
                setSimulationDistance(player)
            }
        }, 10L)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        handlePlayerJoin(event.player)
    }

    private fun setSimulationDistance(player: Player) {
        val simulationDistance = calculateSimulationDistance(player)
        
        try {
            player.setSimulationDistance(simulationDistance)
            log(Level.INFO, "Simulation distance set to $simulationDistance chunks for player: ${player.name}")
        } catch (e: Exception) {
            log(Level.WARNING, "Failed to set simulation distance for ${player.name}: ${e.message}")
        }
    }

    private fun calculateSimulationDistance(player: Player): Int {
        var maxDistance = defaultDistance

        for (permInfo in player.effectivePermissions) {
            val permission = permInfo.permission
            if (permission.startsWith("8b8tcore.simulationdistance.")) {
                try {
                    val chunksStr = permission.substring("8b8tcore.simulationdistance.".length)
                    var chunks = chunksStr.toInt()
                    chunks = maxOf(2, minOf(32, chunks))
                    maxDistance = maxOf(maxDistance, chunks)
                } catch (ignored: NumberFormatException) {
                }
            }
        }

        return maxDistance
    }
}
