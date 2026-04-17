/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.Reloadable
import me.gb8.core.Main
import me.gb8.core.database.GeneralDatabase
import me.gb8.core.util.FoliaCompat
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.EntityType
import org.bukkit.entity.Phantom
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityTargetEvent
import com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent
import org.bukkit.persistence.PersistentDataType
import kotlin.random.Random

class PhantomPatch(private val plugin: Main) : Listener, Reloadable {
    private val aggroKey = NamespacedKey(plugin, "phantom_aggroed")

    @Volatile private var enabled = true
    @Volatile private var blockAll = false
    @Volatile private var onlyInEnd = true
    @Volatile private var onlyHostileIfAttacked = true
    @Volatile private var disableScreechUntilAttacked = true
    @Volatile private var spawnAboveBlocks: List<String> = emptyList()
    private val validMaterialsCache = mutableMapOf<String, Material?>()
    private var cachedValidBlocks: Set<Material>? = null

    init {
        reloadConfig()
        startSpawner()
    }

    override fun reloadConfig() {
        this.enabled = plugin.config.getBoolean("Patch.PhantomFixes.Enabled", true)
        this.blockAll = plugin.config.getBoolean("Patch.PhantomFixes.BlockAll", false)
        this.onlyInEnd = plugin.config.getBoolean("Patch.PhantomFixes.OnlyInEnd", true)
        this.onlyHostileIfAttacked = plugin.config.getBoolean("Patch.PhantomFixes.OnlyHostileIfAttacked", true)
        this.disableScreechUntilAttacked = plugin.config.getBoolean("Patch.PhantomFixes.DisableScreechUntilAttacked", true)
        this.spawnAboveBlocks = plugin.config.getStringList("Patch.PhantomFixes.SpawnAboveBlocks")
    }

    private fun startSpawner() {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, { _ ->
            if (!enabled || blockAll) return@runAtFixedRate

            for (player in Bukkit.getOnlinePlayers()) {
                GeneralDatabase.getInstance().getPreventPhantomSpawnAsync(player.name).thenAccept { preventSpawn ->
                    if (preventSpawn) return@thenAccept
                    
                    FoliaCompat.schedule(player, plugin) {
                        if (!player.isOnline()) return@schedule
                        
                        val world = player.world
                        if (onlyInEnd && world.environment != World.Environment.THE_END) return@schedule

                        if (Random.nextDouble() > 0.10) return@schedule

                        val playerLoc = player.location

                        Bukkit.getRegionScheduler().run(plugin, playerLoc) { _ ->
                            if (!player.isOnline()) return@run

                            val existing = player.getNearbyEntities(64.0, 64.0, 64.0)
                            val phantomCount = existing.count { it.type == EntityType.PHANTOM }
                            if (phantomCount >= 8) return@run

                            var ground: Block? = null
                            for (y in 0 until 64) {
                                val checkY = playerLoc.blockY - y
                                if (checkY < world.minHeight) break
                                val b = world.getBlockAt(playerLoc.blockX, checkY, playerLoc.blockZ)
                                if (!b.type.isAir()) {
                                    ground = b
                                    break
                                }
                            }

                            if (ground == null) return@run

                            if (spawnAboveBlocks.isNotEmpty()) {
                                val groundType = ground.type.name
                                if (spawnAboveBlocks.none { it.equals(groundType, ignoreCase = true) }) return@run
                            }

                            val spawnLoc = playerLoc.clone().add((Random.nextDouble() - 0.5) * 20, 20.0,
                                    (Random.nextDouble() - 0.5) * 20)
                            val count = 2 + Random.nextInt(3)
                            for (i in 0 until count) {
                                val phantom = world.spawnEntity(spawnLoc, EntityType.PHANTOM,
                                        CreatureSpawnEvent.SpawnReason.CUSTOM) as Phantom
                                if (disableScreechUntilAttacked) {
                                    phantom.isSilent = true
                                }
                            }
                        }
                    }
                }
            }
        }, 600, 600)
    }

    @EventHandler(ignoreCancelled = true)
    fun onPhantomPreSpawn(event: PhantomPreSpawnEvent) {
        val spawning = event.spawningEntity as? Player ?: return
        event.isCancelled = true
        GeneralDatabase.getInstance().getPreventPhantomSpawnAsync(spawning.name).thenAccept { preventSpawn ->
            if (!preventSpawn) {
                FoliaCompat.schedule(spawning, plugin) {
                    if (!spawning.isOnline()) return@schedule
                    val loc = spawning.location.clone().add(0.0, 20.0, 0.0)
                    Bukkit.getRegionScheduler().run(plugin, loc) { _ ->
                        loc.world?.spawnEntity(loc, EntityType.PHANTOM, CreatureSpawnEvent.SpawnReason.NATURAL)
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPhantomSpawn(event: CreatureSpawnEvent) {
        if (event.entityType != EntityType.PHANTOM) return

        if (event.spawnReason == CreatureSpawnEvent.SpawnReason.NATURAL) {
            val eventLoc = event.location
            val closest = eventLoc.world.getNearbyEntities(eventLoc, 64.0, 64.0, 64.0)
                .filterIsInstance<Player>()
                .minByOrNull { it.location.distanceSquared(eventLoc) }
            if (closest != null) {
                val target = closest
                event.isCancelled = true
                GeneralDatabase.getInstance().getPreventPhantomSpawnAsync(target.name).thenAccept { preventSpawn ->
                    if (!preventSpawn) {
                        FoliaCompat.schedule(target, plugin) {
                            if (!target.isOnline()) return@schedule
                            Bukkit.getRegionScheduler().run(plugin, eventLoc) { _ ->
                                val phantom = eventLoc.world.spawnEntity(eventLoc, EntityType.PHANTOM, CreatureSpawnEvent.SpawnReason.CUSTOM) as Phantom
                                if (disableScreechUntilAttacked) {
                                    phantom.isSilent = true
                                }
                            }
                        }
                    }
                }
                return
            }
        }

        if (!enabled) return
        if (blockAll) {
            event.isCancelled = true
            return
        }

        if (event.spawnReason == CreatureSpawnEvent.SpawnReason.CUSTOM) return

        val world = event.location.world
        if (onlyInEnd) {
            if (world.environment != World.Environment.THE_END) {
                event.isCancelled = true
                return
            }
        }

        if (spawnAboveBlocks.isNotEmpty()) {
            val validBlocks = getValidBlocks()

            if (validBlocks.isNotEmpty()) {
                val base = event.location.block
                val found = (1..64).any { i ->
                    val below = base.getRelative(0, -i, 0)
                    !below.type.isAir() && below.type in validBlocks
                }
                if (!found) {
                    event.isCancelled = true
                } else {
                    if (disableScreechUntilAttacked) {
                        event.entity.isSilent = true
                    }
                }
            }
        } else if (disableScreechUntilAttacked) {
            event.entity.isSilent = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityTarget(event: EntityTargetEvent) {
        if (event.entityType != EntityType.PHANTOM) return
        if (!enabled || !onlyHostileIfAttacked) return

        val phantom = event.entity as Phantom
        if (!phantom.persistentDataContainer.has(aggroKey, PersistentDataType.BYTE)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPhantomDamage(event: EntityDamageByEntityEvent) {
        if (event.entityType != EntityType.PHANTOM) return
        if (!enabled) return

        var player: Player? = null
        if (event.damager is Player) {
            player = event.damager as Player
        } else if (event.damager is org.bukkit.entity.Projectile) {
            val proj = event.damager as org.bukkit.entity.Projectile
            if (proj.shooter is Player) {
                player = proj.shooter as Player
            }
        }

        if (player != null) {
            val target = player
            val damagedPhantom = event.entity as Phantom

            val nearby = damagedPhantom.getNearbyEntities(32.0, 32.0, 32.0)
            nearby.add(damagedPhantom)

            for (entity in nearby) {
                if (entity is Phantom) {
                    entity.persistentDataContainer.set(aggroKey, PersistentDataType.BYTE, 1.toByte())
                    entity.setTarget(target)
                    if (disableScreechUntilAttacked) {
                        entity.isSilent = false
                    }
                }
            }
        }
    }

    @EventHandler
    fun onPlayerDeath(event: org.bukkit.event.entity.PlayerDeathEvent) {
        resetPhantomAggro(event.entity)
    }

    @EventHandler
    fun onPlayerLeave(event: org.bukkit.event.player.PlayerQuitEvent) {
        resetPhantomAggro(event.player)
    }

    @EventHandler
    fun onWorldChange(event: org.bukkit.event.player.PlayerChangedWorldEvent) {
        resetPhantomAggro(event.player)
    }

    private fun getValidMaterial(name: String): Material? {
        return validMaterialsCache.getOrPut(name) {
            runCatching { Material.valueOf(name) }.getOrNull()
        }
    }

    private fun getValidBlocks(): Set<Material> {
        val currentHash = spawnAboveBlocks.hashCode()
        if (cachedValidBlocks == null || cachedValidBlocks?.hashCode() != currentHash) {
            cachedValidBlocks = spawnAboveBlocks.mapNotNull { getValidMaterial(it) }.toSet()
        }
        return cachedValidBlocks!!
    }

    private fun resetPhantomAggro(player: Player) {
        val loc = player.location.clone()
        val playerUUID = player.uniqueId
        Bukkit.getRegionScheduler().run(plugin, loc) { _ ->
            for (entity in loc.world.getNearbyEntities(loc, 128.0, 128.0, 128.0)) {
                if (entity is Phantom) {
                    val target = entity.target
                    if (target != null && target.uniqueId == playerUUID) {
                        entity.persistentDataContainer.remove(aggroKey)
                        entity.setTarget(null)
                        if (disableScreechUntilAttacked) {
                            entity.isSilent = true
                        }
                    }
                }
            }
        }
    }
}
