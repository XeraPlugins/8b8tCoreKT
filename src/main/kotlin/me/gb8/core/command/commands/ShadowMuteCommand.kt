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
import me.gb8.core.util.GlobalUtils.sendMessage
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.time.Instant

class ShadowMuteCommand(private val plugin: Main) : BaseTabCommand(
    "shadowmute",
    "/shadowmute <add | remove> <player> [hours (default 72)]",
    "8b8tcore.command.shadowmute",
    "Mute a player without their knowledge"
) {
    private val shadowmuteOptions = listOf("add", "remove")
    private val database = GeneralDatabase.getInstance()

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (args.size < 2) {
            sendMessage(sender, "&cSyntax error: /shadowmute <add | remove> <player> [hours (default 72)]")
            return
        }

        val action = args[0].lowercase()
        val playerName = args[1]

        database.isMutedAsync(playerName).thenAccept { isMuted ->
            when (action) {
                "add" -> addMute(sender, playerName, args, isMuted)
                "remove" -> removeMute(sender, playerName, isMuted)
                else -> sendMessage(sender, "&cInvalid Option: /shadowmute <add | remove> <player> [hours (default 72)]")
            }
        }
    }

    override fun onTab(sender: CommandSender, args: Array<String>): List<String> = when (args.size) {
        1 -> shadowmuteOptions.filter { it.startsWith(args[0], ignoreCase = true) }
        2 -> if (args[0].lowercase() in shadowmuteOptions) {
            plugin.visibleOnlinePlayers(sender)
                .map { it.name }
                .filter { it.startsWith(args[1], ignoreCase = true) }
        } else {
            emptyList()
        }
        else -> emptyList()
    }

    private fun addMute(sender: CommandSender, playerName: String, args: Array<String>, isMuted: Boolean) {
        if (isMuted) {
            sendMessage(sender, "&8${playerName} is already muted.")
            return
        }

        val hours = args.getOrNull(2)?.toIntOrNull() ?: run {
            if (args.size >= 3) {
                sendMessage(sender, "&cHours argument must be a numeric value.")
                return
            }
            DEFAULT_MUTE_HOURS
        }

        val muteUntil = Instant.now().epochSecond + hours * SECONDS_PER_HOUR
        database.mute(playerName, muteUntil).thenRun {
            updateCachedMute(playerName, muteUntil)
            sendMessage(sender, "&8${playerName} has been shadowmuted for $hours hours.")
        }
    }

    private fun removeMute(sender: CommandSender, playerName: String, isMuted: Boolean) {
        if (!isMuted) {
            sendMessage(sender, "&c${playerName} is not muted.")
            return
        }

        database.unmute(playerName).thenRun {
            updateCachedMute(playerName, 0)
            sendMessage(sender, "&8${playerName} has been unmuted.")
        }
    }

    private fun updateCachedMute(playerName: String, mutedUntil: Long) {
        val target = Bukkit.getPlayer(playerName) ?: return
        val chatSection = Main.instance.getSectionByName("ChatControl") as? me.gb8.core.chat.ChatSection ?: return
        chatSection.getInfo(target)?.mutedUntil = mutedUntil
    }

    private companion object {
        const val DEFAULT_MUTE_HOURS = 72
        const val SECONDS_PER_HOUR = 3_600
    }
}
