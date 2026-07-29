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
    private val applicableChecks: Array<Check> by lazy {
        main.checks.filter(::isApplicableCheck).toTypedArray()
    }

    override fun check(item: ItemStack?): Boolean {
        if (!shouldCheck(item)) return false
        item ?: return false
        val contents = item.getData(DataComponentTypes.CONTAINER) ?: return false
        @Suppress("UNCHECKED_CAST")
        val containerItems = contents.contents() as List<ItemStack?>

        var failingCheck: Check? = null
        scan@ for (content in containerItems) {
            if (content == null || content.type.isAir) continue
            for (check in applicableChecks) {
                if (check.shouldCheck(content) && check.check(content)) {
                    failingCheck = check
                    break@scan
                }
            }
        }

        if (failingCheck != null && AntiIllegalMain.debug) {
            GlobalUtils.log(Level.INFO, "&cContainerContentCheck flagged container because of item flagged by %s", failingCheck.javaClass.simpleName)
            item.editPersistentDataContainer { pdc ->
                pdc.set(NamespacedKey(main.plugin, "last_failed_check"), PersistentDataType.STRING, failingCheck.javaClass.simpleName)
            }
        }
        return failingCheck != null
    }

    override fun shouldCheck(item: ItemStack?): Boolean {
        // Do not edit to check chests we only check
		// When the shulker is acutally open. As we
		// do not want to delete players illegal items
		// in chests which people have collected for years.
        return item != null &&
            !item.type.name.endsWith("SHULKER_BOX") &&
            item.hasData(DataComponentTypes.CONTAINER)
    }

    override fun fix(item: ItemStack?) {
        if (!shouldCheck(item)) return
        item ?: return
        val contents = item.getData(DataComponentTypes.CONTAINER) ?: return

        var changed = false

        @Suppress("UNCHECKED_CAST")
        val containerItems = contents.contents() as List<ItemStack?>
        val newContents = containerItems.mapNotNull { content ->
            val nestedItem = content?.takeUnless { it.type.isAir } ?: return@mapNotNull null
            for (check in applicableChecks) {
                if (check.shouldCheck(nestedItem) && check.check(nestedItem)) {
                    check.fix(nestedItem)
                    changed = true
                    if (nestedItem.type.isAir || nestedItem.amount <= 0) break
                }
            }
            nestedItem.takeUnless { it.type.isAir || it.amount <= 0 }
        }

        if (changed) {
            item.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(newContents))
        }
    }

    private fun isApplicableCheck(check: Check): Boolean =
        check != this && check !is EnchantCheck && check !is IllegalDataCheck

}
