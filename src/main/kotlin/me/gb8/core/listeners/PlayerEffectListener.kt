/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.antiillegal.AntiIllegalMain
import me.gb8.core.antiillegal.PlayerEffectCheck
import me.gb8.core.util.FoliaCompat
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin

class PlayerEffectListener(private val plugin: Plugin, private val main: AntiIllegalMain) : Listener {

    private val effectCheck = PlayerEffectCheck()

    init {
        startEffectChecker()
        startInventoryChecker()
    }

    private fun startEffectChecker() {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin as JavaPlugin, {
            checkAllPlayersEffects()
        }, 20L, 20L)
    }

    private fun startInventoryChecker() {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin as JavaPlugin, {
            checkAllPlayersInventories()
        }, 20L, 20L)
    }

    private fun checkAllPlayersEffects() {
        Bukkit.getOnlinePlayers().forEach { checkPlayerEffects(it) }
    }

    private fun checkAllPlayersInventories() {
        Bukkit.getOnlinePlayers().forEach { checkPlayerInventory(it) }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        checkPlayerEffects(event.player)
        checkPlayerInventory(event.player)
    }

    fun checkPlayerEffects(player: Player) {
        player.takeIf { it.isValid && !it.isDead }?.let { p ->
            FoliaCompat.schedule(p, plugin) {
                if (p.isOnline) {
                    effectCheck.fixPlayerEffects(p)
                }
            }
        }
    }

    fun checkPlayerInventory(player: Player) {
        player.takeIf { it.isValid && !it.isDead }?.let { p ->
            FoliaCompat.schedule(p, plugin) {
                if (p.isOnline) {
                    val inv = p.inventory
                    inv.itemInMainHand?.let { main.checkFixItem(it, null) }
                    inv.itemInOffHand?.let { main.checkFixItem(it, null) }
                    inv.armorContents.forEach { it?.let { main.checkFixItem(it, null) } }
                    inv.contents.forEach { it?.let { main.checkFixItem(it, null) } }
                }
            }
        }
    }

    fun getEffectCheck(): PlayerEffectCheck = effectCheck
}
