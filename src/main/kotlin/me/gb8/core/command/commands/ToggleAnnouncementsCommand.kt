/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import me.gb8.core.Main
import me.gb8.core.chat.ChatInfo
import me.gb8.core.chat.ChatSection
import me.gb8.core.command.BaseCommand
import me.gb8.core.player.PrefixManager
import me.gb8.core.database.GeneralDatabase
import me.gb8.core.util.FoliaCompat
import me.gb8.core.vote.VoteSection
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

import me.gb8.core.util.GlobalUtils.sendMessage

class ToggleAnnouncementsCommand(private val plugin: Main) : BaseCommand(
    "announcements",
    "/announcements",
    "8b8tcore.command.announcements"
) {
    private val database: GeneralDatabase = GeneralDatabase.getInstance()
    private val prefixManager = PrefixManager()

    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player ?: run {
            sender.sendMessage("This command can only be used by players.")
            return
        }

        val hasRank = prefixManager.hasRank(player)
        val section = plugin.getSectionByName("Vote")
        if (section is VoteSection) {
            section.hasVoterRoleAsync(player.name).thenAccept { hasVoterRole ->
                if (!hasRank && !hasVoterRole) {
                    sendMessage(player, "&cYou must be a voter or have any rank to toggle announcements.")
                    return@thenAccept
                }

                database.getPlayerHideAnnouncementsAsync(player.name).thenAcceptAsync { current ->
                    val newValue = !current
                    database.updateHideAnnouncements(player.name, newValue)

                    val chatSection = plugin.getSectionByName("ChatControl") as? ChatSection
                    if (chatSection != null) {
                        val info = chatSection.getInfo(player)
                        if (info != null) info.hideAnnouncements = newValue
                    }

                    FoliaCompat.schedule(player, plugin) {
                        if (newValue) {
                            sendMessage(player, "&aYou will no longer see server announcements.")
                        } else {
                            sendMessage(player, "&aYou will now see server announcements.")
                        }
                    }
                }
            }
        } else {
            if (!hasRank) {
                sendMessage(player, "&cYou must have any rank to toggle announcements.")
                return
            }
            database.getPlayerHideAnnouncementsAsync(player.name).thenAcceptAsync { current ->
                val newValue = !current
                database.updateHideAnnouncements(player.name, newValue)

                val chatSection = plugin.getSectionByName("ChatControl") as? ChatSection
                if (chatSection != null) {
                    val info = chatSection.getInfo(player)
                    if (info != null) info.hideAnnouncements = newValue
                }

                FoliaCompat.schedule(player, plugin) {
                    if (newValue) {
                        sendMessage(player, "&aYou will no longer see server announcements.")
                    } else {
                        sendMessage(player, "&aYou will now see server announcements.")
                    }
                }
            }
        }
    }
}
