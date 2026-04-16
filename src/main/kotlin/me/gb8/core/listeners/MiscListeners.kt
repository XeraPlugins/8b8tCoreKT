/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import io.papermc.paper.event.block.BlockPreDispenseEvent
import me.gb8.core.antiillegal.AntiIllegalMain
import me.gb8.core.antiillegal.Utils.checkStand
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemFrame
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent

class MiscListeners(private val main: AntiIllegalMain) : Listener {

    @EventHandler
    fun onDispenser(event: BlockPreDispenseEvent) {
        main.checkFixItem(event.itemStack, event)
    }

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (event.isNewChunk) return
        val entities = event.chunk.entities
        for (entity in entities) {
            if (entity is ItemFrame) {
                main.checkFixItem(entity.item, null)
            } else if (entity is ArmorStand) {
                checkStand(entity, main)
            }
        }
    }
}
