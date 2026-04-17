/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent
import me.gb8.core.antiillegal.PlayerEffectCheck
import me.gb8.core.util.FoliaCompat
import me.gb8.core.util.GlobalUtils
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Explosive
import org.bukkit.entity.Fireball
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPotionEffectEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.util.function.Consumer

class EntityEffectListener(private val plugin: Plugin) : Listener {

    private val effectCheck = PlayerEffectCheck()

    init {
        startEntityEffectChecker()
    }

    private fun startEntityEffectChecker() {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin as JavaPlugin, Consumer {
            checkAllEntities()
        }, 20L, 20L)
    }

    private fun checkAllEntities() {
        Bukkit.getWorlds().forEach { world ->
            world.entities.filter { shouldFullCheck(it) }.forEach { entity ->
                FoliaCompat.schedule(entity, plugin) {
                    if (entity.isValid) performChecks(entity)
                }
            }
        }
    }

    private fun shouldFullCheck(entity: Entity): Boolean =
        entity is LivingEntity ||
        entity is Explosive ||
        entity is org.bukkit.entity.Hanging ||
        entity.type == org.bukkit.entity.EntityType.ENDER_DRAGON

    private fun performChecks(entity: Entity) {
        if (!entity.isValid) return

        if (entity.type == org.bukkit.entity.EntityType.ENDER_DRAGON) {
            val world = entity.world
            if (world.environment != World.Environment.THE_END) {
                entity.remove()
                return
            }
            val x = entity.x
            val z = entity.z
            if ((x * x + z * z) > 1000000.0) {
                entity.remove()
                return
            }
        }

        checkAndFixEntityName(entity)

        if (entity is LivingEntity && entity !is org.bukkit.entity.Player) {
            if (entity.activePotionEffects.isNotEmpty()) {
                effectCheck.fixEntityEffects(entity)
            }
        }

        if (entity is Explosive) {
            if (entity.yield > MAX_EXPLOSION_POWER) {
                entity.yield = MAX_EXPLOSION_POWER
            }
            if (entity is Fireball) {
                val acc = entity.acceleration
                if (acc.x.isNaN() || acc.y.isNaN() || acc.z.isNaN()) {
                    entity.remove()
                }
            }
        }
    }

    private fun checkAndFixEntityName(entity: Entity) {
        entity.customName()?.let { name ->
            val isIllegal = run {
                GlobalUtils.getComponentDepth(name) > MAX_ENTITY_NAME_DEPTH ||
                run {
                    val json = GsonComponentSerializer.gson().serialize(name)
                    json.length > MAX_ENTITY_NAME_JSON_LENGTH ||
                    run {
                        val plainText = GlobalUtils.getStringContent(name)
                        plainText.length > MAX_ENTITY_NAME_PLAIN_LENGTH ||
                        countNestingDepth(json) > MAX_ENTITY_NAME_NESTING
                    }
                }
            }

            if (isIllegal) {
                entity.customName(null)
                entity.isCustomNameVisible = false
            }
        }
    }

    private fun countNestingDepth(json: String): Int {
        var maxDepth = 0
        var currentDepth = 0
        var index = 0

        while (index < json.length - 6) {
            if (json.startsWith("\"extra\"", index, ignoreCase = false)) {
                currentDepth++
                maxDepth = maxOf(maxDepth, currentDepth)
                index += 7
            } else {
                index++
            }
        }
        return maxDepth
    }

    @EventHandler
    fun onEntityPotionEffect(event: EntityPotionEffectEvent) {
        if (event.entity is org.bukkit.entity.Player) {
            return
        }

        if (event.action == EntityPotionEffectEvent.Action.ADDED ||
            event.action == EntityPotionEffectEvent.Action.CHANGED) {

            if (event.newEffect != null && effectCheck.isIllegalEffect(event.newEffect)) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        event.chunk.entities.forEach { entity ->
            FoliaCompat.schedule(entity, plugin) {
                performChecks(entity)
            }
        }
    }

    @EventHandler
    fun onEntityAddToWorld(event: EntityAddToWorldEvent) {
        val entity = event.entity
        if (!shouldFullCheck(entity)) return
        FoliaCompat.schedule(entity, plugin) {
            if (entity.isValid) performChecks(entity)
        }
    }

    fun getEffectCheck(): PlayerEffectCheck = effectCheck

    companion object {
        private const val MAX_ENTITY_NAME_PLAIN_LENGTH = 128
        private const val MAX_ENTITY_NAME_JSON_LENGTH = 4096
        private const val MAX_ENTITY_NAME_DEPTH = 8
        private const val MAX_ENTITY_NAME_NESTING = 3
        private const val MAX_EXPLOSION_POWER = 4.0f
    }
}