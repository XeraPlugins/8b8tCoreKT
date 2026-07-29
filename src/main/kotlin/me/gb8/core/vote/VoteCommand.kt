/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.vote

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.jetbrains.annotations.NotNull
import me.gb8.core.util.GlobalUtils.sendMessage
import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage
import me.gb8.core.util.FoliaCompat
import java.util.concurrent.CompletableFuture

sealed class VoteCommand {
    data object Test : VoteCommand()
    data object Clear : VoteCommand()
    data object Check : VoteCommand()
    data object Cleanup : VoteCommand()
    data object Save : VoteCommand()
    data object List : VoteCommand()
    data class Migrate(val player: String, val days: Long) : VoteCommand()
    data object ScanLegacy : VoteCommand()
}

class VoteCommandExecutor(private val voteSection: VoteSection) : CommandExecutor {
    
    override fun onCommand(@NotNull sender: CommandSender, @NotNull command: Command, @NotNull s: String, @NotNull args: Array<String>): Boolean {
        if (args.isNotEmpty() && sender.isOp) {
            when (args[0].lowercase()) {
                "test" -> {
                    if (args.size < 2) {
                        sendMessage(sender, "&cUsage: /vote test <player>")
                        return true
                    }
                    val targetPlayer = args[1]
                    Bukkit.getGlobalRegionScheduler().run(voteSection.plugin) {
                        val target = Bukkit.getPlayerExact(targetPlayer)
                        voteSection.registerVote(targetPlayer).whenComplete { voteAccepted, error ->
                            if (error != null) {
                                sendForSender(sender, "&cFailed to register test vote: ${error.cause?.message ?: error.message}")
                                return@whenComplete
                            }

                            if (!voteAccepted) {
                                val remainingDays = voteSection.getRemainingVoterDays(targetPlayer)
                                sendForSender(sender, "&c$targetPlayer still has voter role for $remainingDays more days")
                                return@whenComplete
                            }

                            if (target != null && target.isOnline) {
                                FoliaCompat.schedule(target, voteSection.plugin) {
                                    if (target.isOnline) voteSection.rewardPlayer(target) else voteSection.announceVote(targetPlayer)
                                }
                                sendForSender(sender, "&aTest vote given to online player: $targetPlayer (voter role granted for 30 days)")
                            } else {
                                voteSection.announceVote(targetPlayer)
                                sendForSender(sender, "&aTest offline vote registered for: $targetPlayer (voter role granted for 30 days)")
                            }
                        }
                    }
                    return true
                }
                
                "clear" -> {
                    if (args.size < 2) {
                        sendMessage(sender, "&cUsage: /vote clear <player>")
                        return true
                    }
                    val targetPlayer = args[1].lowercase()
                    voteSection.markAsRewarded(targetPlayer)
                    sendMessage(sender, "&cCleared offline votes for: $targetPlayer")
                    return true
                }
                
                "check" -> {
                    if (args.size < 2) {
                        sendMessage(sender, "&cUsage: /vote check <player>")
                        return true
                    }
                    val targetPlayer = args[1].lowercase()
                    val entry = voteSection.toReward[PlayerName(targetPlayer)]
                    if (entry != null) {
                        val daysOld = (System.currentTimeMillis() - entry.timestamp) / (24L * 60L * 60L * 1000L)
                        sendMessage(sender, "&e$targetPlayer has ${entry.count} pending offline votes ($daysOld days old)")
                    } else {
                        sendMessage(sender, "&e$targetPlayer has 0 pending offline votes")
                    }
                    return true
                }
                
                "cleanup" -> {
                    voteSection.cleanupExpiredVotes()
                    sendMessage(sender, "&aExpired vote cleanup completed")
                    return true
                }
                
                "save" -> {
                    val storage = voteSection.sqliteStorage
                    if (storage == null) {
                        sendMessage(sender, "&cVote storage is unavailable")
                    } else {
                        storage.save(voteSection.toReward).whenComplete { _, error ->
                            if (error == null) {
                                sendForSender(sender, "&aManually saved offline votes to SQLite")
                            } else {
                                sendForSender(sender, "&cFailed to save offline votes: ${error.cause?.message ?: error.message}")
                            }
                        }
                    }
                    return true
                }
                
                "list" -> {
                    val toReward = voteSection.toReward
                    if (toReward.isEmpty()) {
                        sendMessage(sender, "&eNo pending votes found")
                    } else {
                        sendMessage(sender, "&ePending votes:")
                        toReward.forEach { (name, entry) ->
                            val username = name.value
                            val daysOld = (System.currentTimeMillis() - entry.timestamp) / (24L * 60L * 60L * 1000L)
                            val remainingDays = voteSection.getRemainingVoterDays(username)
                            val status = if (entry.count > 0) "unrewarded" else "voter role ($remainingDays days left)"
                            sendMessage(sender, "&f- $username: $status (voted $daysOld days ago)")
                        }
                    }
                    return true
                }
                
                "migrate" -> {
                    if (args.size < 3) {
                        sendMessage(sender, "&cUsage: /vote migrate <player> <days>")
                        return true
                    }
                    val targetPlayer = args[1].lowercase()
                    runCatching {
                        val daysAgo = args[2].toLong()
                        voteSection.migrateLegacyPlayer(targetPlayer, daysAgo)
                        sendMessage(sender, "&aMigrated legacy player $targetPlayer (voted $daysAgo days ago)")
                    }.onFailure {
                        sendMessage(sender, "&cInvalid number: ${args[2]}")
                    }
                    return true
                }
                
                "scanlegacy" -> {
                    Bukkit.getGlobalRegionScheduler().run(voteSection.plugin) {
                        val candidates = Bukkit.getOnlinePlayers()
                            .map { it.name.lowercase() }
                            .filterNot { voteSection.toReward.containsKey(PlayerName(it)) }
                        val checks = candidates.associateWith(voteSection::hasVoterRoleAsync)

                        CompletableFuture.allOf(*checks.values.toTypedArray()).whenComplete { _, error ->
                            if (error != null) {
                                sendForSender(sender, "&cLegacy scan failed: ${error.cause?.message ?: error.message}")
                                return@whenComplete
                            }

                            val defaultDaysRemaining = voteSection.config?.getInt("LegacyPlayerDefaultDaysRemaining", 20) ?: 20
                            var migratedCount = 0
                            checks.forEach { (name, future) ->
                                if (future.join()) {
                                    voteSection.migrateLegacyPlayer(name, defaultDaysRemaining.toLong())
                                    migratedCount++
                                }
                            }
                            sendForSender(sender, "&aMigrated $migratedCount legacy players who are currently online")
                        }
                    }
                    return true
                }
            }
        }
        
        if (sender is Player) {
            sendPrefixedLocalizedMessage(sender, "vote_info")
        } else {
            sendMessage(sender, "&cThis command is player only")
        }
        return true
    }

    private fun sendForSender(sender: CommandSender, message: String) {
        if (sender is Player) {
            FoliaCompat.schedule(sender, voteSection.plugin) { sendMessage(sender, message) }
        } else {
            Bukkit.getGlobalRegionScheduler().run(voteSection.plugin) { sendMessage(sender, message) }
        }
    }
}
