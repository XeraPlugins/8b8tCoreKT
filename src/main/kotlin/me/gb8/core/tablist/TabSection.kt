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
import me.gb8.core.util.GradientAnimator
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
        val cfg = plugin.getSectionConfig(this).also { config = it }
        cfg?.getInt("UpdateInterval", 1)

        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, {
            try {
                tickCount++
                if (tickCount % 4 == 0L) return@runAtFixedRate

val chatSection = cachedChatSection ?: run {
                    val section = plugin.getSectionByName("ChatControl") as? me.gb8.core.chat.ChatSection
                    cachedChatSection = section
                    section
                } ?: return@runAtFixedRate

                chatSection

                val updatePlaceholders = tickCount % 10 == 0L
                val animTick = GradientAnimator.getAnimationTick()

                Bukkit.getOnlinePlayers().forEach { player ->
                    setTab(player, updatePlaceholders, animTick)
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
        val chatSection = cachedChatSection ?: run {
            val section = plugin.getSectionByName("ChatControl") as? me.gb8.core.chat.ChatSection
            cachedChatSection = section
            section
        } ?: return

        val info = chatSection.getInfo(player) ?: return
        if (!info.dataLoaded) return

        if (info.useVanillaLeaderboard) {
            player.apply {
                sendPlayerListHeader(Component.empty())
                sendPlayerListFooter(Component.empty())
                playerListName(null)
            }
            return
        }

        var displayNameComponent = info.getDisplayNameComponent(animTick)

        prefixManager.getPrefix(info, animTick).takeIf { it.isNotEmpty() }?.let { tag ->
            val tagComponent = tagCache.computeIfAbsent(tag) {
                val converted = GlobalUtils.convertToMiniMessageFormat(tag) ?: tag
                MiniMessage.miniMessage().deserialize(converted)
            }
            displayNameComponent = tagComponent.append(displayNameComponent)
        }

        player.playerListName(displayNameComponent)

        if (updatePlaceholders) {
            @Suppress("DEPRECATION")
            val locale = player.locale()
            val lang = locale.language
            val loc = localeCache.computeIfAbsent(lang) { Localization.getLocalization(lang) }

            val header = Utils.parsePlaceHolders(
                loc.getStringList("TabList.Header").joinToString("\n"),
                player,
                plugin.startTime
            )
            val footer = Utils.parsePlaceHolders(
                loc.getStringList("TabList.Footer").joinToString("\n"),
                player,
                plugin.startTime
            )

            FoliaCompat.schedule(player, plugin) {
                if (player.isOnline) {
                    player.sendPlayerListHeader(header)
                    player.sendPlayerListFooter(footer)
                }
            }
        }
    }

    fun setTab(player: Player, updatePlaceholders: Boolean) {
        setTab(player, updatePlaceholders, GradientAnimator.getAnimationTick())
    }

    fun setTab(player: Player) {
        setTab(player, true, GradientAnimator.getAnimationTick())
    }
}