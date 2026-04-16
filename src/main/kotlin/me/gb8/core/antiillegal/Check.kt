/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import org.bukkit.inventory.ItemStack

interface Check {
    fun check(item: ItemStack?): Boolean
    fun shouldCheck(item: ItemStack?): Boolean = true
    fun fix(item: ItemStack?)
}
