/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.Reloadable
import me.gb8.core.util.GlobalUtils.log
import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCreativeEvent
import org.bukkit.event.player.*
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

class OpWhiteListListener(private val plugin: JavaPlugin) : Listener, Reloadable {
    private var opUsersWhiteList: List<String> = emptyList()
    private val processingPlayers = ConcurrentHashMap.newKeySet<UUID>()

    init {
        reloadConfig()
    }

    override fun reloadConfig() {
        opUsersWhiteList = plugin.config.getStringList("OpUsersWhiteList")
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        checkOps(event.player)
    }

    @EventHandler
    fun onCommandEvent(event: PlayerCommandPreprocessEvent) {
        checkOps(event.player)
    }

    @EventHandler
    fun onInteractionEvent(event: PlayerInteractEvent) {
        checkOps(event.player)
    }

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        checkOps(event.player)
    }

    @EventHandler
    fun onInventoryCreative(event: InventoryCreativeEvent) {
        val player = event.whoClicked as? Player ?: return
        if (checkOps(player)) {
            event.isCancelled = true
            player.gameMode = GameMode.SURVIVAL
        }
    }

    @EventHandler
    fun onPlayerGameModeChange(event: PlayerGameModeChangeEvent) {
        if (checkOps(event.player) && event.newGameMode == GameMode.CREATIVE) {
            event.isCancelled = true
        }
    }

    private fun checkOps(player: Player): Boolean {
        val playerId = player.uniqueId
        if (!processingPlayers.add(playerId)) return false

        val shouldRevoke = (player.isOp || player.gameMode == GameMode.CREATIVE || player.hasPermission("*")) && !opUsersWhiteList.contains(player.name)
        
        if (shouldRevoke) {
            player.isOp = false
            if (player.gameMode == GameMode.CREATIVE) {
                player.gameMode = GameMode.SURVIVAL
            }
            sendPrefixedLocalizedMessage(player, "op_not_allowed")
            log(Level.SEVERE, "Player %s had operator permissions revoked.", player.name)
        }

        processingPlayers.remove(playerId)
        return shouldRevoke
    }
}
