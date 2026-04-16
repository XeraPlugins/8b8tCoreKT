/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import me.gb8.core.Main
import me.gb8.core.command.BaseCommand
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

import me.gb8.core.util.GlobalUtils.sendMessage
import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage

class VanishCommand(private val main: Main) : BaseCommand(
    "vanish",
    "/vanish",
    "8b8tcore.command.vanish",
    "Toggle vanish mode"
) {
    
    override fun execute(sender: CommandSender, args: Array<String>) {
        if (sender !is Player) {
            sendMessage(sender, "&cYou must be a player to use this command.")
            return
        }

        if (!sender.hasPermission("8b8tcore.command.vanish") && !sender.isOp()) {
            sendMessage(sender, "&cYou do not have permissions to use this command.")
            return
        }

        val isVanished = main.vanishedPlayers.contains(sender.uniqueId)

        if (isVanished) {
            for (onlinePlayer in Bukkit.getOnlinePlayers()) {
                onlinePlayer.showPlayer(main, sender)
            }
            main.vanishedPlayers.remove(sender.uniqueId)
            sendPrefixedLocalizedMessage(sender, "vanish_false")
        } else {
            for (onlinePlayer in Bukkit.getOnlinePlayers()) {
                if (!onlinePlayer.hasPermission("8b8tcore.command.vanish") || !onlinePlayer.isOp() || !onlinePlayer.hasPermission("*")) {
                    onlinePlayer.hidePlayer(main, sender)
                }
            }
            main.vanishedPlayers.add(sender.uniqueId)
            sendPrefixedLocalizedMessage(sender, "vanish_true")
        }
    }
}
