/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import me.gb8.core.Localization
import me.gb8.core.command.BaseCommand
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import me.gb8.core.util.GlobalUtils.sendMessage
import me.gb8.core.util.GlobalUtils.translateChars

class HelpCommand : BaseCommand(
    "help",
    "/help",
    "8b8tcore.command.help",
    "Displays a custom help menu"
) {

    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player ?: run {
            sendMessage(sender, "&cYou must be a player")
            return
        }
        val loc = Localization.getLocalization("en")
        val helpMsg = translateChars(loc.getStringList("HelpMessage").joinToString("\n"))
        player.sendMessage(helpMsg)
    }
}
