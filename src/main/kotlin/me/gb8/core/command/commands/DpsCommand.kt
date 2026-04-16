/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import me.gb8.core.command.BaseCommand
import me.gb8.core.database.GeneralDatabase
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage

class DpsCommand : BaseCommand(
    "dps",
    "/dps",
    "8b8tcore.command.dps",
    "Disable phantom spawning for yourself"
) {
    private val database: GeneralDatabase = GeneralDatabase.getInstance()

    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player ?: run {
            sender.sendMessage(PLAYER_ONLY)
            return
        }

        database.getPreventPhantomSpawnAsync(player.name).thenAccept { current ->
            val newValue = !current
            database.updatePreventPhantomSpawn(player.name, newValue)

            val statusKey = if (newValue) "dps_disabled" else "dps_enabled"
            sendPrefixedLocalizedMessage(player, statusKey)
        }
    }
}
