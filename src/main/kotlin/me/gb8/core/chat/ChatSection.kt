/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.chat

import me.gb8.core.IStorage
import me.gb8.core.Main
import me.gb8.core.Section
import me.gb8.core.chat.ChatCommands.IgnoreCommand
import me.gb8.core.chat.ChatCommands.MessageCommand
import me.gb8.core.chat.ChatCommands.ReplyCommand
import me.gb8.core.chat.ChatCommands.ToggleChatCommand
import me.gb8.core.chat.ChatCommands.UnIgnoreCommand
import me.gb8.core.chat.ChatCommands.IgnoreListCommand
import me.gb8.core.chat.ChatFileIO
import me.gb8.core.listeners.ChatListener
import me.gb8.core.listeners.CommandWhitelist
import me.gb8.core.listeners.JoinLeaveListener
import me.gb8.core.listeners.VanishTabListener
import me.gb8.core.database.GeneralDatabase
import me.gb8.core.coordinate.CoordinateSpoofing
import me.gb8.core.util.GlobalUtils
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

class ChatSection(override val plugin: Main) : Section {
    private val map = ConcurrentHashMap<UUID, ChatInfo>()
    var config: ConfigurationSection? = null
    @Volatile var blockedWordsLowercase: List<String> = emptyList()
        private set
    lateinit var chatInfoStore: IStorage<ChatInfo, Player>

    override fun enable() {
        val dataFolder = plugin.getSectionDataFolder(this)
        if (!dataFolder.exists()) dataFolder.mkdir()
        val tldFile = File(dataFolder, "tlds.txt")
        if (!tldFile.exists()) GlobalUtils.unpackResource("tlds.txt", tldFile)
        val ignoresFolder = File(dataFolder, "IgnoreLists")
        chatInfoStore = ChatFileIO(ignoresFolder, this)
        if (!ignoresFolder.exists()) ignoresFolder.mkdir()
        config = plugin.getSectionConfig(this)
        refreshNormalizedConfig()
        plugin.server.pluginManager.registerEvents(JoinLeaveListener(this), plugin)
        plugin.server.pluginManager.registerEvents(CommandWhitelist(this), plugin)
        plugin.server.pluginManager.registerEvents(ChatListener(this, parseTLDS(tldFile)), plugin)
        plugin.server.pluginManager.registerEvents(VanishTabListener(plugin), plugin)
        plugin.getCommand("ignore")?.setExecutor(IgnoreCommand(this))
        plugin.getCommand("msg")?.setExecutor(MessageCommand(this))
        plugin.getCommand("reply")?.setExecutor(ReplyCommand(this))
        plugin.getCommand("togglechat")?.setExecutor(ToggleChatCommand(this))
        plugin.getCommand("unignore")?.setExecutor(UnIgnoreCommand(this))
        plugin.getCommand("ignorelist")?.setExecutor(IgnoreListCommand(this))
        if (!Bukkit.getOnlinePlayers().isEmpty()) Bukkit.getOnlinePlayers().forEach { registerPlayer(it) }
    }

    private fun parseTLDS(tldFile: File): Set<String> {
        return runCatching {
            buildSet {
                BufferedReader(FileReader(tldFile)).use { reader ->
                    reader.lines().filter { !it.startsWith("#") }.forEach { add(it.lowercase()) }
                }
            }
        }.onFailure { t ->
            GlobalUtils.log(Level.WARNING, "&cFailed to parse the TLD file please see the stacktrace below for more info!")
            t.printStackTrace()
        }.getOrDefault(emptySet())
    }

    override fun disable() {
        Bukkit.getOnlinePlayers().forEach { p ->
            val ci = getInfo(p)
            ci?.saveChatInfo()
        }
    }

    override fun reloadConfig() {
        config = plugin.getSectionConfig(this)
        refreshNormalizedConfig()
    }

    private fun refreshNormalizedConfig() {
        blockedWordsLowercase = config?.getStringList("Blocked")?.map(String::lowercase) ?: emptyList()
    }

    override val name: String = "ChatControl"

    fun registerPlayer(player: Player) {
        val info: ChatInfo = chatInfoStore.load(player)
        map[player.uniqueId] = info
        loadAllDataAsync(info)
    }

    fun loadAllDataAsync(info: ChatInfo) {
        val username = info.player.name
        val db = GeneralDatabase.getInstance()

        db.loadPlayerDataCache(username).thenAccept { pd ->
            info.mutedUntil = pd.getLong("muted", 0L)
            info.nickname = pd.getString("displayname")
            info.useVanillaLeaderboard = pd.getBoolean("useVanillaLeaderboard", false)
            info.nameGradient = pd.getString("customGradient")
            info.nameAnimation = pd.getString("gradient_animation")
            info.nameSpeed = pd.getInt("gradient_speed", 5)
            info.nameDecorations = pd.getString("nameDecorations")
            info.hideAnnouncements = pd.getBoolean("hideAnnouncements", false)
            info.hideDeathMessages = pd.getBoolean("hideDeathMessages", false)
            info.hideBadges = pd.getBoolean("hideBadges", false)
            info.preventPhantomSpawn = pd.getBoolean("preventPhantomSpawn", true)
            info.coordinateSpoofing = pd.getBoolean("coordinateSpoofing", false)
            info.menuCloseTpaAccept = pd.getBoolean("menuCloseTpaAccept", true)
            info.menuCloseHelp = pd.getBoolean("menuCloseHelp", true)
            info.menuCloseVote = pd.getBoolean("menuCloseVote", true)
            info.menuCloseUptime = pd.getBoolean("menuCloseUptime", true)
            info.menuCloseDiscord = pd.getBoolean("menuCloseDiscord", true)
            info.menuNoConfirmKill = pd.getBoolean("menuNoConfirmKill", false)
            info.menuHomeColumns = pd.getInt("menuHomeColumns", 4).coerceIn(1, 7)
            info.menuDisableTableColor = pd.getBoolean("menuDisableTableColor", false)
            info.hidePrefix = pd.getBoolean("hidePrefix", false)
            info.selectedRank = pd.getString("selectedRank")
            info.customGradient = pd.getString("prefixGradient")
            info.prefixAnimation = pd.getString("prefix_animation")
            info.prefixSpeed = pd.getInt("prefix_speed", 5)
            info.prefixDecorations = pd.getString("prefixDecorations")
            info.dataLoaded = true
            info.player.scheduler.run(plugin, {
                if (info.player.isOnline) {
                    CoordinateSpoofing.applyLoadedPreference(info.player, info.coordinateSpoofing)
                }
            }, null)
        }.exceptionally { e ->
            e.printStackTrace()
            null
        }
    }

    fun removePlayer(player: Player) {
        val ci = getInfo(player)
        ci?.saveChatInfo()
        map.remove(player.uniqueId)
    }

    fun getInfo(player: Player): ChatInfo? = map.getOrDefault(player.uniqueId, null)
}
