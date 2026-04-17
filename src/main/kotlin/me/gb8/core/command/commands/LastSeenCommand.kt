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
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors

import me.gb8.core.util.GlobalUtils.sendMessage

class LastSeenCommand(private val plugin: Main) : BaseTabCommand(
    "lastseen",
    "/lastseen <player>",
    arrayOf("8b8tcore.command.lastseen"),
    "Check when a player was last online",
    arrayOf("<player>::Player to check last seen time")
), Listener {

    private val lastSeenCache = ConcurrentHashMap<String, PlayerLastSeenData>()
    
    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        
        for (player in Bukkit.getOnlinePlayers()) {
            updateLastSeen(player)
        }
    }
    
    
    override fun execute(sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sendMessage(sender, "&cUsage: /lastseen <player>")
            return
        }
        
        val targetName = args[0]
        
        val onlinePlayer = Bukkit.getPlayer(targetName)
        if (onlinePlayer != null) {
            sendMessage(sender, "&e${onlinePlayer.name} &ais currently online!")
            return
        }
        
        Bukkit.getGlobalRegionScheduler().run(plugin) {
            val offlinePlayer = Bukkit.getOfflinePlayer(targetName)
            
            if (offlinePlayer.firstPlayed == 0L) {
                sendMessage(sender, "&cPlayer '&e$targetName&c' has never joined the server!")
                return@run
            }
            val playerId = offlinePlayer.uniqueId.toString()
            val cachedData = lastSeenCache[playerId]
            
            if (cachedData != null) {
                val sdf = SimpleDateFormat(DATE_FORMAT)
                val formattedDate = sdf.format(Date(cachedData.lastSeen))
                sendMessage(sender, "&e${cachedData.name} &6was last seen on &b$formattedDate&6 in world '&e${cachedData.world}&6'")
                
                if (sender.hasPermission("8b8tcore.command.lastseen.location")) {
                    sendMessage(sender, "&6Last location: [&e${cachedData.x}&6, &e${cachedData.y}&6, &e${cachedData.z}&6]")
                }
            } else {
                val lastPlayed = offlinePlayer.lastSeen
                if (lastPlayed > 0) {
                    val sdf = SimpleDateFormat(DATE_FORMAT)
                    val formattedDate = sdf.format(Date(lastPlayed))
                    sendMessage(sender, "&e${offlinePlayer.name} &6was last seen on &b$formattedDate")
                } else {
                    sendMessage(sender, "&cNo last seen data available for '&e$targetName&c'")
                }
            }
        }
    }
    
    
    override fun onTab(sender: CommandSender, args: Array<String>): List<String> {
        return if (args.size == 1) {
            val suggestions = mutableListOf<String>()
            suggestions.addAll(Bukkit.getOnlinePlayers().map { it.name })
            suggestions.addAll(lastSeenCache.values.map { it.name })
            suggestions.filter { it.lowercase().startsWith(args[0].lowercase()) }
        } else {
            emptyList()
        }
    }
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
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
}
