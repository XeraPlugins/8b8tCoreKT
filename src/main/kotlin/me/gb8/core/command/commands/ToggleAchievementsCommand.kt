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
import me.gb8.core.util.FoliaCompat
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import me.gb8.core.util.GlobalUtils.sendMessage

class ToggleAchievementsCommand : BaseCommand(
    "achievements", 
    "/achievements", 
    "8b8tcore.command.badges"
) {
    private val database = GeneralDatabase.getInstance()

    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player ?: run {
            sender.sendMessage("This command can only be used by players.")
            return
        }

        database.getPlayerHideBadgesAsync(player.name).thenAcceptAsync { current ->
            val newValue = !current
            database.updateHideBadges(player.name, newValue)

            FoliaCompat.schedule(player, Main.getInstance()) {
                sendMessage(player, if (newValue) "&aYou will no longer see other players achievements." else "&aYou will now see other players achievements.")
            }
        }
    }
}
