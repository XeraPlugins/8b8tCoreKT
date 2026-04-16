/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import me.gb8.core.Main
import me.gb8.core.antiillegal.IllegalConstants
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.EnumSet
import kotlin.math.abs
import java.util.regex.Pattern
import java.util.function.Consumer
import java.util.concurrent.CompletableFuture

class IllegalBlocksCleaner(private val plugin: Main, config: ConfigurationSection) : Listener {
    private val illegalMaterials: EnumSet<Material>
    private val batchSize: Int
    private val delayTicks: Long
    private val scanKey = org.bukkit.NamespacedKey(plugin, "clean_scan_hash")
    private val configHash: Int

    init {
        illegalMaterials = buildMaterialSet(config.getStringList("IllegalBlocks"))
        batchSize = maxOf(1, config.getInt("IllegalBlocksCleaner.Batch", 128))
        delayTicks = maxOf(1L, config.getLong("IllegalBlocksCleaner.DelayTicks", 5L))

        val version = config.getInt("IllegalBlocksCleaner.Version", 1)
        configHash = illegalMaterials.hashCode() xor (version * 31)

        plugin.logger.info("[AntiIllegal] System initialized. Version: $version")
        Bukkit.getAsyncScheduler().runNow(plugin, Consumer {
            for (world in Bukkit.getWorlds()) {
                for (chunk in world.loadedChunks) {
                    val cx = chunk.x
                    val cz = chunk.z
                    val loc = Location(world, (cx shl 4) + 8.0, 64.0, (cz shl 4) + 8.0)
                    Bukkit.getRegionScheduler().run(plugin, loc, Consumer {
                        if (world.isChunkLoaded(cx, cz)) {
                            checkAndScan(world.getChunkAt(cx, cz))
                        }
                    })
                }
            }
        })
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onChunkLoad(event: ChunkLoadEvent) {
        checkAndScan(event.chunk)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        invalidateChunk(event.block.chunk)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onExplode(event: EntityExplodeEvent) {
        val affectedChunks = HashSet<Chunk>()
        for (b in event.blockList()) affectedChunks.add(b.chunk)
        for (c in affectedChunks) invalidateChunk(c)
    }

    private fun invalidateChunk(chunk: Chunk) {
        chunk.persistentDataContainer.remove(scanKey)
    }

    private fun checkAndScan(chunk: Chunk) {
        val pdc = chunk.persistentDataContainer
        val storedHash = pdc.get(scanKey, PersistentDataType.INTEGER)

        if (storedHash != null && storedHash == configHash) return

        performDeepScan(chunk)
    }

    private fun performDeepScan(chunk: Chunk) {
        val world = chunk.world
        val cx = chunk.x
        val cz = chunk.z

        if (abs(cx) >= 1875000 || abs(cz) >= 1875000) return

        val minY = world.minHeight
        val maxY = world.maxHeight

        world.getChunkAtAsync(cx, cz).thenAccept { chunkAccess ->
            val snap = chunkAccess.getChunkSnapshot(false, false, false)
            val env = world.environment

            val toRemove = IntArray(256)
            var foundCount = 0

            val minSection = minY shr 4
            val maxSection = (maxY - 1) shr 4

            for (sectionY in minSection..maxSection) {
                if (snap.isSectionEmpty(sectionY - minSection)) continue

                val startY = maxOf(minY, sectionY shl 4)
                val endY = minOf(maxY - 1, (sectionY shl 4) + 15)

                for (y in startY..endY) {
                    for (lx in 0..15) {
                        for (lz in 0..15) {
                            val type = snap.getBlockType(lx, y, lz)
                            if (!illegalMaterials.contains(type)) continue

                            val gx = (cx shl 4) + lx
                            val gz = (cz shl 4) + lz

                            if (isLegitimateBlock(env, gx, y, gz, type, minY)) continue

                            if (foundCount >= toRemove.size) continue
                            toRemove[foundCount] = pack(lx, y, lz)
                            foundCount++
                        }
                    }
                }
            }

            if (foundCount == 0) {
                val anchor = Location(world, (cx shl 4) + 8.0, 64.0, (cz shl 4) + 8.0)
                Bukkit.getRegionScheduler().run(plugin, anchor, Consumer { markChunkClean(world, cx, cz) })
            } else {
                plugin.logger.info("[AntiIllegal] Detected $foundCount illegal blocks in chunk [$cx, $cz]. Liquidating...")

                val queue = toRemove
                val total = foundCount
                val anchor = Location(world, (cx shl 4) + 8.0, 64.0, (cz shl 4) + 8.0)

                Bukkit.getRegionScheduler().run(plugin, anchor, Consumer {
                    processRemovalBatch(world, cx, cz, queue, total, 0, anchor)
                })
            }
        }
    }

    private fun processRemovalBatch(world: World, cx: Int, cz: Int, queue: IntArray, total: Int, index: Int, anchor: Location) {
        if (!world.isChunkLoaded(cx, cz)) return

        var processedInThisTick = 0
        val baseX = cx shl 4
        val baseZ = cz shl 4
        var idx = index

        while (processedInThisTick < batchSize && idx < total) {
            val packed = queue[idx]
            val x = baseX + unpackLX(packed)
            val z = baseZ + unpackLZ(packed)
            val y = unpackY(packed)

            val b = world.getBlockAt(x, y, z)
            if (illegalMaterials.contains(b.type)) {
                b.setType(Material.AIR, false)
            }
            idx++
            processedInThisTick++
        }

        if (idx < total) {
            Bukkit.getRegionScheduler().runDelayed(plugin, anchor, Consumer {
                processRemovalBatch(world, cx, cz, queue, total, idx, anchor)
            }, delayTicks)
        } else {
            markChunkClean(world, cx, cz)
        }
    }

    private fun markChunkClean(world: World, cx: Int, cz: Int) {
        val c = world.getChunkAt(cx, cz)
        c.persistentDataContainer.set(scanKey, PersistentDataType.INTEGER, configHash)
    }

    private fun pack(lx: Int, y: Int, lz: Int): Int = (y shl 8) or (lx shl 4) or lz
    private fun unpackLX(p: Int): Int = (p shr 4) and 0xF
    private fun unpackLZ(p: Int): Int = p and 0xF
    private fun unpackY(p: Int): Int = p shr 8

    private fun isLegitimateBlock(env: World.Environment, x: Int, y: Int, z: Int, type: Material, minY: Int): Boolean {
        if (type == Material.BEDROCK || type == Material.END_GATEWAY) {
            if (type == Material.BEDROCK) {
                if (y < minY + 5) return true
                if (env == World.Environment.NETHER && y >= 123 && y <= 127) return true
            }

            if (env == World.Environment.THE_END) {
                if (type == Material.BEDROCK && y >= 0 && y <= 4) {
                    val distSq = x.toLong() * x + z.toLong() * z
                    if (distSq <= 2500) return true
                }

                if (abs(x) <= IllegalConstants.EXIT_PORTAL_RADIUS &&
                    abs(z) <= IllegalConstants.EXIT_PORTAL_RADIUS &&
                    y >= IllegalConstants.EXIT_PORTAL_Y_MIN &&
                    y <= IllegalConstants.EXIT_PORTAL_Y_MAX) return true

                if (y >= IllegalConstants.GATEWAY_RING_Y_MIN && y <= IllegalConstants.GATEWAY_RING_Y_MAX) {
                    val distSq = x.toLong() * x + z.toLong() * z
                    return distSq >= IllegalConstants.GATEWAY_RING_INNER_RADIUS_SQ &&
                           distSq <= IllegalConstants.GATEWAY_RING_OUTER_RADIUS_SQ
                }

                if (y >= 0 && y <= IllegalConstants.OUTER_ISLAND_Y_MAX) {
                    val distSq = x.toLong() * x + z.toLong() * z
                    if (abs(x) < 700 && abs(z) < 700) return false
                    if (distSq >= IllegalConstants.OUTER_ISLAND_VOID_GAP_SQ) return true
                }
            }
            return false
        }

        if (type == Material.END_PORTAL || type == Material.END_PORTAL_FRAME) {
            if (env == World.Environment.NORMAL) {
                if (abs(x) <= 2 && abs(z) <= 2 && y == minY + 5) return true
                if (y < minY || y > 40) return false
                if (abs(x) < 1280 && abs(z) < 1280) return false
                val distanceSq = x.toLong() * x + z.toLong() * z
                return distanceSq >= IllegalConstants.STRONGHOLD_SAFE_ZONE_SQ
            }
            if (env == World.Environment.THE_END) {
                return abs(x) <= 3 && abs(z) <= 3 && y >= 58 && y <= 64
            }
        }
        return false
    }

    private fun buildMaterialSet(patterns: List<String>): EnumSet<Material> {
        val set = EnumSet.noneOf(Material::class.java)
        for (pat in patterns) {
            try {
                val p = Pattern.compile("^" + pat.replace("*", ".*") + "$", Pattern.CASE_INSENSITIVE)
                for (m in Material.entries) {
                    if (p.matcher(m.name).matches()) set.add(m)
                }
            } catch (e: Exception) {}
        }
        return set
    }
}
