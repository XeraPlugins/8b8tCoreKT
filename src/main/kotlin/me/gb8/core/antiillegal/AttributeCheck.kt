/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import me.gb8.core.antiillegal.AntiIllegalMain
import me.gb8.core.antiillegal.Check
import me.gb8.core.util.GlobalUtils
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.util.logging.Level

class AttributeCheck : Check {
    
    override fun check(item: ItemStack?): Boolean {
        item ?: return false
        if (!item.hasItemMeta()) return false
        val illegal = item.itemMeta.hasAttributeModifiers()
        if (illegal) {
            if (AntiIllegalMain.debug) GlobalUtils.log(Level.INFO, "&cAttributeCheck flagged item %s. HasModifiers: %b", item.type.toString(), item.itemMeta.hasAttributeModifiers())
        }
        return illegal
    }

    override fun shouldCheck(item: ItemStack?): Boolean = true

    override fun fix(item: ItemStack?) {
        val meta = item?.itemMeta ?: return
        if (meta.hasAttributeModifiers()) {
            meta.attributeModifiers?.forEach { attr, _ -> meta.removeAttributeModifier(attr) }
            meta.removeItemFlags(*meta.itemFlags.toTypedArray())
        }
        item.itemMeta = meta
    }
}
