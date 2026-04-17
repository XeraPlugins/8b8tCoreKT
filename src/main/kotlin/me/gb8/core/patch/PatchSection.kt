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
        val cfg = plugin.getSectionConfig(this).also { config = it }
        entityPerChunk = parseEntityConf()

        listOf(
            PhantomPatch(plugin),
            AntiLagChest(plugin),
            FallFlyListener(plugin) as org.bukkit.event.Listener,
            MapCreationListener(plugin),
            MapRemovalPatch(plugin),
            EntitySwitchWorldListener(plugin),
            BoundaryListener(plugin),
            VanishVerifierListener(plugin),
            NbtBanListener(plugin),
            ChestLimiter(plugin)
        ).forEach { plugin.register(it) }

        cfg?.let { setupScheduledTasks(it) }
    }

    private fun setupScheduledTasks(cfg: ConfigurationSection) {
        if (cfg.getBoolean("EntityPerChunk.Enable", false)) {
            val intervalTicks = cfg.getInt("EntityPerChunk.CheckInterval", 5) * 60 * 20L
            val ticks = if (intervalTicks < 200L) 200L else intervalTicks
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, {
                EntityCheckTask(this).run()
            }, 20L, ticks)
        }

        if (cfg.getBoolean("EntitySpawnListener.Enable", true)) {
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

        val buf = cfg.getStringList("EntityPerChunk.EntitiesPerChunk")
            .mapNotNull { parseEntityEntry(it) }
            .toMap()
            .toMutableMap()

        val defMax = cfg.getInt("EntityPerChunk.DefaultMax")
        if (defMax != -1) {
            EntityType.entries.forEach { type -> buf.getOrPut(type) { defMax } }
        }
        return buf
    }

    private fun parseEntityEntry(entry: String): Pair<EntityType, Int>? {
        val parts = entry.split("::")
        if (parts.size != 2) return null
        return try {
            EntityType.valueOf(parts[0].uppercase()) to parts[1].toInt()
        } catch (e: Exception) {
            when (e) {
                is NumberFormatException -> log(Level.INFO, "%s is not a number", parts[1])
                else -> log(Level.INFO, "Unknown EntityType %s", parts[0])
            }
            null
        }
    }
}
