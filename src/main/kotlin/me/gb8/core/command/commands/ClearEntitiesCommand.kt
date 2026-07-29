/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import me.gb8.core.Main
import me.gb8.core.command.BaseTabCommand
import me.gb8.core.util.FoliaCompat
import me.gb8.core.util.GlobalUtils.sendMessage
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.*
import java.util.concurrent.atomic.AtomicInteger

class ClearEntitiesCommand : BaseTabCommand(
    "clearentities",
    "/clearentities <nearest | unnecessary | hostile>",
    "8b8tcore.command.clearentities",
    "Clear entities in a safe manner"
) {
    private val clearEntitiesOptions = listOf("nearest", "unnecessary", "hostile")

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sendMessage(sender, "&cSyntax error: /clearentities <nearest | unnecessary | hostile>")
            return
        }

        when (args[0].lowercase()) {
            "nearest" -> clearNearestEntity(sender)
            "unnecessary" -> clearUnnecessaryEntities(sender)
            "hostile" -> clearHostileEntities(sender)
            else -> sendMessage(sender, "&cInvalid Option: /clearentities <nearest | unnecessary | hostile>")
        }
    }

    private fun clearNearestEntity(sender: CommandSender) {
        val player = sender as? Player ?: run {
            sendMessage(sender, "&cThis command can only be executed by a player.")
            return
        }

        val playerLocation = player.location
        val world = player.world

        Bukkit.getRegionScheduler().execute(Main.instance, playerLocation) {
            val nearestEntity = world.getNearbyEntities(playerLocation, 10.0, 10.0, 10.0)
                .filter { !isProtectedEntity(it) && it !is Player }
                .minByOrNull { it.location.distanceSquared(playerLocation) }

            if (nearestEntity != null && nearestEntity.isValid) {
                nearestEntity.remove()
                sendMessage(sender, "&aCleared the nearest entity.")
            } else {
                sendMessage(sender, "&cNo entities found to remove.")
            }
        }
    }

    private fun clearUnnecessaryEntities(sender: CommandSender) {
        clearAcrossLoadedChunks(sender, "unnecessary") { entity ->
            !isProtectedEntity(entity) && entity !is Player
        }
    }

    private fun clearHostileEntities(sender: CommandSender) {
        clearAcrossLoadedChunks(sender, "hostile") { entity ->
            entity is Enemy || entity is Boss || entity is Monster
        }
    }

    private fun clearAcrossLoadedChunks(
        sender: CommandSender,
        description: String,
        shouldRemove: (Entity) -> Boolean
    ) {
        Bukkit.getGlobalRegionScheduler().execute(Main.instance) {
            val chunks = Bukkit.getWorlds().flatMap { world ->
                world.loadedChunks.map { chunk -> ChunkPosition(world, chunk.x, chunk.z) }
            }
            if (chunks.isEmpty()) {
                sendResult(sender, 0, description)
                return@execute
            }

            val totalCount = AtomicInteger()
            val remaining = AtomicInteger(chunks.size)
            fun completeChunk() {
                if (remaining.decrementAndGet() == 0) {
                    sendResult(sender, totalCount.get(), description)
                }
            }

            chunks.forEach { position ->
                try {
                    Bukkit.getRegionScheduler().execute(
                        Main.instance,
                        position.world,
                        position.x,
                        position.z
                    ) {
                        try {
                            if (!position.world.isChunkLoaded(position.x, position.z)) return@execute
                            val chunk = position.world.getChunkAt(position.x, position.z)
                            var chunkCount = 0
                            for (entity in chunk.entities) {
                                if (entity.isValid && shouldRemove(entity)) {
                                    entity.remove()
                                    chunkCount++
                                }
                            }
                            totalCount.addAndGet(chunkCount)
                        } finally {
                            completeChunk()
                        }
                    }
                } catch (_: Throwable) {
                    completeChunk()
                }
            }
        }
    }

    private fun sendResult(sender: CommandSender, count: Int, description: String) {
        val message = "&aCleared $count $description entities."
        if (sender is Player) {
            FoliaCompat.schedule(sender, Main.instance) { sendMessage(sender, message) }
        } else {
            Bukkit.getGlobalRegionScheduler().execute(Main.instance) { sendMessage(sender, message) }
        }
    }

    private fun isProtectedEntity(entity: Entity): Boolean {
        return entity is ItemFrame || entity is Painting || entity is Minecart ||
               entity is Boat || entity is Tameable || entity is ArmorStand ||
               entity is LeashHitch || entity is IronGolem
    }

    override fun onTab(sender: CommandSender, args: Array<String>): List<String> {
        return if (args.size == 1) {
            clearEntitiesOptions.filter { it.startsWith(args[0].lowercase()) }
        } else {
            emptyList()
        }
    }

    private data class ChunkPosition(val world: World, val x: Int, val z: Int)
}
