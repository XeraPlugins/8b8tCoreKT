/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import me.gb8.core.util.GlobalUtils
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

final class ItemSizeCheck(maxSize: Int) : Check {
    @Volatile
    private var maxSize = maxSize

    fun updateMaxSize(maxSize: Int) {
        this.maxSize = maxSize
    }

    override fun check(item: ItemStack?): Boolean {
        item ?: return false
        return GlobalUtils.calculateItemSize(item) > maxSize
    }

    override fun shouldCheck(item: ItemStack?): Boolean {
        return item != null && item.type != Material.AIR && item.hasItemMeta()
    }

    override fun fix(item: ItemStack?) {
        item?.amount = 0
    }

}
