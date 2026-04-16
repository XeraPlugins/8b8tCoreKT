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
import org.bukkit.inventory.meta.BookMeta
import java.nio.charset.CharsetEncoder
import java.nio.charset.StandardCharsets

class BookCheck : Check {
    private val encoder: CharsetEncoder = StandardCharsets.UTF_8.newEncoder()

    override fun check(item: ItemStack?): Boolean {
        val meta: BookMeta
        try {
            meta = item?.itemMeta as? BookMeta ?: return false
        } catch (e: Exception) {
            return false
        }
        val pages = getPages(meta)
        return !(pages != null && encoder.canEncode(pages.joinToString(" ")))
    }

    override fun shouldCheck(item: ItemStack?): Boolean {
        item ?: return false
        return item.hasItemMeta() && item.itemMeta is BookMeta
    }

    override fun fix(item: ItemStack?) {
        val meta = item?.itemMeta as? BookMeta ?: return
        val cleanPages = mutableListOf<Component>()

        val currPages = getPages(meta) ?: return

        for (page in currPages) {
            val builder = StringBuilder()
            for (c in page.toCharArray()) {
                if (encoder.canEncode(c)) {
                    builder.append(c)
                }
            }
            val cleanComponent = Component.text(builder.toString())
            cleanPages.add(cleanComponent)
        }

        meta.pages(cleanPages)
        item.itemMeta = meta
    }

    private fun getPages(meta: BookMeta): Array<String>? {
        if (!meta.hasPages()) {
            return null
        }

        return meta.pages().map { GlobalUtils.getStringContent(it) }.toTypedArray()
    }
}
