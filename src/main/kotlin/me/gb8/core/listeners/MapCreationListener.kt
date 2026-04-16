/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import me.gb8.core.Main
import me.gb8.core.util.FoliaCompat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.server.MapInitializeEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.map.MapView
import org.bukkit.persistence.PersistentDataType

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayList
import java.util.function.Consumer

class MapCreationListener(private val plugin: Main) : Listener {

    companion object {
        private const val SCAN_RADIUS = 64
        private const val KEY_AUTHOR = "map_author"
    }

    @EventHandler
    fun onMapInitialize(event: MapInitializeEvent) {
        val map = event.map ?: return
        map.setTrackingPosition(true)
        map.setUnlimitedTracking(false)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onMapCreateAttempt(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && 
            event.action != Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        
        val hasMapInMain = player.inventory.itemInMainHand.type == Material.MAP
        val hasMapInOff = player.inventory.itemInOffHand.type == Material.MAP

        if (!hasMapInMain && !hasMapInOff) return

        val signatureText = getNearbyPlayerNames(player)
        val utcTime = getCurrentUtcTime()

        scheduleRetryScan(player, signatureText, utcTime, 0)
    }

    private fun scheduleRetryScan(player: Player, signatureText: String, utcTime: String, attempt: Int) {
        if (attempt > 5) return

        fun runRetry() {
            if (!player.isOnline) return
            
            val signedSomething = scanInventoryAndSign(player, signatureText, utcTime)
            
            if (!signedSomething) {
                scheduleRetryScan(player, signatureText, utcTime, attempt + 1)
            }
        }

        val initialDelay = if (attempt == 0) 1L else 0L
        
        val task = Runnable { runRetry() }
        if (initialDelay > 0) {
            FoliaCompat.scheduleDelayed(player, plugin, task, initialDelay)
        } else {
            FoliaCompat.scheduleDelayed(player, plugin, task, 10L)
        }
    }

    private fun scanInventoryAndSign(player: Player, signatureText: String, timeString: String): Boolean {
        var success = false

        if (attemptSign(player.itemOnCursor, signatureText, timeString)) success = true

        if (attemptSign(player.inventory.itemInOffHand, signatureText, timeString)) success = true

        for (itemStack: ItemStack? in player.inventory.storageContents) {
            if (attemptSign(itemStack, signatureText, timeString)) {
                success = true
            }
        }
        return success
    }

    private fun attemptSign(item: ItemStack?, signatureText: String, timeString: String): Boolean {
        if (item == null || item.type != Material.FILLED_MAP) return false
        
        val meta = item.itemMeta ?: return false

        val authorKey = NamespacedKey(plugin, KEY_AUTHOR)
        
        if (!meta.persistentDataContainer.has(authorKey, PersistentDataType.STRING)) {
            
            var mapId = -1
            if (meta is MapMeta && meta.hasMapId()) {
                mapId = meta.mapId
                
                val view = Bukkit.getMap(mapId)
                view?.apply {
                    setTrackingPosition(true)
                    setUnlimitedTracking(false)
                }
            }
            
            applySignature(item, meta, signatureText, timeString, mapId)
            return true
        }
        return false
    }

    private fun getNearbyPlayerNames(player: Player): String {
        val names = ArrayList<String>()
        names.add(player.name)
        try {
            player.getNearbyEntities(SCAN_RADIUS.toDouble(), SCAN_RADIUS.toDouble(), SCAN_RADIUS.toDouble()).stream()
                    .filter { e -> e is Player && !e.uniqueId.equals(player.uniqueId) }
                    .map { e -> (e as Player).name }
                    .forEach { names.add(it) }
        } catch (e: Exception) {
            // Ignore any errors during nearby entity scanning
        }

        return names.joinToString(", @")
    }
    
    private fun getCurrentUtcTime(): String {
        val formatter = DateTimeFormatter.ofPattern("h:mm a, MMMM d, yyyy")
        return ZonedDateTime.now(ZoneId.of("UTC")).format(formatter)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkLoad(event: ChunkLoadEvent) {
        for (entity in event.chunk.entities) {
            if (entity is ItemFrame) {
                val frameItem = entity.item
                if (frameItem.type == Material.FILLED_MAP) {
                    sanitizeFrameMap(frameItem)
                }
            }
        }
    }

    private fun sanitizeFrameMap(item: ItemStack) {
        if (item.itemMeta !is MapMeta) return
        val mm = item.itemMeta as MapMeta
        if (!mm.hasMapId()) return

        val view = Bukkit.getMap(mm.mapId)
        if (view != null && view.isUnlimitedTracking) {
            view.setTrackingPosition(true)
            view.setUnlimitedTracking(false)
        }
    }

    private fun applySignature(item: ItemStack, meta: ItemMeta, players: String, timeString: String, mapId: Int) {
        val authorComp = Component.text("by @$players", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)

        val timeComp = Component.text("Created: $timeString UTC", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)

        val lore = if (meta.hasLore()) ArrayList(meta.lore()) else ArrayList<Component>()
        
        val visuallySigned = lore.isNotEmpty() && lore[0].toString().contains("by @")

        if (!visuallySigned) {
            lore.add(0, authorComp)
            lore.add(1, timeComp)
        }

        meta.lore(lore)
        
        meta.persistentDataContainer.set(
            NamespacedKey(plugin, KEY_AUTHOR), PersistentDataType.STRING, players)
        meta.persistentDataContainer.set(
             NamespacedKey(plugin, "map_created"), PersistentDataType.STRING, timeString)

        if (mapId != -1) {
            meta.persistentDataContainer.set(
                NamespacedKey(plugin, "map_id"), PersistentDataType.INTEGER, mapId)
        }

        meta.persistentDataContainer.set(
            NamespacedKey(plugin, "map_metadata"), PersistentDataType.STRING, "Authenticated by 8b8tCore Signature System")

        item.itemMeta = meta
    }
}
