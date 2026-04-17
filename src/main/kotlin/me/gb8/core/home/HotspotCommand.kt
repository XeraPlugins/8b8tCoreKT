/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.home.commands

import me.gb8.core.Main
import me.gb8.core.home.HomeManager
import me.gb8.core.util.GlobalUtils
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class HotspotCommand(private val main: HomeManager) : TabExecutor, Listener {
    private val hotspotLocations = ConcurrentHashMap<UUID, Location>()
    private val playerBossBars = ConcurrentHashMap<UUID, BossBar>()
    private val hotspotOptions = listOf("create", "delete", "teleport")
    private val lastCreateTimes = ConcurrentHashMap<UUID, Long>()
    private val creationCooldowns = ConcurrentHashMap<UUID, Long>()
    private val cooldownTasks = ConcurrentHashMap<UUID, ScheduledTask>()

    companion object {
        private const val PERMISSION = "8b8tcore.command.hotspotcreate"
        private const val DURATION_IN_SECONDS = 300
        private const val CREATION_COOLDOWN = 30 * 1000L
        private const val DURATION_MILLISECONDS = DURATION_IN_SECONDS * 1000L
        private const val VIEW_SWITCH_TIME = 5
    }

    private fun hasCreatePermission(player: Player): Boolean =
        player.hasPermission(PERMISSION) || player.isOp

    private fun getHotspotText(player: Player): String {
        val playerDisplayNameStr = MiniMessage.miniMessage().serialize(player.displayName())
        return GlobalUtils.convertToMiniMessageFormat(
            "<bold><gradient:#5555FF:#0000AA>${playerDisplayNameStr}<reset><bold><gradient:#5555FF:#0000AA>'s hotspot - Do <gradient:#FFE259:#FFA751>/hotspot</gradient> to teleport.</gradient>"
        ) ?: ""
    }

    init {
        val pluginMain = main.main
        pluginMain.server.pluginManager.registerEvents(this, pluginMain)
        startBossBarTask()
    }

    private fun startBossBarTask() {
        val pluginMain = main.main
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(pluginMain, {
            for (player in Bukkit.getOnlinePlayers()) {
                updateBossBarsForPlayer(player)
            }
        }, 20L, 20L)
    }

    private fun updateBossBarsForPlayer(player: Player) {
        val playerLocation = player.location
        val thresholdSq = 256.0 * 256.0

        for (entry in hotspotLocations.entries) {
            val hotspotOwnerUUID = entry.key
            val hotspotLocation = entry.value
            val bossBar = playerBossBars[hotspotOwnerUUID] ?: continue

            if (playerLocation.world == hotspotLocation.world &&
                    playerLocation.distanceSquared(hotspotLocation) <= thresholdSq) {
                bossBar.addViewer(player)
            } else {
                bossBar.removeViewer(player)
            }
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage("You must be a player to use this command.")
            return true
        }

        when {
            args.isEmpty() -> teleportToNearestHotspot(player)
            args[0].equals("create", ignoreCase = true) -> {
                if (hasCreatePermission(player)) createHotspot(player)
                else GlobalUtils.sendPrefixedLocalizedMessage(player, "hotspot_no_perms_create")
            }
            args[0].equals("delete", ignoreCase = true) -> {
                if (hasCreatePermission(player)) deleteHotspot(player)
                else GlobalUtils.sendPrefixedLocalizedMessage(player, "hotspot_no_perms_create")
            }
            args[0].equals("teleport", ignoreCase = true) -> {
                if (args.size == 2) teleportToPlayerHotspot(player, args[1])
                else GlobalUtils.sendPrefixedLocalizedMessage(player, "hotspot_no_playername")
            }
            else -> teleportToNearestHotspot(player)
        }
        return true
    }

    private fun createHotspot(player: Player) {
        val lastCreate = lastCreateTimes.getOrDefault(player.uniqueId, 0L)
        if (System.currentTimeMillis() - lastCreate < DURATION_MILLISECONDS) {
            GlobalUtils.sendPrefixedLocalizedMessage(player, "hotspot_already_created")
            return
        }

        if (hotspotLocations.containsKey(player.uniqueId)) {
            GlobalUtils.sendPrefixedLocalizedMessage(player, "hotspot_already_created")
            return
        }

        val lastCreationTime = creationCooldowns[player.uniqueId]
        if (lastCreationTime != null) {
            val timeSinceLastCreation = System.currentTimeMillis() - lastCreationTime
            if (timeSinceLastCreation < CREATION_COOLDOWN) {
                val secondsLeft = (CREATION_COOLDOWN - timeSinceLastCreation) / 1000L
                GlobalUtils.sendPrefixedLocalizedMessage(player, "hotspot_creation_cooldown", secondsLeft)
                return
            }
        }

        val hotspotLocation = player.location
        hotspotLocations[player.uniqueId] = hotspotLocation
        lastCreateTimes[player.uniqueId] = System.currentTimeMillis()
        creationCooldowns[player.uniqueId] = System.currentTimeMillis()

        GlobalUtils.sendPrefixedLocalizedMessage(player, "hotspot_created")

        for (p in Bukkit.getOnlinePlayers()) {
            GlobalUtils.sendPrefixedLocalizedMessage(p, "hotspot_created_to_everyone", player.name, player.name)
            p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_BELL, 2.0f, 0.7f)

            val pluginMain = main.main
            Bukkit.getRegionScheduler().runDelayed(pluginMain, player.location, {
                if (player.isOnline) {
                    p.playSound(p.location, Sound.BLOCK_NOTE_BLOCK_BELL, 2.0f, 1.0f)
                }
            }, 6L)
        }

        val hotspotText = getHotspotText(player)
        val hotspotTextComponent = MiniMessage.miniMessage().deserialize(hotspotText)
        val bossBar = BossBar.bossBar(hotspotTextComponent, 1.0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS)

        for (nearbyPlayer in Bukkit.getOnlinePlayers()) {
            if (nearbyPlayer.world == hotspotLocation.world &&
                    nearbyPlayer.location.distance(hotspotLocation) <= 256.0) {
                bossBar.addViewer(nearbyPlayer)
            }
        }

        playerBossBars[player.uniqueId] = bossBar
        handleCooldown(bossBar, DURATION_IN_SECONDS, player)
    }

    private fun deleteHotspot(player: Player) {
        if (!hotspotLocations.containsKey(player.uniqueId)) {
            GlobalUtils.sendPrefixedLocalizedMessage(player, "hotspot_not_active")
            return
        }

        hotspotLocations.remove(player.uniqueId)
        lastCreateTimes.remove(player.uniqueId)

        cooldownTasks.remove(player.uniqueId)?.cancel()

        playerBossBars.remove(player.uniqueId)?.let { bossBar ->
            bossBar.viewers()
                .filterIsInstance<Player>()
                .forEach { bossBar.removeViewer(it) }
        }

        if (player.isOnline) GlobalUtils.sendPrefixedLocalizedMessage(player, "hotspot_deleted")
    }

    private fun handleCooldown(bossBar: BossBar, duration: Int, player: Player) {
        cooldownTasks.remove(player.uniqueId)?.cancel()

        var timeLeft = duration
        var showingHotspotMessage = true
        val colorRed = "<gradient:#CB2D3E:#EF473A>"
        val colorGreen = "<gradient:#2DCB62:#53AE2C>"
        val colorYellow = "<gradient:#FFE259:#FFA751>"
        val pluginMain = main.main

        val task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(pluginMain, {
            if (timeLeft <= 0 || !player.isOnline) {
                deleteHotspot(player)
                cooldownTasks.remove(player.uniqueId)
                return@runAtFixedRate
            }

            bossBar.progress(timeLeft.toFloat() / duration)

            val minutes = timeLeft / 60
            val seconds = timeLeft % 60
            val timeRemaining = String.format("%02d:%02d", minutes, seconds)

            if (timeLeft % VIEW_SWITCH_TIME == 0) {
                showingHotspotMessage = !showingHotspotMessage
            }

            val hotspotText = if (showingHotspotMessage) {
                getHotspotText(player)
            } else {
                val playerDisplayNameStr = MiniMessage.miniMessage().serialize(player.displayName())
                var color = colorGreen
                if (timeLeft <= 30) {
                    color = colorRed
                    bossBar.color(BossBar.Color.RED)
                } else if (timeLeft <= 60) {
                    color = colorYellow
                }
                GlobalUtils.convertToMiniMessageFormat(
                    "<bold><gradient:#5555FF:#0000AA>${playerDisplayNameStr}<reset><bold><gradient:#5555FF:#0000AA>'s hotspot ends in $color$timeRemaining</gradient> minutes.</gradient></bold>"
                ) ?: ""
            }

            bossBar.name(MiniMessage.miniMessage().deserialize(hotspotText))
            timeLeft--
        }, 1L, 20L)

        cooldownTasks[player.uniqueId] = task
    }

    private fun teleportToPlayerHotspot(player: Player, targetPlayerName: String) {
        val targetPlayer = Bukkit.getPlayerExact(targetPlayerName)

        if (targetPlayer == null || !hotspotLocations.containsKey(targetPlayer.uniqueId)) {
            GlobalUtils.sendPrefixedLocalizedMessage(player, "hotspot_not_found_for_player", targetPlayerName)
            return
        }

        if (GlobalUtils.isTeleportRestricted(player)) {
            val range = GlobalUtils.getTeleportRestrictionRange(player)
            GlobalUtils.sendPrefixedLocalizedMessage(player, "tpa_too_close", range)
            return
        }

        val hotspotLocation = hotspotLocations[targetPlayer.uniqueId] ?: return

        val pluginMain = main.main
        Bukkit.getGlobalRegionScheduler().runDelayed(pluginMain, {
            if (player.isOnline && targetPlayer.isOnline) {
                player.teleportAsync(hotspotLocation)
                GlobalUtils.sendPrefixedLocalizedMessage(player, "hotspot_teleported_to_player", targetPlayerName)
            }
        }, 1L)
    }

    private fun teleportToNearestHotspot(player: Player) {
        val playerLocation = player.location

        val closestHotspot = hotspotLocations.values
            .filter { it.world?.name == playerLocation.world?.name }
            .minByOrNull { playerLocation.distance(it) }
            ?: hotspotLocations.maxByOrNull { lastCreateTimes[it.key] ?: 0L }?.value

        if (closestHotspot == null) {
            GlobalUtils.sendPrefixedLocalizedMessage(player, "hotspot_not_found")
        } else {
            if (GlobalUtils.isTeleportRestricted(player)) {
                val range = GlobalUtils.getTeleportRestrictionRange(player)
                GlobalUtils.sendPrefixedLocalizedMessage(player, "tpa_too_close", range)
                return
            }
            val pluginMain = main.main
            Bukkit.getGlobalRegionScheduler().runDelayed(pluginMain, {
                if (player.isOnline) {
                    player.teleportAsync(closestHotspot)
                    GlobalUtils.sendPrefixedLocalizedMessage(player, "hotspot_teleported_to")
                }
            }, 1L)
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player        
        for (bossBar in playerBossBars.values) {
            bossBar.removeViewer(player)
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<String>): List<String> {
        return when {
            args.size == 1 -> hotspotOptions.filter { it.startsWith(args[0].lowercase()) }
            args.size == 2 && args[0].lowercase().startsWith("teleport") -> playerBossBars.keys.mapNotNull { Bukkit.getPlayer(it) }
                .map { it.name }
                .filter { it.lowercase().startsWith(args[1].lowercase()) }
            else -> emptyList()
        }
    }
}
