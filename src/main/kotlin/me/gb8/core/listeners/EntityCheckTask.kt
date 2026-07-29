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
import org.bukkit.entity.EntityType
import java.util.EnumMap
import java.util.logging.Level

@Suppress("UNUSED_VARIABLE", "LABEL_NAME_CLASH")
class EntityCheckTask(private val main: PatchSection) : Runnable {
    override fun run() {
        try {
            val limits = main.getEntityPerChunk()?.toMap() ?: return
            if (limits.isEmpty()) return
            var scheduledChunks = 0

            for (world in Bukkit.getWorlds()) {
                val loadedChunks = world.loadedChunks
                
                for (chunk in loadedChunks) {
                    if (!chunk.isEntitiesLoaded) continue

                    val chunkX = chunk.x
                    val chunkZ = chunk.z
                    val mainPlugin = main.plugin
                    val currentWorld = world
                    val delayTicks = 1L + scheduledChunks / CHUNKS_PER_TICK
                    scheduledChunks++

                    mainPlugin.server.regionScheduler.runDelayed(
                        mainPlugin,
                        currentWorld,
                        chunkX,
                        chunkZ,
                        scheduler@{
                            if (!currentWorld.isChunkLoaded(chunkX, chunkZ)) return@scheduler
                            val currentChunk = currentWorld.getChunkAt(chunkX, chunkZ)

                            val chunkEntities = currentChunk.entities
                            if (chunkEntities.isEmpty()) return@scheduler

                            val counts = EnumMap<EntityType, Int>(EntityType::class.java)
                            for (entity in chunkEntities) {
                                if (!entity.isValid || !entity.isSubjectToEntityCleanup()) continue

                                val type = entity.type
                                val maxAllowed = limits[type] ?: continue
                                val count = counts.getOrDefault(type, 0)

                                if (count >= maxAllowed) {
                                    FoliaCompat.schedule(entity, mainPlugin) {
                                        if (entity.isValid) {
                                            entity.remove()
                                        }
                                    }
                                } else {
                                    counts[type] = count + 1
                                }
                            }
                        }, delayTicks)
                }
            }
        } catch (ex: Exception) {
            log(Level.SEVERE, "An error occurred while checking entities: %s", ex.message)
            ex.printStackTrace()
        }
    }

    companion object {
        private const val CHUNKS_PER_TICK = 128
    }
}
