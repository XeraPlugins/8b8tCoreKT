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
import me.gb8.core.util.GlobalUtils.sendMessage
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.*
import java.util.ArrayList
import java.util.stream.Collectors

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
            val nearestEntity = world.getNearbyEntities(playerLocation, 10.0, 10.0, 10.0).stream()
                .filter { entity -> !isProtectedEntity(entity) && entity !is Player }
                .min(Comparator.comparingDouble { entity -> entity.location.distanceSquared(playerLocation) })
                .orElse(null)

            if (nearestEntity != null) {
                if (nearestEntity.isValid) {
                    nearestEntity.remove()
                    sendMessage(sender, "&aCleared the nearest entity.")
                }
            } else {
                sendMessage(sender, "&cNo entities found to remove.")
            }
        }
    }

    private fun clearUnnecessaryEntities(sender: CommandSender) {
        var totalCount = 0

        for (world in Bukkit.getWorlds()) {
            for (chunk in world.loadedChunks) {
                Bukkit.getRegionScheduler().execute(Main.instance, chunk.getBlock(0, 0, 0).location) {
                    var count = 0
                    for (entity in chunk.entities) {
                        if (!isProtectedEntity(entity) && entity !is Player) {
                            if (entity.isValid) {
                                entity.remove()
                                count++
                            }
                        }
                    }
                    totalCount += count
                }
            }
        }

        Bukkit.getGlobalRegionScheduler().execute(Main.instance) {
            sendMessage(sender, "&aCleared $totalCount unnecessary entities.")
        }
    }

    private fun clearHostileEntities(sender: CommandSender) {
        var totalCount = 0

        for (world in Bukkit.getWorlds()) {
            for (chunk in world.loadedChunks) {
                Bukkit.getRegionScheduler().execute(Main.instance, chunk.getBlock(0, 0, 0).location) {
                    var count = 0
                    for (entity in chunk.entities) {
                        if (entity is Enemy || entity is Boss || entity is Monster) {
                            if (entity.isValid) {
                                entity.remove()
                                count++
                            }
                        }
                    }
                    totalCount += count
                }
            }
        }

        Bukkit.getGlobalRegionScheduler().execute(Main.instance) {
            sendMessage(sender, "&aCleared $totalCount hostile entities.")
        }
    }

    private fun isProtectedEntity(entity: Entity): Boolean {
        return entity is ItemFrame ||
               entity is Painting ||
               entity is Minecart ||
               entity is Boat ||
               entity is Tameable ||
               entity is ArmorStand ||
               entity is LeashHitch ||
               entity is IronGolem
    }

    override fun onTab(sender: CommandSender, args: Array<String>): List<String> {
        if (args.size == 1) {
            return clearEntitiesOptions.stream()
                .filter { option -> option.startsWith(args[0].lowercase()) }
                .collect(Collectors.toList())
        }
        return ArrayList()
    }
}
