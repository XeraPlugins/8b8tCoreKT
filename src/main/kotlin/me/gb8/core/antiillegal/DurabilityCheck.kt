/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import me.gb8.core.antiillegal.Check
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.ItemMeta

class DurabilityCheck : Check {

    override fun check(item: ItemStack?): Boolean {
        item ?: return false
        if (!item.hasItemMeta()) return false
        val meta = item.itemMeta
        if (meta is Damageable && meta.damage < 0) return true
        return meta.isUnbreakable
    }

    override fun shouldCheck(item: ItemStack?): Boolean = true

    override fun fix(item: ItemStack?) {
        item ?: return
        if (!item.hasItemMeta()) return
        val meta = item.itemMeta
        if (meta.isUnbreakable) meta.isUnbreakable = false
        if (meta is Damageable) {
            if (meta.damage < 0) {
                meta.damage = 1
            } else if (meta.damage > item.type.maxDurability.toInt()) meta.damage = item.type.maxDurability.toInt()
            item.itemMeta = meta
        }
    }
}
