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
import java.util.Objects
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class EndExitPortalBuilder(private val plugin: JavaPlugin) : Runnable {
    companion object {
        private const val EXIT_PORTAL_X = 0
        private const val EXIT_PORTAL_Z = 0
        private const val EXIT_PORTAL_RADIUS = 5
    }

    override fun run() {
        val endWorld = Bukkit.getWorlds().firstOrNull { it.environment == World.Environment.THE_END }
                ?: return

        val centerX = EXIT_PORTAL_X
        val centerZ = EXIT_PORTAL_Z

        val neededChunks = getNeededChunks(centerX, centerZ)

        val loadFutures = mutableListOf<CompletableFuture<Chunk>>()
        for (coord in neededChunks) {
            loadFutures.add(endWorld.getChunkAtAsync(coord.chunkX, coord.chunkZ))
        }

        val finalWorld = endWorld
        val finalX = centerX
        val finalZ = centerZ

        CompletableFuture.allOf(*loadFutures.toTypedArray()).thenRun {
            val location = Location(finalWorld, finalX.toDouble(), 64.0, finalZ.toDouble())
            Bukkit.getRegionScheduler().run(plugin, location, Consumer {
                clearExistingPortal(finalWorld, finalX, finalZ)
                val centerY = findPortalBaseY(finalWorld, finalX, finalZ)
                buildEndPortal(finalWorld, finalX, centerY, finalZ)
            })
        }
    }

    private fun clearExistingPortal(world: World, x: Int, z: Int) {
        for (dx in -EXIT_PORTAL_RADIUS..EXIT_PORTAL_RADIUS) {
            for (dz in -EXIT_PORTAL_RADIUS..EXIT_PORTAL_RADIUS) {
                for (y in world.minHeight until world.maxHeight) {
                    val block = world.getBlockAt(x + dx, y, z + dz)
                    if (block.type == Material.BEDROCK ||
                        block.type == Material.END_PORTAL ||
                        block.type == Material.FIRE ||
                        block.type == Material.TORCH ||
                        block.type == Material.WALL_TORCH) {
                        block.type = Material.AIR
                    }
                }
            }
        }
    }

    private fun findPortalBaseY(world: World, x: Int, z: Int): Int {
        for (y in world.maxHeight - 1 downTo world.minHeight) {
            val type = world.getBlockAt(x, y, z).type
            if (type == Material.AIR ||
                type == Material.CAVE_AIR ||
                type == Material.VOID_AIR ||
                type == Material.BEDROCK ||
                type == Material.END_PORTAL ||
                type == Material.FIRE) continue

            return y - 1
        }

        return world.minHeight
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
            world.getBlockAt(x + offset[0], y, z + offset[1]).type = Material.END_STONE
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
        val r = EXIT_PORTAL_RADIUS
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
