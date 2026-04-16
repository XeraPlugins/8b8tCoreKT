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
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.util.logging.Level

class IllegalItemCheck : Check {
    private val illegals: HashSet<Material>

    init {
        illegals = parseConfig()
    }

    override fun check(item: ItemStack?): Boolean {
        if (item == null) return false
        val listed = illegals.contains(item.type)
        if (!listed) return false
        if (item.hasItemMeta()) {
            val meta = item.itemMeta
            if (meta != null && meta.enchants.isNotEmpty()) {
                if (isPumpkin(item) || isCarvedPumpkin(item) || isHead(item) || isSkull(item)) {
                    val ench = meta.enchants
                    var onlyCurses = true
                    for (e in ench.keys) {
                        val k = e.key.key.lowercase()
                        if (k != "binding_curse" && k != "vanishing_curse") {
                            onlyCurses = false
                            break
                        }
                    }
                    if (onlyCurses) return false
                }
            }
        }
        return true
    }

    override fun shouldCheck(item: ItemStack?): Boolean = true

    override fun fix(item: ItemStack?) {
        item?.amount = 0
    }

    private fun parseConfig(): HashSet<Material> {
        val materialNames = Material.entries.map { it.name }
        val strList = Main.instance.config.getStringList("AntiIllegal.IllegalItems")
        val output = ArrayList<Material>()
        for (raw in strList) {
            try {
                var rawUpper = raw.uppercase()
                if (rawUpper.contains("*")) {
                    rawUpper = rawUpper.replace("*", "")
                    for (materialName in materialNames) {
                        if (materialName.contains(rawUpper)) {
                            Material.getMaterial(materialName)?.let { output.add(it) }
                        }
                    }
                    continue
                }
                val material = Material.getMaterial(rawUpper)
                    ?: throw IllegalArgumentException(rawUpper)
                output.add(material)
            } catch (e: Exception) {
                GlobalUtils.log(Level.WARNING, "&3Unknown material&r&a %s&r&3 in blocks section of the config", raw)
            }
        }
        return HashSet(output)
    }

    private fun isPumpkin(item: ItemStack): Boolean = item.type == Material.PUMPKIN
    private fun isCarvedPumpkin(item: ItemStack): Boolean = item.type == Material.CARVED_PUMPKIN
    private fun isHead(item: ItemStack): Boolean = item.type.name.endsWith("_HEAD")
    private fun isSkull(item: ItemStack): Boolean = item.type.name.endsWith("_SKULL")
}
