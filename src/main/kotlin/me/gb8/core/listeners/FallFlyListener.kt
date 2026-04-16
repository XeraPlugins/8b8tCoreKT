/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.Main
import me.gb8.core.ViolationManager
import me.gb8.core.util.GlobalUtils
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityToggleGlideEvent

class FallFlyListener(main: Main) : ViolationManager(addAmount = 1, plugin = main), Listener {

    @EventHandler
    fun onGlide(event: EntityToggleGlideEvent) {
        val player = event.entity as? Player ?: return
        val vls = getVLS(player.uniqueId)
        increment(player.uniqueId)
        if (vls > 10) {
            GlobalUtils.removeElytra(player)
            remove(player.uniqueId)
        }
    }
}
