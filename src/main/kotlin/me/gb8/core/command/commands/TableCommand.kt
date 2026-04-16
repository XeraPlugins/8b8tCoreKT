/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import me.gb8.core.command.BaseTabCommand
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage

class TableCommand : BaseTabCommand("table", "/table <type>", "8b8tcore.command.table") {
    private val tableTypes: List<String> = getTableTypes()

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
            "crafting", "crafting_table" -> player.openInventory(Bukkit.createInventory(player, InventoryType.WORKBENCH))
            "cartography", "cartography_table" -> player.openInventory(Bukkit.createInventory(player, InventoryType.CARTOGRAPHY))
            "stonecutter" -> player.openInventory(Bukkit.createInventory(player, InventoryType.STONECUTTER))
            "enchanting", "enchanting_table" -> player.openInventory(Bukkit.createInventory(player, InventoryType.ENCHANTING))
            "anvil" -> player.openInventory(Bukkit.createInventory(player, InventoryType.ANVIL))
            "grindstone" -> player.openInventory(Bukkit.createInventory(player, InventoryType.GRINDSTONE))
            "loom" -> player.openInventory(Bukkit.createInventory(player, InventoryType.LOOM))
            "smithing", "smithing_table" -> player.openInventory(Bukkit.createInventory(player, InventoryType.SMITHING))
            else -> {
                sendPrefixedLocalizedMessage(player, "table_invalid_type")
            }
        }
    }

    override fun onTab(sender: CommandSender, args: Array<String>): List<String> {
        if (args.size == 1) {
            return tableTypes.filter { it.lowercase().startsWith(args[0].lowercase()) }
        }
        return mutableListOf()
    }

    private fun getTableTypes(): List<String> {
        return listOf(
                "crafting",
                "cartography",
                "stonecutter",
                "enchanting",
                "anvil",
                "grindstone",
                "loom",
                "smithing"
        )
    }
}
