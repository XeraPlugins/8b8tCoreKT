/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.chat

import me.gb8.core.database.GeneralDatabase
import org.bukkit.command.CommandExecutor
import org.bukkit.entity.Player
import me.gb8.core.util.GlobalUtils.sendLocalizedAmpersandMessage
import me.gb8.core.util.GlobalUtils.sendLocalizedMessage

abstract class ChatCommand(protected val manager: ChatSection) : CommandExecutor {

    fun sendWhisper(player: Player, senderInfo: ChatInfo, target: Player, targetInfo: ChatInfo, msg: String) {
        if (!senderInfo.isIgnoring(target.uniqueId)) {
            if (!targetInfo.isIgnoring(player.uniqueId)) {
                targetInfo.replyTarget = player
                senderInfo.replyTarget = target
                val finalMsg = sanitizeMessage(msg)

                sendLocalizedAmpersandMessage(player, "whisper_to", false, target.name, finalMsg)
                val database = GeneralDatabase.getInstance()

                database.isMutedAsync(player.name).thenAccept { isMuted ->
                    if (isMuted) return@thenAccept
                    sendLocalizedAmpersandMessage(target, "whisper_from", false, player.name, finalMsg)
                }

            } else sendLocalizedMessage(player, "whisper_ignoring", false, target.name)
        } else sendLocalizedMessage(player, "whisper_you_are_ignoring", false)
    }

    private fun sanitizeMessage(msg: String): String {
        val sanitized = msg.replace(Regex("(?i)<(?!black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white|gradient|bold|italic|underlined|strikethrough|obfuscated|reset|st|u|i|b|r)([^>]+)>"), "")
        return sanitized
    }
}
