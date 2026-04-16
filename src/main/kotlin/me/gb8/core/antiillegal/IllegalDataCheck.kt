/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.PotionContents
import io.papermc.paper.datacomponent.item.Tool
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
        if (item == null || item.type.isAir) return false

        val type = item.type

        if (isContainer(type)) return false

        try {
            if (item.hasItemMeta()) {
                val meta = item.itemMeta
                if (meta != null) {
                    if (hasIllegalName(meta)) return true
                    if (meta.isGlider && type != Material.ELYTRA) return true
                }
            }

            if (hasIllegalWaterloggedState(item)) return true

            if (type != Material.TOTEM_OF_UNDYING && item.hasData(DataComponentTypes.DEATH_PROTECTION)) return true

            if (isPotion(type) && hasIllegalPotionEffects(item)) return true

            if (hasIllegalFoodEffects(item)) return true

            if (type.maxDurability > 0 && !item.hasData(DataComponentTypes.MAX_DAMAGE)) return true

            if (item.hasData(DataComponentTypes.MAX_STACK_SIZE)) {
                val maxStack = item.getData(DataComponentTypes.MAX_STACK_SIZE)
                if (maxStack == null || maxStack < 1 || maxStack > 99) return true
            } else {
                val vanillaMaxStack = type.maxStackSize
                val actualMaxStack = item.maxStackSize
                if (vanillaMaxStack > 1 && actualMaxStack == 1) return true
            }

            if (hasIllegalToolComponent(item)) return true

        } catch (e: Exception) {
            return false
        }

        return false
    }

    override fun shouldCheck(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir) return false
        return !isContainer(item.type)
    }

    override fun fix(item: ItemStack?) {
        if (item == null || item.type.isAir) return

        val type = item.type

        if (isContainer(type)) return

        try {
            if (item.hasItemMeta()) {
                val meta = item.itemMeta
                if (meta != null) {
                    var metaChanged = false

                    if (hasIllegalName(meta)) {
                        meta.customName(null)
                        metaChanged = true
                    }

                    if (meta.isGlider && type != Material.ELYTRA) {
                        meta.isGlider = false
                        metaChanged = true
                    }

                    if (metaChanged) item.itemMeta = meta
                }
            }

            fixWaterloggedState(item)

            if (type != Material.TOTEM_OF_UNDYING) {
                item.unsetData(DataComponentTypes.DEATH_PROTECTION)
            }

            if (isPotion(type) && hasIllegalPotionEffects(item)) {
                item.unsetData(DataComponentTypes.POTION_CONTENTS)
            }

            if (hasIllegalFoodEffects(item)) {
                item.unsetData(DataComponentTypes.FOOD)
                item.unsetData(DataComponentTypes.CONSUMABLE)
            }

            if (type.maxDurability > 0 && !item.hasData(DataComponentTypes.MAX_DAMAGE)) {
                item.setData(DataComponentTypes.MAX_DAMAGE, type.maxDurability.toInt())
            }

            if (item.hasData(DataComponentTypes.MAX_STACK_SIZE)) {
                val maxStack = item.getData(DataComponentTypes.MAX_STACK_SIZE)
                if (maxStack == null) {
                    val vanillaMaxStack = type.maxStackSize
                    if (vanillaMaxStack > 1) {
                        item.setData(DataComponentTypes.MAX_STACK_SIZE, vanillaMaxStack)
                    } else {
                        item.unsetData(DataComponentTypes.MAX_STACK_SIZE)
                    }
                } else if (maxStack < 1 || maxStack > 99) {
                    item.unsetData(DataComponentTypes.MAX_STACK_SIZE)
                }
            } else {
                val vanillaMaxStack = type.maxStackSize
                val actualMaxStack = item.maxStackSize

                if (vanillaMaxStack > 1 && actualMaxStack == 1) {
                    item.setData(DataComponentTypes.MAX_STACK_SIZE, vanillaMaxStack)
                }
            }

            if (hasIllegalToolComponent(item)) {
                item.unsetData(DataComponentTypes.TOOL)
            }

        } catch (ignored: Exception) {}
    }

    private fun hasIllegalToolComponent(item: ItemStack): Boolean {
        if (!item.hasData(DataComponentTypes.TOOL)) return false

        val shouldHaveTool = item.type.getDefaultData(DataComponentTypes.TOOL) != null

        if (!shouldHaveTool) return true

        val tool = item.getData(DataComponentTypes.TOOL) ?: return false

        for (rule in tool.rules()) {
            val speed = rule.speed()
            if (speed != null && speed > MAX_LEGAL_TOOL_SPEED) return true
        }
        if (tool.defaultMiningSpeed() > MAX_LEGAL_TOOL_SPEED) return true

        return false
    }

    private fun isContainer(type: Material): Boolean {
        return type.name.contains("SHULKER_BOX") || type.name.endsWith("BUNDLE")
    }

    private fun isPotion(type: Material): Boolean {
        return type == Material.POTION || type == Material.SPLASH_POTION ||
               type == Material.LINGERING_POTION || type == Material.TIPPED_ARROW
    }

    private fun hasIllegalWaterloggedState(item: ItemStack): Boolean {
        if (!item.type.isBlock) return false
        if (!item.hasData(DataComponentTypes.BLOCK_DATA)) return false

        try {
            val properties = item.getData(DataComponentTypes.BLOCK_DATA) ?: return false
            val dataString = properties.toString()
            return dataString.contains("waterlogged=true") || dataString.contains("waterlogged=\"true\"")
        } catch (e: Exception) {
            return false
        }
    }

    private fun fixWaterloggedState(item: ItemStack) {
        if (!item.type.isBlock) return
        try {
            if (hasIllegalWaterloggedState(item)) {
                item.unsetData(DataComponentTypes.BLOCK_DATA)
            }
        } catch (ignored: Exception) {}
    }

    private fun hasIllegalPotionEffects(item: ItemStack): Boolean {
        if (!item.hasData(DataComponentTypes.POTION_CONTENTS)) return false

        try {
            val contents = item.getData(DataComponentTypes.POTION_CONTENTS) ?: return false

            for (effect in contents.customEffects()) {
                if (effect.type == PotionEffectType.LUCK) return true
                if (effect.amplifier > MAX_LEGAL_AMPLIFIER) return true
                if (effect.duration > MAX_LEGAL_DURATION) return true
                if (effect.isInfinite) return true
            }
        } catch (e: Exception) {
            return false
        }

        return false
    }

    private fun hasIllegalFoodEffects(item: ItemStack): Boolean {
        if (!item.hasData(DataComponentTypes.FOOD)) return false

        try {
            val type = item.type
            if (!type.isEdible && item.hasData(DataComponentTypes.FOOD)) return true
        } catch (e: Exception) {
            return false
        }

        return false
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
