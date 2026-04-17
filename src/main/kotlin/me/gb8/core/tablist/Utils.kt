/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.tablist

import me.gb8.core.util.GlobalUtils
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object Utils {

    fun parsePlaceHolders(input: String, player: Player, startTime: Long): Component {
        if (input.isEmpty()) return Component.empty()

        val tps = runCatching {
            val regionTpsArr = GlobalUtils.getTpsNearEntitySync(player)
            regionTpsArr.firstOrNull() ?: 20.0
        }.getOrDefault(20.0)

        val mspt = runCatching { GlobalUtils.getCurrentRegionMspt() }.getOrDefault(0.0)
        val msptAdjusted = if (mspt <= 0.0 && tps > 0) 1000.0 / minOf(tps, 20.0) else mspt

        val placeholders = mapOf(
            "%tps%" to "${getTpsColor(tps)}${getTpsString(tps)}",
            "%mspt%" to "${getMsptColor(msptAdjusted)}${getMsptString(msptAdjusted)}",
            "%players%" to Bukkit.getOnlinePlayers().size.toString(),
            "%ping%" to player.ping.toString(),
            "%uptime%" to getFormattedInterval(System.currentTimeMillis() - startTime)
        )

        var result = input
        for ((placeholder, value) in placeholders) {
            result = result.replace(placeholder, value)
        }

        return GlobalUtils.translateChars(result)
    }

    private fun getTpsColor(tps: Double): String = when {
        tps >= 18.0 -> "<green>"
        tps >= 13.0 -> "<yellow>"
        else -> "<red>"
    }

    private fun getTpsString(tps: Double): String =
        if (tps >= 20.0) "20.00" else "%.2f".format(tps)

    private fun getMsptColor(mspt: Double): String = when {
        mspt < 60 -> "<green>"
        mspt <= 100 -> "<yellow>"
        else -> "<red>"
    }

    private fun getMsptString(mspt: Double): String = "%.1f".format(mspt)

    fun getFormattedInterval(ms: Long): String {
        val seconds = ms / 1000L % 60L
        val minutes = ms / 60000L % 60L
        val hours = ms / 3600000L % 24L
        val days = ms / 86400000L
        return String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds)
    }
}