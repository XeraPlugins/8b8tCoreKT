/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import me.gb8.core.Main
import me.gb8.core.util.GlobalUtils
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.logging.Level

class IllegalItemCheck : Check {
    private val illegals: Set<Material>

    init {
        illegals = parseConfig()
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

    private fun parseConfig(): Set<Material> {
        val materialNames = Material.entries.map { it.name }
        val strList = Main.instance.config.getStringList("AntiIllegal.IllegalItems")

        return strList.flatMap { raw ->
            val rawUpper = raw.uppercase()
            if (rawUpper.contains("*")) {
                val pattern = rawUpper.replace("*", "")
                materialNames.filter { it.contains(pattern) }
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
}