/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import me.gb8.core.command.BaseCommand
import me.gb8.core.util.GlobalUtils
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

class SayCommand : BaseCommand(
    "say",
    "/say <message>",
    "8b8tcore.command.say",
    "Configurable say command"
) {

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (args.isNotEmpty()) {
            val msg = GlobalUtils.translateChars(args.joinToString(" "))
            Bukkit.getOnlinePlayers().forEach { GlobalUtils.sendPrefixedComponent(it, msg) }
            GlobalUtils.sendPrefixedComponent(Bukkit.getServer().consoleSender, msg)
        } else sendErrorMessage(sender, "Message cannot be blank")
    }
}
