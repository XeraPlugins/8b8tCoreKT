/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.dupe

import me.gb8.core.Main
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.ChestedHorse
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

class DonkeyDupe(private val plugin: Main) : Listener {

    private val trackedAnimals = ConcurrentHashMap<UUID, ChestedHorse>()

    companion object {
        private const val MIN_DISTANCE = 6.0
    }

    @EventHandler
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val holder = event.inventory.holder as? ChestedHorse ?: return

        val player = event.player as? Player ?: return

        if (!plugin.config.getBoolean("DonkeyDupe.enabled", true)) return

        val votersOnly = plugin.config.getBoolean("DonkeyDupe.votersOnly", false)
        if (votersOnly && !player.hasPermission("8b8tcore.dupe.donkey")) return

        if (trackedAnimals.containsKey(player.uniqueId)) return

        event.isCancelled = true
        trackedAnimals[player.uniqueId] = holder

        val inv = event.inventory

        Bukkit.getRegionScheduler().runDelayed(plugin, player.location, Consumer {
            if (player.isOnline && trackedAnimals.containsKey(player.uniqueId)) {
                if (holder.isValid) {
                    player.openInventory(inv)
                } else {
                    trackedAnimals.remove(player.uniqueId)
                }
            }
        }, 40L)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return

        if (event.inventory.size == 18 && event.inventory.holder is Player) {
            trackedAnimals.remove(player.uniqueId)
            return
        }

        val holder = event.inventory.holder as? ChestedHorse ?: return

        val animal = trackedAnimals[player.uniqueId] ?: return
        if (holder != animal) return

        val pLoc = player.location
        val aLoc = holder.location

        val distance = if (pLoc.world == aLoc.world) pLoc.distance(aLoc) else Double.MAX_VALUE

        if (distance >= MIN_DISTANCE && player.vehicle != null) {
            val title = holder.customName() ?: Component.text("Entity")
            val fakeInventory = Bukkit.createInventory(player, 18, title)
            fakeInventory.contents = holder.inventory.contents

            Bukkit.getRegionScheduler().runDelayed(plugin, player.location, Consumer {
                if (player.isOnline) {
                    player.openInventory(fakeInventory)
                }
            }, 2L)

        } else {
            trackedAnimals.remove(player.uniqueId)
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        val clickedInventory = event.clickedInventory

        if (clickedInventory != null && clickedInventory.size == 18 && clickedInventory.holder is Player) {
            val animal = trackedAnimals[player.uniqueId] ?: return

            val MAX_DISTANCE = 128.0

            val pLoc = player.location
            val aLoc = animal.location

            val distance = if (pLoc.world == aLoc.world) pLoc.distance(aLoc) else Double.MAX_VALUE

            if (distance < MAX_DISTANCE) {
                event.isCancelled = true
                player.closeInventory()
                trackedAnimals.remove(player.uniqueId)
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        trackedAnimals.remove(event.player.uniqueId)
    }
}
