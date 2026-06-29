/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import me.gb8.core.command.BaseTabCommand
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.MenuType
import org.bukkit.inventory.view.builder.LocationInventoryViewBuilder
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage

class TableCommand : BaseTabCommand("table", "/table <type>", "8b8tcore.command.table") {
    private val tableTypes = listOf(
        "crafting",
        "cartography",
        "stonecutter",
        "enchanting",
        "anvil",
        "grindstone",
        "loom",
        "smithing"
    )

    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player ?: run {
            sender.sendMessage("Only players can use this command.")
            return
        }

        if (args.size != 1) {
            sendPrefixedLocalizedMessage(player, "table_usage")
            return
        }

        val type = args[0].lowercase()

        when (type) {
            "crafting", "crafting_table" -> openMenu(player, MenuType.CRAFTING)
            "cartography", "cartography_table" -> openMenu(player, MenuType.CARTOGRAPHY_TABLE)
            "stonecutter" -> openMenu(player, MenuType.STONECUTTER)
            "enchanting", "enchanting_table" -> openMenu(player, MenuType.ENCHANTMENT)
            "anvil" -> openMenu(player, MenuType.ANVIL)
            "grindstone" -> openMenu(player, MenuType.GRINDSTONE)
            "loom" -> openMenu(player, MenuType.LOOM)
            "smithing", "smithing_table" -> openMenu(player, MenuType.SMITHING)
            else -> {
                sendPrefixedLocalizedMessage(player, "table_invalid_type")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun openMenu(player: Player, menuType: MenuType.Typed<*, *>) {
        val builder = menuType.builder() as LocationInventoryViewBuilder<InventoryView>
        player.openInventory(builder.checkReachable(false).build(player))
    }

    override fun onTab(sender: CommandSender, args: Array<String>): List<String> {
        if (args.size == 1) {
            return tableTypes.filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return emptyList()
    }
}
