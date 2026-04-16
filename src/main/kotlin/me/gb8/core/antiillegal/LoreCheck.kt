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
import org.bukkit.inventory.meta.ItemMeta

class LoreCheck : Check {

    override fun check(item: ItemStack?): Boolean {
        item ?: return false
        return item.hasItemMeta() && item.itemMeta.hasLore()
    }

    override fun shouldCheck(item: ItemStack?): Boolean {
        item ?: return false
        return item.type != Material.MAP && item.type != Material.FILLED_MAP
    }

    override fun fix(item: ItemStack?) {
        val meta = item?.itemMeta ?: return
        meta.lore(emptyList())
        item.itemMeta = meta
    }
}
