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
import java.util.logging.Level

@JvmInline
value class PlayerName(val value: String)

class VoteSection(override val plugin: Main) : Section {

    var sqliteStorage: VoteSQLiteStorage? = null
        private set
    var toReward: MutableMap<PlayerName, VoteEntry> = ConcurrentHashMap()
        private set
    var config: ConfigurationSection? = null
        private set

    override val name: String = "Vote"

    override fun enable() {
        config = plugin.getSectionConfig(this)
        plugin.logger.info("VoteSection: Config loaded: ${if (config != null) "SUCCESS" else "NULL"}")

        plugin.getCommand("vote")?.setExecutor(VoteCommandExecutor(this))
        plugin.logger.info("VoteSection: Vote command registered")

        runCatching {
            val votesFile = File(plugin.getSectionDataFolder(this), "votes.db")
            VoteSQLiteStorage(votesFile)
        }.onSuccess { storage ->
            sqliteStorage = storage
        }.onFailure { t ->
            plugin.logger.log(Level.SEVERE, "Failed to initialize SQLite storage. See stacktrace:", t)
            sqliteStorage = null
        }

        sqliteStorage?.load()?.let { toReward.putAll(it) }

        cleanupExpiredVotes()

        plugin.register(VotifierListener(this))
        plugin.register(VoteJoinListener(this))
        plugin.logger.info("VoteSection: Listeners registered")

        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, {
            sqliteStorage?.takeIf { toReward.isNotEmpty() }?.let { storage ->
                plugin.logger.info("VoteSection: Auto-saving ${toReward.size} votes")
                storage.save(toReward)
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

        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, {
            Bukkit.getOnlinePlayers().forEach { p ->
                val info = chatSection?.getInfo(p)
                if (info?.hideAnnouncements == true) return@forEach

                val loc = Localization.getLocalization(p.locale().language)
                val message = String.format(loc.getWithPlaceholders("vote_announcement", "%days%", votingDays.toString()), voterName)
                val prefixedMessage = loc.getPrefix() + " &r&7>>&r " + message
                p.sendMessage(GlobalUtils.translateChars(prefixedMessage))
            }
        }, 1L)
    }

    private fun executeRewards(player: Player) {
        player.takeIf { it.isOnline } ?: return

        val rewards = config?.getStringList("Rewards") ?: return

        rewards.forEach { cmd ->
            val toRun = String.format(cmd, player.name)
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, {
                runCatching {
                    plugin.server.dispatchCommand(plugin.server.consoleSender, toRun)
                }.onFailure { ex ->
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

        toReward[PlayerName(player.name.lowercase())]?.takeIf { it.count > 0 }?.let { entry ->
            entry.decrementVote()
            sqliteStorage?.save(toReward)
        }
    }

    fun grantVoterRole(player: Player) {
        player.takeIf { it.isOnline }?.let { executeRewards(it) }
    }

    fun rewardOfflineVotes(player: Player, voteCount: Int) {
        val votingDays = config?.getInt("VoterRoleExpirationDays", 30) ?: 30

        val loc = Localization.getLocalization(player.locale().language)
        val message = loc.getWithPlaceholders("vote_thanks", "%days%", votingDays.toString())
        val prefixedMessage = loc.getPrefix() + " &r&7>>&r " + message
        player.sendMessage(GlobalUtils.translateChars(prefixedMessage))

        repeat(voteCount) { executeRewards(player) }

        toReward[PlayerName(player.name.lowercase())]?.let { it.count = 0 }

        sqliteStorage?.save(toReward)
    }

    fun hasVoterRoleExpired(username: String): Boolean {
        val entry = toReward[PlayerName(username.lowercase())] ?: return true

        val expirationDays = config?.getInt("VoterRoleExpirationDays", 30) ?: 30
        if (expirationDays <= 0) return false

        val expirationTime = entry.timestamp + (expirationDays * 24L * 60L * 60L * 1000L)
        return System.currentTimeMillis() > expirationTime
    }

    fun getRemainingVoterDays(username: String): Long {
        val entry = toReward[PlayerName(username.lowercase())] ?: return 0

        val expirationDays = config?.getInt("VoterRoleExpirationDays", 30) ?: 30
        if (expirationDays <= 0) return -1

        val expirationTime = entry.timestamp + (expirationDays * 24L * 60L * 60L * 1000L)
        val remaining = expirationTime - System.currentTimeMillis()
        return maxOf(0, remaining / (24L * 60L * 60L * 1000L))
    }

    fun removeVoterRole(username: String) {
        val expirationCommand = config?.getString("ExpirationCommand", "lp user %s group remove voter") ?: "lp user %s group remove voter"
        val commandToRun = String.format(expirationCommand, username)

        runCatching {
            plugin.server.dispatchCommand(plugin.server.consoleSender, commandToRun)
        }.onFailure { e ->
            plugin.logger.warning("Failed to execute expiration command for $username: ${e.message}")
        }
    }

    fun hasVoterRoleAsync(username: String): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        runCatching {
            val player = Bukkit.getPlayerExact(username)
            if (player != null && player.isOnline) {
                Bukkit.getRegionScheduler().run(plugin, player.location) {
                    val hasRole = player.hasPermission("group.voter") || player.hasPermission("voter")
                    future.complete(hasRole)
                }
            } else {
                future.complete(false)
            }
        }.onFailure { e ->
            future.completeExceptionally(e)
        }
        return future
    }

    fun checkAndMigrateLegacyPlayer(username: String) {
        if (config?.getBoolean("EnableLegacyPlayerMigration", true) != true) return
        if (toReward.containsKey(PlayerName(username.lowercase()))) return

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
            val key = PlayerName(username.lowercase())
            val existingEntry = toReward[key]
            if (existingEntry != null) {
                extendVoterRole(username, existingEntry)
            } else {
                toReward[key] = VoteEntry(1)
            }

            plugin.logger.info("VoteSection: Vote registered for $username. Total tracked votes: ${toReward.size}")

            sqliteStorage?.save(toReward)
        }

        val player = Bukkit.getPlayerExact(username)
        if (player != null && player.isOnline &&
            config?.getBoolean("EnableLegacyPlayerMigration", true) == true &&
            !toReward.containsKey(PlayerName(username.lowercase()))) {

            hasVoterRoleAsync(username).thenAccept { hasRole ->
                if (hasRole) {
                    if (!toReward.containsKey(PlayerName(username.lowercase()))) {
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

        val newEntry = VoteEntry(existingEntry.count + 1, baseTime)
        toReward[PlayerName(username.lowercase())] = newEntry

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
        toReward[PlayerName(username.lowercase())] = entry

        sqliteStorage?.save(toReward)

        plugin.logger.info("Migrated legacy player $username to tracking system ($daysRemaining days remaining)")
    }

    fun getToRewardEntry(player: Player): Optional<Int> {
        val entry = toReward[PlayerName(player.name.lowercase())]
        return if (entry != null) Optional.of(entry.count) else Optional.empty()
    }

    fun markAsRewarded(username: String) {
        toReward.remove(PlayerName(username.lowercase()))
    }

    fun cleanupExpiredVotes() {
        val offlineExpirationDays = config?.getInt("OfflineVoteExpirationDays", 7) ?: 7

        val iterator = toReward.entries.iterator()
        var removedCount = 0
        var rolesRemovedCount = 0

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val username = entry.key.value
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

        if (removedCount > 0 && sqliteStorage != null) {
            sqliteStorage?.save(toReward)
        }
    }
}