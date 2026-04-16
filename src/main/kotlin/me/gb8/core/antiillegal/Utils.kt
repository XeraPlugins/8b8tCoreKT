/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import me.gb8.core.antiillegal.AntiIllegalMain
import org.bukkit.entity.ArmorStand
import org.bukkit.inventory.EntityEquipment
import org.bukkit.inventory.ItemStack

object Utils {
    @JvmStatic
    fun checkStand(stand: ArmorStand, main: AntiIllegalMain) {
        val eq: EntityEquipment = stand.equipment
        main.checkFixItem(eq.itemInMainHand, null)
        main.checkFixItem(eq.itemInOffHand, null)
        for (item in eq.armorContents) main.checkFixItem(item, null)
    }
}
