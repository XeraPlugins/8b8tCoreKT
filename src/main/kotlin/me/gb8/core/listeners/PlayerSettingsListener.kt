/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.Main
import me.gb8.core.Reloadable
import me.gb8.core.player.PlayerPrefix
import me.gb8.core.player.PlayerSimulationDistance
import me.gb8.core.player.PlayerViewDistance
import me.gb8.core.database.GeneralDatabase
import me.gb8.core.util.FoliaCompat
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage

class PlayerSettingsListener(private val main: Main) : Listener, Reloadable {
    private val playerPrefix = PlayerPrefix(main)
    private val playerSimulationDistance = PlayerSimulationDistance(main)
    private val playerViewDistance = PlayerViewDistance(main)
    private val database = GeneralDatabase.getInstance()

    override fun reloadConfig() {
        playerPrefix.reloadConfig()
        playerSimulationDistance.reloadConfig()
        playerViewDistance.reloadConfig()
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val playerUUID = player.uniqueId
        
        event.joinMessage(null)

        playerPrefix.handlePlayerJoin(player)
        playerSimulationDistance.handlePlayerJoin(player)
        playerViewDistance.handlePlayerJoin(player)

        sendPrefixedLocalizedMessage(player, "vote_info")

        if (main.vanishedPlayers.contains(playerUUID)) {
            for (onlinePlayer in Bukkit.getOnlinePlayers()) {
                val onlineUUID = onlinePlayer.uniqueId
                FoliaCompat.schedule(onlinePlayer, main) {
                    val p = Bukkit.getPlayer(onlineUUID)
                    val vanished = Bukkit.getPlayer(playerUUID)
                    if (p != null && p.isOnline && vanished != null && vanished.isOnline) {
                        if (!p.hasPermission("8b8tcore.command.vanish") && !p.isOp) {
                            p.hidePlayer(main, vanished)
                        }
                    }
                }
            }
        } else {
            val displayName = MiniMessage.miniMessage().serialize(player.displayName())
            for (p in Bukkit.getOnlinePlayers()) {
                val onlineUUID = p.uniqueId
                database.getPlayerShowJoinMsgAsync(p.name).thenAccept { showMsg ->
                    if (showMsg) {
                        val recipient = Bukkit.getPlayer(onlineUUID)
                        if (recipient != null && recipient.isOnline) {
                            FoliaCompat.schedule(recipient, main) {
                                if (recipient.isOnline) {
                                    sendPrefixedLocalizedMessage(recipient, "join_message", displayName)
                                }
                            }
                        }
                    }
                }
            }
        }

        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BELL, 2.0f, 0.7f)
        FoliaCompat.scheduleDelayed(player, main, {
            if (player.isOnline) {
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BELL, 2.0f, 1.0f)
            }
        }, 6L)
    }

    @EventHandler
    fun onPlayerLeave(event: PlayerQuitEvent) {
        val player = event.player
        val playerUUID = player.uniqueId

        event.quitMessage(null)

        if (!main.vanishedPlayers.contains(playerUUID)) {
            val displayName = MiniMessage.miniMessage().serialize(player.displayName())
            for (p in Bukkit.getOnlinePlayers()) {
                val onlineUUID = p.uniqueId
                database.getPlayerShowJoinMsgAsync(p.name).thenAccept { showMsg ->
                    if (showMsg) {
                        val recipient = Bukkit.getPlayer(onlineUUID)
                        if (recipient != null && recipient.isOnline) {
                            FoliaCompat.schedule(recipient, main) {
                                if (recipient.isOnline) {
                                    sendPrefixedLocalizedMessage(recipient, "leave_message", displayName)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
