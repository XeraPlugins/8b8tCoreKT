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
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import me.gb8.core.util.GlobalUtils.sendMessage

class KillCommand(private val plugin: Main) : BaseCommand(
    "kill",
    "/kill [player]",
    arrayOf("8b8tcore.command.kill", "8b8tcore.command.kill.others"),
    "Kill yourself or another player",
    arrayOf("<player>::Kill another player")
) {

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            val player = sender as? Player ?: run {
                sendMessage(sender, "&cYou must specify a player name when using this command from console!")
                return
            }
            killPlayer(sender, player)
            return
        }

        if (!hasPermission(sender, "8b8tcore.command.kill.others")) {
            sendNoPermission(sender)
            return
        }

        val targetName = args[0]
        val target = Bukkit.getPlayer(targetName)

        if (target == null) {
            sendMessage(sender, "&cPlayer '&e$targetName&c' is not online!")
            return
        }

        killPlayer(sender, target)
    }

    private fun killPlayer(sender: CommandSender, target: Player) {
        FoliaCompat.schedule(target, Main.getInstance()) {
            target.health = 0.0

            if (sender == target) {
                sendMessage(sender, "&6You have killed yourself!")
            } else {
                sendMessage(sender, "&6You have killed &e${target.name}&6!")
                sendMessage(target, "&cYou have been killed by &e${sender.name}&c!")
            }
        }
    }

    private fun hasPermission(sender: CommandSender, permission: String): Boolean {
        return sender.hasPermission(permission) || sender.isOp || sender.hasPermission("*")
    }
}
