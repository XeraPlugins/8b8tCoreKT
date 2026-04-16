/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.tablist.TabSection
import me.gb8.core.util.FoliaCompat
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class TablistPlayerJoinListener(private val tabSection: TabSection) : Listener {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val plugin = tabSection.main
        FoliaCompat.scheduleDelayed(player, plugin, Runnable {
            if (player.isOnline) tabSection.setTab(player)
        }, 5L)
    }
}
