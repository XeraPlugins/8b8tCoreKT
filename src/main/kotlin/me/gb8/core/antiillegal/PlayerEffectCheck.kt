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
        if (player == null) return false

        for (effect in player.activePotionEffects) {
            if (isIllegalEffect(effect)) return true
        }

        return false
    }

    fun fixPlayerEffects(player: Player?) {
        if (player == null) return

        for (effect in player.activePotionEffects) {
            if (isIllegalEffect(effect)) {
                player.removePotionEffect(effect.type)
            }
        }
    }

    fun isIllegalEffect(effect: PotionEffect?): Boolean {
        if (effect == null) return false

        val type = effect.type
        val level = effect.amplifier + 1

        if (!VANILLA_LEVEL_LIMITS.containsKey(type)) return true

        val maxAllowedLevel = VANILLA_LEVEL_LIMITS[type] ?: return true

        if (maxAllowedLevel == 0) return true

        if (level > maxAllowedLevel) return true

        if (effect.isInfinite) return true

        val duration = effect.duration
        if (isExcessiveDuration(duration, type)) return true

        return false
    }

    private fun isExcessiveDuration(duration: Int, type: PotionEffectType): Boolean {
        if (type == PotionEffectType.BAD_OMEN || type == PotionEffectType.RAID_OMEN) {
            return duration > MAX_LEGAL_DURATION_SPECIAL
        }
        return duration > MAX_LEGAL_DURATION
    }

    fun checkEntityEffects(entity: LivingEntity?): Boolean {
        if (entity == null) return false

        for (effect in entity.activePotionEffects) {
            if (isIllegalEffect(effect)) return true
        }

        return false
    }

    fun fixEntityEffects(entity: LivingEntity?) {
        if (entity == null) return

        for (effect in entity.activePotionEffects) {
            if (isIllegalEffect(effect)) {
                entity.removePotionEffect(effect.type)
            }
        }
    }

    companion object {
        private val VANILLA_LEVEL_LIMITS = HashMap<PotionEffectType, Int>()
        private const val MAX_LEGAL_DURATION = 9600
        private const val MAX_LEGAL_DURATION_SPECIAL = 144000

        init {
            VANILLA_LEVEL_LIMITS[PotionEffectType.NIGHT_VISION] = 1
            VANILLA_LEVEL_LIMITS[PotionEffectType.INVISIBILITY] = 1
            VANILLA_LEVEL_LIMITS[PotionEffectType.SLOW_FALLING] = 1
            VANILLA_LEVEL_LIMITS[PotionEffectType.WATER_BREATHING] = 1
            VANILLA_LEVEL_LIMITS[PotionEffectType.FIRE_RESISTANCE] = 1
            VANILLA_LEVEL_LIMITS[PotionEffectType.WEAKNESS] = 1
            VANILLA_LEVEL_LIMITS[PotionEffectType.DOLPHINS_GRACE] = 1
            VANILLA_LEVEL_LIMITS[PotionEffectType.CONDUIT_POWER] = 1
            VANILLA_LEVEL_LIMITS[PotionEffectType.DARKNESS] = 1
            VANILLA_LEVEL_LIMITS[PotionEffectType.OOZING] = 1
            VANILLA_LEVEL_LIMITS[PotionEffectType.GLOWING] = 1
            VANILLA_LEVEL_LIMITS[PotionEffectType.NAUSEA] = 1
            VANILLA_LEVEL_LIMITS[PotionEffectType.BLINDNESS] = 1
            VANILLA_LEVEL_LIMITS[PotionEffectType.WIND_CHARGED] = 1
            VANILLA_LEVEL_LIMITS[PotionEffectType.INFESTED] = 1
            VANILLA_LEVEL_LIMITS[PotionEffectType.SATURATION] = 1
            VANILLA_LEVEL_LIMITS[PotionEffectType.TRIAL_OMEN] = 1
            VANILLA_LEVEL_LIMITS[PotionEffectType.WEAVING] = 1

            VANILLA_LEVEL_LIMITS[PotionEffectType.SPEED] = 2
            VANILLA_LEVEL_LIMITS[PotionEffectType.STRENGTH] = 2
            VANILLA_LEVEL_LIMITS[PotionEffectType.REGENERATION] = 2
            VANILLA_LEVEL_LIMITS[PotionEffectType.POISON] = 2
            VANILLA_LEVEL_LIMITS[PotionEffectType.JUMP_BOOST] = 2
            VANILLA_LEVEL_LIMITS[PotionEffectType.HASTE] = 2
            VANILLA_LEVEL_LIMITS[PotionEffectType.INSTANT_DAMAGE] = 2
            VANILLA_LEVEL_LIMITS[PotionEffectType.INSTANT_HEALTH] = 2
            VANILLA_LEVEL_LIMITS[PotionEffectType.WITHER] = 2
            VANILLA_LEVEL_LIMITS[PotionEffectType.LEVITATION] = 2

            VANILLA_LEVEL_LIMITS[PotionEffectType.SLOWNESS] = 4
            VANILLA_LEVEL_LIMITS[PotionEffectType.RESISTANCE] = 4
            VANILLA_LEVEL_LIMITS[PotionEffectType.ABSORPTION] = 4

            VANILLA_LEVEL_LIMITS[PotionEffectType.MINING_FATIGUE] = 3
            VANILLA_LEVEL_LIMITS[PotionEffectType.HUNGER] = 3
            VANILLA_LEVEL_LIMITS[PotionEffectType.HERO_OF_THE_VILLAGE] = 5
            VANILLA_LEVEL_LIMITS[PotionEffectType.BAD_OMEN] = 5
            VANILLA_LEVEL_LIMITS[PotionEffectType.RAID_OMEN] = 5
        }
    }
}
