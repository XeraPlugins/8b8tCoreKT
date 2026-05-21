/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.home.commands

import me.gb8.core.home.Home
import me.gb8.core.home.HomeData
import me.gb8.core.home.HomeManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player
import me.gb8.core.util.GlobalUtils
import me.gb8.core.util.FoliaCompat

class HomeCommand(private val main: HomeManager) : TabExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            GlobalUtils.sendMessage(sender, "&3You must be a player to use this command")
            return true
        }
        val player = sender
        val homesData = main.getHomes(player.uniqueId)
        if (!homesData.hasHomes()) {
            GlobalUtils.sendPrefixedLocalizedMessage(player, "home_no_homes")
            return true
        }
        val homesList = homesData.getHomes()
        val names = homesList.map { it.name }.joinToString(", ")
        val homesCount = homesList.size.toString()
        if (args.isEmpty()) {
            GlobalUtils.sendPrefixedLocalizedMessage(player, "home_specify_home", homesCount, names)
            return true
        }

        if (GlobalUtils.isTeleportRestricted(player)) {
            val range = GlobalUtils.getTeleportRestrictionRange(player)
            GlobalUtils.sendPrefixedLocalizedMessage(player, "home_too_close", range)
            return true
        }

        val targetHome = resolveHome(homesList, args[0])
        if (targetHome != null) {
            vanish(player)
            main.main.lastLocations[player.uniqueId] = player.location
            
            var targetLoc = targetHome.location
            if (targetLoc.world == null) {
                val world = Bukkit.getWorld(targetHome.worldName)
                if (world == null) {
                    GlobalUtils.sendPrefixedLocalizedMessage(player, "home_world_not_loaded", targetHome.worldName)
                    return true
                }
                targetLoc.world = world
            }

            val homeName = targetHome.name
            player.teleportAsync(targetLoc).thenAccept { success ->
                if (player.isOnline) {
                    if (!main.main.vanishedPlayers.contains(player.uniqueId)) unVanish(player)
                    if (success) {
                        GlobalUtils.sendPrefixedLocalizedMessage(player, "home_success", homeName)
                    }
                }
            }
        } else {
            GlobalUtils.sendPrefixedLocalizedMessage(player, "home_not_found", args[0])
        }
        return true
    }

    private fun resolveHome(homes: List<Home>, input: String): Home? {
        val exactMatch = homes.firstOrNull { it.name.equals(input, ignoreCase = true) }
        if (exactMatch != null) return exactMatch

        val prefixMatches = homes.filter { it.name.startsWith(input, ignoreCase = true) }
        return prefixMatches.singleOrNull()
    }

    private fun vanish(player: Player) {
        for (onlinePlayer in Bukkit.getOnlinePlayers()) {
            if (onlinePlayer != player) {
                onlinePlayer.hidePlayer(main.main, player)
            }
        }
    }

    private fun unVanish(player: Player) {
        for (onlinePlayer in Bukkit.getOnlinePlayers()) {
            if (onlinePlayer != player) {
                onlinePlayer.showPlayer(main.main, player)
            }
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<String>): List<String> {
        return if (sender is Player && args.size == 1) {
            main.tabComplete(sender, args)
        } else emptyList()
    }
}
