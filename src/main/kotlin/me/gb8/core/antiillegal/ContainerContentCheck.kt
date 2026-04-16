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

        for (content in contents.contents()) {
            if (content == null || content.type.isAir) continue
            for (check in main.checks) {
                if (check == this || check is AntiPrefilledContainers) continue
                if (check is EnchantCheck || check is IllegalDataCheck) continue
                if (check.shouldCheck(content) && check.check(content)) {
                    if (AntiIllegalMain.debug) GlobalUtils.log(Level.INFO, "&cContainerContentCheck flagged shulker because of item %s flagged by %s", content.type.toString(), check.javaClass.simpleName)
                    item.editPersistentDataContainer { pdc ->
                        pdc.set(NamespacedKey(main.plugin, "last_failed_check"), PersistentDataType.STRING, check.javaClass.simpleName)
                    }
                    return true
                }
            }
        }
        return false
    }

    override fun shouldCheck(item: ItemStack?): Boolean {
        return item != null && item.hasData(DataComponentTypes.CONTAINER)
    }

    override fun fix(item: ItemStack?) {
        if (item == null || !item.hasData(DataComponentTypes.CONTAINER)) return
        val contents = item.getData(DataComponentTypes.CONTAINER) ?: return

        val newContents = mutableListOf<ItemStack?>()
        var changed = false
        for (content in contents.contents()) {
            if (content == null || content.type.isAir) {
                newContents.add(content)
                continue
            }
            for (check in main.checks) {
                if (check == this || check is AntiPrefilledContainers) continue
                if (check.shouldCheck(content) && check.check(content)) {
                    if (check is EnchantCheck || check is IllegalDataCheck) {
                        check.fix(content)
                        changed = true
                    }
                }
            }
            newContents.add(content)
        }
        if (changed) {
            item.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(newContents))
        }
    }
}
