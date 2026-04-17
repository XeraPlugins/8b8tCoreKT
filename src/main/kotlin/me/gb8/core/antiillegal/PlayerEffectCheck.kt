/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import me.gb8.core.antiillegal.Check
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.inventory.ItemStack

class PlayerEffectCheck : Check {

    override fun check(item: ItemStack?): Boolean = false

    override fun shouldCheck(item: ItemStack?): Boolean = false

    override fun fix(item: ItemStack?) {}

    fun checkPlayerEffects(player: Player?): Boolean {
        return player?.activePotionEffects?.any { isIllegalEffect(it) } ?: false
    }

    fun fixPlayerEffects(player: Player?) {
        player ?: return
        player.activePotionEffects
            .filter { isIllegalEffect(it) }
            .forEach { player.removePotionEffect(it.type) }
    }

    fun isIllegalEffect(effect: PotionEffect?): Boolean {
        effect ?: return false

        val type = effect.type
        val level = effect.amplifier + 1
        val maxLevel = VANILLA_LEVEL_LIMITS[type] ?: return true

        return when {
            maxLevel == 0 -> true
            level > maxLevel -> true
            effect.isInfinite -> true
            isExcessiveDuration(effect.duration, type) -> true
            else -> false
        }
    }

    private fun isExcessiveDuration(duration: Int, type: PotionEffectType): Boolean {
        return if (type == PotionEffectType.BAD_OMEN || type == PotionEffectType.RAID_OMEN) {
            duration > MAX_LEGAL_DURATION_SPECIAL
        } else {
            duration > MAX_LEGAL_DURATION
        }
    }

    fun checkEntityEffects(entity: LivingEntity?): Boolean {
        return entity?.activePotionEffects?.any { isIllegalEffect(it) } ?: false
    }

    fun fixEntityEffects(entity: LivingEntity?) {
        entity ?: return
        entity.activePotionEffects
            .filter { isIllegalEffect(it) }
            .forEach { entity.removePotionEffect(it.type) }
    }

    companion object {
        private val VANILLA_LEVEL_LIMITS = mapOf(
            PotionEffectType.NIGHT_VISION to 1,
            PotionEffectType.INVISIBILITY to 1,
            PotionEffectType.SLOW_FALLING to 1,
            PotionEffectType.WATER_BREATHING to 1,
            PotionEffectType.FIRE_RESISTANCE to 1,
            PotionEffectType.WEAKNESS to 1,
            PotionEffectType.DOLPHINS_GRACE to 1,
            PotionEffectType.CONDUIT_POWER to 1,
            PotionEffectType.DARKNESS to 1,
            PotionEffectType.OOZING to 1,
            PotionEffectType.GLOWING to 1,
            PotionEffectType.NAUSEA to 1,
            PotionEffectType.BLINDNESS to 1,
            PotionEffectType.WIND_CHARGED to 1,
            PotionEffectType.INFESTED to 1,
            PotionEffectType.SATURATION to 1,
            PotionEffectType.TRIAL_OMEN to 1,
            PotionEffectType.WEAVING to 1,

            PotionEffectType.SPEED to 2,
            PotionEffectType.STRENGTH to 2,
            PotionEffectType.REGENERATION to 2,
            PotionEffectType.POISON to 2,
            PotionEffectType.JUMP_BOOST to 2,
            PotionEffectType.HASTE to 2,
            PotionEffectType.INSTANT_DAMAGE to 2,
            PotionEffectType.INSTANT_HEALTH to 2,
            PotionEffectType.WITHER to 2,
            PotionEffectType.LEVITATION to 2,

            PotionEffectType.SLOWNESS to 4,
            PotionEffectType.RESISTANCE to 4,
            PotionEffectType.ABSORPTION to 4,

            PotionEffectType.MINING_FATIGUE to 3,
            PotionEffectType.HUNGER to 3,
            PotionEffectType.HERO_OF_THE_VILLAGE to 5,
            PotionEffectType.BAD_OMEN to 5,
            PotionEffectType.RAID_OMEN to 5
        )

        private const val MAX_LEGAL_DURATION = 9600
        private const val MAX_LEGAL_DURATION_SPECIAL = 144000
    }
}