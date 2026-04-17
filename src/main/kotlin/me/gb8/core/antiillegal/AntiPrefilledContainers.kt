/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import io.papermc.paper.datacomponent.DataComponentTypes
import me.gb8.core.antiillegal.Check
import org.bukkit.Material
import org.bukkit.block.Container
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta

class AntiPrefilledContainers : Check {

    override fun check(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir || !shouldCheck(item)) return false

        item.getData(DataComponentTypes.CONTAINER)?.let { contents ->
            return contents.contents().any { content ->
                content?.let { !it.type.isAir && ALL_STORAGE.contains(it.type) } ?: false
            }
        }

        (item.itemMeta as? BlockStateMeta)?.takeIf { it.hasBlockState() }?.let { meta ->
            val state = meta.blockState
            if (state is Container) {
                return state.inventory.contents.any { content ->
                    content != null && !content.type.isAir
                }
            }
        }

        return false
    }

    override fun shouldCheck(item: ItemStack?): Boolean {
        return item?.type in CONTAINERS
    }

    override fun fix(item: ItemStack?) {
        item?.takeIf { !it.type.isAir }?.let { stack ->
            if (stack.hasData(DataComponentTypes.CONTAINER)) {
                stack.unsetData(DataComponentTypes.CONTAINER)
            }

            (stack.itemMeta as? BlockStateMeta)?.takeIf { it.hasBlockState() }?.let { meta ->
                val state = meta.blockState
                if (state is Container) {
                    state.inventory.clear()
                    state.update()
                    meta.blockState = state
                    stack.itemMeta = meta
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

        private val ALL_STORAGE = CONTAINERS + setOf(
            Material.SHULKER_BOX, Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX, Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX, Material.PURPLE_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX, Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX
        )
    }
}