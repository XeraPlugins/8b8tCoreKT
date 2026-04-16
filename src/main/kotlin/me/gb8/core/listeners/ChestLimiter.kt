/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey
import me.gb8.core.Reloadable
import java.util.ArrayList
import java.util.HashMap
import java.util.Map

import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage

class ChestLimiter(private val plugin: JavaPlugin) : Listener, Reloadable {
    private var maxChestPerChunk = 192
    private val chestCountKey = NamespacedKey(plugin, "chest_count")

    init {
        reloadConfig()
    }

    override fun reloadConfig() {
        this.maxChestPerChunk = plugin.config.getInt("Patch.ChestLimitPerChunk", 192)
    }

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (event.isNewChunk) return
        val chunk = event.chunk
        if (!chunk.persistentDataContainer.has(chestCountKey, PersistentDataType.INTEGER)) {
            recalculateChestsAsync(chunk)
        }
    }

    private fun recalculateChestsAsync(chunk: Chunk) {
        val world = chunk.world
        val chunkX = chunk.x
        val chunkZ = chunk.z
        val chunkLoc = Location(world, (chunkX shl 4).toDouble(), 64.0, (chunkZ shl 4).toDouble())

        Bukkit.getRegionScheduler().run(plugin, chunkLoc) { _ ->
            if (!world.isChunkLoaded(chunkX, chunkZ)) return@run

            val currentChunk = world.getChunkAt(chunkX, chunkZ)
            var count = 0
            
            for (state in currentChunk.tileEntities) {
                if (isChest(state.type)) {
                    count++
                }
            }

            currentChunk.persistentDataContainer.set(chestCountKey, PersistentDataType.INTEGER, count)
        }
    }

    private fun isChest(type: Material): Boolean {
        return type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val block = event.blockPlaced
        if (!isChest(block.type)) return

        val chunk = block.chunk
        val pdc = chunk.persistentDataContainer
        val currentCount = pdc.getOrDefault(chestCountKey, PersistentDataType.INTEGER, 0)

        if (currentCount >= maxChestPerChunk) {
            event.isCancelled = true
            sendPrefixedLocalizedMessage(event.player, "chest_limit_reached")
            return
        }

        pdc.set(chestCountKey, PersistentDataType.INTEGER, currentCount + 1)
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        if (isChest(block.type)) {
            updateCount(block.chunk, -1)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBurn(event: BlockBurnEvent) {
        val block = event.block
        if (isChest(block.type)) {
            updateCount(block.chunk, -1)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        handleExplosion(event.blockList().toList())
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        handleExplosion(event.blockList().toList())
    }

    private fun handleExplosion(blocks: List<Block>) {
        val byChunk = HashMap<Long, MutableList<Block>>()

        for (block in blocks) {
            if (isChest(block.type)) {
                val key = getChunkKey(block.chunk)
                byChunk.getOrPut(key) { ArrayList() }.add(block)
            }
        }

        for ((_, chestBlocks) in byChunk) {
            if (chestBlocks.isEmpty()) continue

            val first = chestBlocks[0]
            val delta = -chestBlocks.size
            val loc = first.location

            Bukkit.getRegionScheduler().run(plugin, loc) { _ ->
                if (loc.world?.isChunkLoaded(loc.blockX shr 4, loc.blockZ shr 4) == true) {
                    updateCount(loc.chunk, delta)
                }
            }
        }
    }

    private fun getChunkKey(chunk: Chunk): Long {
        return ((chunk.x.toLong() shl 32) or (chunk.z.toLong() and 0xFFFFFFFFL))
    }

    private fun updateCount(chunk: Chunk, delta: Int) {
        val pdc = chunk.persistentDataContainer
        val current = pdc.getOrDefault(chestCountKey, PersistentDataType.INTEGER, -1)
        if (current == -1) {
            recalculateChestsAsync(chunk)
        } else {
            pdc.set(chestCountKey, PersistentDataType.INTEGER, maxOf(0, current + delta))
        }
    }
}
