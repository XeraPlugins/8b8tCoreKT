/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import io.papermc.paper.datacomponent.DataComponentType
import io.papermc.paper.registry.keys.DataComponentTypeKeys
import me.gb8.core.antiillegal.AntiIllegalMain
import me.gb8.core.antiillegal.Check
import me.gb8.core.util.GlobalUtils
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.Material
import org.bukkit.Registry
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.util.logging.Level

class NameCheck(private val config: ConfigurationSection) : Check {

    override fun check(item: ItemStack?): Boolean {
        if (item == null || item.type == Material.AIR) return false

        if (item.hasData(ENTITY_DATA) && !item.type.name.contains("SHULKER_BOX")) {
            if (AntiIllegalMain.debug) GlobalUtils.log(Level.INFO, "&cNameCheck flagged item %s for having ENTITY_DATA", item.type.toString())
            return true
        }

        if (!item.hasItemMeta()) return false

        val meta = item.itemMeta
        if (meta == null || !meta.hasDisplayName()) return false

        val illegal = isIllegalNameContent(meta.displayName())
        if (illegal) {
            if (AntiIllegalMain.debug) GlobalUtils.log(Level.INFO, "&cNameCheck flagged item %s for illegal name content: %s", item.type.toString(), GlobalUtils.getStringContent(meta.displayName()))
        }
        return illegal
    }

    override fun shouldCheck(item: ItemStack?): Boolean {
        return item != null && (item.hasItemMeta() || item.hasData(ENTITY_DATA))
    }

    override fun fix(item: ItemStack?) {
        if (item == null) return

        if (item.hasData(ENTITY_DATA)) {
            item.unsetData(ENTITY_DATA)
        }

        if (!item.hasItemMeta()) return

        val meta = item.itemMeta
        if (meta != null && meta.hasDisplayName()) {
            if (isIllegalNameContent(meta.displayName())) {
                meta.displayName(null)
                item.itemMeta = meta
            }
        }
    }

    private fun isIllegalNameContent(component: Component?): Boolean {
        if (component == null) return false

        val content = GlobalUtils.getStringContent(component)
        if (content.length > STRICT_MAX_LENGTH) return true

        for (c in content) {
            val cp = c.code
            if (Character.isISOControl(cp) || Character.getType(cp) == Character.PRIVATE_USE.toInt()) return true
        }

        val json = GsonComponentSerializer.gson().serialize(component)
        return json.length > MAX_JSON_LENGTH
    }

    companion object {
        private const val STRICT_MAX_LENGTH = 255
        private const val MAX_JSON_LENGTH = 8192

        private val ENTITY_DATA =
            Registry.DATA_COMPONENT_TYPE.getOrThrow(DataComponentTypeKeys.ENTITY_DATA)
    }
}
