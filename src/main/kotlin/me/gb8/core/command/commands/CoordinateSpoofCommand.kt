/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 *
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import me.gb8.core.Main
import me.gb8.core.chat.ChatSection
import me.gb8.core.command.BaseCommand
import me.gb8.core.coordinate.CoordinateSpoofing
import me.gb8.core.database.GeneralDatabase
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CoordinateSpoofCommand(private val plugin: Main) : BaseCommand(
    "coordinatespoof",
    "/coordinatespoof",
    "8b8tcore.command.coordinatespoof",
    "Toggle your coordinate offset"
) {
    private val database = GeneralDatabase.getInstance()

    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player ?: run {
            sender.sendMessage(PLAYER_ONLY)
            return
        }
        if (args.isNotEmpty()) {
            player.sendMessage(Component.text(usage, NamedTextColor.RED))
            return
        }
        if (!CoordinateSpoofing.isAvailable) {
            player.sendMessage(Component.text("Coordinate spoofing is currently unavailable.", NamedTextColor.RED))
            return
        }
        if (CoordinateSpoofing.isBedrock(player)) {
            player.sendMessage(
                Component.text("Coordinate spoofing is available to Java Edition players only.", NamedTextColor.RED)
            )
            return
        }

        val info = (plugin.getSectionByName("ChatControl") as? ChatSection)?.getInfo(player)
        if (info?.dataLoaded != true) {
            player.sendMessage(Component.text("Your player settings are still loading.", NamedTextColor.RED))
            return
        }

        val previous = info.coordinateSpoofing
        val enabled = !previous
        runCatching {
            if (enabled) CoordinateSpoofing.enable(player) else CoordinateSpoofing.disable(player)
        }.onFailure {
            plugin.logger.warning("Failed to change coordinate spoofing for ${player.name}: ${it.message}")
            player.sendMessage(Component.text("Could not change coordinate spoofing.", NamedTextColor.RED))
            return
        }

        info.coordinateSpoofing = enabled
        database.upsertPlayer(player.name, "coordinateSpoofing", enabled).whenComplete { _, error ->
            player.scheduler.run(plugin, {
                if (!player.isOnline) return@run
                if (error != null) {
                    info.coordinateSpoofing = previous
                    runCatching {
                        if (previous) CoordinateSpoofing.enable(player) else CoordinateSpoofing.disable(player)
                    }
                    player.sendMessage(Component.text("Could not save coordinate spoofing.", NamedTextColor.RED))
                    return@run
                }
                val state = if (enabled) "enabled" else "disabled"
                player.sendMessage(Component.text("Coordinate spoofing $state.", NamedTextColor.GREEN))
            }, null)
        }
    }

}
