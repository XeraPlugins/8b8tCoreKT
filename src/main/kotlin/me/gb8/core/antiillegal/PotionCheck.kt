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
import org.bukkit.potion.PotionEffect
import org.bukkit.inventory.meta.PotionMeta

class PotionCheck : Check {

    override fun check(item: ItemStack?): Boolean {
        if (!shouldCheck(item)) return false

        val meta = item?.itemMeta as? PotionMeta ?: return false
        return meta.customEffects.any { isIllegalEffect(it) }
    }

    override fun shouldCheck(item: ItemStack?): Boolean {
        return item?.takeIf { it.hasItemMeta() }?.let { it.itemMeta is PotionMeta } ?: false
    }

    override fun fix(item: ItemStack?) {
        item?.takeIf { shouldCheck(it) }?.apply { amount = 0 }
    }

    private fun isIllegalEffect(effect: PotionEffect): Boolean {
        return effect.isInfinite ||
               effect.duration > MAX_LEGAL_DURATION ||
               effect.amplifier > MAX_LEGAL_AMPLIFIER
    }

    companion object {
        private const val MAX_LEGAL_DURATION = 12000
        private const val MAX_LEGAL_AMPLIFIER = 2
    }
}
