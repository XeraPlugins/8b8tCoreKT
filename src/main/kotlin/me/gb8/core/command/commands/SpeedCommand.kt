/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import me.gb8.core.command.BaseCommand
import org.bukkit.command.CommandSender
import me.gb8.core.util.GlobalUtils.sendMessage

class SpeedCommand : BaseCommand(
    "speed",
    "/speed <number>",
    "8b8tcore.command.speed",
    "Turn up your fly speed"
) {

    override fun execute(sender: CommandSender, args: Array<String>) {
        getSenderAsPlayer(sender)?.let { player ->
            try {
                if (args.isNotEmpty()) {
                    val speed = args[0].toFloat()
                    if (speed <= 1) {
                        player.flySpeed = speed
                        sendMessage(player, "&3Fly speed set to&r&a %f", speed)
                    } else sendMessage(player, "Flying speed must not be above 1")
                } else {
                    sendMessage(sender, "&3Please note that the default flight speed is&r&a 0.1")
                    sendErrorMessage(sender, usage)
                }
            } catch (e: NumberFormatException) {
                sendErrorMessage(player, usage)
            }
        } ?: sendErrorMessage(sender, PLAYER_ONLY)
    }
}
