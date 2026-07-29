/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import me.gb8.core.Main
import me.gb8.core.Reloadable
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
import org.bukkit.persistence.PersistentDataType
import kotlin.math.absoluteValue
import java.util.Arrays
import java.util.EnumSet
import java.util.function.Consumer

class IllegalBlocksCleaner(private val plugin: Main, config: ConfigurationSection) : Listener, Reloadable {
    companion object {
        const val EXIT_PORTAL_X = 0
        const val EXIT_PORTAL_Z = 0
        const val EXIT_PORTAL_Y_MIN = 0
        const val EXIT_PORTAL_Y_MAX = 90
        const val EXIT_PORTAL_RADIUS = 5
        const val GATEWAY_RING_Y_MIN = 74
        const val GATEWAY_RING_Y_MAX = 76
        const val GATEWAY_RING_INNER_RADIUS_SQ = 7500
        const val GATEWAY_RING_OUTER_RADIUS_SQ = 11000
        const val OUTER_ISLAND_VOID_GAP_SQ = 562500
        const val OUTER_ISLAND_Y_MAX = 80
        const val STRONGHOLD_SAFE_ZONE_SQ = 1638400
        const val HASH_REVISION = 2

        private val Int.blockCoord get() = this shl 4
    }

    private data class Settings(
        val enabled: Boolean,
        val illegalMaterials: EnumSet<Material>,
        val batchSize: Int,
        val delayTicks: Long,
        val configHash: Int,
        val version: Int
    )

    @Volatile
    private var settings = readSettings(config)
    private val scanKey = org.bukkit.NamespacedKey(plugin, "clean_scan_hash")
    private val airBlockData = Material.AIR.createBlockData()

    init {
        plugin.logger.info("[AntiIllegal] System initialized. Version: ${settings.version}")
        if (settings.enabled) scanLoadedChunks(force = false)
    }

    override fun reloadConfig() {
        val config = plugin.config.getConfigurationSection("AntiIllegal") ?: return
        val oldSettings = settings
        val newSettings = readSettings(config)
        settings = newSettings

        if (newSettings.enabled && (!oldSettings.enabled || oldSettings.configHash != newSettings.configHash)) {
            // Blocks may have changed while the cleaner was disabled so we need to recheck here.
            scanLoadedChunks(force = !oldSettings.enabled)
        }
    }

    private fun scanLoadedChunks(force: Boolean) {
        Bukkit.getAsyncScheduler().runNow(plugin) {
            for (world in Bukkit.getWorlds()) {
                for (chunk in world.loadedChunks) {
                    val cx = chunk.x
                    val cz = chunk.z
                    val loc = Location(world, cx.blockCoord + 8.0, 64.0, cz.blockCoord + 8.0)
                    Bukkit.getRegionScheduler().run(plugin, loc) {
                        if (world.isChunkLoaded(cx, cz)) {
                            checkAndScan(world.getChunkAt(cx, cz), force)
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!settings.enabled) {
            invalidateChunk(event.chunk)
            return
        }
        checkAndScan(event.chunk)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        invalidateChunk(event.block.chunk)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onExplode(event: EntityExplodeEvent) {
        event.blockList().mapTo(HashSet()) { it.chunk }.forEach(::invalidateChunk)
    }

    private fun invalidateChunk(chunk: Chunk) {
        chunk.persistentDataContainer.remove(scanKey)
    }

    private fun checkAndScan(chunk: Chunk, force: Boolean = false) {
        val scanSettings = settings
        if (!scanSettings.enabled) return
        val savedHash = chunk.persistentDataContainer.get(scanKey, PersistentDataType.INTEGER)
        if (force || savedHash != scanSettings.configHash) performDeepScan(chunk, scanSettings)
    }

    private fun performDeepScan(chunk: Chunk, scanSettings: Settings) {
        val world = chunk.world
        val cx = chunk.x
        val cz = chunk.z

        if (cx.absoluteValue >= 1875000 || cz.absoluteValue >= 1875000) return

        val minY = world.minHeight
        val maxY = world.maxHeight

        world.getChunkAtAsync(cx, cz).thenAccept { chunkAccess ->
            val snap = chunkAccess.getChunkSnapshot(false, false, false)
            val env = world.environment

            var toRemove = IntArray(256)
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
                            if (type !in scanSettings.illegalMaterials) continue

                            val gx = cx.blockCoord + lx
                            val gz = cz.blockCoord + lz

                            if (isLegitimateBlock(env, gx, y, gz, type, minY)) continue

                            if (foundCount >= toRemove.size) {
                                toRemove = Arrays.copyOf(toRemove, toRemove.size * 2)
                            }
                            toRemove[foundCount] = pack(lx, y, lz)
                            foundCount++
                        }
                    }
                }
            }

            if (settings != scanSettings) {
                val anchor = Location(world, cx.blockCoord + 8.0, 64.0, cz.blockCoord + 8.0)
                Bukkit.getRegionScheduler().run(plugin, anchor) {
                    if (world.isChunkLoaded(cx, cz)) checkAndScan(world.getChunkAt(cx, cz))
                }
            } else if (foundCount == 0) {
                val anchor = Location(world, cx.blockCoord + 8.0, 64.0, cz.blockCoord + 8.0)
                Bukkit.getRegionScheduler().run(plugin, anchor) { markChunkClean(world, cx, cz, scanSettings) }
            } else {
                plugin.logger.info("[AntiIllegal] Detected $foundCount illegal blocks in chunk [$cx, $cz]. Liquidating...")

                val queue = toRemove
                val total = foundCount
                val anchor = Location(world, cx.blockCoord + 8.0, 64.0, cz.blockCoord + 8.0)

                Bukkit.getRegionScheduler().run(plugin, anchor) {
                    processRemovalBatch(world, cx, cz, queue, total, 0, anchor, 0, scanSettings)
                }
            }
        }
    }

    private fun processRemovalBatch(
        world: World,
        cx: Int,
        cz: Int,
        queue: IntArray,
        total: Int,
        index: Int,
        anchor: Location,
        failedRemovals: Int,
        scanSettings: Settings
    ) {
        if (!world.isChunkLoaded(cx, cz)) return
        if (settings != scanSettings) {
            if (settings.enabled) checkAndScan(world.getChunkAt(cx, cz))
            return
        }

        var processedInThisTick = 0
        val chunk = world.getChunkAt(cx, cz)
        var idx = index
        var failures = failedRemovals

        while (processedInThisTick < scanSettings.batchSize && idx < total) {
            val packed = queue[idx]
            val lx = unpackLX(packed)
            val lz = unpackLZ(packed)
            val y = unpackY(packed)

            val block = chunk.getBlock(lx, y, lz)
            if (block.type in scanSettings.illegalMaterials) {
                block.setBlockData(airBlockData, false)
                if (block.type in scanSettings.illegalMaterials) failures++
            }
            idx++
            processedInThisTick++
        }

        if (idx < total) {
            Bukkit.getRegionScheduler().runDelayed(plugin, anchor, Consumer { _ ->
                processRemovalBatch(world, cx, cz, queue, total, idx, anchor, failures, scanSettings)
            }, scanSettings.delayTicks)
        } else if (failures > 0) {
            plugin.logger.warning("[AntiIllegal] Failed to remove $failures illegal blocks in chunk [$cx, $cz]. Chunk left unmarked for future scan.")
        } else {
            markChunkClean(world, cx, cz, scanSettings)
        }
    }

    private fun markChunkClean(world: World, cx: Int, cz: Int, scanSettings: Settings) {
        if (!world.isChunkLoaded(cx, cz)) return
        if (settings != scanSettings) {
            if (settings.enabled) checkAndScan(world.getChunkAt(cx, cz))
            return
        }
        val c = world.getChunkAt(cx, cz)
        c.persistentDataContainer.set(scanKey, PersistentDataType.INTEGER, scanSettings.configHash)
    }

    private fun pack(lx: Int, y: Int, lz: Int): Int = (y shl 8) or (lx shl 4) or lz
    private fun unpackLX(p: Int): Int = (p shr 4) and 0xF
    private fun unpackLZ(p: Int): Int = p and 0xF
    private fun unpackY(p: Int): Int = p shr 8

    private fun isLegitimateBlock(env: World.Environment, x: Int, y: Int, z: Int, type: Material, minY: Int): Boolean {
        return when (type) {
            Material.BEDROCK, Material.END_GATEWAY -> {
                if (type == Material.BEDROCK) {
                    if (y < minY + 5) return true
                    if (env == World.Environment.NETHER && y in 123..127) return true
                }

                if (env == World.Environment.THE_END) {
                    if (type == Material.BEDROCK && y in 0..4) {
                        val distSq = x.toLong() * x + z.toLong() * z
                        if (distSq <= 2500) return true
                    }

                    if (x.absoluteValue <= EXIT_PORTAL_RADIUS &&
                        z.absoluteValue <= EXIT_PORTAL_RADIUS &&
                        y in EXIT_PORTAL_Y_MIN..EXIT_PORTAL_Y_MAX) return true

                    if (y in GATEWAY_RING_Y_MIN..GATEWAY_RING_Y_MAX) {
                        val distSq = x.toLong() * x + z.toLong() * z
                        return distSq >= GATEWAY_RING_INNER_RADIUS_SQ &&
                               distSq <= GATEWAY_RING_OUTER_RADIUS_SQ
                    }

                    if (type == Material.BEDROCK && y in (GATEWAY_RING_Y_MIN - 1)..(GATEWAY_RING_Y_MAX + 1)) {
                        val distSq = x.toLong() * x + z.toLong() * z
                        return distSq >= GATEWAY_RING_INNER_RADIUS_SQ &&
                               distSq <= GATEWAY_RING_OUTER_RADIUS_SQ
                    }

                    if (y in 0..OUTER_ISLAND_Y_MAX) {
                        val distSq = x.toLong() * x + z.toLong() * z
                        if (x.absoluteValue < 700 && z.absoluteValue < 700) return false
                        if (distSq >= OUTER_ISLAND_VOID_GAP_SQ) return true
                    }
                }
                false
            }
            Material.END_PORTAL, Material.END_PORTAL_FRAME -> {
                when (env) {
                    World.Environment.NORMAL -> {
                        (x.absoluteValue <= 2 && z.absoluteValue <= 2 && y == minY + 5) ||
                        (y in minY..40 && x.absoluteValue >= 1280 && z.absoluteValue >= 1280 && (x.toLong() * x + z.toLong() * z) >= STRONGHOLD_SAFE_ZONE_SQ)
                    }
                    World.Environment.THE_END -> x.absoluteValue <= 3 && z.absoluteValue <= 3 && y in 58..64
                    else -> false
                }
            }
            else -> false
        }
    }

    private fun buildMaterialSet(patterns: List<String>): EnumSet<Material> =
        patterns.flatMap { pat ->
            runCatching {
                val regex = "^${pat.replace("*", ".*")}$".toRegex(RegexOption.IGNORE_CASE)
                Material.entries.filter { regex.matches(it.name) }
            }.getOrElse { emptyList() }
        }.toCollection(EnumSet.noneOf(Material::class.java))

    private fun readSettings(config: ConfigurationSection): Settings {
        val illegalMaterials = buildMaterialSet(config.getStringList("IllegalBlocks"))
        val version = config.getInt("IllegalBlocksCleaner.Version", 1)
        return Settings(
            enabled = config.getBoolean("EnableIllegalBlocksCleaner", true),
            illegalMaterials = illegalMaterials,
            batchSize = config.getInt("IllegalBlocksCleaner.Batch", 128).coerceAtLeast(1),
            delayTicks = config.getLong("IllegalBlocksCleaner.DelayTicks", 5L).coerceAtLeast(1L),
            configHash = illegalMaterials.hashCode() xor (version * 31) xor HASH_REVISION,
            version = version
        )
    }
}
