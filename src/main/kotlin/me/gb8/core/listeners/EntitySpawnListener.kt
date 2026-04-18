/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import me.gb8.core.patch.PatchSection
import me.gb8.core.util.FoliaCompat
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

import java.util.HashMap
import java.util.concurrent.ConcurrentHashMap

class EntitySpawnListener(private val main: PatchSection) : Listener {
    
    private val cache: MutableMap<Long, MutableMap<EntityType, Int>> = ConcurrentHashMap()

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityAddToWorld(event: EntityAddToWorldEvent) {
        val entity = event.entity
        val type = entity.type
        
        val entityPerChunk = main.getEntityPerChunk() ?: return
        val max = entityPerChunk[type] ?: return

        val chunkKey = entity.location.chunk.chunkKey
        
        val chunkMap = cache.getOrPut(chunkKey) { HashMap() }
        val currentCount = chunkMap.getOrDefault(type, 0) + 1
        chunkMap[type] = currentCount

        if (currentCount > max) {
            chunkMap[type] = currentCount - 1
            
            FoliaCompat.schedule(entity, main.plugin) {
                if (entity.isValid) {
                    if (entity is org.bukkit.entity.Player) {
                        entity.kick(net.kyori.adventure.text.Component.text("Entity limit exceeded"))
                    } else {
                        entity.remove()
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onEntityRemove(event: EntityRemoveFromWorldEvent) {
        val type = event.entity.type
        val entityPerChunk = main.getEntityPerChunk()
        if (entityPerChunk == null || !entityPerChunk.containsKey(type)) return

        val chunkKey = event.entity.location.chunk.chunkKey
        val chunkMap = cache[chunkKey]
        
        chunkMap?.computeIfPresent(type) { _, count -> if (count > 1) count - 1 else null }
        
        if (chunkMap.isNullOrEmpty()) {
            cache.remove(chunkKey)
        }
    }
}
