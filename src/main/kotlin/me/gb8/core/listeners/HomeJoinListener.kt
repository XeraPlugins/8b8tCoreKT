/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.home.HomeManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class HomeJoinListener(private val main: HomeManager) : Listener {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val storage = main.storage
        if (storage != null) {
            main.homes[uuid] = storage.load(uuid)
        }
    }

    @EventHandler
    fun onLeave(event: PlayerQuitEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val storage = main.storage
        val homeData = main.homes[uuid]
        if (storage != null && homeData != null) {
            storage.save(homeData, uuid)
        }
        main.homes.remove(uuid)
        main.main.lastLocations.remove(uuid)
    }
}
