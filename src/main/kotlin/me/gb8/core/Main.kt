/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core

import me.gb8.core.achievements.AchievementsListener
import me.gb8.core.antiillegal.AntiIllegalMain
import me.gb8.core.chat.ChatSection
import me.gb8.core.listeners.KickListener
import me.gb8.core.listeners.OpWhiteListListener
import me.gb8.core.chat.AnnouncementTask
import me.gb8.core.command.CommandSection
import me.gb8.core.listeners.PlayerSettingsListener
import me.gb8.core.database.GeneralDatabase
import me.gb8.core.deathmessages.DeathMessageListener
import me.gb8.core.dupe.DupeSection
import me.gb8.core.home.HomeManager
import me.gb8.core.patch.PatchSection
import me.gb8.core.patch.EndExitPortalBuilder
import me.gb8.core.patch.EndPortalBuilder
import me.gb8.core.patch.EndPortalGateways
import me.gb8.core.tablist.TabSection
import me.gb8.core.tpa.TPASection
import me.gb8.core.util.GlobalUtils

import me.gb8.core.vote.VoteSection
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.UUID

class Main : JavaPlugin(), Listener {

    companion object {
        @get:JvmName("retrieveMainInstance")
        lateinit var instance: Main
            private set
        var prefix: String = ""
            private set
        lateinit var executorService: ScheduledExecutorService
            private set
    }

    internal val sections = mutableListOf<Section>()
    private val reloadables = mutableListOf<Reloadable>()
    internal val violationManagers = mutableListOf<ViolationManager>()

    var startTime: Long = 0L
        private set

    val lastLocations = ConcurrentHashMap<UUID, Location>()
    val vanishedPlayers = ConcurrentHashMap.newKeySet<UUID>()

    override fun onEnable() {
        Bukkit.getConsoleSender().sendMessage("\u00A73   ___    _        \u00A73___    _      \u00A77____                       ")
        Bukkit.getConsoleSender().sendMessage("\u00A73  ( _ )  | |__    \u00A73( _ )  | |_   \u00A77/ ___|   ___    _ __    ___ ")
        Bukkit.getConsoleSender().sendMessage("\u00A73  / _ \\  | '_ \\   \u00A73/ _ \\  | __| \u00A77| |      / _ \\  | '__|  / _ \\")
        Bukkit.getConsoleSender().sendMessage("\u00A73 | (_) | | |_) | \u00A73| (_) | | |_  \u00A77| |___  | (_) | | |    |  __/")
        Bukkit.getConsoleSender().sendMessage("\u00A73  \\___/  |_.__/   \u00A73\\___/   \\__|  \u00A77\\____|  \\___/  |_|     \\___|")
        Bukkit.getConsoleSender().sendMessage("")
        Bukkit.getConsoleSender().sendMessage("\u00A78+============================================================+")
        Bukkit.getConsoleSender().sendMessage("\u00A73  8b8tCore \u00A75Kotlin Edition \u00A77| Folia Core Plugin                  ")
        Bukkit.getConsoleSender().sendMessage("\u00A78+============================================================+")
        Bukkit.getConsoleSender().sendMessage("\u00A72 v${pluginMeta.version}                              \u00A73by 8b8tTeam")
        Bukkit.getConsoleSender().sendMessage("")

        instance = this
        executorService = Executors.newScheduledThreadPool(4)
        startTime = System.currentTimeMillis()
        saveDefaultConfig()
        prefix = config.getString("prefix", "&8[&98b&78t&8]") ?: "&8[&98b&78t&8]"
        logger.addHandler(LoggerHandler())
        Localization.loadLocalizations(dataFolder)

        GeneralDatabase.initialize(dataFolder.absolutePath)
        GlobalUtils.log(Level.INFO, "GeneralDatabase initialized successfully")

        Bukkit.getAsyncScheduler().runAtFixedRate(this, { violationManagers.forEach { it.decrementAll() } }, 0L, 1L, TimeUnit.SECONDS)
        Bukkit.getAsyncScheduler().runAtFixedRate(this, { AnnouncementTask().run() }, 10L, config.getInt("AnnouncementInterval").toLong(), TimeUnit.SECONDS)

        Bukkit.getGlobalRegionScheduler().runDelayed(this, {
            EndPortalBuilder(this).run()
        }, 200L)
        Bukkit.getGlobalRegionScheduler().runDelayed(this, {
            EndExitPortalBuilder(this).run()
        }, 200L)
        Bukkit.getGlobalRegionScheduler().runDelayed(this, {
            EndPortalGateways.EndExitGatewayBuilder(this).run()
        }, 200L)

        register(TabSection(this))
        register(ChatSection(this))
        register(TPASection(this))
        register(HomeManager(this))
        register(CommandSection(this))
        register(PatchSection(this) as Section)
        register(DupeSection(this))
        register(DeathMessageListener() as org.bukkit.event.Listener)
        register(AchievementsListener())
        register(PlayerSettingsListener(this))
        register(OpWhiteListListener(this))
        register(KickListener(this))

        if (config.getBoolean("AntiIllegal.Enabled", true)) register(AntiIllegalMain(this))
        register(VoteSection(this))
        register(GeneralDatabase.getInstance())

        for (section in sections.toList()) {
            try {
                section.enable()
            } catch (e: Exception) {
                GlobalUtils.log(Level.SEVERE, "Failed to enable section %s. Please see stacktrace below for more info", section.name)
                e.printStackTrace()
            }
        }
        register(this)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        lastLocations.remove(uuid)
        vanishedPlayers.remove(uuid)
    }

    override fun onDisable() {
        server.pluginManager.disablePlugin(this)

        violationManagers.clear()
        sections.forEach { it.disable() }
        sections.clear()
        reloadables.clear()

        runCatching {
            GeneralDatabase.getInstance().close()
            GlobalUtils.log(Level.INFO, "GeneralDatabase closed successfully")
        }.onFailure { e ->
            if (e !is IllegalStateException) throw e
        }

        executorService.takeIf { !it.isShutdown }?.let { service ->
            service.shutdown()
            try {
                if (!service.awaitTermination(5, TimeUnit.SECONDS)) {
                    service.shutdownNow()
                }
            } catch (e: InterruptedException) {
                service.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
    }

    override fun reloadConfig() {
        super.reloadConfig()
        sections.forEach { section ->
            val sectionConfig = config.getConfigurationSection(section.name)
            if (sectionConfig != null) section.reloadConfig()
        }
        reloadables.forEach { it.reloadConfig() }
    }

    fun register(vararg listeners: Listener) {
        listeners.forEach { listener ->
            server.pluginManager.registerEvents(listener, this)
            if (listener is Reloadable) reloadables.add(listener)
        }
    }

    fun register(section: Section) {
        check(getSectionByName(section.name) == null) { "Section has already been registered ${section.name}" }
        sections.add(section)
    }

    fun register(manager: ViolationManager) {
        if (violationManagers.contains(manager)) return
        violationManagers.add(manager)
        if (manager is Listener) register(manager as Listener)
    }

    fun registerReloadable(reloadable: Reloadable): Reloadable {
        reloadables.add(reloadable)
        return reloadable
    }

    fun getSectionConfig(section: Section): ConfigurationSection? {
        return config.getConfigurationSection(section.name)
    }

    fun getSectionDataFolder(section: Section): File {
        val dataFolder = File(getDataFolder(), section.name)
        if (!dataFolder.exists()) dataFolder.mkdirs()
        return dataFolder
    }

    fun getSectionByName(name: String): Section? {
        return sections.find { it.name == name }
    }

    fun getLastLocation(player: Player): Location? {
        return lastLocations[player.uniqueId]
    }
}
