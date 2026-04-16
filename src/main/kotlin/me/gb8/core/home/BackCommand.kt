/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.home.commands

import me.gb8.core.home.HomeManager
import me.gb8.core.util.GlobalUtils
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent

class BackCommand(private val main: HomeManager) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage("Only players can use this command.")
            return true
        }

        if (GlobalUtils.isTeleportRestricted(player)) {
            val range = GlobalUtils.getTeleportRestrictionRange(player)
            GlobalUtils.sendPrefixedLocalizedMessage(player, "home_too_close", range)
            return true
        }

        val lastLocation = main.main.getLastLocation(player)

        if (lastLocation == null) {
            GlobalUtils.sendPrefixedLocalizedMessage(player, "back_no_last_location")
            return true
        }

        val loc = lastLocation
        loc.world?.getChunkAtAsyncUrgently(loc.blockX, loc.blockZ)?.thenAccept {
            if (player.isOnline) {
                player.teleportAsync(loc, PlayerTeleportEvent.TeleportCause.PLUGIN)
                GlobalUtils.sendPrefixedLocalizedMessage(player, "back_teleported")
                main.main.lastLocations.remove(player.uniqueId)
            }
        }
        main.main.lastLocations.remove(player.uniqueId)
        return true
    }
}
