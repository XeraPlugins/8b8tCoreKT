/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.patch

import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.data.type.EndPortalFrame
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import java.util.concurrent.CompletableFuture

class EndPortalBuilder(private val plugin: JavaPlugin) : Runnable {

    override fun run() {
        val world = Bukkit.getWorlds().firstOrNull { it.environment == World.Environment.NORMAL }
            ?: return

        val x = 0
        val y = world.minHeight + 5
        val z = 0

        val chunksToLoad = mutableSetOf<ChunkCoord>()

        val offsets = listOf(
            listOf(-1, -2), listOf(0, -2), listOf(1, -2),
            listOf(-2, -1), listOf(2, -1),
            listOf(-2, 0), listOf(2, 0),
            listOf(-2, 1), listOf(2, 1),
            listOf(-1, 2), listOf(0, 2), listOf(1, 2)
        )

        for (offset in offsets) {
            val blockX = x + offset[0]
            val blockZ = z + offset[1]
            chunksToLoad.add(ChunkCoord(blockX shr 4, blockZ shr 4))
        }

        for (dx in -1..1) {
            for (dz in -1..1) {
                chunksToLoad.add(ChunkCoord((x + dx) shr 4, (z + dz) shr 4))
            }
        }

        val futures = mutableListOf<CompletableFuture<Chunk>>()
        for (coord in chunksToLoad) {
            futures.add(world.getChunkAtAsync(coord.chunkX, coord.chunkZ))
        }

        val finalWorld = world
        val finalX = x
        val finalY = y
        val finalZ = z

        CompletableFuture.allOf(*futures.toTypedArray())
            .thenRun {
                Bukkit.getRegionScheduler().run(plugin, finalWorld, finalX shr 4, finalZ shr 4) {
                    buildEndPortal(finalWorld, finalX, finalY, finalZ)
                }
            }
    }

    private fun buildEndPortal(world: World, x: Int, y: Int, z: Int) {
        val offsets = listOf(
            listOf(-1, -2), listOf(0, -2), listOf(1, -2),
            listOf(-2, -1), listOf(2, -1),
            listOf(-2, 0), listOf(2, 0),
            listOf(-2, 1), listOf(2, 1),
            listOf(-1, 2), listOf(0, 2), listOf(1, 2)
        )

        for (offset in offsets) {
            val frame = world.getBlockAt(x + offset[0], y, z + offset[1])
            frame.type = Material.END_PORTAL_FRAME
            val data = frame.blockData as? EndPortalFrame
            if (data != null) {
                data.setEye(true)
                frame.blockData = data
            }
        }

        for (dx in -1..1) {
            for (dz in -1..1) {
                val portalBlock = world.getBlockAt(x + dx, y, z + dz)
                portalBlock.type = Material.END_PORTAL
            }
        }
    }

    @Suppress("NAMED_PARAMETER_SHADOWING")
    private class ChunkCoord(val chunkX: Int, val chunkZ: Int) {
        override fun equals(other: Any?): Boolean {
            if (other !is ChunkCoord) return false
            return chunkX == other.chunkX && chunkZ == other.chunkZ
        }

        override fun hashCode(): Int = Objects.hash(chunkX, chunkZ)
    }
}
