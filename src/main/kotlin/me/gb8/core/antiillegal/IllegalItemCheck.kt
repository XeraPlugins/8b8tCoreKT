/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class IllegalItemCheck(illegalItems: List<String>) : Check {
    @Volatile
    private var illegals: Set<Material> = parseConfig(illegalItems)

    fun updateIllegalItems(illegalItems: List<String>) {
        illegals = parseConfig(illegalItems)
    }

    override fun check(item: ItemStack?): Boolean {
        item ?: return false
        if (item.type !in illegals) return false

        item.itemMeta?.takeIf { it.enchants.isNotEmpty() }?.let { meta ->
            if (isSpecialBlock(item)) {
                val onlyCurses = meta.enchants.keys.all { enchant ->
                    val key = enchant.key.key.lowercase()
                    key == "binding_curse" || key == "vanishing_curse"
                }
                if (onlyCurses) return false
            }
        }

        return true
    }

    override fun shouldCheck(item: ItemStack?): Boolean = true

    override fun fix(item: ItemStack?) {
        item?.amount = 0
    }

    private fun parseConfig(illegalItems: List<String>): Set<Material> {
        return illegalItems.flatMap { raw ->
            val rawUpper = raw.uppercase()
            if (rawUpper.contains("*")) {
                val pattern = rawUpper.replace("*", "")
                MATERIAL_NAMES.filter { it.contains(pattern) }
                    .mapNotNull { Material.getMaterial(it) }
            } else {
                listOfNotNull(Material.getMaterial(rawUpper))
            }
        }.toSet()
    }

    private fun isPumpkin(item: ItemStack): Boolean = item.type == Material.PUMPKIN
    private fun isCarvedPumpkin(item: ItemStack): Boolean = item.type == Material.CARVED_PUMPKIN
    private fun isHead(item: ItemStack): Boolean = item.type.name.endsWith("_HEAD")
    private fun isSkull(item: ItemStack): Boolean = item.type.name.endsWith("_SKULL")

    private fun isSpecialBlock(item: ItemStack): Boolean =
        isPumpkin(item) || isCarvedPumpkin(item) || isHead(item) || isSkull(item)

    private companion object {
        val MATERIAL_NAMES = Material.entries.map { it.name }
    }
}
