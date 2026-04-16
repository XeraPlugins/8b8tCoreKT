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
import me.gb8.core.tablist.TabSection
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage

class ToggleLeaderboardCommand(private val plugin: Main) : BaseCommand(
    "toggleleaderboard",
    "/toggleleaderboard [vanilla|custom]",
    "8b8tcore.command.toggleleaderboard",
    "Toggle between vanilla and custom leaderboard display",
    arrayOf(
        "vanilla::Switch to vanilla leaderboard",
        "custom::Switch to custom leaderboard"
    )
) {
    private val database: GeneralDatabase = GeneralDatabase.getInstance()

    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player ?: run {
            sender.sendMessage("This command can only be used by players.")
            return
        }

        val pluginInstance = Main.instance
        val chatSection = pluginInstance.getSectionByName("ChatControl") as? me.gb8.core.chat.ChatSection
        if (chatSection == null) return
        val info = chatSection.getInfo(player)
        if (info == null) return

        if (args.isNotEmpty()) {
            val arg = args[0].lowercase()
            if (arg == "vanilla" || arg == "custom") {
                val useVanilla = arg == "vanilla"
                if (info.useVanillaLeaderboard == useVanilla) {
                    sendPrefixedLocalizedMessage(player, if (useVanilla) "leaderboard_vanilla" else "leaderboard_custom")
                    return
                }
                
                info.useVanillaLeaderboard = useVanilla
                database.setVanillaLeaderboard(player.name, useVanilla)
                refreshPlayer(player)
                sendPrefixedLocalizedMessage(player, if (useVanilla) "leaderboard_vanilla" else "leaderboard_custom")
                return
            }
        }

        val current = info.useVanillaLeaderboard
        val newValue = !current
        info.useVanillaLeaderboard = newValue
        database.setVanillaLeaderboard(player.name, newValue)
        refreshPlayer(player)
        sendPrefixedLocalizedMessage(player, if (newValue) "leaderboard_vanilla" else "leaderboard_custom")
    }

    private fun refreshPlayer(player: Player) {
        val pluginInstance = Main.instance
        FoliaCompat.schedule(player, pluginInstance) {
            val section = pluginInstance.getSectionByName("TabList")
            if (section is TabSection) {
                section.setTab(player, true)
            }
        }
    }
}
