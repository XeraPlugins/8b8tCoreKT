/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.tpa

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent

class LeaveListener(private val main: TPASection) : Listener {
    @EventHandler
    fun onLeave(event: PlayerQuitEvent) {
        main.cleanupPlayer(event.player.uniqueId)
    }

    @EventHandler
    fun onKick(event: PlayerKickEvent) {
        main.cleanupPlayer(event.player.uniqueId)
    }
}
