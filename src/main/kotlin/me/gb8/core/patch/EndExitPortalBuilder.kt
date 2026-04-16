/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.patch

import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.plugin.java.JavaPlugin
import me.gb8.core.antiillegal.IllegalConstants
import java.util.Objects
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class EndExitPortalBuilder(private val plugin: JavaPlugin) : Runnable {

    override fun run() {
        val endWorld = Bukkit.getWorlds().firstOrNull { it.environment == World.Environment.THE_END }
                ?: return

        val centerX = IllegalConstants.EXIT_PORTAL_X
        val centerY = IllegalConstants.EXIT_PORTAL_Y_MIN + 1
        val centerZ = IllegalConstants.EXIT_PORTAL_Z

        val neededChunks = getNeededChunks(centerX, centerZ)

        val loadFutures = mutableListOf<CompletableFuture<Chunk>>()
        for (coord in neededChunks) {
            loadFutures.add(endWorld.getChunkAtAsync(coord.chunkX, coord.chunkZ))
        }

        val finalWorld = endWorld
        val finalX = centerX
        val finalY = centerY
        val finalZ = centerZ

        CompletableFuture.allOf(*loadFutures.toTypedArray()).thenRun {
            val location = Location(finalWorld, finalX.toDouble(), finalY.toDouble(), finalZ.toDouble())
            Bukkit.getRegionScheduler().run(plugin, location, Consumer {
                buildEndPortal(finalWorld, finalX, finalY, finalZ)
            })
        }
    }

    private fun buildEndPortal(world: World, x: Int, y: Int, z: Int) {
        val bedrockLayer1 = listOf(
            listOf(-1, -2), listOf(0, -2), listOf(1, -2),
            listOf(-2, -1), listOf(-1, -1), listOf(0, -1), listOf(1, -1), listOf(2, -1),
            listOf(-2, 0), listOf(-1, 0), listOf(0, 0), listOf(1, 0), listOf(2, 0),
            listOf(-2, 1), listOf(-1, 1), listOf(0, 1), listOf(1, 1), listOf(2, 1),
            listOf(-1, 2), listOf(0, 2), listOf(1, 2)
        )

        for (offset in bedrockLayer1) {
            world.getBlockAt(x + offset[0], y, z + offset[1]).type = Material.BEDROCK
        }

        val bedrockLayer2 = listOf(
            listOf(-1, -3), listOf(0, -3), listOf(1, -3),
            listOf(-2, -2), listOf(2, -2),
            listOf(-3, -1), listOf(3, -1),
            listOf(-3, 0), listOf(3, 0),
            listOf(-3, 1), listOf(3, 1),
            listOf(-2, 2), listOf(2, 2),
            listOf(-1, 3), listOf(0, 3), listOf(1, 3)
        )

        for (offset in bedrockLayer2) {
            world.getBlockAt(x + offset[0], y + 1, z + offset[1]).type = Material.BEDROCK
        }

        val portalBlocks = listOf(
            listOf(-1, -2), listOf(0, -2), listOf(1, -2),
            listOf(-2, -1), listOf(-1, -1), listOf(0, -1), listOf(1, -1), listOf(2, -1),
            listOf(-2, 0), listOf(-1, 0), listOf(1, 0), listOf(2, 0),
            listOf(-2, 1), listOf(-1, 1), listOf(0, 1), listOf(1, 1), listOf(2, 1),
            listOf(-1, 2), listOf(0, 2), listOf(1, 2)
        )

        for (offset in portalBlocks) {
            world.getBlockAt(x + offset[0], y + 1, z + offset[1]).type = Material.END_PORTAL
        }

        for (dy in 1..4) {
            world.getBlockAt(x, y + dy, z).type = Material.BEDROCK
        }
    }

    private fun getNeededChunks(centerX: Int, centerZ: Int): Set<ChunkCoord> {
        val chunks = mutableSetOf<ChunkCoord>()
        val r = IllegalConstants.EXIT_PORTAL_RADIUS
        for (dx in -r..r) {
            for (dz in -r..r) {
                val blockX = centerX + dx
                val blockZ = centerZ + dz
                chunks.add(ChunkCoord(blockX shr 4, blockZ shr 4))
            }
        }
        return chunks
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
