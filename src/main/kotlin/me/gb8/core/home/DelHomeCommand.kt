/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.home.commands

import me.gb8.core.home.HomeManager
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player
import me.gb8.core.util.GlobalUtils

class DelHomeCommand(private val main: HomeManager) : TabExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        val player = sender as? Player ?: run {
            GlobalUtils.sendMessage(sender, "&3You must be a player to use this command")
            return true
        }

        val homesData = main.getHomes(player.uniqueId)
        if (!homesData.hasHomes()) {
            GlobalUtils.sendPrefixedLocalizedMessage(player, "delhome_no_homes")
            return true
        }

        val homesList = homesData.getHomes()
        if (args.isEmpty()) {
            val names = homesList.joinToString(", ") { it.name }
            GlobalUtils.sendPrefixedLocalizedMessage(player, "delhome_specify_home", names)
            return true
        }

        val homeName = args[0]
        homesList.find { it.name == homeName }?.let { home ->
            homesData.deleteHome(home)
            GlobalUtils.sendPrefixedLocalizedMessage(player, "delhome_success", home.name)
        } ?: GlobalUtils.sendPrefixedLocalizedMessage(player, "delhome_home_not_found", homeName)

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<String>): List<String> {
        return if (sender is Player && args.size == 1) {
            main.getHomes(sender.uniqueId).getHomes()
                .map { it.name }
                .filter { it.startsWith(args[0], ignoreCase = true) }
        } else emptyList()
    }
}