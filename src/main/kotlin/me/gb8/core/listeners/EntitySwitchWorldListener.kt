/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.Main

import me.gb8.core.util.FoliaCompat
import me.gb8.core.util.GlobalUtils
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPortalEnterEvent
import java.util.Arrays

class EntitySwitchWorldListener(private val main: Main) : Listener {
    private val blacklist = EntityType.entries.filter { 
        it.name.contains("BOAT") || it.name.contains("MINECART") 
    }.toMutableSet().apply {
        add(EntityType.ITEM)
    }

    @EventHandler
    fun onEntitySwitchWorld(event: EntityPortalEnterEvent) {
        val entity = event.entity
        if (blacklist.contains(entity.type)) {
            FoliaCompat.schedule(entity, main) { entity.remove() }
        }
    }
}
