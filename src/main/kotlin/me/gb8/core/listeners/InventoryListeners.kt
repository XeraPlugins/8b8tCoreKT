/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.BundleContents
import io.papermc.paper.datacomponent.item.ItemContainerContents
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
        val invType = inv.type.name
        val openedShulker = invType.contains("SHULKER")
        for (i in 0 until inv.size) {
            var item = inv.getItem(i)
            if (item == null || item.type.isAir) continue
            main.checkFixItem(item, event)
            inv.setItem(i, item)
            if (openedShulker && isContainer(item.type)) {
                clearContainerContents(item)
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        for (item in event.inventory) {
            main.checkFixItem(item, null)
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
        for (item in event.newItems.values) {
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
        for (item in event.inventory.contents) {
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
        if (!container.hasData(DataComponentTypes.CONTAINER)) return

        val contents = container.getData(DataComponentTypes.CONTAINER) ?: return

        val hasContents = contents.contents().stream()
            .anyMatch { item -> item != null && !item.type.isAir }

        if (hasContents) {
            container.unsetData(DataComponentTypes.CONTAINER)
        }
    }

    private fun checkBundleContents(item: ItemStack?) {
        if (item == null || !isBundle(item.type)) return
        if (!item.hasData(DataComponentTypes.BUNDLE_CONTENTS)) return

        if (checkBundleRecursion(item, 0)) {
            item.amount = 0
            return
        }

        val contents = item.getData(DataComponentTypes.BUNDLE_CONTENTS) ?: return

        var totalWeight = 0

        for (bundleItem in contents.contents()) {
            if (bundleItem == null || bundleItem.type.isAir) continue

            if (isBundle(bundleItem.type)) {
                item.amount = 0
                return
            }

            if (bundleItem.amount > bundleItem.type.maxStackSize) {
                item.amount = 0
                return
            }

            val maxStack = bundleItem.type.maxStackSize
            totalWeight += bundleItem.amount * (64 / maxStack)

            for (check in main.checks) {
                if (check.shouldCheck(bundleItem) && check.check(bundleItem)) {
                    item.amount = 0
                    return
                }
            }
        }

        if (totalWeight > MAX_BUNDLE_WEIGHT) {
            item.amount = 0
        }
    }

    private fun isBundle(type: Material): Boolean = type.name.endsWith("BUNDLE")
    private fun isShulkerBox(type: Material): Boolean = type.name.contains("SHULKER_BOX")

    private fun isContainer(type: Material): Boolean {
        return type.name.contains("SHULKER_BOX") ||
               type == Material.CHEST ||
               type == Material.TRAPPED_CHEST ||
               type == Material.BARREL ||
               type == Material.DISPENSER ||
               type == Material.DROPPER ||
               type == Material.HOPPER ||
               type == Material.CHISELED_BOOKSHELF
    }

    private fun checkBundleRecursion(item: ItemStack?, depth: Int): Boolean {
        if (item == null || !isBundle(item.type)) return false
        if (depth >= 1) return true

        try {
            if (item.hasItemMeta() && item.itemMeta is org.bukkit.inventory.meta.BundleMeta) {
                val bundleMeta = item.itemMeta as org.bukkit.inventory.meta.BundleMeta
                for (inner in bundleMeta.items) {
                    if (inner == null) continue
                    if (isBundle(inner.type)) return true
                }
            }
        } catch (e: Exception) {
            return false
        }
        return false
    }

    companion object {
        private const val MAX_BUNDLE_WEIGHT = 64
    }
}
