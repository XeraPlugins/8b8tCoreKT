/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import me.gb8.core.Main
import me.gb8.core.command.BaseTabCommand
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

import me.gb8.core.util.FoliaCompat
import me.gb8.core.util.GlobalUtils.sendMessage

class LastSeenCommand(private val plugin: Main) : BaseTabCommand(
    "lastseen",
    "/lastseen <player>",
    arrayOf("8b8tcore.command.lastseen"),
    "Check when a player was last online",
    arrayOf("<player>::Player to check last seen time")
), Listener {

    private val lastSeenCache = ConcurrentHashMap<String, PlayerLastSeenData>()
    private val hiddenLastSeenCache = ConcurrentHashMap<String, HiddenLastSeenTimestamp>()
    
    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }
    
    
    override fun execute(sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sendMessage(sender, "&cUsage: /lastseen <player>")
            return
        }
        
        val targetName = args[0]
        
        val onlinePlayer = Bukkit.getPlayerExact(targetName)
        if (onlinePlayer != null && sender is Player && !plugin.canSeePlayer(sender, onlinePlayer)) {
            sendLastSeenTimestamp(sender, onlinePlayer.name, hiddenLastSeenTimestamp(onlinePlayer))
            return
        }

        if (onlinePlayer != null) {
            sendMessage(sender, "&e${onlinePlayer.name} &ais currently online!")
            return
        }
        
        Bukkit.getGlobalRegionScheduler().run(plugin) {
            val offlinePlayer = Bukkit.getOfflinePlayer(targetName)
            
            if (offlinePlayer.firstPlayed == 0L) {
                runForSender(sender) {
                    sendMessage(sender, "&cPlayer '&e$targetName&c' has never joined the server!")
                }
                return@run
            }
            val playerId = offlinePlayer.uniqueId.toString()
            val cachedData = lastSeenCache[playerId]
            
            if (cachedData != null) {
                runForSender(sender) {
                    sendLastSeenData(sender, cachedData)
                }
            } else {
                val lastPlayed = offlinePlayer.lastSeen
                if (lastPlayed > 0) {
                    runForSender(sender) {
                        sendLastSeenTimestamp(sender, offlinePlayer.name ?: targetName, lastPlayed)
                    }
                } else {
                    runForSender(sender) {
                        sendMessage(sender, "&cNo last seen data available for '&e$targetName&c'")
                    }
                }
            }
        }
    }

    private fun sendLastSeenTimestamp(sender: CommandSender, name: String, timestamp: Long) {
        val formattedDate = SimpleDateFormat(DATE_FORMAT).format(Date(timestamp))
        sendMessage(sender, "&e$name &6was last seen on &b$formattedDate")
    }

    private fun sendLastSeenData(sender: CommandSender, data: PlayerLastSeenData) {
        val sdf = SimpleDateFormat(DATE_FORMAT)
        val formattedDate = sdf.format(Date(data.lastSeen))
        sendMessage(sender, "&e${data.name} &6was last seen on &b$formattedDate&6 in world '&e${data.world}&6'")

        if (sender.hasPermission("8b8tcore.command.lastseen.location")) {
            sendMessage(sender, "&6Last location: [&e${data.x}&6, &e${data.y}&6, &e${data.z}&6]")
        }
    }

    private fun hiddenLastSeenTimestamp(player: Player): Long {
        val generatedForDate = LocalDate.now()
        val playerId = player.uniqueId.toString()
        return hiddenLastSeenCache.compute(playerId) { _, cached ->
            if (cached != null && cached.generatedForDate == generatedForDate) {
                cached
            } else {
                val random = ThreadLocalRandom.current()
                val timestamp = generatedForDate
                    .minusDays(1)
                    .atTime(
                        random.nextInt(24),
                        random.nextInt(60),
                        random.nextInt(60)
                    )
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                HiddenLastSeenTimestamp(generatedForDate, timestamp)
            }
        }!!.timestamp
    }

    private fun runForSender(sender: CommandSender, action: Runnable) {
        if (sender is Player) {
            FoliaCompat.schedule(sender, plugin) {
                if (sender.isOnline) action.run()
            }
        } else {
            action.run()
        }
    }
    
    
    override fun onTab(sender: CommandSender, args: Array<String>): List<String> {
        return if (args.size == 1) {
            val prefix = args[0]
            val suggestions = mutableListOf<String>()
            suggestions.addAll(plugin.visibleOnlinePlayers(sender).map { it.name })
            suggestions.addAll(lastSeenCache.values.mapNotNull { cached ->
                val online = Bukkit.getPlayerExact(cached.name)
                if (online == null || sender !is Player || plugin.canSeePlayer(sender, online)) cached.name else null
            })
            suggestions
                .distinct()
                .filter { it.startsWith(prefix, ignoreCase = true) }
        } else {
            emptyList()
        }
    }
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        hiddenLastSeenCache.remove(event.player.uniqueId.toString())
        updateLastSeen(event.player)
    }
    
    fun updateLastSeen(player: Player) {
        val loc = player.location
        val data = PlayerLastSeenData(
            player.uniqueId.toString(),
            player.name,
            System.currentTimeMillis(),
            loc.x,
            loc.y,
            loc.z,
            loc.world.name
        )
        lastSeenCache[player.uniqueId.toString()] = data
    }

    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"
    }

    data class PlayerLastSeenData(
        val uuid: String,
        val name: String,
        val lastSeen: Long,
        val x: Double,
        val y: Double,
        val z: Double,
        val world: String
    )

    private data class HiddenLastSeenTimestamp(
        val generatedForDate: LocalDate,
        val timestamp: Long
    )
}
