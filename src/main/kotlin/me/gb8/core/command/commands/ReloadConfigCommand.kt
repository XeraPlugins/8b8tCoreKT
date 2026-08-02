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
import me.gb8.core.util.FoliaCompat
import me.gb8.core.util.GlobalUtils.log
import me.gb8.core.util.GlobalUtils.sendMessage
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.logging.Level

class ReloadConfigCommand(private val plugin: Main) : BaseCommand(
    "reloadconfig",
    "/reloadconfig",
    "8b8tcore.command.reloadconfig",
    "Hot reload config.yml without restarting the server"
) {
    override fun execute(sender: CommandSender, args: Array<String>) {
        reloadConfigForSender(
            plugin,
            sender,
            "&aReloaded config.yml, localization files, and all reloadable sections."
        )
    }
}

internal fun reloadConfigForSender(plugin: Main, sender: CommandSender, successMessage: String) {
    plugin.reloadConfigOnGlobalScheduler().whenComplete { _, error ->
        val reply = Runnable {
            if (error == null) {
                sendMessage(sender, successMessage)
                log(Level.INFO, "%s reloaded config.yml, localization files, and reloadable sections", sender.name)
            } else {
                val cause = error.cause ?: error
                sendMessage(sender, "&cFailed to reload config: %s", cause.message ?: "unknown error")
                log(Level.SEVERE, "Failed to reload plugin configuration requested by %s", sender.name)
                cause.printStackTrace()
            }
        }

        if (sender is Player) {
            FoliaCompat.schedule(sender, plugin, reply)
        } else {
            reply.run()
        }
    }
}
