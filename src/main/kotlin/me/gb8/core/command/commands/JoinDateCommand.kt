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
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.text.SimpleDateFormat
import java.util.Date
import java.util.stream.Collectors
import me.gb8.core.util.GlobalUtils.sendMessage

class JoinDateCommand(private val plugin: Main) : BaseTabCommand(
    "joindate",
    "/joindate [player|reload]",
    arrayOf("8b8tcore.command.joindate", "8b8tcore.command.joindate.others", "8b8tcore.command.joindate.reload"),
    "Check when a player first joined the server",
    arrayOf("<player>::Check another player's join date", "reload::Reload the join date configuration")
) {

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            val player = sender as? Player ?: run {
                sendMessage(sender, "&cYou must specify a player name when using this command from console!")
                return
            }
            showJoinDate(sender, player)
            return
        }

        if (args[0].equals("reload", ignoreCase = true)) {
            if (!hasPermission(sender, "8b8tcore.command.joindate.reload")) {
                sendNoPermission(sender)
                return
            }
            plugin.reloadConfig()
            sendMessage(sender, "&aJoin date configuration reloaded successfully!")
            return
        }

        if (!hasPermission(sender, "8b8tcore.command.joindate.others")) {
            sendNoPermission(sender)
            return
        }

        val targetName = args[0]

        val target = Bukkit.getPlayer(targetName)
        if (target != null) {
            showJoinDate(sender, target)
            return
        }

        Bukkit.getGlobalRegionScheduler().run(plugin) {
            val offlinePlayer = Bukkit.getOfflinePlayer(targetName)
            if (offlinePlayer.firstPlayed == 0L) {
                sendMessage(sender, "&cPlayer '&e$targetName&c' has never joined the server!")
                return@run
            }
            showJoinDate(sender, offlinePlayer)
        }
    }

    private fun showJoinDate(sender: CommandSender, player: Player) {
        val joinDate = Date(player.firstPlayed)
        val playerName = player.name
        displayJoinDate(sender, joinDate, playerName, sender == player)
    }

    private fun showJoinDate(sender: CommandSender, offlinePlayer: OfflinePlayer) {
        val joinDate = Date(offlinePlayer.firstPlayed)
        val playerName = offlinePlayer.name
        displayJoinDate(sender, joinDate, playerName, sender == offlinePlayer)
    }

    private fun displayJoinDate(sender: CommandSender, joinDate: Date, playerName: String?, isSelf: Boolean) {
        val sdf = SimpleDateFormat(DATE_FORMAT)
        val formattedDate = sdf.format(joinDate)

        if (isSelf) {
            sendMessage(sender, "&6You first joined on: &e$formattedDate")
        } else {
            sendMessage(sender, "&6$playerName &efirst joined on: &b$formattedDate")
        }
    }

    private fun hasPermission(sender: CommandSender, permission: String): Boolean {
        return sender.hasPermission(permission) || sender.isOp || sender.hasPermission("*")
    }

    override fun onTab(sender: CommandSender, args: Array<String>): List<String> {
        if (args.size == 1) {
            val suggestions = ArrayList<String>()
            suggestions.add("reload")
            suggestions.addAll(Bukkit.getOnlinePlayers().map { it.name })
            return suggestions.stream()
                .filter { name -> name.lowercase().startsWith(args[0].lowercase()) }
                .collect(Collectors.toList())
        }
        return ArrayList()
    }

    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"
    }
}
