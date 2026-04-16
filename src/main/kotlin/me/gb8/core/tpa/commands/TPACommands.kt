/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.tpa.commands

import me.gb8.core.Localization
import me.gb8.core.tpa.TPASection
import me.gb8.core.util.GlobalUtils
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.TextReplacementConfig
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import me.gb8.core.util.GlobalUtils.sendMessage
import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage
import me.gb8.core.util.GlobalUtils.sendPrefixedComponent

class TPACommands {

    class TPACommand(private val main: TPASection) : CommandExecutor {
        override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
            if (sender !is Player) {
                sendMessage(sender, "&cYou must be a player")
                return true
            }
            val from = sender

            if (args.isEmpty()) {
                sendPrefixedLocalizedMessage(from, "tpa_syntax")
                return true
            }

            val to = Bukkit.getPlayer(args[0])
            if (to == null) {
                sendPrefixedLocalizedMessage(from, "tpa_player_not_online", args[0])
                return true
            }
            val target = to

            if (main.checkToggle(target) || main.checkBlocked(target, from)) {
                sendPrefixedLocalizedMessage(from, "tpa_request_blocked")
                return true
            }

            if (target == from) {
                sendPrefixedLocalizedMessage(from, "tpa_self_tpa")
                return true
            }

            if (GlobalUtils.isTeleportRestricted(from)) {
                val range = GlobalUtils.getTeleportRestrictionRange(from)
                sendPrefixedLocalizedMessage(from, "tpa_too_close", range)
                return true
            }

            val acceptButton = Component.text("ACCEPT").clickEvent(ClickEvent.runCommand("/tpayes ${from.name}"))
            val denyButton = Component.text("DENY").clickEvent(ClickEvent.runCommand("/tpano ${from.name}"))
                    val acceptReplace = TextReplacementConfig.builder().match("accept").replacement(acceptButton).build()
            val denyReplace = TextReplacementConfig.builder().match("deny").replacement(denyButton).build()

                    @Suppress("DEPRECATION")
                    val toLocale = target.locale()
                    val loc = Localization.getLocalization(toLocale.language)
            val template = String.format(loc.get("tpa_request_received"), from.name, "accept", "deny")
            val message = GlobalUtils.translateChars(template).replaceText(acceptReplace).replaceText(denyReplace) as TextComponent

            if (main.hasRequested(from, target) || main.hasHereRequested(from, target)) {
                sendPrefixedLocalizedMessage(from, "tpa_already_sent", target.name)
            } else {
                sendPrefixedComponent(target, message)
                sendPrefixedLocalizedMessage(from, "tpa_request_sent", target.name)
                main.registerRequest(from, target)
            }
            return true
        }
    }

    class TPAAcceptCommand(private val main: TPASection) : CommandExecutor {
        override fun onCommand(sender: CommandSender, command: Command, s: String, args: Array<String>): Boolean {
            if (sender is Player) {
                val requested = sender
                var requester: Player? = null
                if (args.isEmpty()) {
                    requester = main.getLastRequest(requested)
                    if (requester == null) {
                        sendPrefixedLocalizedMessage(requested, "tpa_no_request_found")
                        return true
                    }
                } else if (args.size == 1) {
                    val potentialRequester = Bukkit.getPlayer(args[0])
                    if (potentialRequester != null && main.hasRequested(potentialRequester, requested)) {
                        requester = potentialRequester
                    } else if (potentialRequester != null && main.hasHereRequested(potentialRequester, requested)) {
                        requester = potentialRequester
                    }
                    if (requester == null) {
                        sendPrefixedLocalizedMessage(requested, "tpa_no_request_found")
                        return true
                    }
                } else {
                    sendPrefixedLocalizedMessage(requested, "tpa_syntax")
                    return true
                }

                if (main.hasHereRequested(requester, requested)) {
                    if (GlobalUtils.isTeleportRestricted(requested)) {
                        val range = GlobalUtils.getTeleportRestrictionRange(requested)
                        sendPrefixedLocalizedMessage(requested, "tpa_too_close", range)
                        return true
                    }
                    acceptTPAHere(requested, requester)
                } else if (main.hasRequested(requester, requested)) {
                    if (GlobalUtils.isTeleportRestricted(requester)) {
                        val range = GlobalUtils.getTeleportRestrictionRange(requester)
                        sendPrefixedLocalizedMessage(requested, "tpa_too_close_other", range, requester.name)
                        return true
                    }
                    acceptTPA(requested, requester)
                }
            } else {
                sendMessage(sender, "&cYou must be a player")
            }
            return true
        }

        private fun acceptTPA(requested: Player, requester: Player?) {
            if (requester == null || !requester.isOnline || !requested.isOnline) {
                sendPrefixedLocalizedMessage(requested, "tpa_no_request_found")
                if (requester != null) main.removeRequest(requester, requested)
                return
            }
            main.main.lastLocations[requester.uniqueId] = requester.location
            val targetLoc = requested.location
            targetLoc.world?.getChunkAtAsyncUrgently(targetLoc.block)?.thenAccept {
                if (requester.isOnline) {
                    requester.teleportAsync(targetLoc, PlayerTeleportEvent.TeleportCause.PLUGIN)
                }
            }
            sendPrefixedLocalizedMessage(requester, "tpa_teleporting")
            sendPrefixedLocalizedMessage(requested, "tpa_teleporting")
            main.removeRequest(requester, requested)
        }

        private fun acceptTPAHere(requested: Player, requester: Player?) {
            if (requester == null || !requester.isOnline || !requested.isOnline) {
                sendPrefixedLocalizedMessage(requested, "tpa_no_request_found")
                if (requester != null) main.removeHereRequest(requester, requested)
                return
            }
            main.main.lastLocations[requested.uniqueId] = requested.location
            val targetLoc = requester.location
            targetLoc.world?.getChunkAtAsyncUrgently(targetLoc.block)?.thenAccept {
                if (requested.isOnline) {
                    requested.teleportAsync(targetLoc, PlayerTeleportEvent.TeleportCause.PLUGIN)
                }
            }
            sendPrefixedLocalizedMessage(requester, "tpa_teleporting")
            sendPrefixedLocalizedMessage(requested, "tpa_teleporting")
            main.removeHereRequest(requester, requested)
        }
    }

    class TPADenyCommand(private val main: TPASection) : CommandExecutor {
        override fun onCommand(sender: CommandSender, command: Command, s: String, args: Array<String>): Boolean {
            if (sender !is Player) {
                sendMessage(sender, "&cYou must be a player")
                return true
            }
            val requested = sender
            if (args.isEmpty()) {
                val lastRequester = main.getLastRequest(requested)
                denyTPA(requested, lastRequester)
            } else if (args.size == 1) {
                val targetPlayer = Bukkit.getPlayer(args[0])
                if (targetPlayer != null && main.hasRequested(targetPlayer, requested)) {
                    denyTPA(requested, targetPlayer)
                } else {
                    sendPrefixedLocalizedMessage(requested, "tpa_no_request_found")
                }
            } else {
                sendPrefixedLocalizedMessage(requested, "tpa_syntax")
            }
            return true
        }

        private fun denyTPA(requested: Player, requester: Player?) {
            if (requester == null) {
                sendPrefixedLocalizedMessage(requested, "tpa_no_request_found")
                return
            }
            sendPrefixedLocalizedMessage(requester, "tpa_request_denied_from", requested.name)
            sendPrefixedLocalizedMessage(requested, "tpa_request_denied_to", requester.name)
            if (main.hasRequested(requester, requested)) main.removeRequest(requester, requested)
            if (main.hasHereRequested(requester, requested)) main.removeHereRequest(requester, requested)
        }
    }

    class TPACancelCommand(private val main: TPASection) : CommandExecutor {
        override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
            if (sender is Player) {
                val player = sender
                if (args.isEmpty()) {
                    for (onlinePlayer in Bukkit.getOnlinePlayers()) {
                        if (main.hasRequested(player, onlinePlayer)) {
                            sendPrefixedLocalizedMessage(player, "tpa_request_cancelled_from", onlinePlayer.name)
                            sendPrefixedLocalizedMessage(onlinePlayer, "tpa_request_cancelled_to", player.name)
                            main.removeRequest(player, onlinePlayer)
                        }
                        if (main.hasHereRequested(player, onlinePlayer)) {
                            sendPrefixedLocalizedMessage(player, "tpa_request_cancelled_from", onlinePlayer.name)
                            sendPrefixedLocalizedMessage(onlinePlayer, "tpa_request_cancelled_to", player.name)
                            main.removeHereRequest(player, onlinePlayer)
                        }
                    }
                } else if (args.size == 1) {
                    val target = Bukkit.getPlayer(args[0])
                    if (target == null) {
                        sendPrefixedLocalizedMessage(player, "tpa_player_not_online", args[0])
                        return true
                    }
                    if (main.hasRequested(player, target)) {
                        main.removeRequest(player, target)
                        sendPrefixedLocalizedMessage(player, "tpa_request_cancelled_from", target.name)
                        sendPrefixedLocalizedMessage(target, "tpa_request_cancelled_to", player.name)
                    } else {
                        sendPrefixedLocalizedMessage(player, "tpa_no_request_found")
                    }
                }
            } else {
                sendMessage(sender, "&cYou must be a player")
            }
            return true
        }
    }

    class TPAHereCommand(private val main: TPASection) : CommandExecutor {
        override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
            if (sender is Player) {
                val from = sender
                if (args.size == 1) {
                    val to = Bukkit.getPlayer(args[0])
                    if (to == null) {
                        sendPrefixedLocalizedMessage(from, "tpa_player_not_online", args[0])
                        return true
                    }
                    val target = to

                    if (target == from) {
                        sendPrefixedLocalizedMessage(from, "tpa_self_tpa")
                        return true
                    }
                    if (main.checkToggle(target) || main.checkBlocked(target, from)) {
                        sendPrefixedLocalizedMessage(from, "tpa_request_blocked")
                        return true
                    }
                    val acceptButton = Component.text("ACCEPT").clickEvent(ClickEvent.runCommand("/tpayes ${from.name}"))
                    val denyButton = Component.text("DENY").clickEvent(ClickEvent.runCommand("/tpano ${from.name}"))
                    val acceptReplace = TextReplacementConfig.builder().match("accept").replacement(acceptButton).build()
                    val denyReplace = TextReplacementConfig.builder().match("deny").replacement(denyButton).build()

            @Suppress("DEPRECATION")
            val toLocale = target.locale()
                    val loc = Localization.getLocalization(toLocale.language)
                    val str = String.format(loc.get("tpahere_request_received"), from.name, "accept", "deny")
                    val component = GlobalUtils.translateChars(str).replaceText(acceptReplace).replaceText(denyReplace) as TextComponent

                    if (main.hasRequested(from, target) || main.hasHereRequested(from, target)) {
                        sendPrefixedLocalizedMessage(from, "tpa_already_sent", target.name)
                    } else {
                        sendPrefixedComponent(target, component)
                        sendPrefixedLocalizedMessage(from, "tpa_request_sent", target.name)
                        main.registerHereRequest(from, target)
                    }
                } else sendPrefixedLocalizedMessage(from, "tpahere_syntax")
            } else sendMessage(sender, "&cYou must be a player")
            return true
        }
    }

    class TPAIgnoreCommand(private val main: TPASection) : CommandExecutor {
        override fun onCommand(sender: CommandSender, command: Command, alias: String, args: Array<String>): Boolean {
            if (sender is Player) {
                val from = sender
                if (args.size == 1) {
                    val blocked = Bukkit.getPlayer(args[0])
                    if (blocked == null) {
                        sendPrefixedLocalizedMessage(from, "tpa_player_not_online", args[0])
                        return true
                    }
                    if (main.checkBlocked(from, blocked)) {
                        sendPrefixedLocalizedMessage(from, "tpa_requests_unblocked", blocked.name)
                        main.removeBlockedPlayer(from, blocked)
                    } else {
                        sendPrefixedLocalizedMessage(from, "tpa_requests_blocked", blocked.name)
                        main.addBlockedPlayer(from, blocked)
                    }
                } else {
                    sendPrefixedLocalizedMessage(from, "tpa_block_syntax")
                }
            } else {
                sendMessage(sender, "You must be a player.")
            }
            return true
        }
    }

    class TPAToggleCommand(private val main: TPASection) : CommandExecutor {
        override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
            if (sender !is Player) {
                sendMessage(sender, "&cYou must be a player")
                return true
            }
            val player = sender
            if (args.isNotEmpty()) {
                sendPrefixedLocalizedMessage(player, "tpatoggle_syntax")
                return true
            }
            main.togglePlayer(player)
            if (main.checkToggle(player)) {
                sendPrefixedLocalizedMessage(player, "tpa_requests_disabled")
            } else {
                sendPrefixedLocalizedMessage(player, "tpa_requests_enabled")
            }
            return true
        }
    }
}
