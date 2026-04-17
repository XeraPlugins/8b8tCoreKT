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

class LegacyTextCheck : Check {

    override fun check(item: ItemStack?): Boolean {
        item?.itemMeta?.let { meta ->
            if (isLegitimatelySigned(meta)) return false

            meta.displayName()?.let { displayName ->
                if (LEGACY_COLOR_REGEX.containsMatchIn(GlobalUtils.getStringContent(displayName))) {
                    return true
                }
            }

            return meta.lore()?.any { lineComp ->
                LEGACY_COLOR_REGEX.containsMatchIn(GlobalUtils.getStringContent(lineComp))
            } ?: false
        }
        return false
    }

    override fun fix(item: ItemStack?) {
        item?.itemMeta?.let { meta ->
            if (isLegitimatelySigned(meta)) return@let

            meta.displayName()?.let { displayName ->
                val stripped = LEGACY_COLOR_REGEX.replace(
                    GlobalUtils.getStringContent(displayName),
                    ""
                )
                meta.displayName(Component.text(stripped))
            }

            meta.lore()?.let { oldLore ->
                val newLore = oldLore.map { lineComp ->
                    val line = GlobalUtils.getStringContent(lineComp)
                    Component.text(LEGACY_COLOR_REGEX.replace(line, ""))
                }
                meta.lore(newLore)
            }

            item.itemMeta = meta
        }
    }

    override fun shouldCheck(item: ItemStack?): Boolean = true

    private fun isLegitimatelySigned(meta: ItemMeta): Boolean {
        return meta.lore()?.any { lineComp ->
            val line = GlobalUtils.getStringContent(lineComp)
            SIGNED_ITEM_REGEX.containsMatchIn(line) ||
            BOOK_AUTHOR_REGEX.containsMatchIn(line) ||
            MAP_AUTHOR_REGEX.containsMatchIn(line)
        } ?: false
    }

    companion object {
        private val LEGACY_COLOR_REGEX = Regex("§[0-9a-fk-or]", RegexOption.IGNORE_CASE)
        private val SIGNED_ITEM_REGEX = Regex("§[0-9a-f]by §[0-9a-f]@[^§\n\r]*", RegexOption.IGNORE_CASE)
        private val BOOK_AUTHOR_REGEX = Regex("§[0-9a-f]Author: §[0-9a-f][^§\n\r]*", RegexOption.IGNORE_CASE)
        private val MAP_AUTHOR_REGEX = Regex("§7by @[^§\n\r]*", RegexOption.IGNORE_CASE)
    }
}