/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.Main
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerTeleportEvent

class VanishVerifierListener(private val main: Main) : Listener {

    @EventHandler
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val player = event.player

        if (main.vanishedPlayers.contains(player.uniqueId)) {
            for (onlinePlayer in Bukkit.getOnlinePlayers()) {
                if (!main.canSeePlayer(onlinePlayer, player)) {
                    onlinePlayer.hidePlayer(main, player)
                }
            }
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        for (onlinePlayer in Bukkit.getOnlinePlayers()) {
            if (main.vanishedPlayers.contains(onlinePlayer.uniqueId)) {
                if (!main.canSeePlayer(player, onlinePlayer)) {
                    player.hidePlayer(main, onlinePlayer)
                }
            }
        }
    }
}
