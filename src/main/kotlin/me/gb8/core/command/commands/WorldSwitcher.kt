/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import me.gb8.core.command.BaseCommand
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.CommandSender

import me.gb8.core.util.GlobalUtils.sendMessage

class WorldSwitcher(private val main: me.gb8.core.Main) : BaseCommand(
    "world",
    "/world <worldName>",
    "8b8tcore.command.world",
    "Switch worlds",
    arrayOf("overworld::Teleport your self to the overworld", "nether::Teleport your self to the nether", "end::Teleport your self to the end")
) {
    
    override fun execute(sender: CommandSender, args: Array<String>) {
        getSenderAsPlayer(sender)?.let { player ->
            if (args.isNotEmpty()) {
                val worldName = Bukkit.getWorlds()[0].name
                val x = player.location.x
                val y = player.location.y
                val z = player.location.z
                when (args[0]) {
                    "overworld" -> {
                        val overWorld = Bukkit.getWorld(worldName)
                        player.teleportAsync(Location(overWorld, x, y, z))
                        sendMessage(player, "&3Teleporting to &r&a${args[0]}")
                    }
                    "nether" -> {
                        val netherWorld = Bukkit.getWorld("${worldName}_nether")
                        val targetY = if (y > 128) 125.0 else y
                        player.teleportAsync(Location(netherWorld, x, targetY, z))
                        sendMessage(player, "&3Teleporting to &r&a${args[0]}")
                    }
                    "end" -> {
                        val endWorld = Bukkit.getWorld("${worldName}_the_end")
                        player.teleportAsync(Location(endWorld, x, y, z))
                        sendMessage(player, "&3Teleporting to &r&a${args[0]}")
                    }
                    else -> sendMessage(sender, "&4Error:&r&c Unknown world")
                }
            } else {
                sendErrorMessage(sender, "Please include one argument /world <end | overworld | nether>")
            }
        } ?: sendErrorMessage(sender, PLAYER_ONLY)
    }
}
