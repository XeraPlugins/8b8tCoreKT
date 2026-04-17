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

final class ItemSizeCheck : Check {
    override fun check(item: ItemStack?): Boolean {
        item ?: return false
        return GlobalUtils.calculateItemSize(item) > MAX_SIZE
    }

    override fun shouldCheck(item: ItemStack?): Boolean {
        if (item == null || item.type == Material.AIR || !item.hasItemMeta()) return false

        return when (item.type) {
            Material.WRITTEN_BOOK,
            Material.WRITABLE_BOOK,
            Material.FILLED_MAP -> true
            else -> item.itemMeta is BlockStateMeta
        }
    }

    override fun fix(item: ItemStack?) {
        item?.amount = 0
    }

    companion object {
        private const val MAX_SIZE = 106476
    }
}