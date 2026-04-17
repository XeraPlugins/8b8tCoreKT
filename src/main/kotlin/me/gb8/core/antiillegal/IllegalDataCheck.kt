/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import io.papermc.paper.datacomponent.DataComponentTypes
import me.gb8.core.antiillegal.AntiIllegalMain
import me.gb8.core.antiillegal.Check
import me.gb8.core.util.GlobalUtils
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.logging.Level

class IllegalDataCheck : Check {

    override fun check(item: ItemStack?): Boolean {
        item ?: return false
        if (item.type.isAir || item.type.isContainer()) return false

        val metaIssues = item.itemMeta?.let { meta ->
            hasIllegalName(meta) || (meta.isGlider && item.type != Material.ELYTRA)
        } ?: false

        val dataIssues = runCatching {
            hasIllegalWaterloggedState(item) ||
            (item.type != Material.TOTEM_OF_UNDYING && item.hasData(DataComponentTypes.DEATH_PROTECTION)) ||
            (item.type.isPotion() && hasIllegalPotionEffects(item)) ||
            hasIllegalFoodEffects(item) ||
            (item.type.maxDurability > 0 && !item.hasData(DataComponentTypes.MAX_DAMAGE)) ||
            hasIllegalMaxStack(item) ||
            hasIllegalToolComponent(item)
        }.getOrDefault(false)

        return !metaIssues && dataIssues
    }

    private fun Material.isContainer(): Boolean =
        name.contains("SHULKER_BOX") || name.endsWith("BUNDLE")

    private fun Material.isPotion(): Boolean =
        this in setOf(Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION, Material.TIPPED_ARROW)

    private fun hasIllegalMaxStack(item: ItemStack): Boolean {
        val type = item.type
        return if (item.hasData(DataComponentTypes.MAX_STACK_SIZE)) {
            val maxStack = item.getData(DataComponentTypes.MAX_STACK_SIZE)
            maxStack?.let { it < 1 || it > 99 } ?: false
        } else {
            type.maxStackSize > 1 && item.maxStackSize == 1
        }
    }

    override fun shouldCheck(item: ItemStack?): Boolean {
        item ?: return false
        return !item.type.isAir && !item.type.isContainer()
    }

    override fun fix(item: ItemStack?) {
        item ?: return
        if (item.type.isAir || item.type.isContainer()) return

        item.itemMeta?.apply {
            if (hasIllegalName(this)) customName(null)
            if (isGlider && item.type != Material.ELYTRA) isGlider = false
        }

        fixWaterloggedState(item)

        if (item.type != Material.TOTEM_OF_UNDYING) {
            item.unsetData(DataComponentTypes.DEATH_PROTECTION)
        }

        if (item.type.isPotion() && hasIllegalPotionEffects(item)) {
            item.unsetData(DataComponentTypes.POTION_CONTENTS)
        }

        if (hasIllegalFoodEffects(item)) {
            item.unsetData(DataComponentTypes.FOOD)
            item.unsetData(DataComponentTypes.CONSUMABLE)
        }

        if (item.type.maxDurability > 0 && !item.hasData(DataComponentTypes.MAX_DAMAGE)) {
            item.setData(DataComponentTypes.MAX_DAMAGE, item.type.maxDurability.toInt())
        }

        fixItemMaxStack(item)

        if (hasIllegalToolComponent(item)) {
            item.unsetData(DataComponentTypes.TOOL)
        }
    }

    private fun fixItemMaxStack(item: ItemStack) {
        val type = item.type
        if (item.hasData(DataComponentTypes.MAX_STACK_SIZE)) {
            val maxStack = item.getData(DataComponentTypes.MAX_STACK_SIZE)
            when {
                maxStack == null -> {
                    if (type.maxStackSize > 1) {
                        item.setData(DataComponentTypes.MAX_STACK_SIZE, type.maxStackSize)
                    } else {
                        item.unsetData(DataComponentTypes.MAX_STACK_SIZE)
                    }
                }
                maxStack < 1 || maxStack > 99 -> item.unsetData(DataComponentTypes.MAX_STACK_SIZE)
            }
        } else {
            if (type.maxStackSize > 1 && item.maxStackSize == 1) {
                item.setData(DataComponentTypes.MAX_STACK_SIZE, type.maxStackSize)
            }
        }
    }

    private fun hasIllegalToolComponent(item: ItemStack): Boolean {
        if (!item.hasData(DataComponentTypes.TOOL)) return false

        val shouldHaveTool = item.type.getDefaultData(DataComponentTypes.TOOL) != null
        if (!shouldHaveTool) return true

        val tool = item.getData(DataComponentTypes.TOOL) ?: return false

        return tool.rules().any { rule ->
            rule.speed()?.let { speed -> speed > MAX_LEGAL_TOOL_SPEED } == true
        } || tool.defaultMiningSpeed() > MAX_LEGAL_TOOL_SPEED
    }

    private fun hasIllegalWaterloggedState(item: ItemStack): Boolean {
        if (!item.type.isBlock) return false
        if (!item.hasData(DataComponentTypes.BLOCK_DATA)) return false

        return runCatching {
            val properties = item.getData(DataComponentTypes.BLOCK_DATA) ?: return@runCatching false
            val dataString = properties.toString()
            "waterlogged=true" in dataString || "waterlogged=\"true\"" in dataString
        }.getOrDefault(false)
    }

    private fun fixWaterloggedState(item: ItemStack) {
        if (!item.type.isBlock) return
        if (hasIllegalWaterloggedState(item)) {
            item.unsetData(DataComponentTypes.BLOCK_DATA)
        }
    }

    private fun hasIllegalPotionEffects(item: ItemStack): Boolean {
        if (!item.hasData(DataComponentTypes.POTION_CONTENTS)) return false

        return runCatching {
            val contents = item.getData(DataComponentTypes.POTION_CONTENTS) ?: return@runCatching false
            contents.customEffects().any { effect ->
                effect.type == PotionEffectType.LUCK ||
                effect.amplifier > MAX_LEGAL_AMPLIFIER ||
                effect.duration > MAX_LEGAL_DURATION ||
                effect.isInfinite
            }
        }.getOrDefault(false)
    }

    private fun hasIllegalFoodEffects(item: ItemStack): Boolean {
        if (!item.hasData(DataComponentTypes.FOOD)) return false

        return runCatching {
            !item.type.isEdible && item.hasData(DataComponentTypes.FOOD)
        }.getOrDefault(false)
    }

    private fun hasIllegalName(meta: ItemMeta): Boolean {
        if (!meta.hasCustomName()) return false
        val customName = meta.customName() ?: return false

        if (GlobalUtils.getComponentDepth(customName) > 8) {
            if (AntiIllegalMain.debug) GlobalUtils.log(Level.INFO, "&cIllegalDataCheck flagged name for depth > 8")
            return true
        }

        val json = GsonComponentSerializer.gson().serialize(customName)
        if (json.length > MAX_NAME_JSON_LENGTH) {
            if (AntiIllegalMain.debug) GlobalUtils.log(Level.INFO, "&cIllegalDataCheck flagged name for JSON length > %d", MAX_NAME_JSON_LENGTH)
            return true
        }

        val plainText = GlobalUtils.getStringContent(customName)
        if (plainText.length > MAX_NAME_PLAIN_LENGTH) {
            if (AntiIllegalMain.debug) GlobalUtils.log(Level.INFO, "&cIllegalDataCheck flagged name for plain length > %d", MAX_NAME_PLAIN_LENGTH)
            return true
        }

        return false
    }

    companion object {
        private const val MAX_LEGAL_AMPLIFIER = 5
        private const val MAX_LEGAL_DURATION = 9600
        private const val MAX_NAME_PLAIN_LENGTH = 128
        private const val MAX_NAME_JSON_LENGTH = 4096
        private const val MAX_LEGAL_TOOL_SPEED = 50.0f
    }
}