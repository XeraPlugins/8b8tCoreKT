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
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.block.Container

import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage

class SignCommand : BaseCommand(
    "sign",
    "/sign",
    "8b8tcore.command.sign",
    "Sign an item with your username",
    null
) {
    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = getSenderAsPlayer(sender)
        if (player != null) {
            val item = player.inventory.itemInMainHand

            if (item.type == Material.AIR) {
                sendPrefixedLocalizedMessage(player, "sign_fail_no_item")
                return
            }

            if (args.isNotEmpty() && args[0].lowercase() == "all") {
                if (isStorageItem(item)) {
                    signAllItems(item, player.name)
                    sendPrefixedLocalizedMessage(player, "sign_success")
                }
                return
            }

            signSingleItem(item, player)
        } else {
            sendErrorMessage(sender, PLAYER_ONLY)
        }
    }

    private fun isStorageItem(item: ItemStack): Boolean {
        return item.type.toString().endsWith("SHULKER_BOX") ||
                item.type.toString().endsWith("CHEST") ||
                item.type.toString().endsWith("TRAPPED_CHEST") ||
                item.type.toString().endsWith("BARREL") ||
                item.type.toString().endsWith("DISPENSER") ||
                item.type.toString().endsWith("DROPPER") ||
                item.type.toString().endsWith("HOPPER")
    }

    private fun signAllItems(containerItem: ItemStack, playerName: String) {
        val blockStateMeta = containerItem.itemMeta as? BlockStateMeta
        if (blockStateMeta != null) {
            val blockState = blockStateMeta.blockState
            if (blockState is Container) {
                val containerInventory = blockState.inventory

                for (item in containerInventory.contents) {
                    if (item != null && !isItemSigned(item)) {
                        signItem(item, playerName)
                    }
                }

                blockStateMeta.blockState = blockState
                containerItem.itemMeta = blockStateMeta
            }
        }
    }

    private fun signSingleItem(item: ItemStack, player: Player) {
        if (isItemSigned(item)) {
            sendPrefixedLocalizedMessage(player, "sign_fail_already_signed")
            return
        }

        signItem(item, player.name)
        sendPrefixedLocalizedMessage(player, "sign_success")
    }

    private fun isItemSigned(item: ItemStack): Boolean {
        val meta = item.itemMeta
        if (meta != null && meta.hasLore()) {
            val loreList = meta.lore()
            if (loreList != null) {
                for (loreComp in loreList) {
                    val lore = GlobalUtils.getStringContent(loreComp)
                    if (lore.contains("by @")) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun signItem(item: ItemStack, playerName: String) {
        val meta = item.itemMeta
        val signature = Component.text("by ", NamedTextColor.BLUE)
                .append(Component.text("@${playerName}", NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false)

        if (meta != null) {
            val lore = if (meta.hasLore()) ArrayList(meta.lore()) else ArrayList<Component>()
            lore.add(signature)
            meta.lore(lore)
            item.itemMeta = meta
        }
    }
}
