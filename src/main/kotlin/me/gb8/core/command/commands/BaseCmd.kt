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

class BaseCmd(private val main: CommandSection) : BaseCommand(
    "lef",
    "/lef reload | version | help",
    "8b8tcore.command.lef",
    "Base command of the plugin",
    arrayOf(
        "reload::Reloads all plugin sections and config",
        "version::Displays current build version",
        "help::Shows this diagnostic menu"
    )
) {

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sendErrorMessage(sender, usage)
            return
        }

        when (args[0].lowercase()) {
            "reload" -> handleReload(sender)
            "version" -> sendMessage(sender, "&3Version &r&c%s", main.plugin.pluginMeta.version)
            "help" -> handleHelp(sender)
            else -> sendMessage(sender, "&cUnknown sub-command:&r&3 %s", args[0])
        }
    }

    private fun handleReload(sender: CommandSender) {
        main.plugin.reloadConfig()
        sendMessage(sender, "&aSuccessfully reloaded configuration and cleared internal caches.")
    }

    private fun handleHelp(sender: CommandSender) {
        sendMessage(sender, "&1---&r %s &6Help &r&1---", PREFIX)

        main.commandHandler?.commands?.forEach { command ->
            val perm = command.permissions.firstOrNull()
            if (perm != null && !sender.hasPermission(perm)) return@forEach

            sendMessage(sender, "&3/%s &7- %s", command.name, command.description)

            if (command.subCommands != null) {
                for (sub in command.subCommands) {
                    val split = sub.split("::", limit = 2)
                    if (split.size == 2) {
                        sendMessage(sender, "  &6» &e%s &7| %s", split[0], split[1])
                    } else {
                        sendMessage(sender, "  &6» &e%s", sub)
                    }
                }
            }
        }
    }
}
