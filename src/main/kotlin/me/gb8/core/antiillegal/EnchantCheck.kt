/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class EnchantCheck : Check {

    override fun check(item: ItemStack?): Boolean {
        item ?: return false
        if (!item.hasItemMeta()) return false
        val meta = item.itemMeta
        if (item.type == Material.ENCHANTED_BOOK) return false
        if (item.type.isBlock) {
            if (isSpecialBlock(item)) {
                val enchants = meta.enchants
                return enchants.keys.any { keyOf(it) !in curseEnchantments }
            }
            return meta.hasEnchants()
        }
        val enchants = meta.enchants
        val allowedKeys = allowedKeysFor(item)
        val hasIllegalEnchant = enchants.entries.any { (ench, lvl) ->
            lvl > ench.maxLevel || !ench.canEnchantItem(item) || keyOf(ench) !in allowedKeys
        }
        if (hasIllegalEnchant) return true

        val keys = enchants.keys.toList()
        for (first in keys.indices) {
            for (second in first + 1 until keys.size) {
                if (keys[first].conflictsWith(keys[second])) return true
            }
        }
        return false
    }

    private fun isSpecialBlock(item: ItemStack): Boolean =
        isCarvedPumpkin(item) || isPumpkin(item) || isHead(item) || isSkull(item)

    override fun shouldCheck(item: ItemStack?): Boolean = true

    override fun fix(item: ItemStack?) {
        item?.itemMeta?.let { meta ->
            if (item.type == Material.ENCHANTED_BOOK) return
            val enchants = meta.enchants.toMutableMap()
            var changed = false
            val removeAll = item.type.isBlock && !isSpecialBlock(item)

            enchants.entries.toList().forEach { (ench, lvl) ->
                when {
                    removeAll -> {
                        meta.removeEnchant(ench)
                        enchants.remove(ench)
                        changed = true
                    }
                    item.type.isBlock && isSpecialBlock(item) -> {
                        val k = keyOf(ench)
                        if (k !in curseEnchantments) {
                            meta.removeEnchant(ench)
                            enchants.remove(ench)
                            changed = true
                        } else if (lvl > ench.maxLevel) {
                            meta.removeEnchant(ench)
                            meta.addEnchant(ench, ench.maxLevel, false)
                            enchants[ench] = ench.maxLevel
                            changed = true
                        }
                    }
                    lvl > ench.maxLevel -> {
                        meta.removeEnchant(ench)
                        meta.addEnchant(ench, ench.maxLevel, false)
                        enchants[ench] = ench.maxLevel
                        changed = true
                    }
                    !ench.canEnchantItem(item) -> {
                        meta.removeEnchant(ench)
                        enchants.remove(ench)
                        changed = true
                    }
                }
            }

            val sorted = enchants.entries.sortedByDescending { it.value }
            val kept = mutableSetOf<Enchantment>()
            sorted.forEach { e ->
                val conflict = kept.any { it.conflictsWith(e.key) }
                if (conflict) {
                    meta.removeEnchant(e.key)
                    enchants.remove(e.key)
                    changed = true
                } else kept.add(e.key)
            }
            if (changed) item.itemMeta = meta
        }
    }

    private fun keyOf(e: Enchantment): String = e.key.key.lowercase()

    private fun isPumpkin(item: ItemStack): Boolean = item.type == Material.PUMPKIN
    private fun isCarvedPumpkin(item: ItemStack): Boolean = item.type == Material.CARVED_PUMPKIN
    private fun isHead(item: ItemStack): Boolean = item.type.name.endsWith("_HEAD")
    private fun isSkull(item: ItemStack): Boolean = item.type.name.endsWith("_SKULL")

    private fun allowedKeysFor(item: ItemStack): Set<String> {
        if (isHelmet(item)) return HELMET_ENCHANTS
        if (isChest(item)) return CHESTPLATE_ENCHANTS
        if (isLeggings(item)) return LEGGINGS_ENCHANTS
        if (isBoots(item)) return BOOTS_ENCHANTS
        if (isSword(item)) return SWORD_ENCHANTS
        if (isAxe(item)) return AXE_ENCHANTS
        if (isMiningTool(item)) return MINING_TOOL_ENCHANTS
        if (isBow(item)) return BOW_ENCHANTS
        if (isCrossbow(item)) return CROSSBOW_ENCHANTS
        if (isTrident(item)) return TRIDENT_ENCHANTS
        if (isFishingRod(item)) return FISHING_ROD_ENCHANTS
        if (isShears(item)) return SHEARS_ENCHANTS
        if (isElytra(item)) return ELYTRA_ENCHANTS
        if (isShield(item) || isFlint(item) || isCarrotRod(item) || isWarpedRod(item) || isBrush(item)) return BREAKABLE_ENCHANTS
        if (isCompass(item)) return COMPASS_ENCHANTS
        if (isMace(item)) return MACE_ENCHANTS
        if (isSpear(item)) return SPEAR_ENCHANTS
        return emptySet()
    }

    private fun isHelmet(item: ItemStack): Boolean {
        val n = item.type.name
        return n.endsWith("HELMET") || n == "TURTLE_HELMET"
    }
    private fun isChest(item: ItemStack): Boolean = item.type.name.endsWith("CHESTPLATE")
    private fun isLeggings(item: ItemStack): Boolean = item.type.name.endsWith("LEGGINGS")
    private fun isBoots(item: ItemStack): Boolean = item.type.name.endsWith("BOOTS")
    private fun isSword(item: ItemStack): Boolean = item.type.name.endsWith("SWORD")
    private fun isAxe(item: ItemStack): Boolean = item.type.name.endsWith("AXE")
    private fun isBow(item: ItemStack): Boolean = item.type == Material.BOW
    private fun isCrossbow(item: ItemStack): Boolean = item.type == Material.CROSSBOW
    private fun isTrident(item: ItemStack): Boolean = item.type == Material.TRIDENT
    private fun isElytra(item: ItemStack): Boolean = item.type == Material.ELYTRA
    private fun isShield(item: ItemStack): Boolean = item.type == Material.SHIELD
    private fun isFishingRod(item: ItemStack): Boolean = item.type == Material.FISHING_ROD
    private fun isBrush(item: ItemStack): Boolean = item.type == Material.BRUSH
    private fun isFlint(item: ItemStack): Boolean = item.type == Material.FLINT_AND_STEEL
    private fun isCarrotRod(item: ItemStack): Boolean = item.type == Material.CARROT_ON_A_STICK
    private fun isWarpedRod(item: ItemStack): Boolean = item.type == Material.WARPED_FUNGUS_ON_A_STICK
    private fun isCompass(item: ItemStack): Boolean = item.type == Material.COMPASS || item.type == Material.RECOVERY_COMPASS
    private fun isShears(item: ItemStack): Boolean = item.type == Material.SHEARS
    private fun isMiningTool(item: ItemStack): Boolean {
        val n = item.type.name
        return n.endsWith("PICKAXE") || n.endsWith("SHOVEL") || n.endsWith("HOE")
    }
    private fun isMace(item: ItemStack): Boolean = item.type.name == "MACE"
    private fun isSpear(item: ItemStack): Boolean = item.type.name.endsWith("_SPEAR")

    private companion object {
        val curseEnchantments = setOf("binding_curse", "vanishing_curse")
        val HELMET_ENCHANTS = setOf("mending", "unbreaking", "thorns", "respiration", "aqua_affinity", "binding_curse", "vanishing_curse", "protection", "projectile_protection", "fire_protection", "blast_protection")
        val CHESTPLATE_ENCHANTS = setOf("mending", "unbreaking", "thorns", "binding_curse", "vanishing_curse", "protection", "projectile_protection", "fire_protection", "blast_protection")
        val LEGGINGS_ENCHANTS = setOf("mending", "unbreaking", "thorns", "swift_sneak", "binding_curse", "vanishing_curse", "protection", "projectile_protection", "fire_protection", "blast_protection")
        val BOOTS_ENCHANTS = setOf("mending", "unbreaking", "thorns", "feather_falling", "soul_speed", "binding_curse", "vanishing_curse", "protection", "projectile_protection", "fire_protection", "blast_protection", "depth_strider", "frost_walker")
        val SWORD_ENCHANTS = setOf("mending", "unbreaking", "fire_aspect", "looting", "knockback", "sweeping_edge", "vanishing_curse", "sharpness", "smite", "bane_of_arthropods")
        val AXE_ENCHANTS = setOf("mending", "unbreaking", "efficiency", "vanishing_curse", "fortune", "silk_touch", "sharpness", "smite", "bane_of_arthropods", "cleaving")
        val MINING_TOOL_ENCHANTS = setOf("mending", "unbreaking", "efficiency", "vanishing_curse", "fortune", "silk_touch")
        val BOW_ENCHANTS = setOf("unbreaking", "power", "punch", "flame", "vanishing_curse", "infinity", "mending")
        val CROSSBOW_ENCHANTS = setOf("mending", "unbreaking", "quick_charge", "vanishing_curse", "piercing", "multishot")
        val TRIDENT_ENCHANTS = setOf("mending", "unbreaking", "impaling", "vanishing_curse", "channeling", "loyalty", "riptide")
        val FISHING_ROD_ENCHANTS = setOf("mending", "unbreaking", "lure", "luck_of_the_sea", "vanishing_curse")
        val SHEARS_ENCHANTS = setOf("mending", "unbreaking", "efficiency", "vanishing_curse")
        val ELYTRA_ENCHANTS = setOf("mending", "unbreaking", "binding_curse", "vanishing_curse")
        val BREAKABLE_ENCHANTS = setOf("mending", "unbreaking", "vanishing_curse")
        val COMPASS_ENCHANTS = setOf("vanishing_curse")
        val MACE_ENCHANTS = setOf("mending", "unbreaking", "fire_aspect", "wind_burst", "vanishing_curse", "smite", "bane_of_arthropods", "density", "breach")
        val SPEAR_ENCHANTS = setOf("mending", "unbreaking", "vanishing_curse", "sharpness", "lunge")
    }
}
