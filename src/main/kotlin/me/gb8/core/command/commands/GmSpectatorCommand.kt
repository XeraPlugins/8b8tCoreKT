/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import me.gb8.core.command.BaseCommand
import org.bukkit.GameMode
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class GmSpectatorCommand : BaseCommand(
    "gmsp",
    "/gmsp",
    "8b8tcore.command.gmsp",
    "Simple way to change your gamemode to Spectator"
) {

    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = getSenderAsPlayer(sender)
        if (player != null) {
            if (player.hasPermission("8b8tcore.command.gmsp") || player.isOp || player.hasPermission("*")) {
                player.gameMode = GameMode.SPECTATOR
            }
        } else sendErrorMessage(sender, PLAYER_ONLY)
    }
}
