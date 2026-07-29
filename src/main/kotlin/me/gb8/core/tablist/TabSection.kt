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
import java.util.concurrent.ConcurrentLinkedQueue

class TabSection(override val plugin: Main) : Section {
    val main: Main get() = plugin
    private var config: ConfigurationSection? = null
    private val prefixManager = PrefixManager()
    private var tickCount = 0L

    private var cachedChatSection: me.gb8.core.chat.ChatSection? = null
    private val tagCache = ConcurrentHashMap<String, Component>()
    private val tagCacheOrder = ConcurrentLinkedQueue<CachedTag>()
    private val localeCache = ConcurrentHashMap<String, Localization>()
    private val templateCache = ConcurrentHashMap<String, TabTemplates>()

    override fun enable() {
        val cfg = plugin.getSectionConfig(this).also { config = it }
        val updateInterval = (cfg?.getLong("UpdateInterval", 1L) ?: 1L).coerceAtLeast(1L)

        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, {
            try {
                tickCount++
                if (tickCount % 4 == 0L) return@runAtFixedRate

                cachedChatSection ?: run {
                    val section = plugin.getSectionByName("ChatControl") as? me.gb8.core.chat.ChatSection
                    cachedChatSection = section
                    section
                } ?: return@runAtFixedRate

                val updatePlaceholders = tickCount % 10 == 0L
                val animTick = GradientAnimator.getAnimationTick()

                Bukkit.getOnlinePlayers().forEach { player ->
                    FoliaCompat.schedule(player, plugin) {
                        if (player.isOnline) setTab(player, updatePlaceholders, animTick)
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
                plugin.logger.warning("Error in TabList update task: ${t.message}")
            }
        }, updateInterval, updateInterval)
        plugin.register(TablistPlayerJoinListener(this))
    }

    override fun disable() {}

    override fun reloadConfig() {
        config = plugin.getSectionConfig(this)
        tagCache.clear()
        tagCacheOrder.clear()
        localeCache.clear()
        templateCache.clear()
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
            info.lastSentTabName = null
            return
        }

        var displayNameComponent = info.getDisplayNameComponent(animTick)

        prefixManager.getPrefix(info, animTick).takeIf { it.isNotEmpty() }?.let { tag ->
            val tagComponent = getCachedTag(tag)
            displayNameComponent = tagComponent.append(displayNameComponent)
        }

        if (displayNameComponent != info.lastSentTabName) {
            player.playerListName(displayNameComponent)
            info.lastSentTabName = displayNameComponent
        }

        if (updatePlaceholders) {
            @Suppress("DEPRECATION")
            val locale = player.locale()
            val lang = locale.language
            val loc = localeCache.computeIfAbsent(lang) { Localization.getLocalization(lang) }
            val templates = templateCache.computeIfAbsent(lang) {
                TabTemplates(
                    loc.getStringList("TabList.Header").joinToString("\n"),
                    loc.getStringList("TabList.Footer").joinToString("\n")
                )
            }
            val placeholderContext = Utils.createPlaceholderContext(player, plugin.startTime)

            val header = Utils.parsePlaceHolders(
                templates.header,
                placeholderContext
            )
            val footer = Utils.parsePlaceHolders(
                templates.footer,
                placeholderContext
            )

            player.sendPlayerListHeaderAndFooter(header, footer)
        }
    }

    fun setTab(player: Player, updatePlaceholders: Boolean) {
        setTab(player, updatePlaceholders, GradientAnimator.getAnimationTick())
    }

    fun setTab(player: Player) {
        setTab(player, true, GradientAnimator.getAnimationTick())
    }

    private fun getCachedTag(tag: String): Component {
        tagCache[tag]?.let { return it }

        val converted = GlobalUtils.convertToMiniMessageFormat(tag) ?: tag
        val parsed = MiniMessage.miniMessage().deserialize(converted)
        val existing = tagCache.putIfAbsent(tag, parsed)
        if (existing != null) return existing

        tagCacheOrder.offer(CachedTag(tag, parsed))
        while (tagCache.size > MAX_TAG_CACHE_SIZE) {
            val oldest = tagCacheOrder.poll() ?: break
            tagCache.remove(oldest.key, oldest.component)
        }
        return parsed
    }

    private data class TabTemplates(val header: String, val footer: String)
    private data class CachedTag(val key: String, val component: Component)

    private companion object {
        const val MAX_TAG_CACHE_SIZE = 512
    }
}
