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

class GmSurvivalCommand : BaseCommand(
    "gms",
    "/gms",
    "8b8tcore.command.gms",
    "Simple way to change your gamemode to Survival"
) {

    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = getSenderAsPlayer(sender)
        if (player != null) {
            if (player.hasPermission("8b8tcore.command.gms") || player.isOp || player.hasPermission("*")) {
                player.gameMode = GameMode.SURVIVAL
            }
        } else sendErrorMessage(sender, PLAYER_ONLY)
    }
}
