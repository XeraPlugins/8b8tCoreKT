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

class ToggleJoinMessagesCommand(private val plugin: Main) : BaseCommand(
    "togglejoinmessages",
    "/togglejoinmessages",
    "8b8tcore.command.togglejoinmessages"
) {
    private val database = GeneralDatabase.getInstance()

    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player ?: run {
            sender.sendMessage("This command can only be used by players.")
            return
        }

        val chatSection = Main.instance.getSectionByName("ChatControl") as? me.gb8.core.chat.ChatSection ?: return
        val info = chatSection.getInfo(player) ?: return

        val currentValue = info.joinMessages
        val newValue = !currentValue

        info.joinMessages = newValue
        database.updateShowJoinMsg(player.name, newValue)

        val messageKey = if (newValue) "show_join_leave_messages_true" else "show_join_leave_messages_false"
        sendPrefixedLocalizedMessage(player, messageKey)
    }
}
