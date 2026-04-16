/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import me.gb8.core.command.BaseCommand
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import me.gb8.core.util.GlobalUtils.sendMessage

class OpenInv : BaseCommand(
    "open",
    "/open <inv | ender> <player>",
    "8b8tcore.command.openinv",
    "Open peoples inventories",
    arrayOf("inv::Open the inventory of the specified player", "ender::Open the ender chest of the specified player")
) {

    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = getSenderAsPlayer(sender)
        if (player != null) {
            if (args.size < 2) {
                sendErrorMessage(sender, usage)
            } else {
                val target = Bukkit.getPlayer(args[1])
                if (target == null) {
                    sendMessage(sender, "&cPlayer&r&a %s&r&c not online&r", args[1])
                    return
                }
                when (args[0]) {
                    "ender" -> player.openInventory(target.enderChest)
                    "inv", "inventory" -> {
                        val title = net.kyori.adventure.text.Component.text(target.name + "'s Inventory")
                        val inv = Bukkit.createInventory(null, 36, title)
                        inv.contents = target.inventory.contents.copyOfRange(0, 36)
                        player.openInventory(inv)
                    }
                    else -> sendErrorMessage(sender, "Unknown argument " + args[0])
                }
            }
        } else sendErrorMessage(sender, PLAYER_ONLY)
    }
}
