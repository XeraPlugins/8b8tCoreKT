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
import org.bukkit.block.EndGateway
import org.bukkit.plugin.java.JavaPlugin
import java.util.Objects
import java.util.concurrent.CompletableFuture

class EndPortalGateways {

    class EndExitGatewayBuilder(private val plugin: JavaPlugin) : Runnable {

        override fun run() {
            val endWorld = Bukkit.getWorlds().firstOrNull { it.environment == World.Environment.THE_END }
                    ?: return

            for (pos in EXIT_GATEWAY_POSITIONS) {
                buildGatewayAsync(endWorld, pos[0], GATEWAY_Y, pos[1])
            }
        }

        private fun buildGatewayAsync(world: World, x: Int, y: Int, z: Int) {
            val neededChunks = getNeededChunks(x, z)
            val loadFutures = mutableListOf<CompletableFuture<Chunk>>()
            for (coord in neededChunks) {
                loadFutures.add(world.getChunkAtAsync(coord.chunkX, coord.chunkZ))
            }

            CompletableFuture.allOf(*loadFutures.toTypedArray())
                    .thenRun {
                        Bukkit.getRegionScheduler().run(plugin, world, x shr 4, z shr 4) {
                            buildSingleGateway(world, x, y, z)
                        }
                    }
        }

        private fun buildSingleGateway(world: World, x: Int, y: Int, z: Int) {
            val bedrockOffsets = listOf(listOf(0, -1, 0), listOf(0, 1, 0), listOf(-1, 0, 0), listOf(1, 0, 0), listOf(0, 0, -1), listOf(0, 0, 1))
            for (offset in bedrockOffsets) {
                val block = world.getBlockAt(x + offset[0], y + offset[1], z + offset[2])
                if (block.type != Material.BEDROCK) block.type = Material.BEDROCK
            }
            val gatewayBlock = world.getBlockAt(x, y, z)
            gatewayBlock.type = Material.END_GATEWAY
            val state = gatewayBlock.state
            if (state is EndGateway) {
                state.exitLocation = Location(world, 0.0, 64.0, 0.0)
                state.isExactTeleport = false
                state.update(true, false)
            }
        }

        private fun getNeededChunks(centerX: Int, centerZ: Int): Set<ChunkCoord> {
            val chunks = mutableSetOf<ChunkCoord>()
            for (dx in -1..1) {
                for (dz in -1..1) {
                    chunks.add(ChunkCoord((centerX + dx) shr 4, (centerZ + dz) shr 4))
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

        companion object {
            private const val GATEWAY_Y = 75
            private val EXIT_GATEWAY_POSITIONS = listOf(
                listOf(96, 0), listOf(91, 30), listOf(77, 57), listOf(57, 77), listOf(30, 91),
                listOf(0, 96), listOf(-30, 91), listOf(-57, 77), listOf(-77, 57), listOf(-91, 30),
                listOf(-96, 0), listOf(-91, -30), listOf(-77, -57), listOf(-57, -77), listOf(-30, -91),
                listOf(0, -96), listOf(30, -91), listOf(57, -77), listOf(77, -57), listOf(-91, -30)
            )
        }
    }
}
