/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.player

import me.gb8.core.Main
import me.gb8.core.database.GeneralDatabase
import me.gb8.core.util.FoliaCompat
import me.gb8.core.util.GradientAnimator
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

class PrefixManager {

    private val database = GeneralDatabase.getInstance()

    fun refreshPrefixDataAsync(info: me.gb8.core.chat.ChatInfo): CompletableFuture<Void> {
        val username = info.player.name

        val hidePrefixFuture = database.getPlayerHidePrefixAsync(username)
        val selectedRankFuture = database.getSelectedRankAsync(username)
        val gradientFuture = database.getPrefixGradientAsync(username)
        val animationFuture = database.getPrefixAnimationAsync(username)
        val speedFuture = database.getPrefixSpeedAsync(username)
        val decorationsFuture = database.getPrefixDecorationsAsync(username)

        return CompletableFuture.allOf(
            hidePrefixFuture,
            selectedRankFuture,
            gradientFuture,
            animationFuture,
            speedFuture,
            decorationsFuture
        ).thenRun {
            info.hidePrefix = hidePrefixFuture.join()
            info.selectedRank = selectedRankFuture.join()
            info.customGradient = gradientFuture.join()
            info.prefixAnimation = animationFuture.join()
            info.prefixSpeed = speedFuture.join()
            info.prefixDecorations = decorationsFuture.join()
        }
    }

    fun getRankDisplayName(permission: String): String {
        return RANK_NAMES[permission] ?: permission.replace("8b8tcore.prefix.", "")
    }

    fun getPrefix(player: Player): String {
        val chatSection = Main.instance.getSectionByName("ChatControl") as? me.gb8.core.chat.ChatSection
        val info = chatSection?.getInfo(player) ?: return ""
        return if (info.dataLoaded) getPrefix(info) else ""
    }

    fun getPrefix(info: me.gb8.core.chat.ChatInfo): String {
        return getPrefix(info, GradientAnimator.getAnimationTick())
    }

    fun getPrefix(info: me.gb8.core.chat.ChatInfo, tick: Long): String {
        if (info.hidePrefix) return ""

        val player = info.player
        val selectedRank = info.selectedRank
        val customGradient = info.customGradient
        val animationType = info.prefixAnimation
        val speed = info.prefixSpeed
        val decorations = info.prefixDecorationList

        var highestPermission = ""
        if (selectedRank != null && (player.hasPermission(selectedRank) || player.isOp)) {
            highestPermission = selectedRank
        } else {
            for (permission in PREFIX_HIERARCHY) {
                if (player.hasPermission(permission) || (player.isOp && permission != "8b8tcore.prefix.custom")) {
                    highestPermission = permission
                    break
                }
            }
            if (highestPermission.isEmpty() || highestPermission == "8b8tcore.prefix.custom") {
                var dynamicPerm = ""
                for (attachment in player.effectivePermissions) {
                    val perm = attachment.permission
                    if (perm.startsWith("8b8tcore.prefix.") && attachment.value && perm != "8b8tcore.prefix.custom") {
                        dynamicPerm = perm
                        break
                    }
                }
                if (dynamicPerm.isNotEmpty()) {
                    highestPermission = dynamicPerm
                } else if (highestPermission.isEmpty() && player.hasPermission("8b8tcore.prefix.custom")) {
                    highestPermission = "8b8tcore.prefix.custom"
                }
            }
        }

        if (highestPermission.isEmpty()) return ""

        val basePrefix = getBasePrefix(highestPermission) ?: return ""

        if (!customGradient.isNullOrEmpty()) {
            var finalGradient = GradientAnimator.applyAnimation(customGradient, animationType, speed, tick)
            if (finalGradient == null) {
                return getDefaultAnimatedPrefix(basePrefix, tick)
            }

            var body = basePrefix
            if (basePrefix.contains("<gradient:")) {
                val firstClose = basePrefix.indexOf('>')
                if (firstClose != -1) body = basePrefix.substring(firstClose + 1).replace("</gradient>", "")
            }

            val result = StringBuilder()
            if (decorations.isNotEmpty()) {
                for (decoration in decorations) result.append("<").append(decoration).append(">")
            }

            var isGradient = finalGradient.contains(":") && finalGradient.indexOf('#') != finalGradient.lastIndexOf('#') && !finalGradient.lowercase().contains("tobias:")
            if (isGradient && finalGradient.indexOf('#') == finalGradient.lastIndexOf('#')) isGradient = false

            if (isGradient) {
                result.append("<gradient:").append(finalGradient).append(">").append(body).append("</gradient>")
            } else {
                val color = finalGradient.split(":")[0]
                if (color.lowercase().contains("tobias")) {
                    result.append(body)
                } else {
                    result.append("<color:").append(color).append(">").append(body).append("</color>")
                }
            }

            if (decorations.isNotEmpty()) {
                for (i in decorations.indices.reversed()) result.append("</").append(decorations[i].trim()).append(">")
            }

            return result.toString().replace("%s", "0.0").replace("%g", "") + " "
        }

        return getDefaultAnimatedPrefix(basePrefix, tick)
    }

    fun getPrefixAsync(player: Player): CompletableFuture<String> {
        val username = player.name

        val hidePrefixFuture = database.getPlayerHidePrefixAsync(username)
        val selectedRankFuture = database.getSelectedRankAsync(username)
        val prefixGradientFuture = database.getPrefixGradientAsync(username)
        val prefixAnimationFuture = database.getPrefixAnimationAsync(username)
        val prefixSpeedFuture = database.getPrefixSpeedAsync(username)
        val prefixDecorationsFuture = database.getPrefixDecorationsAsync(username)

        return CompletableFuture.allOf(
            hidePrefixFuture, selectedRankFuture, prefixGradientFuture,
            prefixAnimationFuture, prefixSpeedFuture, prefixDecorationsFuture
        ).thenCompose {
            onPlayerThread(player) prefix@{
            val hidePrefix = hidePrefixFuture.join()
            if (hidePrefix) return@prefix ""

            val selectedRank = selectedRankFuture.join()
            val customGradient = prefixGradientFuture.join()
            val animationType = prefixAnimationFuture.join()
            val speed = prefixSpeedFuture.join()
            val decorationsStr = prefixDecorationsFuture.join()
            val decorations = if (decorationsStr.isNullOrEmpty()) {
                emptyList()
            } else {
                decorationsStr.split(',').map(String::trim)
            }

            var highestPermission = ""
            if (selectedRank != null && (player.hasPermission(selectedRank) || player.isOp)) {
                highestPermission = selectedRank
            } else {
                for (permission in PREFIX_HIERARCHY) {
                    if (player.hasPermission(permission) || (player.isOp && permission != "8b8tcore.prefix.custom")) {
                        highestPermission = permission
                        break
                    }
                }
                if (highestPermission.isEmpty() || highestPermission == "8b8tcore.prefix.custom") {
                    var dynamicPerm = ""
                    for (attachment in player.effectivePermissions) {
                        val perm = attachment.permission
                        if (perm.startsWith("8b8tcore.prefix.") && attachment.value && perm != "8b8tcore.prefix.custom") {
                            dynamicPerm = perm
                            break
                        }
                    }
                    if (dynamicPerm.isNotEmpty()) {
                        highestPermission = dynamicPerm
                    } else if (highestPermission.isEmpty() && player.hasPermission("8b8tcore.prefix.custom")) {
                        highestPermission = "8b8tcore.prefix.custom"
                    }
                }
            }

            if (highestPermission.isEmpty()) return@prefix ""

            val basePrefix = getBasePrefix(highestPermission) ?: return@prefix ""
            val tick = GradientAnimator.getAnimationTick()

            if (!customGradient.isNullOrEmpty()) {
                var finalGradient = GradientAnimator.applyAnimation(customGradient, animationType, speed, tick)
                if (finalGradient == null) {
                    return@prefix getDefaultAnimatedPrefix(basePrefix, tick)
                }

                var body = basePrefix
                if (basePrefix.contains("<gradient:")) {
                    val firstClose = basePrefix.indexOf('>')
                    if (firstClose != -1) body = basePrefix.substring(firstClose + 1).replace("</gradient>", "")
                }

                val result = StringBuilder()
                if (decorations.isNotEmpty()) {
                    for (decoration in decorations) result.append("<").append(decoration).append(">")
                }

                var isGradient = finalGradient.contains(":") && finalGradient.indexOf('#') != finalGradient.lastIndexOf('#') && !finalGradient.lowercase().contains("tobias:")
                if (isGradient && finalGradient.indexOf('#') == finalGradient.lastIndexOf('#')) isGradient = false

                if (isGradient) {
                    result.append("<gradient:").append(finalGradient).append(">").append(body).append("</gradient>")
                } else {
                    val color = finalGradient.split(":")[0]
                    if (color.lowercase().contains("tobias")) {
                        result.append(body)
                    } else {
                        result.append("<color:").append(color).append(">").append(body).append("</color>")
                    }
                }

                if (decorations.isNotEmpty()) {
                    for (i in decorations.indices.reversed()) result.append("</").append(decorations[i].trim()).append(">")
                }

                return@prefix result.toString().replace("%s", "0.0").replace("%g", "") + " "
            }

            getDefaultAnimatedPrefix(basePrefix, tick)
            }
        }
    }

    private fun <T> onPlayerThread(player: Player, action: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        FoliaCompat.schedule(player, Main.instance) {
            if (!player.isOnline) {
                future.completeExceptionally(IllegalStateException("Player is no longer online"))
                return@schedule
            }
            runCatching(action)
                .onSuccess(future::complete)
                .onFailure(future::completeExceptionally)
        }
        return future
    }

    fun getAvailableRanks(player: Player): List<String> {
        val available = mutableListOf<String>()
        val isOp = player.isOp
        for (permission in PREFIX_HIERARCHY) {
            if (isOp || player.hasPermission(permission)) available.add(permission)
        }
        return available
    }

    fun hasRank(player: Player): Boolean {
        return hasRankPermission(player)
    }

    private fun getBasePrefix(permission: String): String? {
        PREFIXES[permission]?.let { return it }
        val name = permission.replace("8b8tcore.prefix.", "")
        if (name.isEmpty()) return null
        val formattedName = name.first().uppercase() + name.drop(1)
        return "<gradient:%%g:%%s>[%s]</gradient>".format(formattedName)
    }

    private fun getDefaultAnimatedPrefix(basePrefix: String, tick: Long): String {
        val phaseStr = getDefaultPhaseString(tick)
        return basePrefix.replace("%s", phaseStr).replace("%g", "#FFFFFF:#AAAAAA:#FFFFFF") + " "
    }

    private fun getDefaultPhaseString(tick: Long): String {
        cachedDefaultPhase.takeIf { it.tick == tick }?.let { return it.value }
        return calculateAndCacheDefaultPhase(tick)
    }

    private fun calculateAndCacheDefaultPhase(tick: Long): String {
        return synchronized(DEFAULT_PHASE_LOCK) {
            cachedDefaultPhase.takeIf { it.tick == tick }?.let { return@synchronized it.value }
            var t = (tick * 0.05) % 2.0
            if (t > 1.0) t = 2.0 - t
            val phase = t * t * (3 - 2 * t)
            val value = "%.2f".format(phase)
            cachedDefaultPhase = CachedPhase(tick, value)
            value
        }
    }

    companion object {
        fun hasRankPermission(player: Player): Boolean {
            if (player.isOp) return true
            return PREFIX_HIERARCHY.any(player::hasPermission)
        }

        @Volatile private var cachedDefaultPhase = CachedPhase(Long.MIN_VALUE, "0.00")
        private val DEFAULT_PHASE_LOCK = Any()
        private data class CachedPhase(val tick: Long, val value: String)
        private val PREFIXES = mutableMapOf(
            "8b8tcore.prefix.owner" to "<gradient:#a860ff:#743ad5:#d0a2ff:%s>[OWNER<green>✔</green>]</gradient>",
            "8b8tcore.prefix.dev" to "<gradient:#00d2ff:#3a7bd5:#00d2ff:%s>[DEV<green>✔</green>]</gradient>",
            "8b8tcore.prefix.bot" to "<gradient:#11998e:#38ef7d:#11998e:%s>[BOT]</gradient>",
            "8b8tcore.prefix.youtuber" to "<gradient:#cb2d3e:#ef473a:#cb2d3e:%s>[Youtuber]</gradient>",
            "8b8tcore.prefix.thetroll2001" to "<gradient:#FF0000:#FF7F00:#FFFF00:#00FF00:#0000FF:#4B0082:#8F00FF:%s>[Troll]</gradient>",
            "8b8tcore.prefix.qtdonkey" to "<gradient:#f8ff00:#3ad59f:%s>[Television]</gradient>",
            "8b8tcore.prefix.orasan080" to "<gradient:#9bc4e2:#e9eff5:#9bc4e2:%s>[lurker]</gradient>",
            "8b8tcore.prefix.lucky2007" to "<gradient:#1f4037:#99f2c8:%s>[Addict]</gradient>",
            "8b8tcore.prefix.xmas2025" to "<gradient:#FF0000:#FFFFFF:#32CD32:%s>[XMAS2025]</gradient>",
            "8b8tcore.prefix.donator6" to "<gradient:#8e2de2:#4a00e0:#8e2de2:%s>[Ultra]</gradient>",
            "8b8tcore.prefix.donator5" to "<gradient:#f2994a:#f2c94c:#f2994a:%s>[Pro+]</gradient>",
            "8b8tcore.prefix.donator4" to "<gradient:#ee9ca7:#ffdde1:#ee9ca7:%s>[Pro]</gradient>",
            "8b8tcore.prefix.donator3" to "<gradient:#bdc3c7:#2c3e50:#bdc3c7:%s>[Mini]</gradient>",
            "8b8tcore.prefix.donator2" to "<gradient:#FFA500:#FFD700:#FFFF00:%s>[SE]</gradient>",
            "8b8tcore.prefix.donator1" to "<gradient:#434343:#000000:#434343:%s>[Basic]</gradient>",
            "8b8tcore.prefix.custom" to "<gradient:%g:%s>[Custom]</gradient>"
        )

        private val RANK_NAMES = mapOf(
            "8b8tcore.prefix.owner" to "Owner",
            "8b8tcore.prefix.dev" to "Dev",
            "8b8tcore.prefix.bot" to "Bot",
            "8b8tcore.prefix.youtuber" to "Youtuber",
            "8b8tcore.prefix.thetroll2001" to "Troll",
            "8b8tcore.prefix.qtdonkey" to "Television",
            "8b8tcore.prefix.orasan080" to "lurker",
            "8b8tcore.prefix.lucky2007" to "Addict",
            "8b8tcore.prefix.xmas2025" to "XMAS2025",
            "8b8tcore.prefix.donator6" to "Ultra",
            "8b8tcore.prefix.donator5" to "Pro+",
            "8b8tcore.prefix.donator4" to "Pro",
            "8b8tcore.prefix.donator3" to "Mini",
            "8b8tcore.prefix.donator2" to "SE",
            "8b8tcore.prefix.donator1" to "Basic",
            "8b8tcore.prefix.custom" to "Custom"
        )

        private val PREFIX_HIERARCHY = listOf(
            "8b8tcore.prefix.owner",
            "8b8tcore.prefix.dev",
            "8b8tcore.prefix.bot",
            "8b8tcore.prefix.youtuber",
            "8b8tcore.prefix.thetroll2001",
            "8b8tcore.prefix.qtdonkey",
            "8b8tcore.prefix.orasan080",
            "8b8tcore.prefix.lucky2007",
            "8b8tcore.prefix.xmas2025",
            "8b8tcore.prefix.donator6",
            "8b8tcore.prefix.donator5",
            "8b8tcore.prefix.donator4",
            "8b8tcore.prefix.donator3",
            "8b8tcore.prefix.donator2",
            "8b8tcore.prefix.donator1",
            "8b8tcore.prefix.custom"
        )
    }
}
