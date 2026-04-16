/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.home.commands

import me.gb8.core.home.Home
import me.gb8.core.home.HomeManager
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import me.gb8.core.util.GlobalUtils

class SetHomeCommand(private val main: HomeManager) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            GlobalUtils.sendMessage(sender, "&3You must be a player to use this command")
            return true
        }
        val player = sender
        if (args.isEmpty()) {
            GlobalUtils.sendPrefixedLocalizedMessage(player, "sethome_include_name")
            return true
        }
        val playerUniqueId = player.uniqueId
        val maxHomes = main.getMaxHomes(player)
        val homes = main.getHomes(playerUniqueId)
        val homesList = homes.getHomes()
        if (homesList.any { it.name == args[0] }) {
            val home = homesList.first { it.name == args[0] }
            GlobalUtils.sendPrefixedLocalizedMessage(player, "sethome_home_already_exists", home.name)
            return true
        }
        if (homesList.size >= maxHomes && !player.isOp) {
            GlobalUtils.sendPrefixedLocalizedMessage(player, "sethome_max_reached")
            return true
        }
        homes.addHome(Home(args[0], player.world.name, player.location))
        GlobalUtils.sendPrefixedLocalizedMessage(player, "sethome_success", args[0])
        return true
    }
}
