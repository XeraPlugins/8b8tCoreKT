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

        var tps: Double
        try {
            val regionTpsArr = GlobalUtils.getTpsNearEntitySync(player)
            tps = if (regionTpsArr.isNotEmpty()) regionTpsArr[0] else 20.0
        } catch (t: Throwable) {
            tps = 20.0
        }

        val tpsColor = if (tps >= 18.0) "<green>" else if (tps >= 13.0) "<yellow>" else "<red>"
        val tpsStr = if (tps >= 20.0) "20.00" else String.format("%.2f", tps)

        val mspt = GlobalUtils.getCurrentRegionMspt()
        var msptAdjusted = mspt
        if (msptAdjusted <= 0.0 && tps > 0) msptAdjusted = 1000.0 / minOf(tps, 20.0)
        val msptColor = if (msptAdjusted < 60) "<green>" else if (msptAdjusted <= 100) "<yellow>" else "<red>"
        val msptStr = String.format("%.1f", msptAdjusted)

        val uptime = getFormattedInterval(System.currentTimeMillis() - startTime)

        val result = input
            .replace("%tps%", "$tpsColor$tpsStr")
            .replace("%mspt%", "$msptColor$msptStr")
            .replace("%players%", Bukkit.getOnlinePlayers().size.toString())
            .replace("%ping%", player.ping.toString())
            .replace("%uptime%", uptime)

        return GlobalUtils.translateChars(result)
    }

    fun getFormattedInterval(ms: Long): String {
        val seconds = ms / 1000L % 60L
        val minutes = ms / 60000L % 60L
        val hours = ms / 3600000L % 24L
        val days = ms / 86400000L
        return String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds)
    }
}
