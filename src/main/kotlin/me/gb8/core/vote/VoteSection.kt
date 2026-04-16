/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.vote

import me.gb8.core.Main
import me.gb8.core.Section
import me.gb8.core.chat.ChatInfo
import me.gb8.core.chat.ChatSection
import me.gb8.core.listeners.VotifierListener
import me.gb8.core.listeners.VoteJoinListener
import me.gb8.core.util.GlobalUtils
import me.gb8.core.Localization
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import java.io.File
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.logging.Level

class VoteSection(override val plugin: Main) : Section {

    var sqliteStorage: VoteSQLiteStorage? = null
        private set
    var toReward: MutableMap<String, VoteEntry> = ConcurrentHashMap()
        private set
    var config: ConfigurationSection? = null
        private set

    override val name: String = "Vote"

    override fun enable() {
        config = plugin.getSectionConfig(this)
        plugin.logger.info("VoteSection: Config loaded: ${if (config != null) "SUCCESS" else "NULL"}")

        plugin.getCommand("vote")?.setExecutor(VoteCommand(this))
        plugin.logger.info("VoteSection: Vote command registered")

        try {
            val votesFile = File(plugin.getSectionDataFolder(this), "votes.db")
            sqliteStorage = VoteSQLiteStorage(votesFile)
        } catch (t: Throwable) {
            plugin.logger.log(Level.SEVERE, "Failed to initialize SQLite storage. See stacktrace:", t)
            sqliteStorage = null
        }

        sqliteStorage?.load()?.let { loaded ->
            toReward.putAll(loaded)
        }

        cleanupExpiredVotes()

        plugin.register(VotifierListener(this))
        plugin.register(VoteJoinListener(this))
        plugin.logger.info("VoteSection: Listeners registered")

        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, { task ->
            sqliteStorage?.let { storage ->
                if (toReward.isNotEmpty()) {
                    plugin.logger.info("VoteSection: Auto-saving ${toReward.size} votes")
                    storage.save(toReward)
                }
            }
        }, 5 * 60 * 20L, 5 * 60 * 20L)

        plugin.logger.info("VoteSection: Successfully enabled!")
    }

    override fun disable() {
        sqliteStorage?.save(toReward)
        sqliteStorage?.close()
        toReward.clear()
    }

    override fun reloadConfig() {
        config = plugin.getSectionConfig(this)
    }

    fun announceVote(voterName: String) {
        val votingDays = config?.getInt("VoterRoleExpirationDays", 30) ?: 30
        val chatSection = plugin.getSectionByName("ChatControl") as? ChatSection

        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { task ->
            for (p in Bukkit.getOnlinePlayers()) {
                val info = chatSection?.getInfo(p)
                if (info != null && info.hideAnnouncements) continue

                val loc = Localization.getLocalization(p.locale().language)
                val message = loc.getWithPlaceholders("vote_announcement", "%days%", votingDays.toString())
                val finalMessage = java.lang.String.format(message, voterName)
                val prefixedMessage = loc.getPrefix() + " &r&7>>&r " + finalMessage
                p.sendMessage(GlobalUtils.translateChars(prefixedMessage))
            }
        }, 1L)
    }

    private fun executeRewards(player: Player) {
        if (player == null || !player.isOnline) return

        val rewards = config?.getStringList("Rewards") ?: return

        for (cmd in rewards) {
            val toRun = String.format(cmd, player.name)

            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { task ->
                try {
                    plugin.server.dispatchCommand(plugin.server.consoleSender, toRun)
                } catch (ex: Exception) {
                    plugin.logger.warning("Failed to execute vote reward command: $toRun - ${ex.message}")
                }
            }, 100L)
        }
    }

    fun rewardPlayer(player: Player) {
        val votingDays = config?.getInt("VoterRoleExpirationDays", 30) ?: 30

        val loc = Localization.getLocalization(player.locale().language)
        val message = loc.getWithPlaceholders("vote_thanks", "%days%", votingDays.toString())
        val prefixedMessage = loc.getPrefix() + " &r&7>>&r " + message
        player.sendMessage(GlobalUtils.translateChars(prefixedMessage))

        announceVote(player.name)

        executeRewards(player)

        val entry = toReward[player.name.lowercase()]
        if (entry != null && entry.count > 0) {
            entry.decrementVote()
            sqliteStorage?.save(toReward)
        }
    }

    fun grantVoterRole(player: Player) {
        if (player != null && player.isOnline) {
            executeRewards(player)
        }
    }

    fun rewardOfflineVotes(player: Player, voteCount: Int) {
        val votingDays = config?.getInt("VoterRoleExpirationDays", 30) ?: 30

        val loc = Localization.getLocalization(player.locale().language)
        val message = loc.getWithPlaceholders("vote_thanks", "%days%", votingDays.toString())
        val prefixedMessage = loc.getPrefix() + " &r&7>>&r " + message
        player.sendMessage(GlobalUtils.translateChars(prefixedMessage))

        for (i in 0 until voteCount) {
            executeRewards(player)
        }

        val entry = toReward[player.name.lowercase()]
        if (entry != null) {
            entry.count = 0
        }

        sqliteStorage?.save(toReward)
    }

    fun hasVoterRoleExpired(username: String): Boolean {
        val entry = toReward[username.lowercase()] ?: return true

        val expirationDays = config?.getInt("VoterRoleExpirationDays", 30) ?: 30
        if (expirationDays <= 0) return false

        val expirationTime = entry.timestamp + (expirationDays * 24L * 60L * 60L * 1000L)
        return System.currentTimeMillis() > expirationTime
    }

    fun getRemainingVoterDays(username: String): Long {
        val entry = toReward[username.lowercase()] ?: return 0

        val expirationDays = config?.getInt("VoterRoleExpirationDays", 30) ?: 30
        if (expirationDays <= 0) return -1

        val expirationTime = entry.timestamp + (expirationDays * 24L * 60L * 60L * 1000L)
        val remaining = expirationTime - System.currentTimeMillis()
        return maxOf(0, remaining / (24L * 60L * 60L * 1000L))
    }

    fun removeVoterRole(username: String) {
        try {
            val expirationCommand = config?.getString("ExpirationCommand", "lp user %s group remove voter") ?: "lp user %s group remove voter"
            val commandToRun = String.format(expirationCommand, username)
            plugin.server.dispatchCommand(plugin.server.consoleSender, commandToRun)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to execute expiration command for $username: ${e.message}")
        }
    }

    fun hasVoterRoleAsync(username: String): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        try {
            val player = Bukkit.getPlayerExact(username)
            if (player != null && player.isOnline) {
                Bukkit.getRegionScheduler().run(plugin, player.location, { task ->
                    val hasRole = player.hasPermission("group.voter") || player.hasPermission("voter")
                    future.complete(hasRole)
                })
            } else {
                future.complete(false)
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }
        return future
    }

    fun checkAndMigrateLegacyPlayer(username: String) {
        if (config?.getBoolean("EnableLegacyPlayerMigration", true) != true) return
        if (toReward.containsKey(username.lowercase())) return

        hasVoterRoleAsync(username).thenAccept { hasRole ->
            if (hasRole) {
                val defaultDaysRemaining = config?.getInt("LegacyPlayerDefaultDaysRemaining", 20) ?: 20
                migrateLegacyPlayer(username, defaultDaysRemaining.toLong())
            }
        }.exceptionally { ex ->
            plugin.logger.warning("Error migrating legacy player $username: ${ex.message}")
            null
        }
    }

    fun registerVote(username: String): Boolean {
        plugin.logger.info("VoteSection: Attempting to register vote for $username")

        val applyVote = {
            val existingEntry = toReward[username.lowercase()]
            if (existingEntry != null) {
                extendVoterRole(username, existingEntry)
            } else {
                toReward[username.lowercase()] = VoteEntry(1)
            }

            plugin.logger.info("VoteSection: Vote registered for $username. Total tracked votes: ${toReward.size}")

            sqliteStorage?.save(toReward)
        }

        val player = Bukkit.getPlayerExact(username)
        if (player != null && player.isOnline &&
            config?.getBoolean("EnableLegacyPlayerMigration", true) == true &&
            !toReward.containsKey(username.lowercase())) {

            hasVoterRoleAsync(username).thenAccept { hasRole ->
                if (hasRole) {
                    if (!toReward.containsKey(username.lowercase())) {
                        val defaultDays = config?.getInt("LegacyPlayerDefaultDaysRemaining", 20) ?: 20
                        migrateLegacyPlayer(username, defaultDays.toLong())
                    }
                }
                applyVote()
            }.exceptionally { ex ->
                plugin.logger.warning("Error registering vote for $username: ${ex.message}")
                null
            }

        } else {
            applyVote()
        }

        return true
    }

    fun extendVoterRole(username: String, existingEntry: VoteEntry) {
        val expirationDays = config?.getInt("VoterRoleExpirationDays", 30) ?: 30

        val currentExpirationTime = existingEntry.timestamp + (expirationDays * 24L * 60L * 60L * 1000L)
        val baseTime = maxOf(currentExpirationTime, System.currentTimeMillis())
        val newTimestamp = baseTime

        val newEntry = VoteEntry(existingEntry.count + 1, newTimestamp)
        toReward[username.lowercase()] = newEntry

        val totalDaysRemaining = (baseTime + (expirationDays * 24L * 60L * 60L * 1000L) - System.currentTimeMillis()) / (24L * 60L * 60L * 1000L)
        plugin.logger.info("Extended voter role for $username by $expirationDays days. Total remaining: $totalDaysRemaining days")
    }

    fun migrateLegacyPlayer(username: String, daysRemaining: Long) {
        val currentTime = System.currentTimeMillis()
        val totalExpirationDays = config?.getInt("VoterRoleExpirationDays", 30) ?: 30

        val daysRemainingMillis = daysRemaining * 24L * 60L * 60L * 1000L
        val totalExpirationMillis = totalExpirationDays * 24L * 60L * 60L * 1000L
        val timestamp = currentTime + daysRemainingMillis - totalExpirationMillis

        val entry = VoteEntry(0, timestamp)
        toReward[username.lowercase()] = entry

        sqliteStorage?.save(toReward)

        plugin.logger.info("Migrated legacy player $username to tracking system ($daysRemaining days remaining)")
    }

    fun getToRewardEntry(player: Player): Optional<Int> {
        val entry = toReward[player.name.lowercase()]
        return if (entry != null) Optional.of(entry.count) else Optional.empty()
    }

    fun markAsRewarded(username: String) {
        toReward.remove(username.lowercase())
    }

    fun cleanupExpiredVotes() {
        val offlineExpirationDays = config?.getInt("OfflineVoteExpirationDays", 7) ?: 7
        val voterRoleExpirationDays = config?.getInt("VoterRoleExpirationDays", 30) ?: 30

        var removedCount = 0
        var rolesRemovedCount = 0

        val iterator = toReward.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val username = entry.key
            val voteEntry = entry.value

            if (hasVoterRoleExpired(username)) {
                removeVoterRole(username)
                rolesRemovedCount++
                iterator.remove()
                removedCount++
                plugin.logger.info("Removed $username from voting database after role expiration")
            } else if (offlineExpirationDays > 0 && voteEntry.count > 0 && voteEntry.isExpired(offlineExpirationDays)) {
                iterator.remove()
                removedCount++
            }
        }

        if (removedCount > 0) {
            plugin.logger.info("Cleaned up $removedCount expired vote entries")
        }
        if (rolesRemovedCount > 0) {
            plugin.logger.info("Removed $rolesRemovedCount expired voter roles")
        }

        if ((removedCount > 0 || rolesRemovedCount > 0) && sqliteStorage != null) {
            sqliteStorage?.save(toReward)
        }
    }
}
