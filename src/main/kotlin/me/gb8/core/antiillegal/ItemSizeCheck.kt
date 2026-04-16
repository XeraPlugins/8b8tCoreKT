/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import me.gb8.core.antiillegal.Check
import me.gb8.core.util.GlobalUtils
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta

class ItemSizeCheck : Check {
    override fun check(item: ItemStack?): Boolean {
        item ?: return false
        val size = getSize(item)
        return size > MAX_SIZE
    }

    override fun shouldCheck(item: ItemStack?): Boolean {
        if (item == null || item.type == Material.AIR || !item.hasItemMeta()) return false

        val type = item.type
        return item.itemMeta is BlockStateMeta ||
                type == Material.WRITTEN_BOOK ||
                type == Material.WRITABLE_BOOK ||
                type == Material.FILLED_MAP
    }

    override fun fix(item: ItemStack?) {
        item?.amount = 0
    }

    private fun getSize(itemStack: ItemStack): Int {
        return GlobalUtils.calculateItemSize(itemStack)
    }

    companion object {
        private const val MAX_SIZE = 106476
    }
}
