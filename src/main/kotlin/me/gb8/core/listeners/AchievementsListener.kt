/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.Main
import me.gb8.core.database.GeneralDatabase
import me.gb8.core.util.FoliaCompat
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerAdvancementDoneEvent

class AchievementsListener : Listener {
    private val database = GeneralDatabase.getInstance()

    @EventHandler
    fun onAdvancement(event: PlayerAdvancementDoneEvent) {
        val msg = event.message() ?: return
        event.message(null)

        Bukkit.getGlobalRegionScheduler().runDelayed(Main.instance, {
            for (p in Bukkit.getOnlinePlayers()) {
                database.getPlayerHideBadgesAsync(p.name).thenAccept { hideBadges ->
                    if (hideBadges || !p.isOnline) return@thenAccept
                    FoliaCompat.schedule(p, Main.instance) {
                        if (p.isOnline) p.sendMessage(msg)
                    }
                }
            }
        }, 1L)
    }
}
