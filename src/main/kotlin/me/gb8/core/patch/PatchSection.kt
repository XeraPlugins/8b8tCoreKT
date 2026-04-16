/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.patch

import me.gb8.core.Main
import me.gb8.core.Section
import me.gb8.core.listeners.*

import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.Bukkit
import java.util.UUID
import java.util.logging.Level
import me.gb8.core.util.GlobalUtils.log

class PatchSection(override val plugin: Main) : Section, Listener {
    override val name: String = "Patch"
    private val positions = mutableMapOf<UUID, Location>()
    private var entityPerChunk: MutableMap<EntityType, Int>? = null
    private var config: ConfigurationSection? = null

    fun getPositions(): Map<UUID, Location> = positions
    fun getEntityPerChunk(): MutableMap<EntityType, Int>? = entityPerChunk
    fun getConfig(): ConfigurationSection? = config

    override fun enable() {
        val cfg = plugin.getSectionConfig(this)
        config = cfg
        entityPerChunk = parseEntityConf()
        plugin.register(PhantomPatch(plugin))
        plugin.register(AntiLagChest(plugin))
        plugin.register(FallFlyListener(plugin) as org.bukkit.event.Listener)
        plugin.register(MapCreationListener(plugin))
        plugin.register(MapRemovalPatch(plugin))
        plugin.register(EntitySwitchWorldListener(plugin))
        plugin.register(BoundaryListener(plugin))
        plugin.register(VanishVerifierListener(plugin))
        plugin.register(NbtBanListener(plugin))
        plugin.register(ChestLimiter(plugin))
        if (cfg?.getBoolean("EntityPerChunk.Enable", false) == true) {
            val intervalTicks = (cfg.getInt("EntityPerChunk.CheckInterval", 5) * 60 * 20L)
            org.bukkit.Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, {
                EntityCheckTask(this).run()
            }, 20L, maxOf(200L, intervalTicks))
        }
        if (cfg?.getBoolean("EntitySpawnListener.Enable", true) == true) {
            plugin.register(EntitySpawnListener(this))
        }
    }

    @EventHandler
    fun onLeave(event: PlayerQuitEvent) {
        positions.remove(event.player.uniqueId)
    }

    override fun disable() {}

    override fun reloadConfig() {
        config = plugin.getSectionConfig(this)
        if (entityPerChunk != null) {
            entityPerChunk?.clear()
            entityPerChunk = parseEntityConf()
        }
    }

    private fun parseEntityConf(): MutableMap<EntityType, Int> {
        val cfg = config ?: return mutableMapOf()
        val raw = cfg.getStringList("EntityPerChunk.EntitiesPerChunk")
        val buf = mutableMapOf<EntityType, Int>()
        for (str in raw) {
            val split = str.split("::")
            try {
                val type = EntityType.valueOf(split[0].uppercase())
                val i = split[1].toInt()
                buf[type] = i
            } catch (e: Exception) {
                when (e) {
                    is NumberFormatException -> log(Level.INFO, "%s%s%s%s is not a number", split[1])
                    else -> log(Level.INFO, "Unknown EntityType %s", split[0])
                }
            }
        }
        val defMax = cfg.getInt("EntityPerChunk.DefaultMax")
        if (defMax != -1) {
            for (type in EntityType.entries) buf.getOrPut(type) { defMax }
        }
        return buf
    }
}
