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
import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage

class LeaderboardCommand : BaseCommand(
    "leaderboard", 
    "/leaderboard <vanilla|custom>", 
    "8b8tcore.command.leaderboard",
    "Switch between vanilla and custom leaderboard display",
    arrayOf(
        "vanilla::Switch to vanilla leaderboard",
        "custom::Switch to custom leaderboard"
    )
) {
    private val database = GeneralDatabase.getInstance()

    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player ?: run {
            sender.sendMessage("This command can only be used by players.")
            return
        }

        if (args.isEmpty()) {
            database.isVanillaLeaderboardAsync(player.name).thenAccept { current ->
                val newValue = !current
                database.setVanillaLeaderboard(player.name, newValue)
                val mode = if (newValue) "vanilla" else "custom"
                sendPrefixedLocalizedMessage(player, "leaderboard_toggled", mode)
            }
        } else {
            val mode = args[0].lowercase()
            if (mode == "vanilla" || mode == "custom") {
                val useVanilla = mode == "vanilla"
                database.setVanillaLeaderboard(player.name, useVanilla)
                sendPrefixedLocalizedMessage(player, "leaderboard_set", mode)
            } else {
                sendPrefixedLocalizedMessage(player, "leaderboard_usage")
            }
        }
    }
}
