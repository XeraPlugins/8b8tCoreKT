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
import me.gb8.core.database.GeneralDatabase
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

import java.time.Instant
import java.util.stream.Collectors

import me.gb8.core.util.GlobalUtils.sendMessage

class ShadowMuteCommand(private val plugin: Main) : BaseTabCommand(
    "shadowmute",
    "/shadowmute <add | remove> <player> [hours (default 72)]",
    "8b8tcore.command.shadowmute",
    "Mute a player without their knowledge"
) {
    private val shadowmuteOptions = listOf("add", "remove")
    private val database: GeneralDatabase = GeneralDatabase.getInstance()

    
    override fun execute(sender: CommandSender, args: Array<String>) {
        if (args.size < 2) {
            sendMessage(sender, "&cSyntax error: /shadowmute <add | remove> <player> [hours (default 72)]")
            return
        }

        val action = args[0].lowercase()
        val playerName = args[1]

        database.isMutedAsync(playerName).thenAccept { isMuted ->
            if (action == "add") {
                if (isMuted) {
                    sendMessage(sender, "&8${playerName} is already muted.")
                    return@thenAccept
                }

                var hours = 72
                if (args.size >= 3) {
                    try {
                        hours = args[2].toInt()
                    } catch (e: NumberFormatException) {
                        sendMessage(sender, "&cHours argument must be a numeric value.")
                        return@thenAccept
                    }
                }

                val currentTime = Instant.now().epochSecond
                val finalMuteUntil = currentTime + (hours * 3600)
                val finalHours = hours

                database.mute(playerName).thenRun {
                    val target = Bukkit.getPlayer(playerName)
                    if (target != null) {
                        val pluginInstance = Main.instance
                        val chatSection = pluginInstance.getSectionByName("ChatControl") as? me.gb8.core.chat.ChatSection
                        if (chatSection != null) {
                            val info = chatSection.getInfo(target)
                            if (info != null) info.mutedUntil = finalMuteUntil
                        }
                    }
                    sendMessage(sender, "&8${playerName} has been shadowmuted for ${finalHours} hours.")
                }
            } else if (action == "remove") {
                if (!isMuted) {
                    sendMessage(sender, "&c${playerName} is not muted.")
                    return@thenAccept
                }

                database.unmute(playerName).thenRun {
                    val target = Bukkit.getPlayer(playerName)
                    if (target != null) {
                        val pluginInstance = Main.instance
                        val chatSection = pluginInstance.getSectionByName("ChatControl") as? me.gb8.core.chat.ChatSection
                        if (chatSection != null) {
                            val info = chatSection.getInfo(target)
                            if (info != null) info.mutedUntil = 0
                        }
                    }
                    sendMessage(sender, "&8${playerName} has been unmuted.")
                }
            } else {
                sendMessage(sender, "&cInvalid Option: /shadowmute <add | remove> <player> [hours (default 72)]")
            }
        }
    }

    
    override fun onTab(sender: CommandSender, args: Array<String>): List<String> {
        if (args.size == 1) {
            return shadowmuteOptions.stream()
                    .filter { it.lowercase().startsWith(args[0].lowercase()) }
                    .collect(Collectors.toList())
        } else if (args.size == 2) {
            val firstArg = args[0].lowercase()
            if (firstArg == "add" || firstArg == "remove") {
                return getOnlinePlayers().stream()
                        .filter { it.lowercase().startsWith(args[1].lowercase()) }
                        .collect(Collectors.toList())
            }
        }
        return mutableListOf()
    }

    private fun getOnlinePlayers(): List<String> {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList())
    }
}
