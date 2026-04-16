/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import me.gb8.core.antiillegal.Check
import me.gb8.core.util.GlobalUtils
import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.util.regex.Pattern

class LegacyTextCheck : Check {

    override fun check(item: ItemStack?): Boolean {
        item ?: return false
        if (!item.hasItemMeta()) return false
        val meta = item.itemMeta

        if (isLegitimatelySigned(meta)) return false

        if (meta.hasDisplayName()) {
            val name = GlobalUtils.getStringContent(meta.displayName())
            if (LEGACY_COLOR_PATTERN.matcher(name).find()) return true
        }

        if (meta.hasLore()) {
            for (lineComp in meta.lore() ?: emptyList()) {
                val line = GlobalUtils.getStringContent(lineComp)
                if (LEGACY_COLOR_PATTERN.matcher(line).find()) return true
            }
        }

        return false
    }

    override fun fix(item: ItemStack?) {
        val meta = item?.itemMeta ?: return

        if (isLegitimatelySigned(meta)) return

        if (meta.hasDisplayName()) {
            val legacy = GlobalUtils.getStringContent(meta.displayName())
            val stripped = LEGACY_COLOR_PATTERN.matcher(legacy).replaceAll("")
            meta.displayName(Component.text(stripped))
        }

        if (meta.hasLore()) {
            val oldLore = meta.lore() ?: emptyList()
            val newLore = oldLore.map { lineComp ->
                val line = GlobalUtils.getStringContent(lineComp)
                Component.text(LEGACY_COLOR_PATTERN.matcher(line).replaceAll(""))
            }
            meta.lore(newLore)
        }

        item.itemMeta = meta
    }

    override fun shouldCheck(item: ItemStack?): Boolean = true

    private fun isLegitimatelySigned(meta: ItemMeta): Boolean {
        if (!meta.hasLore()) return false
        for (lineComp in meta.lore() ?: emptyList()) {
            val line = GlobalUtils.getStringContent(lineComp)
            if (SIGNED_ITEM_PATTERN.matcher(line).find() ||
                BOOK_AUTHOR_PATTERN.matcher(line).find() ||
                MAP_AUTHOR_PATTERN.matcher(line).find()) {
                return true
            }
        }
        return false
    }

    companion object {
        private val LEGACY_COLOR_PATTERN = Pattern.compile("§[0-9a-fk-or]", Pattern.CASE_INSENSITIVE)
        private val SIGNED_ITEM_PATTERN = Pattern.compile("§[0-9a-f]by §[0-9a-f]@[^§\n\r]*", Pattern.CASE_INSENSITIVE)
        private val BOOK_AUTHOR_PATTERN = Pattern.compile("§[0-9a-f]Author: §[0-9a-f][^§\n\r]*", Pattern.CASE_INSENSITIVE)
        private val MAP_AUTHOR_PATTERN = Pattern.compile("§7by @[^§\n\r]*", Pattern.CASE_INSENSITIVE)
    }
}
