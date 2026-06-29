/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.patch.PatchSection
import me.gb8.core.util.FoliaCompat
import me.gb8.core.util.GlobalUtils.log
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.entity.EntityType
import org.bukkit.entity.Item
import java.util.logging.Level

@Suppress("UNUSED_VARIABLE", "LABEL_NAME_CLASH")
class EntityCheckTask(private val main: PatchSection) : Runnable {
    private val vanillaItemDespawnTicks = 6000
    
    override fun run() {
        try {
            for (world in Bukkit.getWorlds()) {
                val loadedChunks = world.loadedChunks
                
                for (chunk in loadedChunks) {
                    val chunkX = chunk.x
                    val chunkZ = chunk.z
                    
                    val chunkLoc = Location(world, (chunkX shl 4).toDouble(), 64.0, (chunkZ shl 4).toDouble())
                    val mainPlugin = main.plugin
                    val currentWorld = world
                    
                    mainPlugin.server.regionScheduler.run(mainPlugin, chunkLoc) scheduler@{
                        if (!currentWorld.isChunkLoaded(chunkX, chunkZ)) return@scheduler
                        val currentChunk = currentWorld.getChunkAt(chunkX, chunkZ)
                        
                        val chunkEntities = currentChunk.entities
                        if (chunkEntities.isEmpty()) return@scheduler

                        main.getEntityPerChunk()?.forEach { (entityType, maxAllowed) ->
                            val matchingEntities = chunkEntities
                                .filter { it.type == entityType && it.isValid }

                            val excessCount = matchingEntities.size - maxAllowed
                            if (excessCount > 0) {
                                val removalCandidates = if (entityType == EntityType.ITEM) {
                                    val minAgeTicks = main.getConfig()
                                        ?.getInt("EntityPerChunk.DroppedItemMinAgeTicks", vanillaItemDespawnTicks)
                                        ?: vanillaItemDespawnTicks

                                    matchingEntities
                                        .filterIsInstance<Item>()
                                        .filter { it.ticksLived >= minAgeTicks }
                                        .sortedByDescending { it.ticksLived }
                                } else {
                                    matchingEntities
                                }

                                for (entityToRemove in removalCandidates.take(excessCount)) {
                                    FoliaCompat.schedule(entityToRemove, mainPlugin) {
                                        if (entityToRemove.isValid) {
                                            entityToRemove.remove()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            log(Level.SEVERE, "An error occurred while checking entities: %s", ex.message)
            ex.printStackTrace()
        }
    }
}
