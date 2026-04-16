/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.patch

import me.gb8.core.Main
import me.gb8.core.util.GlobalUtils
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AntiLagChest(private val plugin: Main) : Listener {
    private val lastOpen = ConcurrentHashMap<UUID, Long>()
    private val cooldown: Long

    init {
        cooldown = plugin.config.getLong("AntiLagChest.openCooldown", 2000L)
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        if (!isChest(block.type)) return

        val tps = GlobalUtils.getCurrentRegionTps()
        if (tps > 15.0 || tps == -1.0) return

        val player = event.player
        val uuid = player.uniqueId
        val now = System.currentTimeMillis()
        val last = lastOpen.getOrDefault(uuid, 0L)

        if (now - last < cooldown) {
            event.isCancelled = true
            GlobalUtils.sendPrefixedLocalizedMessage(player, "chest_cooldown")
        } else {
            lastOpen[uuid] = now
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        lastOpen.remove(event.player.uniqueId)
    }

    private fun isChest(type: Material): Boolean {
        return type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL ||
               type == Material.SHULKER_BOX || type.name.contains("SHULKER_BOX") ||
               type == Material.ENDER_CHEST || type == Material.DISPENSER || type == Material.DROPPER ||
               type == Material.HOPPER
    }
}
