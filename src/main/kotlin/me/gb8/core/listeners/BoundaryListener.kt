/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.Main
import me.gb8.core.Reloadable
import me.gb8.core.util.FoliaCompat
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.WorldBorder
import org.bukkit.block.Block
import org.bukkit.entity.EnderPearl
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Vehicle
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.vehicle.VehicleEnterEvent
import org.bukkit.event.vehicle.VehicleMoveEvent
import org.bukkit.util.Vector
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.Bukkit
import kotlin.jvm.Volatile
import kotlin.math.*

class BoundaryListener(private val plugin: Main) : Listener, Reloadable {
    private val lastNetherRoofDamage = ConcurrentHashMap<UUID, Long>()
    private val lastBottomTeleport = ConcurrentHashMap<UUID, Long>()
    private val lastBorderMessage = ConcurrentHashMap<UUID, Long>()
    private val NETHER_ROOF_DAMAGE_COOLDOWN = 1000L
    private val BOTTOM_TELEPORT_COOLDOWN = 3000L
    private val BORDER_MESSAGE_COOLDOWN = 3000L
    private val PRECISION_LIMIT = 29999984.0
    private val EARLY_EXIT_DISTANCE = 29000000.0

    companion object {
        private val LEAVES = Material.entries.filter { it.name.contains("LEAVES", ignoreCase = true) }.toSet()
    }

    init {
        reloadConfig()
        startNetherRoofMonitor()
        startWorldBorderMonitor()
    }

    @Volatile private var enabled = false
    @Volatile private var netherRoofEnabled = false
    @Volatile private var netherYLevel = 0
    @Volatile private var damagePlayers = false
    @Volatile private var blockPlayers = false
    @Volatile private var ensureSafeTeleport = false
    @Volatile private var createNetherPlatform = false
    @Volatile private var worldBorderEnabled = false
    @Volatile private var worldBorderBuffer = 0.0

    override fun reloadConfig() {
        enabled = plugin.config.getBoolean("Patch.BoundaryProtection.Enabled", true)
        netherRoofEnabled = plugin.config.getBoolean("Patch.BoundaryProtection.NetherRoofProtection.Enabled", true)
        netherYLevel = plugin.config.getInt("Patch.BoundaryProtection.NetherRoofProtection.NetherYLevel", 128)
        damagePlayers = plugin.config.getBoolean("Patch.BoundaryProtection.NetherRoofProtection.DamagePlayers", true)
        blockPlayers = plugin.config.getBoolean("Patch.BoundaryProtection.NetherRoofProtection.BlockPlayers", true)
        ensureSafeTeleport = plugin.config.getBoolean("Patch.BoundaryProtection.NetherRoofProtection.EnsureSafeTeleport", true)
        createNetherPlatform = plugin.config.getBoolean("Patch.BoundaryProtection.NetherRoofProtection.CreateNetherPlatform", true)
        worldBorderEnabled = plugin.config.getBoolean("Patch.BoundaryProtection.WorldBorderProtection.Enabled", true)
        worldBorderBuffer = 0.1
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!enabled) return

        val from = event.from
        val to = event.to

        val fx = from.x
        val fy = from.y
        val fz = from.z
        val tx = to.x
        val ty = to.y
        val tz = to.z

        if (fx.toInt() == tx.toInt() && fy.toInt() == ty.toInt() && fz.toInt() == tz.toInt()) return

        val player = event.player

        val world = to.world ?: return

        val y = to.y
        val env = world.environment

        if (env == World.Environment.NORMAL && (y < -64 || event.from.y < -64)) {
            handleBottomBoundary(player, event, world, -59.0)
        } else if (env == World.Environment.NETHER && (y < 0 || event.from.y < 0)) {
            handleBottomBoundary(player, event, world, 5.0)
        }

        if (env == World.Environment.NETHER && netherRoofEnabled) {
            if (event.from.y < netherYLevel.toDouble() && y >= netherYLevel.toDouble()) {
                handleNetherRoofAttempt(player, event)
            } else if (y >= netherYLevel.toDouble()) {
                handleNetherRoof(player, event)
            }
        }

        if (worldBorderEnabled && !player.isOp) {
            if (isOutsideWorldBorder(to) || getDistanceOutsideBorder(to) > -worldBorderBuffer) {
                handleWorldBorderViolation(player, event)
                return
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    fun onVehicleMove(event: VehicleMoveEvent) {
        if (!enabled || !worldBorderEnabled) return

        val vehicle = event.vehicle

        if (isOutsideWorldBorder(event.to) || getDistanceOutsideBorder(event.to) > -worldBorderBuffer) {
            vehicle.velocity = Vector(0.0, 0.0, 0.0)

            for (passenger in vehicle.passengers) {
                if (passenger is Player) {
                    if (passenger.isOp) continue

                    vehicle.eject()

                    val safeLocation = findSafeLocationInsideBorder(event.from)

                    passenger.teleportAsync(safeLocation)
                    sendBorderMessage(passenger, "§cYou cannot go beyond the world border!")
                }
            }

            val vehicleSafe = findSafeLocationInsideBorder(event.from)
            vehicle.teleportAsync(vehicleSafe)
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBoatEnter(event: VehicleEnterEvent) {
        if (!enabled || !worldBorderEnabled) return

        val entered = event.entered
        if (entered !is Player) return
        val player = entered
        if (player.isOp) return

        if (isOutsideWorldBorder(event.vehicle.location)) {
            event.isCancelled = true
            player.sendMessage("§cThis vehicle is outside the world border!")
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEnderPearlLaunch(event: ProjectileLaunchEvent) {
        if (!enabled || !netherRoofEnabled) return

        val entity = event.entity
        if (entity !is EnderPearl) return
        val pearl = entity

        val shooter = pearl.shooter
        if (shooter !is Player) return
        val player = shooter

        if (player.isOp) return

        FoliaCompat.scheduleAtFixedRate(pearl, plugin, {
            if (pearl.isDead || !pearl.isValid) return@scheduleAtFixedRate

            val pearlLoc = pearl.location
            val pearlWorld = pearlLoc.world
            if (pearlWorld == null) return@scheduleAtFixedRate

            if (netherRoofEnabled && pearlWorld.environment == World.Environment.NETHER && pearlLoc.y >= netherYLevel.toDouble() - 5) {
                pearl.remove()
                player.sendMessage("§cEnder pearls cannot take you above the nether roof!")
                return@scheduleAtFixedRate
            }

            if (worldBorderEnabled) {
                val px = pearlLoc.x
                val pz = pearlLoc.z

                if (px > -29000000 && px < 29000000 && pz > -29000000 && pz < 29000000) return@scheduleAtFixedRate

                var velocity = pearl.velocity
                val vx = velocity.x
                val vz = velocity.z

                val predX = px + vx
                val predZ = pz + vz

                val border = pearlWorld.worldBorder
                val center = border.center
                val radius = border.size / 2.0
                val precisionLimit = 29999984.0
                val effectiveRadius = minOf(radius, precisionLimit) - worldBorderBuffer

                val xDistFromCenter = predX - center.x
                val zDistFromCenter = predZ - center.z

                var bounced = false

                if (abs(xDistFromCenter) >= effectiveRadius) {
                    val reflectX = -maxOf(abs(vx), 0.5) * kotlin.math.sign(xDistFromCenter)
                    velocity.x = reflectX
                    pearlLoc.x = center.x + (kotlin.math.sign(xDistFromCenter) * (effectiveRadius - 1.0))
                    bounced = true
                }

                if (abs(zDistFromCenter) >= effectiveRadius) {
                    val reflectZ = -maxOf(abs(vz), 0.5) * kotlin.math.sign(zDistFromCenter)
                    velocity.z = reflectZ
                    pearlLoc.z = center.z + (kotlin.math.sign(zDistFromCenter) * (effectiveRadius - 1.0))
                    bounced = true
                }

                if (bounced) {
                    pearl.teleportAsync(pearlLoc)
                    pearl.velocity = velocity
                }
            }
        }, 1L, 1L)
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    fun onEnderPearlTeleport(event: PlayerTeleportEvent) {
        if (!enabled) return

        if (event.cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return

        val to = event.to
        val world = to.world ?: return

        val player = event.player
        if (player.isOp) return

        if (netherRoofEnabled && world.environment == World.Environment.NETHER && to.y >= netherYLevel.toDouble()) {
            event.isCancelled = true
            player.sendMessage("§cEnder pearls cannot take you above the nether roof!")
            return
        }

        if (worldBorderEnabled && (isOutsideWorldBorder(to) || getDistanceOutsideBorder(to) > -worldBorderBuffer)) {
            event.isCancelled = true
            player.sendMessage("§cEnder pearls cannot take you beyond the world border!")
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        if (!enabled) return

        @Suppress("USELESS_ELVIS")
        val to = event.to ?: return
        val world = to.world ?: return

        val player = event.player

        if (worldBorderEnabled && !player.isOp) {
            if (isOutsideWorldBorder(to) || getDistanceOutsideBorder(to) > -worldBorderBuffer) {
                event.isCancelled = true
                val safeLocation = findSafeLocationInsideBorder(event.from)
                player.teleportAsync(safeLocation)
                sendBorderMessage(player, "§cYou cannot teleport beyond the world border!")
                return
            }
        }

        if (netherRoofEnabled && world.environment == World.Environment.NETHER && to.y >= netherYLevel.toDouble() && !player.isOp) {
            event.isCancelled = true

            val originalLocation = player.location.clone()

            val safeLocation = findSafeLocationBelowBedrock(to)
            val finalSafe = if (safeLocation != null) safeLocation else originalLocation

            plugin.server.globalRegionScheduler.runDelayed(plugin, {
                if (!player.isOnline) return@runDelayed

                FoliaCompat.schedule(player, plugin, {
                    player.teleportAsync(finalSafe, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept {
                        FoliaCompat.scheduleDelayed(player, plugin, {
                            if (player.isOnline && player.location.y >= netherYLevel.toDouble()) {
                                player.teleportAsync(finalSafe)
                                player.velocity = org.bukkit.util.Vector(0.0, 0.0, 0.0)
                            }
                        }, 3L)
                    }
                })
            }, 1L)

            player.sendMessage("§cYou cannot teleport above the nether roof!")
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    fun onPlayerTeleportMonitor(event: PlayerTeleportEvent) {
        if (!enabled || !netherRoofEnabled || event.isCancelled) return

        val to = event.to
        val world = to.world ?: return

        val player = event.player

        if (world.environment == World.Environment.NETHER && to.y >= netherYLevel.toDouble() && !player.isOp) {
            FoliaCompat.scheduleDelayed(player, plugin, {
                if (player.isOnline && player.location.y >= netherYLevel.toDouble()) {
                    val safe = findSafeLocationBelowBedrock(player.location)
                    safe?.let { player.teleportAsync(it) }
                }
            }, 2L)
        }
    }

    private fun handleBottomBoundary(player: Player, event: PlayerMoveEvent, world: World, targetY: Double) {
        if (player.isOp) return

        val currentTime = System.currentTimeMillis()
        val lastTeleport = lastBottomTeleport.getOrDefault(player.uniqueId, 0L)

        val forceTeleport = (world.environment == World.Environment.NORMAL && event.from.y < -64) ||
                (world.environment == World.Environment.NETHER && event.from.y < 0)

        if (currentTime - lastTeleport < BOTTOM_TELEPORT_COOLDOWN && !forceTeleport) {
            event.isCancelled = true
            return
        }

        if (event.from.y >= targetY && !forceTeleport) return

        lastBottomTeleport[player.uniqueId] = currentTime

        val safeLocation = findSafeLocationAboveBottom(world, event.to.x, event.to.z, targetY)
        safeLocation.add(0.0, 0.1, 0.0)
        safeLocation.yaw = event.to.yaw
        safeLocation.pitch = event.to.pitch

        event.isCancelled = true

        if (isPlayerMounted(player)) {
            dismountPlayer(player)
            FoliaCompat.scheduleDelayed(player, plugin, {
                if (player.isOnline) {
                    performBottomBoundaryTeleport(player, safeLocation)
                }
            }, 3L)
        } else {
            performBottomBoundaryTeleport(player, safeLocation)
        }

        player.isInvulnerable = true
        FoliaCompat.scheduleDelayed(player, plugin, {
            if (player.isOnline) player.isInvulnerable = false
        }, 20L)

        lastBottomTeleport[player.uniqueId] = System.currentTimeMillis() + 1000

        player.sendMessage("§eYou were teleported to safety above the bottom boundary!")

        FoliaCompat.scheduleDelayed(player, plugin, {
            lastBottomTeleport.remove(player.uniqueId)
        }, 100L)
    }

    private fun handleNetherRoofAttempt(player: Player, event: PlayerMoveEvent) {
        if (player.isOp) return

        event.isCancelled = true
        player.velocity = player.velocity.setY(0.0)

        var safeLocation: Location? = null
        if (ensureSafeTeleport) {
            safeLocation = findSafeLocationBelowBedrock(event.from)
        }

        safeLocation?.let { teleportPlayer(player, it) } ?: run {
            var fallbackLocation = event.from.clone().subtract(0.0, 10.0, 0.0)
            if (fallbackLocation.y < 1.0) {
                fallbackLocation.y = 5.0
            }
            teleportPlayer(player, fallbackLocation)
        }

        player.sendMessage("§cYou cannot go above the nether roof!")
    }

    private fun handleNetherRoof(player: Player, event: PlayerMoveEvent) {
        if (player.isOp) return

        val from = event.from
        if (from.blockY >= netherYLevel) {
            if (damagePlayers) {
                val currentTime = System.currentTimeMillis()
                val lastDamage = lastNetherRoofDamage.getOrDefault(player.uniqueId, 0L)

                if (currentTime - lastDamage < NETHER_ROOF_DAMAGE_COOLDOWN) return

                lastNetherRoofDamage[player.uniqueId] = currentTime

                player.isFlying = false
                if (player.isGliding) player.isGliding = false

                if (player.gameMode != org.bukkit.GameMode.SURVIVAL) {
                    player.gameMode = org.bukkit.GameMode.SURVIVAL
                }

                if (player.isInvulnerable) player.isInvulnerable = false

                event.isCancelled = true

                if (player.isOnline && !player.isDead) {
                    player.health = 0.0
                }

                player.sendMessage("§cYou cannot be above the Nether roof!")

                FoliaCompat.scheduleDelayed(player, plugin, {
                    try {
                        lastNetherRoofDamage.remove(player.uniqueId)
                    } catch (_: Exception) {
                    }
                }, 200L)
            } else {
                teleportPlayerDownFromNetherRoof(player, event, from)
            }
            return
        }

        if (!blockPlayers) return
        teleportPlayerDownFromNetherRoof(player, event, from)
    }

    private fun teleportPlayerDownFromNetherRoof(player: Player, event: PlayerMoveEvent, from: Location) {
        event.isCancelled = true
        player.velocity = player.velocity.setY(0.0)

        var safeLocation: Location? = null
        if (ensureSafeTeleport) {
            safeLocation = findSafeLocationBelowBedrock(from)
        }

        safeLocation?.let { teleportPlayer(player, it) } ?: run {
            val fallbackLocation = from.clone().subtract(0.0, 3.0, 0.0)
            teleportPlayer(player, fallbackLocation)
        }

        player.sendMessage("§cYou cannot go above the nether roof!")
    }

    private fun isPlayerMounted(player: Player): Boolean {
        return player.vehicle != null
    }

    private fun dismountPlayer(player: Player) {
        if (isPlayerMounted(player)) {
            player.vehicle?.let { vehicle ->
                vehicle.eject()
                FoliaCompat.scheduleDelayed(player, plugin, {
                    if (player.isOnline && isPlayerMounted(player)) {
                        player.leaveVehicle()
                    }
                }, 1L)
            }
        }
    }

    private fun teleportPlayer(player: Player, location: Location) {
        if (isPlayerMounted(player)) {
            dismountPlayer(player)

            FoliaCompat.scheduleDelayed(player, plugin, {
                if (player.isOnline) {
                    performTeleport(player, location)
                }
            }, 3L)
        } else {
            performTeleport(player, location)
        }
    }

    private fun performTeleport(player: Player, location: Location) {
        player.teleportAsync(location)

        val finalLocation = location
        FoliaCompat.scheduleDelayed(player, plugin, {
            if (player.isOnline && player.location.y >= netherYLevel.toDouble()) {
                player.teleportAsync(finalLocation)
                player.velocity = player.velocity.setY(0.0)
            }
        }, 5L)
    }

    private fun performBottomBoundaryTeleport(player: Player, safeLocation: Location) {
        player.teleportAsync(safeLocation)
        player.velocity = Vector(0.0, 0.0, 0.0)
        player.fallDistance = 0f


        player.teleportAsync(safeLocation.clone().add(0.0, 0.1, 0.0))

        FoliaCompat.scheduleDelayed(player, plugin, {
            if (player.isOnline) {
                player.teleportAsync(safeLocation)
                player.velocity = org.bukkit.util.Vector(0.0, 0.0, 0.0)
                player.fallDistance = 0f

                if (player.location.y < player.world.minHeight) {
                    player.teleportAsync(safeLocation.clone().add(0.0, 1.0, 0.0))
                    player.velocity = Vector(0.0, 0.0, 0.0)
                }
            }
        }, 1L)
    }

    private fun findSafeLocationAboveBottom(world: World, x: Double, z: Double, startY: Double): Location {
        val maxY = world.maxHeight
        val minY = world.minHeight

        val maxX = x.toInt()
        val maxZ = z.toInt()
        for (y in startY.toInt() until maxY) {
            if (y >= startY.toInt() + 50) break
            val ground = world.getBlockAt(maxX, y - 1, maxZ)
            val body = world.getBlockAt(maxX, y, maxZ)
            val head = world.getBlockAt(maxX, y + 1, maxZ)

            if (isSolidBlock(ground) && isAirSpace(body) && isAirSpace(head)) {
                val location = Location(world, x, y.toDouble(), z)
                if (!isAirSpace(body)) Bukkit.getRegionScheduler().execute(plugin, body.location) { body.type = Material.AIR }
                if (!isAirSpace(head)) Bukkit.getRegionScheduler().execute(plugin, head.location) { head.type = Material.AIR }

                return location
            }
        }

        val y = startY.toInt()
        if (y >= minY && y < maxY) {
            val ground = world.getBlockAt(x.toInt(), y - 1, z.toInt())
            val body = world.getBlockAt(x.toInt(), y, z.toInt())
            val head = world.getBlockAt(x.toInt(), y + 1, z.toInt())

            if (!isSolidBlock(ground)) {
                Bukkit.getRegionScheduler().execute(plugin, ground.location) { ground.type = Material.STONE }
            }

            Bukkit.getRegionScheduler().execute(plugin, body.location) { body.type = Material.AIR }
            Bukkit.getRegionScheduler().execute(plugin, head.location) { head.type = Material.AIR }

            return Location(world, x, y.toDouble(), z)
        }

        return Location(world, x, startY, z)
    }

    private fun findSafeLocationBelowBedrock(from: Location): Location? {
        val world = from.world
        var bedrockCeiling = netherYLevel
        val bx = from.blockX
        val bz = from.blockZ
        for (y in netherYLevel + 5 downTo netherYLevel - 10) {
            val block = world.getBlockAt(bx, y, bz)
            if (block.type == Material.BEDROCK) {
                bedrockCeiling = y
                break
            }
        }

        for (y in bedrockCeiling - 8 downTo 1) {
            if (y <= bedrockCeiling - 40) break
            val ground = world.getBlockAt(bx, y - 1, bz)
            val body = world.getBlockAt(bx, y, bz)
            val head = world.getBlockAt(bx, y + 1, bz)

            if (isSolidBlock(ground) && isAirSpace(body) && isAirSpace(head)) {
                val location = Location(world, from.x, y.toDouble(), from.z)

                if (!isAirSpace(body)) Bukkit.getRegionScheduler().execute(plugin, body.location) { body.type = Material.AIR }
                if (!isAirSpace(head)) Bukkit.getRegionScheduler().execute(plugin, head.location) { head.type = Material.AIR }

                for (clearY in y + 2..y + 4) {
                    if (clearY >= bedrockCeiling) break
                    val aboveBlock = world.getBlockAt(from.blockX, clearY, from.blockZ)
                    if (!isAirSpace(aboveBlock) && aboveBlock.type != Material.BEDROCK) {
                        Bukkit.getRegionScheduler().execute(plugin, aboveBlock.location) { aboveBlock.type = Material.AIR }
                    }
                }

                if (createNetherPlatform) {
                    createNetherPlatform(world, from.blockX, y - 1, from.blockZ)
                }

                return location
            }
        }

        val forceY = maxOf(5, bedrockCeiling - 10)
        val ground = world.getBlockAt(from.blockX, forceY - 1, from.blockZ)
        val body = world.getBlockAt(from.blockX, forceY, from.blockZ)
        val head = world.getBlockAt(from.blockX, forceY + 1, from.blockZ)

        if (!isSolidBlock(ground) && ground.type != Material.BEDROCK) {
            Bukkit.getRegionScheduler().execute(plugin, ground.location) { ground.type = Material.NETHERRACK }
        }
        Bukkit.getRegionScheduler().execute(plugin, body.location) { body.type = Material.AIR }
        Bukkit.getRegionScheduler().execute(plugin, head.location) { head.type = Material.AIR }

        for (clearY in forceY + 2..forceY + 5) {
            if (clearY >= bedrockCeiling) break
            val aboveBlock = world.getBlockAt(from.blockX, clearY, from.blockZ)
            if (aboveBlock.type != Material.BEDROCK) {
                Bukkit.getRegionScheduler().execute(plugin, aboveBlock.location) { aboveBlock.type = Material.AIR }
            }
        }

        if (createNetherPlatform) {
            createNetherPlatform(world, from.blockX, forceY - 1, from.blockZ)
        }

        return Location(world, from.x, forceY.toDouble(), from.z)
    }

    private fun createNetherPlatform(world: World, x: Int, y: Int, z: Int) {
        if (y < 0 || y >= world.maxHeight) return

        for (dx in -1..1) {
            for (dz in -1..1) {
                val block = world.getBlockAt(x + dx, y, z + dz)
                if ((block.type == Material.AIR || block.type == Material.VOID_AIR || block.type == Material.CAVE_AIR || block.type == Material.LAVA) && block.type != Material.END_PORTAL && block.type != Material.END_PORTAL_FRAME) {
                    Bukkit.getRegionScheduler().execute(plugin, block.location) { block.type = Material.NETHERRACK }
                }
            }
        }

        for (dx in -1..1) {
            for (dz in -1..1) {
                for (dy in 1..4) {
                    val block = world.getBlockAt(x + dx, y + dy, z + dz)
                    if (block.type.isSolid && block.type != Material.BEDROCK) {
                        Bukkit.getRegionScheduler().execute(plugin, block.location) { block.type = Material.AIR }
                    }
                }
            }
        }
    }

    private fun isSolidBlock(block: Block): Boolean {
        val type = block.type
        return type.isSolid && type != Material.LAVA && type != Material.WATER && type != Material.FIRE && type != Material.MAGMA_BLOCK && type !in LEAVES
    }

    private fun isAirSpace(block: Block): Boolean {
        val type = block.type
        return type == Material.AIR || type == Material.VOID_AIR || type == Material.CAVE_AIR || (!type.isSolid && type != Material.LAVA && type != Material.WATER && type != Material.FIRE && type != Material.MAGMA_BLOCK)
    }

    private fun handleWorldBorderViolation(player: Player, event: PlayerMoveEvent) {
        event.isCancelled = true
        player.velocity = Vector(0.0, 0.0, 0.0)

        if (isPlayerMounted(player)) {
            player.vehicle?.let { vehicle ->
                dismountPlayer(player)
                FoliaCompat.schedule(vehicle, plugin) { vehicle.remove() }
            }
        }

        val safeLocation = findSafeLocationInsideBorder(event.from)

        player.teleportAsync(safeLocation)
        player.velocity = Vector(0.0, 0.0, 0.0)

        sendBorderMessage(player, "§cYou cannot go beyond the world border!")
    }

    private fun findSafeLocationInsideBorder(from: Location): Location {
        val world = from.world
        val border = world.worldBorder
        val center = border.center

        var maxDist = (border.size / 2.0) - worldBorderBuffer
        val precisionLimit = 29999984.0 - worldBorderBuffer
        maxDist = minOf(maxDist, precisionLimit)

        var x = from.x
        var z = from.z
        var relX = x - center.x
        var relZ = z - center.z

        relX = maxOf(-maxDist, minOf(maxDist, relX))
        relZ = maxOf(-maxDist, minOf(maxDist, relZ))

        x = center.x + relX
        z = center.z + relZ

        var y = from.y
        if (world.environment == World.Environment.NETHER) {
            y = minOf(y, netherYLevel.toDouble() - 2)
            val checkLoc = Location(world, x, y, z)
            val safe = findSafeLocationBelowBedrock(checkLoc)
            safe?.let {
            it.yaw = from.yaw
            it.pitch = from.pitch
            return it
        }
            y = 100.0
        } else {
            val safeY = world.getHighestBlockYAt(x.toInt(), z.toInt()) + 1
            y = maxOf(safeY.toDouble(), world.minHeight.toDouble() + 5.0)
        }

        val safe = Location(world, x, y, z)
        safe.yaw = from.yaw
        safe.pitch = from.pitch

        return safe
    }

    private fun isOutsideWorldBorder(location: Location?): Boolean {
        if (location == null) return false
        return isOutsideWorldBorder(location.x, location.z, location.world)
    }

    private fun isOutsideWorldBorder(x: Double, z: Double, world: World?): Boolean {
        if (world == null) return false

        val absX = abs(x)
        val absZ = abs(z)

        if (absX < EARLY_EXIT_DISTANCE && absZ < EARLY_EXIT_DISTANCE) return false
        if (absX >= PRECISION_LIMIT || absZ >= PRECISION_LIMIT) return true

        val border = world.worldBorder
        val radius = border.size / 2.0
        val center = border.center

        return abs(x - center.x) >= radius || abs(z - center.z) >= radius
    }

    private fun getDistanceOutsideBorder(location: Location?): Double {
        if (location == null || location.world == null) return 0.0

        val border = location.world.worldBorder
        val center = border.center
        val radius = border.size / 2.0

        val dx = abs(location.x - center.x) - radius
        val dz = abs(location.z - center.z) - radius

        return maxOf(dx, dz)
    }

    fun startWorldBorderMonitor() {
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, {
            for (player in plugin.server.onlinePlayers) {
                if (player.isOp) continue

                val loc = player.location
                if (isOutsideWorldBorder(loc.x, loc.z, loc.world)) {
                    FoliaCompat.schedule(player, plugin) {
                        if (!player.isOnline) return@schedule

                        if (isPlayerMounted(player)) {
                            val vehicle = player.vehicle
                            player.leaveVehicle()
                            vehicle?.let { FoliaCompat.schedule(it, plugin) { it.remove() } }
                        }

                        val safe = findSafeLocationInsideBorder(player.location)
                        player.teleportAsync(safe)
                        player.sendMessage("§cYou were teleported inside the world border!")
                    }
                }
            }
        }, 20L, 10L)
    }

    fun startNetherRoofMonitor() {
        plugin.server.globalRegionScheduler.runAtFixedRate(plugin, {
            for (player in plugin.server.onlinePlayers) {
                if (player.isOp) continue

                val loc = player.location
                val world = loc.world
                if (world == null) continue

                if (world.environment == World.Environment.NETHER && loc.y >= netherYLevel.toDouble()) {
                    FoliaCompat.schedule(player, plugin) {
                        if (!player.isOnline) return@schedule

                        val safe = findSafeLocationBelowBedrock(player.location)
                        safe?.let {
                            player.teleportAsync(it)
                            player.sendMessage("§cYou cannot be above the nether roof!")
                        }
                    }
                }
            }
        }, 10L, 5L)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        lastNetherRoofDamage.remove(uuid)
        lastBottomTeleport.remove(uuid)
        lastBorderMessage.remove(uuid)
    }

    private fun sendBorderMessage(player: Player, message: String) {
        val now = System.currentTimeMillis()
        val last = lastBorderMessage.getOrDefault(player.uniqueId, 0L)
        if (now - last > BORDER_MESSAGE_COOLDOWN) {
            player.sendMessage(message)
            lastBorderMessage[player.uniqueId] = now
        }
    }
}
