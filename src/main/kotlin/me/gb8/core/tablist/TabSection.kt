/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.tablist

import me.gb8.core.Localization
import me.gb8.core.Main
import me.gb8.core.Section
import me.gb8.core.player.PrefixManager
import me.gb8.core.listeners.TablistPlayerJoinListener
import me.gb8.core.tablist.Utils
import me.gb8.core.util.GlobalUtils
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.Component
import me.gb8.core.util.FoliaCompat
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

class TabSection(override val plugin: Main) : Section {
    val main: Main get() = plugin
    private var config: ConfigurationSection? = null
    private val prefixManager = PrefixManager()
    private var tickCount = 0L

    private var cachedChatSection: me.gb8.core.chat.ChatSection? = null
    private val tagCache = ConcurrentHashMap<String, Component>()
    private val localeCache = ConcurrentHashMap<String, Localization>()

    override fun enable() {
        val cfg = plugin.getSectionConfig(this)
        config = cfg
        cfg?.getInt("UpdateInterval", 1)

        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, {
            try {
                tickCount++
                if (tickCount % 4 == 0L) return@runAtFixedRate

                if (cachedChatSection == null) {
                    cachedChatSection = plugin.getSectionByName("ChatControl") as? me.gb8.core.chat.ChatSection
                    if (cachedChatSection == null) return@runAtFixedRate
                }

                val updatePlaceholders = (tickCount % 10 == 0L)
                val animTick = me.gb8.core.util.GradientAnimator.getAnimationTick()

                for (p in Bukkit.getOnlinePlayers()) {
                    setTab(p, updatePlaceholders, animTick)
                }
            } catch (t: Throwable) {
                t.printStackTrace()
                plugin.logger.warning("Error in TabList update task: ${t.message}")
            }
        }, 1L, 1L)
        plugin.register(TablistPlayerJoinListener(this))
    }

    override fun disable() {}

    override fun reloadConfig() {
        config = plugin.getSectionConfig(this)
        tagCache.clear()
        localeCache.clear()
    }

    override val name: String = "TabList"


    fun setTab(player: Player, updatePlaceholders: Boolean, animTick: Long) {
        val chatSection = cachedChatSection ?: plugin.getSectionByName("ChatControl") as? me.gb8.core.chat.ChatSection ?: return
        val info = chatSection.getInfo(player) ?: return
        if (!info.dataLoaded) return

        if (info.useVanillaLeaderboard) {
            player.sendPlayerListHeader(Component.empty())
            player.sendPlayerListFooter(Component.empty())
            player.playerListName(null)
            return
        }

        var displayNameComponent = info.getDisplayNameComponent(animTick)

        val tag = prefixManager.getPrefix(info, animTick)
        if (tag.isNotEmpty()) {
            val tagComponent = tagCache.getOrPut(tag) {
                val converted = GlobalUtils.convertToMiniMessageFormat(tag) ?: tag
                MiniMessage.miniMessage().deserialize(converted)
            }
            displayNameComponent = tagComponent.append(displayNameComponent)
        }

        player.playerListName(displayNameComponent)

        if (updatePlaceholders) {
            @Suppress("DEPRECATION")
            val locale = player.locale()
            val lang: String = locale.language
            val loc = localeCache.getOrPut(lang) { Localization.getLocalization(lang) }
            val header = Utils.parsePlaceHolders(loc.getStringList("TabList.Header").joinToString("\n"), player, plugin.startTime)
            val footer = Utils.parsePlaceHolders(loc.getStringList("TabList.Footer").joinToString("\n"), player, plugin.startTime)
            FoliaCompat.schedule(player, plugin) {
                if (player.isOnline) {
                    player.sendPlayerListHeader(header)
                    player.sendPlayerListFooter(footer)
                }
            }
        }
    }

    fun setTab(player: Player, updatePlaceholders: Boolean) {
        setTab(player, updatePlaceholders, me.gb8.core.util.GradientAnimator.getAnimationTick())
    }

    fun setTab(player: Player) {
        setTab(player, true, me.gb8.core.util.GradientAnimator.getAnimationTick())
    }
}
