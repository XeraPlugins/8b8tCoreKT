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

class OverStackCheck : Check {

    override fun check(item: ItemStack?): Boolean {
        item ?: return false
        val vanillaMaxStack = item.type.maxStackSize
        return item.amount > vanillaMaxStack
    }

    override fun shouldCheck(item: ItemStack?): Boolean = true

    override fun fix(item: ItemStack?) {
        item ?: return
        val vanillaMaxStack = item.type.maxStackSize
        item.amount = vanillaMaxStack
    }
}
