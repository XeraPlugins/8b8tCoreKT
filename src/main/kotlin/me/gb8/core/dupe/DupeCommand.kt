/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.dupe

import me.gb8.core.dupe.DupeSection
import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.jetbrains.annotations.NotNull
import java.util.UUID
import me.gb8.core.util.GlobalUtils.sendMessage
import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage

class DupeCommand(private val main: DupeSection) : CommandExecutor {

    private val cooldowns = mutableMapOf<UUID, Long>()

    companion object {
        private const val PERM_NORMAL = "8b8tcore.command.dupe"
        private const val PERM_FULL = "8b8tcore.command.fulldupe"
    }

    override fun onCommand(@NotNull sender: CommandSender, @NotNull command: Command, @NotNull label: String, args: Array<String>): Boolean {
        val player = sender as? Player ?: run {
            sendMessage(sender, "&cYou must be a player to use this command.")
            return true
        }

        if (!main.getPlugin().config.getBoolean("DupeCommand.enabled", false)) return true

        val votersOnly = main.getPlugin().config.getBoolean("DupeCommand.votersOnly", false)
        if (votersOnly && !player.hasPermission("8b8tcore.dupe.command")) return true

        val item = player.inventory.itemInMainHand
        if (item.type == Material.AIR) {
            sendPrefixedLocalizedMessage(player, "dupe_failed")
            return true
        }

        if (player.hasPermission(PERM_FULL) || player.isOp) {
            handleFullDupe(player, item, args)
        } else if (player.hasPermission(PERM_NORMAL)) {
            handleNormalDupe(player, item)
        } else {
            sendPrefixedLocalizedMessage(player, "dupe_permission")
            player.health = 0.0
        }

        return true
    }

    private fun handleFullDupe(player: Player, item: ItemStack, args: Array<String>) {
        if (checkCooldown(player, 5000L)) return

        var copies = 1
        if (args.isNotEmpty()) {
            try {
                copies = args[0].toInt()
                if (copies < 1 || copies > 9) copies = 1
            } catch (e: NumberFormatException) { }
        }
        processDuplication(player, item, copies, 60)
    }

    private fun handleNormalDupe(player: Player, item: ItemStack) {
        if (checkCooldown(player, 30000L)) return
        processDuplication(player, item, 1, 9)
    }

    private fun checkCooldown(player: Player, cooldownTime: Long): Boolean {
        val lastUsed = cooldowns.getOrDefault(player.uniqueId, 0L)
        if (System.currentTimeMillis() - lastUsed < cooldownTime) {
            sendPrefixedLocalizedMessage(player, "framedupe_cooldown")
            return true
        }
        return false
    }

    private fun processDuplication(player: Player, item: ItemStack, copies: Int, chunkLimit: Int) {
        val currentItems = countItemsInChunk(player.location.chunk)

        if (currentItems + copies > chunkLimit) {
            sendPrefixedLocalizedMessage(player, "framedupe_items_limit")
            return
        }

        repeat(copies) {
            player.world.dropItemNaturally(player.location, item.clone())
        }

        sendPrefixedLocalizedMessage(player, "dupe_success")
        cooldowns[player.uniqueId] = System.currentTimeMillis()
    }

    private fun countItemsInChunk(chunk: Chunk): Int {
        var count = 0
        for (entity in chunk.entities) {
            if (entity is Item) {
                count++
            }
        }
        return count
    }
}
