/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.Main
import net.kyori.adventure.text.Component
import org.bukkit.BanList
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerKickEvent

class KickListener(private val main: Main) : Listener {

    @EventHandler
    fun onPlayerKick(event: PlayerKickEvent) {
        if (!main.config.getBoolean("AnonymousKickMessages.Enabled", true)) return
        
        val message = main.config.getString("AnonymousKickMessages.Message", "You have been disconnected from the server")
            ?: "You have been disconnected from the server"
        
        event.reason(Component.text(message))
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onAsyncPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (!main.config.getBoolean("AnonymousKickMessages.Enabled", true)) return
        if (event.loginResult != AsyncPlayerPreLoginEvent.Result.ALLOWED) return
        
        val profile = event.playerProfile
        @Suppress("UNCHECKED_CAST")
        val banList = main.server.getBanList(BanList.Type.PROFILE) as? BanList<com.destroystokyo.paper.profile.PlayerProfile>
        
        if (banList != null && profile != null && banList.isBanned(profile)) {
            val message = main.config.getString("AnonymousKickMessages.Message", "You have been disconnected from the server")
                ?: "You have been disconnected from the server"
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, message)
        }
    }
}
