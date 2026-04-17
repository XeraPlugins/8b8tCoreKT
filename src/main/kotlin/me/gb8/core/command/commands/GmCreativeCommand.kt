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

class GmCreativeCommand : BaseCommand(
    "gmc",
    "/gmc",
    "8b8tcore.command.gmc",
    "Simple way to change your gamemode to Creative"
) {

    override fun execute(sender: CommandSender, args: Array<String>) {
        getSenderAsPlayer(sender)?.let { player ->
            if (player.hasPermission("8b8tcore.command.gmc") || player.isOp || player.hasPermission("*")) {
                player.gameMode = GameMode.CREATIVE
            }
        } ?: sendErrorMessage(sender, PLAYER_ONLY)
    }
}
