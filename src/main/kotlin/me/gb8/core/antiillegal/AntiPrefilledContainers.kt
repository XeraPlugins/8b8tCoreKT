/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemContainerContents
import me.gb8.core.antiillegal.Check
import org.bukkit.Material
import org.bukkit.block.BlockState
import org.bukkit.block.Container
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta

class AntiPrefilledContainers : Check {

    override fun check(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir) return false
        if (!shouldCheck(item)) return false

        if (item.hasData(DataComponentTypes.CONTAINER)) {
            val contents = item.getData(DataComponentTypes.CONTAINER)
            if (contents != null) {
                val items = contents.contents()
                for (content in items) {
                    if (content != null && !content.type.isAir) {
                        if (ALL_STORAGE.contains(content.type)) {
                            return true
                        }
                        return true
                    }
                }
            }
        }

        if (item.hasItemMeta() && item.itemMeta is BlockStateMeta) {
            val meta = item.itemMeta as BlockStateMeta
            if (meta.hasBlockState()) {
                val state = meta.blockState
                if (state is Container) {
                    for (content in state.inventory.contents) {
                        if (content != null && !content.type.isAir) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    override fun shouldCheck(item: ItemStack?): Boolean {
        return item != null && CONTAINERS.contains(item.type)
    }

    override fun fix(item: ItemStack?) {
        if (item == null || item.type.isAir) return

        if (item.hasData(DataComponentTypes.CONTAINER)) {
            item.unsetData(DataComponentTypes.CONTAINER)
        }

        if (item.hasItemMeta() && item.itemMeta is BlockStateMeta) {
            val meta = item.itemMeta as BlockStateMeta
            if (meta.hasBlockState()) {
                val state = meta.blockState
                if (state is Container) {
                    state.inventory.clear()
                    state.update()
                    meta.blockState = state
                    item.itemMeta = meta
                }
            }
        }
    }

    companion object {
        private val CONTAINERS = setOf(
            Material.CHEST,
            Material.TRAPPED_CHEST,
            Material.BARREL,
            Material.DISPENSER,
            Material.DROPPER,
            Material.HOPPER,
            Material.CHISELED_BOOKSHELF
        )

        private val ALL_STORAGE = setOf(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.DISPENSER, Material.DROPPER, Material.HOPPER,
            Material.CHISELED_BOOKSHELF,
            Material.SHULKER_BOX, Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX, Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX, Material.PURPLE_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX, Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX
        )
    }
}
