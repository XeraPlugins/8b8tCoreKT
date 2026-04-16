/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.chat.ChatSection
import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent

class CommandWhitelist(private val main: ChatSection) : Listener {

    @EventHandler
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        if (event.player.isOp) return
        val allowedCommands = main.config?.getStringList("CommandWhitelist") ?: emptyList()
        val command = event.message.split(" ")[0].lowercase()
        val isAllowed = allowedCommands.any { it.lowercase() == command }
        if (!isAllowed) {
            event.isCancelled = true
            sendPrefixedLocalizedMessage(event.player, "cmdwhitelist_cmd_not_allowed", command)
        }
    }
}
