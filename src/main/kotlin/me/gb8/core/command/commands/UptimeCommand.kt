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
import me.gb8.core.tablist.Utils
import org.bukkit.command.CommandSender

import me.gb8.core.util.GlobalUtils.sendMessage

class UptimeCommand(private val plugin: Main) : BaseCommand(
    "uptime",
    "/uptime",
    "8b8tcore.command.uptime",
    "Show the uptime of the server"
) {
    override fun execute(sender: CommandSender, args: Array<String>) {
        sendMessage(sender, "&3The server has had&r&a %s&r&3 uptime", Utils.getFormattedInterval(System.currentTimeMillis() - plugin.startTime))
    }
}
