/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemContainerContents
import me.gb8.core.antiillegal.AntiIllegalMain
import me.gb8.core.antiillegal.Check
import me.gb8.core.util.GlobalUtils
import org.bukkit.inventory.ItemStack
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import java.util.logging.Level

class ContainerContentCheck(private val main: AntiIllegalMain) : Check {

    override fun check(item: ItemStack?): Boolean {
        if (item == null || !item.hasData(DataComponentTypes.CONTAINER)) return false
        val contents = item.getData(DataComponentTypes.CONTAINER) ?: return false

        val applicableChecks = main.checks.filter { isApplicableCheck(it) }

        return contents.contents().any { content ->
            hasIllegalContent(content, applicableChecks)
        }.also { hasIllegal ->
            if (hasIllegal && AntiIllegalMain.debug) {
                val failingCheck = applicableChecks.firstOrNull { check ->
                    contents.contents().any { content -> hasIllegalContent(content, listOf(check)) }
                }
                failingCheck?.let {
                    GlobalUtils.log(Level.INFO, "&cContainerContentCheck flagged shulker because of item flagged by %s", it.javaClass.simpleName)
                    item.editPersistentDataContainer { pdc ->
                        pdc.set(NamespacedKey(main.plugin, "last_failed_check"), PersistentDataType.STRING, it.javaClass.simpleName)
                    }
                }
            }
        }
    }

    override fun shouldCheck(item: ItemStack?): Boolean {
        return item != null && item.hasData(DataComponentTypes.CONTAINER)
    }

    override fun fix(item: ItemStack?) {
        if (item == null || !item.hasData(DataComponentTypes.CONTAINER)) return
        val contents = item.getData(DataComponentTypes.CONTAINER) ?: return

        val fixableChecks = main.checks.filter { isFixableCheck(it) }
        var changed = false

        val newContents = contents.contents().map { content ->
            content?.let { item ->
                if (item.type.isAir) return@let item
                fixableChecks.forEach { check ->
                    if (check.shouldCheck(item) && check.check(item)) {
                        check.fix(item)
                        changed = true
                    }
                }
                item
            }
        }

        if (changed) {
            item.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(newContents))
        }
    }

    private fun isApplicableCheck(check: Check): Boolean =
        check != this && check !is AntiPrefilledContainers && check !is EnchantCheck && check !is IllegalDataCheck

    private fun isFixableCheck(check: Check): Boolean =
        check is EnchantCheck || check is IllegalDataCheck

    private fun hasIllegalContent(content: ItemStack?, checks: List<Check>): Boolean {
        content ?: return false
        if (content.type.isAir) return false
        return checks.any { check -> check.shouldCheck(content) && check.check(content) }
    }
}