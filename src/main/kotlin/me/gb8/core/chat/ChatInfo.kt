/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.chat

import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ChatInfo @JvmOverloads constructor(
    val player: Player,
    val manager: ChatSection,
    ignoring: Set<UUID> = emptySet(),
    toggledChat: Boolean = false,
    joinMessages: Boolean = false
) {
    val ignoring: MutableSet<UUID> = ConcurrentHashMap.newKeySet<UUID>().apply { addAll(ignoring) }
    var replyTarget: Player? = null
    @Volatile var toggledChat: Boolean = toggledChat
    @Volatile var joinMessages: Boolean = joinMessages
    @Volatile var chatLock: Boolean = false
    @Volatile var mutedUntil: Long = 0L
    @Volatile var hidePrefix: Boolean = false
    var selectedRank: String? = null
    var customGradient: String? = null
    var prefixAnimation: String? = null
    var prefixSpeed: Int = 0
    var prefixDecorations: String? = null
    var nickname: String? = null
    var useVanillaLeaderboard: Boolean = false
    var nameGradient: String? = null
    var nameAnimation: String? = null
    var nameSpeed: Int = 0
    var nameDecorations: String? = null
    var hideAnnouncements: Boolean = false
    @Volatile var dataLoaded: Boolean = false

    fun isIgnoring(`player`: UUID): Boolean = ignoring.contains(`player`)

    fun ignorePlayer(`player`: UUID) {
        ignoring.add(`player`)
    }

    fun unignorePlayer(`player`: UUID) {
        ignoring.remove(`player`)
    }

    fun getIgnoringSet(): Set<UUID> = ignoring

    fun isToggledChat(): Boolean = toggledChat
    fun isJoinMessages(): Boolean = joinMessages

    fun shouldNotSave(): Boolean = ignoring.isEmpty() && !toggledChat && !joinMessages

    fun saveChatInfo() {
        manager.chatInfoStore.save(this, player)
    }

    @Volatile var cachedDisplayName: net.kyori.adventure.text.Component? = null
    @Volatile private var lastAnimTick: Long = -1
    private val animatedNameCache = ConcurrentHashMap<String, net.kyori.adventure.text.Component>()
    @Volatile private var lastCacheKey: String? = null

    fun getDisplayNameComponent(): net.kyori.adventure.text.Component {
        return getDisplayNameComponent(me.gb8.core.util.GradientAnimator.getAnimationTick())
    }

    @Synchronized
    fun getDisplayNameComponent(animTick: Long): net.kyori.adventure.text.Component {
        val cached = cachedDisplayName
        if (animTick == lastAnimTick && cached != null) return cached

        val currentGradient = me.gb8.core.util.GradientAnimator.applyAnimation(nameGradient, nameAnimation, nameSpeed, animTick)
        val cacheKey = "$nickname|$currentGradient|$nameDecorations"

        if (cacheKey == lastCacheKey && cached != null) {
            lastAnimTick = animTick
            return cached
        }

        var displayName = animatedNameCache[cacheKey]
        if (displayName == null) {
            displayName = me.gb8.core.util.GlobalUtils.parseDisplayName(player.name, nickname, nameGradient, nameAnimation, nameSpeed, nameDecorations, animTick)
            if (animatedNameCache.size > 120) animatedNameCache.clear()
            animatedNameCache[cacheKey] = displayName
        }

        cachedDisplayName = displayName
        lastCacheKey = cacheKey
        lastAnimTick = animTick
        return displayName
    }

    @Synchronized
    fun clearAnimatedNameCache() {
        animatedNameCache.clear()
        cachedDisplayName = null
        lastCacheKey = null
    }
}
