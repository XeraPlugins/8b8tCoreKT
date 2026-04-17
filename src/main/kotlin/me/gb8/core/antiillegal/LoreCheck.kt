/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import me.gb8.core.antiillegal.Check
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class LoreCheck : Check {

    override fun check(item: ItemStack?): Boolean {
        return item?.hasItemMeta() == true && item.itemMeta?.hasLore() == true
    }

    override fun shouldCheck(item: ItemStack?): Boolean {
        return when (item?.type) {
            Material.MAP, Material.FILLED_MAP -> false
            else -> true
        }
    }

    override fun fix(item: ItemStack?) {
        item?.itemMeta?.let { meta ->
            meta.lore(emptyList())
            item.itemMeta = meta
        }
    }
}
