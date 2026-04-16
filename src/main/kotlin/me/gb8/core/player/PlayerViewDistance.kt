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
import org.bukkit.permissions.PermissionAttachmentInfo
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class PlayerViewDistance(private val plugin: JavaPlugin) : Listener {
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
            val renderDistance = maxOf(4, minOf(32, calculateRenderDistance(player)))
            val simulationDistance = maxOf(2, minOf(renderDistance, calculateSimulationDistance(player)))
            
            player.setSimulationDistance(simulationDistance)
            player.setSendViewDistance(renderDistance)
            player.setViewDistance(renderDistance)
        } catch (e: Exception) {
            log(Level.WARNING, "Failed to set view distance for ${player.name}: ${e.message}")
        }
    }

    private fun calculateRenderDistance(player: Player): Int {
        var maxDistance = defaultDistance

        for (permInfo in player.effectivePermissions) {
            val permission = permInfo.permission
            if (permission.startsWith("8b8tcore.viewdistance.")) {
                try {
                    val chunksStr = permission.substring("8b8tcore.viewdistance.".length)
                    var chunks = chunksStr.toInt()
                    chunks = maxOf(4, minOf(32, chunks))
                    maxDistance = maxOf(maxDistance, chunks)
                } catch (ignored: NumberFormatException) {
                }
            }
        }

        return maxDistance
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
