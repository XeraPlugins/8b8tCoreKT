/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import io.papermc.paper.datacomponent.DataComponentTypes
import me.gb8.core.antiillegal.AntiIllegalMain
import me.gb8.core.antiillegal.Check
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.*
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class InventoryListeners(private val main: AntiIllegalMain) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        checkInventory(event.inventory, event)
        checkInventory(event.view.bottomInventory, event)
    }

    private fun checkInventory(inv: Inventory, event: InventoryOpenEvent) {
        val openedShulker = inv.type.name.contains("SHULKER")
        repeat(inv.size) { slot ->
            inv.getItem(slot)?.takeUnless { it.type.isAir }?.let { item ->
                main.checkFixItem(item, event)
                inv.setItem(slot, item)
                if (openedShulker && isContainer(item.type)) {
                    clearContainerContents(item)
                }
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        event.inventory.forEach { item ->
            if (item != null) main.checkFixItem(item, null)
        }
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        main.checkFixItem(event.cursor, event)
        main.checkFixItem(event.currentItem, event)
        if (event.click == ClickType.NUMBER_KEY) {
            val hotbarItem = event.view.bottomInventory.getItem(event.hotbarButton)
            main.checkFixItem(hotbarItem, event)
        }
        checkBundleContents(event.cursor)
        checkBundleContents(event.currentItem)
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        main.checkFixItem(event.oldCursor, event)
        main.checkFixItem(event.cursor, event)
        event.newItems.values.forEach { item ->
            main.checkFixItem(item, event)
        }
    }

    @EventHandler
    fun onCreative(event: InventoryCreativeEvent) {
        main.checkFixItem(event.cursor, event)
        main.checkFixItem(event.currentItem, event)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        main.checkFixItem(event.currentItem, event)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onTrade(event: TradeSelectEvent) {
        event.inventory.contents.forEach { item ->
            if (item != null) main.checkFixItem(item, event)
        }
    }

    @EventHandler
    fun onHopper(event: InventoryMoveItemEvent) {
        main.checkFixItem(event.item, event)
        checkBundleContents(event.item)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onConsume(event: PlayerItemConsumeEvent) {
        if (main.checkFixItem(event.item, event)) {
            event.isCancelled = true
        }
    }

    private fun clearContainerContents(container: ItemStack) {
        container.takeIf { it.hasData(DataComponentTypes.CONTAINER) }?.let { item ->
            val contents = item.getData(DataComponentTypes.CONTAINER) ?: return@let
            @Suppress("UNCHECKED_CAST")
            val containerItems = contents.contents() as List<ItemStack?>
            if (containerItems.any { it != null && !it.type.isAir }) {
                item.unsetData(DataComponentTypes.CONTAINER)
            }
        }
    }

    private fun checkBundleContents(item: ItemStack?) {
        item?.takeIf { isBundle(it.type) && it.hasData(DataComponentTypes.BUNDLE_CONTENTS) }?.let { bundle ->
            if (checkBundleRecursion(bundle, 0)) {
                bundle.amount = 0
                return@let
            }

            val contents = bundle.getData(DataComponentTypes.BUNDLE_CONTENTS) ?: return@let
            var totalWeight = 0

            contents.contents().forEach { bundleItem ->
                val contentItem = bundleItem?.takeUnless { it.type.isAir } ?: return@forEach
                when {
                    isBundle(contentItem.type) || contentItem.amount > contentItem.type.maxStackSize -> {
                        bundle.amount = 0
                        return@forEach
                    }
                    main.checks.any { check -> check.shouldCheck(contentItem) && check.check(contentItem) } -> {
                        bundle.amount = 0
                        return@forEach
                    }
                    else -> {
                        totalWeight += contentItem.amount * (64 / contentItem.type.maxStackSize)
                    }
                }
            }

            if (totalWeight > MAX_BUNDLE_WEIGHT) {
                bundle.amount = 0
            }
        }
    }

    private fun isBundle(type: Material): Boolean = type.name.endsWith("BUNDLE")

    private fun isContainer(type: Material): Boolean =
        type.name.contains("SHULKER_BOX") ||
        type == Material.CHEST ||
        type == Material.TRAPPED_CHEST ||
        type == Material.BARREL ||
        type == Material.DISPENSER ||
        type == Material.DROPPER ||
        type == Material.HOPPER ||
        type == Material.CHISELED_BOOKSHELF

    private fun checkBundleRecursion(item: ItemStack?, depth: Int): Boolean {
        if (item == null || !isBundle(item.type)) return false
        if (depth >= 1) return true

        return try {
            val bundleMeta = item.itemMeta as? org.bukkit.inventory.meta.BundleMeta ?: return false
            bundleMeta.items.any { inner -> isBundle(inner.type) }
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val MAX_BUNDLE_WEIGHT = 64
    }
}
