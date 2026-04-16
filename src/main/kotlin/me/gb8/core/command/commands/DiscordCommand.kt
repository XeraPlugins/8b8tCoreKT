/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import me.gb8.core.command.BaseCommand
import me.gb8.core.command.CommandSection
import org.bukkit.command.CommandSender
import me.gb8.core.util.GlobalUtils.sendMessage

class DiscordCommand(private val main: CommandSection) : BaseCommand(
    "discord",
    "/discord",
    "8b8tcore.command.discord",
    "Shows a discord link"
) {

    override fun execute(sender: CommandSender, args: Array<String>) {
        val configSection = main.config
        val discordUrl = if (configSection != null) configSection.getString("Discord") ?: "" else ""
        sendMessage(sender, discordUrl)
    }
}
