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
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.plugin.java.JavaPlugin

import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage

class MapRemovalPatch(private val plugin: JavaPlugin) : Listener {

    private val restrictedMapIds = mutableSetOf<Int>()

    init {
        restrictedMapIds += plugin.config.getIntegerList("RestrictedMapIDs")
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        removeRestrictedMaps(player.inventory, player)
        removeRestrictedMaps(player.enderChest, player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        removeRestrictedMaps(event.inventory, player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        removeRestrictedMaps(event.inventory, player)
        if (isRestrictedMap(event.cursor)) {
            event.view.setCursor(null)
            sendPrefixedLocalizedMessage(player, "mapart_deleted_inventory")
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryMove(event: InventoryMoveItemEvent) {
        removeRestrictedMaps(event.source, null)
        removeRestrictedMaps(event.destination, null)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerPickupItem(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return

        val item = event.item.itemStack

        if (isRestrictedMap(item)) {
            event.isCancelled = true
            event.item.remove()
            sendPrefixedLocalizedMessage(player, "mapart_deleted_inventory")
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (event.isNewChunk) return

        val chunk = event.chunk

        chunk.entities.filterIsInstance<ItemFrame>().forEach { entity ->
            val item = entity.item
            if (isRestrictedMap(item)) {
                    entity.setItem(null)

                    val chunkCenter = Location(
                        chunk.world,
                        ((chunk.x shl 4) + 8).toDouble(),
                        64.0,
                        ((chunk.z shl 4) + 8).toDouble()
                    )

                    chunk.world.getNearbyPlayers(chunkCenter, 16.0).forEach { player ->
                        if (player.location.chunk == chunk) sendPrefixedLocalizedMessage(player, "mapart_deleted_frame")
                    }
            }
        }
    }

    private fun removeRestrictedMaps(inventory: Inventory?, player: Player?) {
        if (inventory == null) return

        repeat(inventory.size) { slot ->
            val item = inventory.getItem(slot)
            if (isRestrictedMap(item)) {
                inventory.setItem(slot, null)
                player?.let { sendPrefixedLocalizedMessage(it, "mapart_deleted_inventory") }
            }
        }
    }

    private fun isRestrictedMap(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.FILLED_MAP) return false

        val meta = item.itemMeta as? MapMeta ?: return false
        return meta.hasMapId() && meta.mapId in restrictedMapIds
    }
}
