/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import me.gb8.core.command.BaseCommand
import me.gb8.core.util.GlobalUtils
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage

class RenameCommand : BaseCommand(
    "rename",
    "/rename <name>",
    "8b8tcore.command.rename"
) {
    private val miniMessage = MiniMessage.miniMessage()

    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player ?: run {
            sender.sendMessage("This command can only be used by players.")
            return
        }

        if (args.isEmpty()) {
            sendPrefixedLocalizedMessage(player, "rename_no_name_provided")
            return
        }

        val itemName = args.joinToString(" ").replace(Regex("(?i)<(hover:.*?|click:.*?|insert:.*?|selector:.*?|nbt:.*?|newline)[^>]*>"), "").trim()

        val miniMessageFormatted = GlobalUtils.convertToMiniMessageFormat(itemName) ?: itemName

        val displayName = miniMessage.deserialize(miniMessageFormatted)

        val itemInHand = player.inventory.itemInMainHand
        if (itemInHand.type.isAir) {
            sendPrefixedLocalizedMessage(player, "rename_no_item")
            return
        }

        val meta = itemInHand.itemMeta
        if (meta == null) {
            sendPrefixedLocalizedMessage(player, "rename_invalid_item")
            return
        }

        if (extractPlainText(displayName).length > 50) {
            sendPrefixedLocalizedMessage(player, "rename_too_large")
            return
        }

        meta.displayName(displayName.decoration(TextDecoration.ITALIC, false))
        itemInHand.itemMeta = meta

        val displayNameLegacy = miniMessage.serialize(displayName)
        sendPrefixedLocalizedMessage(player, "rename_success", displayNameLegacy)
    }

    private fun extractPlainText(component: Component): String {
        val plainText = StringBuilder()
        extractPlainTextRecursive(component, plainText)
        return plainText.toString()
    }

    private fun extractPlainTextRecursive(component: Component, plainText: StringBuilder) {
        if (component is TextComponent) {
            plainText.append(component.decoration(TextDecoration.ITALIC, false).content())
        }
        for (child in component.children()) {
            extractPlainTextRecursive(child, plainText)
        }
    }
}
