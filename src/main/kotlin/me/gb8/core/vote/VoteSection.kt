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
import me.gb8.core.util.FoliaCompat
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
                FoliaCompat.schedule(p, plugin) {
                    if (!p.isOnline) return@schedule
                    val info = chatSection?.getInfo(p)
                    if (info?.hideAnnouncements == true) return@schedule

                    val loc = Localization.getLocalization(p.locale().language)
                    val message = String.format(loc.getWithPlaceholders("vote_announcement", "%days%", votingDays.toString()), voterName)
                    val prefixedMessage = loc.getPrefix() + " &r&7>>&r " + message
                    p.sendMessage(GlobalUtils.translateChars(prefixedMessage))
                }
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

        val key = PlayerName(player.name.lowercase())
        toReward.computeIfPresent(key) { _, entry ->
            val updated = entry.copy(count = (entry.count - 1).coerceAtLeast(0))
            sqliteStorage?.upsert(key, updated)
            updated
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

        val key = PlayerName(player.name.lowercase())
        toReward.computeIfPresent(key) { _, entry ->
            val updated = entry.copy(count = 0)
            sqliteStorage?.upsert(key, updated)
            updated
        }
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

        Bukkit.getGlobalRegionScheduler().run(plugin) {
            runCatching {
                plugin.server.dispatchCommand(plugin.server.consoleSender, commandToRun)
            }.onFailure { e ->
                plugin.logger.warning("Failed to execute expiration command for $username: ${e.message}")
            }
        }
    }

    fun hasVoterRoleAsync(username: String): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        Bukkit.getGlobalRegionScheduler().run(plugin) {
            runCatching {
                val player = Bukkit.getPlayerExact(username)
                if (player != null && player.isOnline) {
                    FoliaCompat.schedule(player, plugin) {
                        val hasRole = player.hasPermission("group.voter")
                        future.complete(hasRole)
                    }
                } else {
                    future.complete(false)
                }
            }.onFailure { e ->
                future.completeExceptionally(e)
            }
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

    fun registerVote(username: String): CompletableFuture<Boolean> {
        plugin.logger.info("VoteSection: Attempting to register vote for $username")

        val applyVote = {
            val key = PlayerName(username.lowercase())
            var persistence: CompletableFuture<Void>? = null
            val updated = toReward.compute(key) { _, existingEntry ->
                val newEntry = if (existingEntry == null) {
                    VoteEntry(1)
                } else {
                    extendedVoterEntry(username, existingEntry)
                }
                persistence = sqliteStorage?.upsert(key, newEntry)
                newEntry
            }

            plugin.logger.info("VoteSection: Vote registered for $username. Total tracked votes: ${toReward.size}")

            when {
                updated == null -> CompletableFuture.failedFuture(IllegalStateException("Vote map rejected update for $username"))
                persistence == null -> CompletableFuture.failedFuture(IllegalStateException("Vote storage is unavailable"))
                else -> persistence!!.thenApply { true }
            }
        }

        val player = Bukkit.getPlayerExact(username)
        if (player != null && player.isOnline &&
            config?.getBoolean("EnableLegacyPlayerMigration", true) == true &&
            !toReward.containsKey(PlayerName(username.lowercase()))) {

            return hasVoterRoleAsync(username).thenCompose { hasRole ->
                if (hasRole) {
                    if (!toReward.containsKey(PlayerName(username.lowercase()))) {
                        val defaultDays = config?.getInt("LegacyPlayerDefaultDaysRemaining", 20) ?: 20
                        migrateLegacyPlayer(username, defaultDays.toLong())
                    }
                }
                applyVote()
            }.whenComplete { _, ex ->
                if (ex == null) return@whenComplete
                plugin.logger.warning("Error registering vote for $username: ${ex.message}")
            }
        }

        return applyVote()
    }

    fun extendVoterRole(username: String, existingEntry: VoteEntry) {
        val key = PlayerName(username.lowercase())
        toReward.compute(key) { _, current ->
            val updated = extendedVoterEntry(username, current ?: existingEntry)
            sqliteStorage?.upsert(key, updated)
            updated
        }
    }

    private fun extendedVoterEntry(username: String, existingEntry: VoteEntry): VoteEntry {
        val expirationDays = config?.getInt("VoterRoleExpirationDays", 30) ?: 30

        val currentExpirationTime = existingEntry.timestamp + (expirationDays * 24L * 60L * 60L * 1000L)
        val baseTime = maxOf(currentExpirationTime, System.currentTimeMillis())

        val totalDaysRemaining = (baseTime + (expirationDays * 24L * 60L * 60L * 1000L) - System.currentTimeMillis()) / (24L * 60L * 60L * 1000L)
        plugin.logger.info("Extended voter role for $username by $expirationDays days. Total remaining: $totalDaysRemaining days")
        return VoteEntry(existingEntry.count + 1, baseTime)
    }

    fun migrateLegacyPlayer(username: String, daysRemaining: Long) {
        val currentTime = System.currentTimeMillis()
        val totalExpirationDays = config?.getInt("VoterRoleExpirationDays", 30) ?: 30

        val daysRemainingMillis = daysRemaining * 24L * 60L * 60L * 1000L
        val totalExpirationMillis = totalExpirationDays * 24L * 60L * 60L * 1000L
        val timestamp = currentTime + daysRemainingMillis - totalExpirationMillis

        val entry = VoteEntry(0, timestamp)
        val key = PlayerName(username.lowercase())
        toReward.compute(key) { _, _ ->
            sqliteStorage?.upsert(key, entry)
            entry
        }

        plugin.logger.info("Migrated legacy player $username to tracking system ($daysRemaining days remaining)")
    }

    fun getToRewardEntry(player: Player): Optional<Int> {
        val entry = toReward[PlayerName(player.name.lowercase())]
        return if (entry != null) Optional.of(entry.count) else Optional.empty()
    }

    fun markAsRewarded(username: String) {
        val key = PlayerName(username.lowercase())
        toReward.compute(key) { _, _ ->
            sqliteStorage?.delete(key)
            null
        }
    }

    fun cleanupExpiredVotes() {
        val offlineExpirationDays = config?.getInt("OfflineVoteExpirationDays", 7) ?: 7
        val voterRoleExpirationDays = config?.getInt("VoterRoleExpirationDays", 30) ?: 30
        val now = System.currentTimeMillis()

        val iterator = toReward.entries.iterator()
        var removedCount = 0
        var rolesRemovedCount = 0

        fun removeIfUnchanged(key: PlayerName, expected: VoteEntry): Boolean {
            var removed = false
            toReward.compute(key) { _, current ->
                if (current === expected) {
                    sqliteStorage?.delete(key)
                    removed = true
                    null
                } else {
                    current
                }
            }
            return removed
        }

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val username = entry.key.value
            val voteEntry = entry.value
            val roleExpired = voterRoleExpirationDays > 0 &&
                now > voteEntry.timestamp + (voterRoleExpirationDays * 24L * 60L * 60L * 1000L)

            if (roleExpired) {
                if (removeIfUnchanged(entry.key, voteEntry)) {
                    removeVoterRole(username)
                    rolesRemovedCount++
                    removedCount++
                    plugin.logger.info("Removed $username from voting database after role expiration")
                }
            } else if (offlineExpirationDays > 0 && voteEntry.count > 0 && voteEntry.isExpired(offlineExpirationDays)) {
                if (removeIfUnchanged(entry.key, voteEntry)) {
                    removedCount++
                }
            }
        }

        if (removedCount > 0) {
            plugin.logger.info("Cleaned up $removedCount expired vote entries")
        }
    }
}
