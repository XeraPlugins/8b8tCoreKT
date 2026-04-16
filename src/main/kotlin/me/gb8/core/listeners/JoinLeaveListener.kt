/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.chat.ChatSection
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent

class JoinLeaveListener(private val manager: ChatSection) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        manager.registerPlayer(event.player)
    }

    @EventHandler
    fun onLeave(event: PlayerQuitEvent) {
        manager.removePlayer(event.player)
    }

    @EventHandler
    fun onKick(event: PlayerKickEvent) {
        manager.removePlayer(event.player)
    }
}
