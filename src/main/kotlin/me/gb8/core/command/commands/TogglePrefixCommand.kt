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
import me.gb8.core.player.PrefixManager
import me.gb8.core.database.GeneralDatabase
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import me.gb8.core.util.GlobalUtils.sendMessage

class TogglePrefixCommand(private val plugin: Main) : BaseCommand(
    "toggleprefix",
    "/toggleprefix",
    "8b8tcore.command.toggleprefix"
) {
    private val miniMessage = MiniMessage.miniMessage()
    private val database = GeneralDatabase.getInstance()
    private val prefixManager = PrefixManager()

    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player ?: run {
            sender.sendMessage("This command can only be used by players.")
            return
        }

        if (!prefixManager.hasRank(player)) {
            sendMessage(player, "&cYou must have a rank to hide your prefix.")
            return
        }

        database.getPlayerHidePrefixAsync(player.name).thenAccept { current ->
            val newValue = !current
            database.updateHidePrefix(player.name, newValue)

            val chatSection = Main.instance.getSectionByName("ChatControl") as? me.gb8.core.chat.ChatSection
            if (chatSection != null) {
                val info = chatSection.getInfo(player)
                if (info != null) {
                    info.hidePrefix = newValue
                    val tag = if (newValue) "" else prefixManager.getPrefix(info)
                    if (tag.isEmpty()) {
                        player.playerListName(player.displayName())
                    } else {
                        player.playerListName(miniMessage.deserialize(tag).append(player.displayName()))
                    }
                }
            }

            if (newValue) {
                sendMessage(player, "&aYour prefix is now hidden.")
            } else {
                sendMessage(player, "&aYour prefix is now visible.")
            }
        }
    }
}
