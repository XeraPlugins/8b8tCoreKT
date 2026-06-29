/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.home

import me.gb8.core.IStorage
import me.gb8.core.Main
import me.gb8.core.Reloadable
import me.gb8.core.Section
import me.gb8.core.home.commands.*
import me.gb8.core.listeners.HomeJoinListener
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID

class HomeManager(override val plugin: Main) : Section {
    val homes = mutableMapOf<UUID, HomeData>()
    var storage: IStorage<HomeData, UUID>? = null
    private var config: ConfigurationSection? = null
    override val name: String = "Home"
    val main: Main get() = plugin

    fun getHomes(uuid: UUID): HomeData = homes.getOrPut(uuid) { HomeData() }

    fun getMaxHomes(player: Player): Int {
        val maxL = player.effectivePermissions
            .map { it.permission }
            .filter { it.startsWith("8b8tcore.home.max.") }
            .mapNotNull { it.substringAfterLast('.').toIntOrNull() }
        return maxL.maxOrNull() ?: config?.getInt("MaxHomes") ?: 1
    }

    fun tabComplete(sender: CommandSender, args: Array<String>): List<String> {
        if (sender is Player) {
            val homes = homes[sender.uniqueId] ?: return emptyList()
            val homeNames = homes.getHomes().map { it.name }.sortedWith(String.CASE_INSENSITIVE_ORDER)
            return if (args.isEmpty()) {
                homeNames
            } else {
                homeNames.filter { it.lowercase().startsWith(args[0].lowercase()) }
            }
        }
        return emptyList()
    }

    override fun enable() {
        val homesFolder = File(plugin.getSectionDataFolder(this), "PlayerHomes")
        if (!homesFolder.exists()) homesFolder.mkdir()
        storage = HomeJsonStorage(homesFolder)
        config = plugin.getSectionConfig(this)
        if (Bukkit.getOnlinePlayers().isNotEmpty()) {
            val homeStorage = storage ?: return
            Bukkit.getOnlinePlayers().forEach { homes[it.uniqueId] = homeStorage.load(it.uniqueId) }
        }
        plugin.register(HomeJoinListener(this))
        plugin.getCommand("home")?.setExecutor(HomeCommand(this))
        plugin.getCommand("sethome")?.setExecutor(SetHomeCommand(this))
        plugin.getCommand("delhome")?.setExecutor(DelHomeCommand(this))
        plugin.getCommand("back")?.setExecutor(BackCommand(this))
        plugin.getCommand("hotspot")?.setExecutor(HotspotCommand(this))
    }

    override fun disable() {
        homes.forEach { (uuid, d) -> storage?.save(d, uuid) }
        homes.clear()
    }

    override fun reloadConfig() {
        config = plugin.getSectionConfig(this)
    }
}
