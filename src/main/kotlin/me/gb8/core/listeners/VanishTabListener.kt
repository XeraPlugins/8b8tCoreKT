/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.Main
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.TabCompleteEvent

class VanishTabListener(private val main: Main) : Listener {

    @EventHandler
    fun onTabComplete(event: TabCompleteEvent) {
        val sender = event.sender as? Player ?: return
        val completions = ArrayList(event.completions)

        val iterator = completions.iterator()
        while (iterator.hasNext()) {
            val name = iterator.next()
            val target = main.server.getPlayerExact(name)

            if (target != null && main.vanishedPlayers.contains(target.uniqueId)) {
                if (!sender.hasPermission("*") && !sender.isOp) {
                    iterator.remove()
                }
            }
        }
        event.completions = completions
    }
}
