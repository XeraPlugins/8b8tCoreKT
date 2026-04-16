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
import me.gb8.core.database.GeneralDatabase
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

import me.gb8.core.util.GlobalUtils.sendMessage

class ToggleDeathMessageCommand(private val plugin: Main) : BaseCommand(
    "deathmessage",
    "/deathmessage",
    "8b8tcore.command.deathmessage"
) {
    private val database: GeneralDatabase = GeneralDatabase.getInstance()

    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player ?: run {
            sender.sendMessage("This command can only be used by players.")
            return
        }

        database.getPlayerHideDeathMessagesAsync(player.name).thenAccept { current ->
            val newValue = !current
            database.updateHideDeathMessages(player.name, newValue)

            if (newValue) {
                sendMessage(player, "&aYou will no longer see death messages.")
            } else {
                sendMessage(player, "&aYou will now see death messages.")
            }
        }
    }
}
