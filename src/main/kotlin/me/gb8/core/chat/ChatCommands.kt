/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.chat

import me.gb8.core.chat.ChatCommand
import me.gb8.core.chat.ChatSection
import me.gb8.core.util.GlobalUtils.sendMessage
import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ChatCommands {

    class IgnoreCommand(private val manager: ChatSection) : CommandExecutor {
        override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
            val player = sender as? Player ?: run {
                sendMessage(sender, "&cYou must be a player")
                return true
            }
            when (args.size) {
                1 -> {
                    val info = manager.getInfo(player) ?: return true
                    val target = Bukkit.getOfflinePlayer(args[0])
                    if (!info.isIgnoring(target.uniqueId)) {
                        info.ignorePlayer(target.uniqueId)
                        sendPrefixedLocalizedMessage(player, "ignore_successful", target.name)
                    } else sendPrefixedLocalizedMessage(player, "already_ignoring")
                }
                else -> sendPrefixedLocalizedMessage(player, "ignore_command_syntax")
            }
            return true
        }
    }

    class UnIgnoreCommand(private val manager: ChatSection) : CommandExecutor {
        override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
            val player = sender as? Player ?: run {
                sendMessage(sender, "&cYou must be a player")
                return true
            }
            when (args.size) {
                1 -> {
                    val info = manager.getInfo(player) ?: return true
                    val target = Bukkit.getOfflinePlayer(args[0])
                    if (info.isIgnoring(target.uniqueId)) {
                        info.unignorePlayer(target.uniqueId)
                        sendPrefixedLocalizedMessage(player, "unignore_successful", target.name)
                    } else sendPrefixedLocalizedMessage(player, "unignore_not_ignoring", target.name)
                }
                else -> sendPrefixedLocalizedMessage(player, "unignore_command_syntax")
            }
            return true
        }
    }

    class IgnoreListCommand(private val manager: ChatSection) : CommandExecutor {
        override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
            val player = sender as? Player ?: run {
                sendMessage(sender, "You must be a player to use this command.")
                return true
            }
            val info = manager.getInfo(player)
            when {
                info == null -> sendPrefixedLocalizedMessage(player, "ignorelist_failed")
                info.ignoring.isEmpty() -> sendPrefixedLocalizedMessage(player, "ignorelist_not_ignoring")
                else -> {
                    val ignoredList = info.ignoring.mapNotNull { uuid -> Bukkit.getServer().getOfflinePlayer(uuid).name }
                        .filter { it.isNotEmpty() }
                        .joinToString("&3, &c")
                    sendPrefixedLocalizedMessage(player, "ignorelist_successful", ignoredList)
                }
            }
            return true
        }
    }

    class MessageCommand(manager: ChatSection) : ChatCommand(manager) {
        override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
            val player = sender as? Player ?: run {
                sendMessage(sender, "&cYou must be a player")
                return true
            }
            when {
                args.size < 2 -> sendPrefixedLocalizedMessage(player, "msg_command_syntax")
                else -> {
                    val target = Bukkit.getPlayer(args[0])
                    if (target != null && target.isOnline) {
                        val senderInfo = manager.getInfo(player) ?: return true
                        val targetInfo = manager.getInfo(target) ?: return true
                        val msg = args.drop(1).joinToString(" ")
                        sendWhisper(player, senderInfo, target, targetInfo, msg)
                    } else sendPrefixedLocalizedMessage(player, "msg_could_not_find_player", args[0])
                }
            }
            return true
        }
    }

    class ReplyCommand(manager: ChatSection) : ChatCommand(manager) {
        override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
            val player = sender as? Player ?: run {
                sendMessage(sender, "&cYou must be a player")
                return true
            }
            when {
                args.isEmpty() -> sendPrefixedLocalizedMessage(player, "reply_command_syntax")
                else -> {
                    val senderInfo = manager.getInfo(player) ?: return true
                    val replyTarget = senderInfo.replyTarget
                    if (replyTarget != null && replyTarget.isOnline) {
                        val targetInfo = manager.getInfo(replyTarget) ?: return true
                        val msg = args.joinToString(" ")
                        sendWhisper(player, senderInfo, replyTarget, targetInfo, msg)
                    } else sendPrefixedLocalizedMessage(player, "reply_no_target")
                }
            }
            return true
        }
    }

    class ToggleChatCommand(private val manager: ChatSection) : CommandExecutor {
        override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
            val player = sender as? Player ?: run {
                sendMessage(sender, "&cYou must be a player")
                return true
            }
            val info = manager.getInfo(player) ?: return true
            val newState = !info.isToggledChat
            info.toggledChat = newState
            val messageKey = if (newState) "togglechat_chat_disabled" else "togglechat_chat_enabled"
            sendPrefixedLocalizedMessage(player, messageKey)
            return true
        }
    }
}
