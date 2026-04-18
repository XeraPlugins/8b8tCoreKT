/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.Main
import me.gb8.core.util.FoliaCompat
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AttackListener(private val plugin: Main) : Listener {

    private val lastGroundTime = ConcurrentHashMap<UUID, Long>()
    private val lastSmashTime = ConcurrentHashMap<UUID, Long>()
    private val tickStartLocations = ConcurrentHashMap<UUID, Location>()

    init {
        Bukkit.getOnlinePlayers().forEach { startTracking(it) }
    }

    private fun startTracking(player: Player) {
        FoliaCompat.scheduleAtFixedRate(player, plugin, Runnable {
            tickStartLocations[player.uniqueId] = player.location
        }, 1L, 1L)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        startTracking(event.player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        tickStartLocations.remove(uuid)
        lastGroundTime.remove(uuid)
        lastSmashTime.remove(uuid)
    }

    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        if (player.isOnGround) {
            lastGroundTime[player.uniqueId] = System.currentTimeMillis()
        }
    }

    
    @EventHandler
    fun onTeleport(event: PlayerTeleportEvent) {
        val player = event.player
        val to = event.to
        tickStartLocations[player.uniqueId] = to
        if (player.isOnGround) {
            lastGroundTime[player.uniqueId] = System.currentTimeMillis()
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onAttack(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return

        val mainHand = player.inventory.itemInMainHand
        val offHand = player.inventory.itemInOffHand

        val weaponType = when {
            mainHand.type.name == "MACE" || offHand.type.name == "MACE" -> "MACE"
            mainHand.type.name.endsWith("_SPEAR") || offHand.type.name.endsWith("_SPEAR") -> "SPEAR"
            mainHand.type == Material.TRIDENT || offHand.type == Material.TRIDENT -> "TRIDENT"
            else -> null
        }

        if (weaponType == null) return

        val damage = event.damage
        val targetLoc = event.entity.location
        val targetWorld = targetLoc.world ?: return
        val playerWorld = player.location.world
        if (playerWorld != targetWorld) return
        val currDistance = player.location.distance(targetLoc)
        val origin = tickStartLocations.getOrDefault(player.uniqueId, player.location)
        val tickStartDistance = origin.distance(targetLoc)

        val reachLimit = 8.5
        if (currDistance > reachLimit || tickStartDistance > reachLimit + 3.0) {
            cancelAttack(event)
            return
        }

        if (weaponType == "MACE") {
            val fallDist = player.fallDistance
            val now = System.currentTimeMillis()
            val timeInAir = now - lastGroundTime.getOrDefault(player.uniqueId, now)
            val t = timeInAir / 1000.0
            val maxPhysFall = (25.0 * (t * t)) + (t * 2.0) + 1.5

            when {
                fallDist > maxPhysFall && timeInAir < 60000 -> {
                    cancelAttack(event)
                    return
                }
                damage > 30.0 && now - lastSmashTime.getOrDefault(player.uniqueId, 0L) < 600 -> {
                    cancelAttack(event)
                    return
                }
                fallDist < 1.0 && damage > 35.0 -> event.damage = 25.0
                fallDist >= 1.0 -> {
                    val ceiling = (7.5 * fallDist) + 30.0
                    if (damage > ceiling && damage > 50.0) {
                        event.damage = ceiling
                    }
                }
                damage > 1000.0 -> event.damage = 1000.0
            }

            if (damage > 30.0) {
                lastSmashTime[player.uniqueId] = now
            }
            return
        }

        if (weaponType == "SPEAR" || weaponType == "TRIDENT") {
            if (damage > 150.0) {
                cancelAttack(event)
            }
        }
    }

    private fun cancelAttack(event: EntityDamageByEntityEvent) {
        event.isCancelled = true
    }
}
