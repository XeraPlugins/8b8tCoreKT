/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.player.PlayerMoveEvent
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PvpPatchListeners : Listener {
    private val suffocationMaterials = EnumSet.of(
        Material.OBSIDIAN, Material.BEDROCK, Material.NETHERITE_BLOCK,
        Material.BARRIER, Material.END_PORTAL_FRAME, Material.ANVIL,
        Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL, Material.REINFORCED_DEEPSLATE
    )
    private val lastDamage = ConcurrentHashMap<UUID, Long>()

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerSuffocation(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (event.cause == DamageCause.SUFFOCATION) {
            if (suffocationMaterials.contains(player.location.block.type)) {
                event.damage = 7.0
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (event.from.blockX == event.to.blockX &&
            event.from.blockY == event.to.blockY &&
            event.from.blockZ == event.to.blockZ) return

        val player = event.player
        if (suffocationMaterials.contains(event.to.block.type)) {
            val now = System.currentTimeMillis()
            val last = lastDamage[player.uniqueId]
            if (last == null || (now - last) >= 500) {
                lastDamage[player.uniqueId] = now
                player.damage(7.0)
                player.teleportAsync(event.from.add(0.0, 0.1, 0.0))
            }
        }
    }
}
