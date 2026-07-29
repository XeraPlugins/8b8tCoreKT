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
        startPlayerChecker()
    }

    private fun startPlayerChecker() {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin as JavaPlugin, {
            Bukkit.getOnlinePlayers().forEach(::checkPlayer)
        }, 20L, 20L)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        checkPlayer(event.player)
    }

    private fun checkPlayer(player: Player) {
        player.takeIf { it.isValid && !it.isDead }?.let { p ->
            FoliaCompat.schedule(p, plugin) {
                if (p.isOnline) {
                    effectCheck.fixPlayerEffects(p)
                    for (item in p.inventory.contents) {
                        if (item != null && !item.type.isAir) main.checkFixItem(item, null)
                    }
                }
            }
        }
    }

    fun getEffectCheck(): PlayerEffectCheck = effectCheck
}
