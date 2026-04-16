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
        if (sender !is Player) {
            GlobalUtils.sendMessage(sender, "&3You must be a player to use this command")
            return true
        }
        val player = sender
        val homesData = main.getHomes(player.uniqueId)
        if (!homesData.hasHomes()) {
            GlobalUtils.sendPrefixedLocalizedMessage(player, "delhome_no_homes")
            return true
        }
        val homesList = homesData.getHomes()
        if (args.isEmpty()) {
            val names = homesList.map { home -> home.name }.joinToString(", ")
            GlobalUtils.sendPrefixedLocalizedMessage(player, "delhome_specify_home", names)
            return true
        }
        val home = homesList.find { home -> home.name == args[0] }
        if (home == null) {
            GlobalUtils.sendPrefixedLocalizedMessage(player, "delhome_home_not_found", args[0])
            return true
        }
        homesData.deleteHome(home)
        GlobalUtils.sendPrefixedLocalizedMessage(player, "delhome_success", home.name)
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<String>): List<String> {
        return if (sender is Player && args.size == 1) {
            main.getHomes(sender.uniqueId).getHomes().map { it.name }
        } else emptyList()
    }
}
