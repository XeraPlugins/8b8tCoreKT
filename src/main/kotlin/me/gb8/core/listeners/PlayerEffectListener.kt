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
import org.bukkit.inventory.PlayerInventory
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.util.function.Consumer

class PlayerEffectListener(private val plugin: Plugin, private val main: AntiIllegalMain) : Listener {

    private val effectCheck = PlayerEffectCheck()

    init {
        startEffectChecker()
        startInventoryChecker()
    }

    private fun startEffectChecker() {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin as JavaPlugin, Consumer {
            checkAllPlayersEffects()
        }, 20L, 20L)
    }

    private fun startInventoryChecker() {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin as JavaPlugin, Consumer {
            checkAllPlayersInventories()
        }, 20L, 20L)
    }

    private fun checkAllPlayersEffects() {
        for (player in Bukkit.getOnlinePlayers()) {
            checkPlayerEffects(player)
        }
    }

    private fun checkAllPlayersInventories() {
        for (player in Bukkit.getOnlinePlayers()) {
            checkPlayerInventory(player)
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        checkPlayerEffects(event.player)
        checkPlayerInventory(event.player)
    }

    fun checkPlayerEffects(player: Player) {
        if (player.isValid && !player.isDead) {
            FoliaCompat.schedule(player, plugin) {
                if (player.isOnline) {
                    effectCheck.fixPlayerEffects(player)
                }
            }
        }
    }

    fun checkPlayerInventory(player: Player) {
        if (player.isValid && !player.isDead) {
            FoliaCompat.schedule(player, plugin) {
                if (player.isOnline) {
                    val inv = player.inventory

                    main.checkFixItem(inv.itemInMainHand, null)
                    main.checkFixItem(inv.itemInOffHand, null)

                    for (armor in inv.armorContents) {
                        if (armor != null) main.checkFixItem(armor, null)
                    }

                    for (item in inv.contents) {
                        if (item != null) main.checkFixItem(item, null)
                    }
                }
            }
        }
    }

    fun getEffectCheck(): PlayerEffectCheck = effectCheck
}
