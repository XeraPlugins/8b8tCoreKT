/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.deathmessages

import org.apache.logging.log4j.LogManager

enum class DeathCause(val configPath: String) {
    PLAYER("global-pvp-death-messages"),
    VOID("void-messages"),
    FALL("fall-damage-messages"),
    LAVA("lava-messages"),
    FIRE("fire-messages"),
    DROWNING("drowning-messages"),
    SUICIDE("suicide-messages"),
    PROJECTILE("Projectile-Arrow"),
    BLOCK("falling-block-messages"),
    ENTITY_ATTACK("unknown-messages"),
    UNKNOWN("unknown-messages"),
    CUSTOM("unknown-messages"),
    CUSTOM_NAMED_ENTITY("custom-name-entity-messages"),
    FIRE_TICK("fire-tick-messages"),
    KILL("suicide-messages"),
    STARVATION("starvation-messages"),
    POISON("potion-messages"),
    MAGIC("potion-messages"),
    WITHER("wither-messages"),
    WITHER_BOSS("witherboss-messages"),
    FALLING_BLOCK("falling-block-messages"),
    THORNS("thorns-messages"),
    SUFFOCATION("suffocation-messages"),
    CONTACT("cactus-messages"),
    BLOCK_EXPLOSION("tnt-messages"),
    ENTITY_EXPLOSION("creeper-messages"),
    LIGHTNING("lightning-messages"),
    DRAGON_BREATH("dragon-breath-messages"),
    FLY_INTO_WALL("elytra-messages"),
    HOT_FLOOR("magma-block-messages"),
    CRAMMING("cramming-messages"),
    FREEZE("freeze-messages"),
    SONIC_BOOM("warden-sonic-boom-messages"),
    ELDER_GUARDIAN("elderguardian-messages"),
    WITHER_SKELETON("witherskeleton-messages"),
    STRAY("stray-messages"),
    ARROW("arrow-messages"),
    FIREBALL("fireball-messages"),
    SMALL_FIREBALL("fireball-messages"),
    WITHER_SKULL("witherboss-messages"),
    PRIMED_TNT("tnt-messages"),
    FIREWORK("firework-messages"),
    HUSK("husk-messages"),
    SPECTRAL_ARROW("arrow-messages"),
    SHULKER_BULLET("shulker-messages"),
    DRAGON_FIREBALL("dragon-messages"),
    ZOMBIE_VILLAGER("zombievillager-messages"),
    EVOKER_FANGS("evoker-messages"),
    EVOKER("evoker-messages"),
    VEX("vex-messages"),
    VINDICATOR("vindicator-messages"),
    ILLUSIONER("illusioner-messages"),
    CREEPER("creeper-messages"),
    SKELETON("skeleton-messages"),
    SPIDER("spider-messages"),
    GIANT("zombie-messages"),
    ZOMBIE("zombie-messages"),
    SLIME("slime-messages"),
    GHAST("ghast-messages"),
    ZOMBIFIED_PIGLIN("zombified-piglin-messages"),
    ENDERMAN("enderman-messages"),
    CAVE_SPIDER("cavespider-messages"),
    SILVERFISH("silverfish-messages"),
    BLAZE("blaze-messages"),
    MAGMA_CUBE("magmacube-messages"),
    ENDER_DRAGON("dragon-messages"),
    WITCH("witch-messages"),
    ENDERMITE("endermite-messages"),
    GUARDIAN("guardian-messages"),
    SHULKER("shulker-messages"),
    WOLF("wolf-messages"),
    IRON_GOLEM("golem-messages"),
    POLAR_BEAR("polar-bear-messages"),
    LLAMA("llama-messages"),
    LLAMA_SPIT("llama-messages"),
    END_CRYSTAL("end-crystal-messages"),
    PHANTOM("phantom-messages"),
    TRIDENT("drowned-messages"),
    PUFFERFISH("pufferfish-messages"),
    DROWNED("drowned-messages"),
    DOLPHIN("dolphin-messages"),
    PANDA("panda-messages"),
    PILLAGER("pillager-messages"),
    RAVAGER("ravager-messages"),
    FOX("fox-messages"),
    BEE("bee-messages"),
    HOGLIN("hoglin-messages"),
    PIGLIN("piglin-messages"),
    PIG_ZOMBIE("pigman-messages"),
    ZOGLIN("zoglin-messages"),
    PIGLIN_BRUTE("piglin-messages"),
    GOAT("goat-messages"),
    WARDEN("warden-messages"),
    MELEE_DEATH("melee-death-messages");

    companion object {
        private val lookupTable = entries.associateBy { it.name }
        private val validPaths = entries.map { it.configPath }.toSet()

        fun resolve(cause: Enum<*>): DeathCause {
            return lookupTable[cause.name] ?: run {
                LogManager.getLogger().warn("Unhandled damage cause: ${cause.name}")
                UNKNOWN
            }
        }

        fun isValidPath(path: String): Boolean = path in validPaths
    }
}
