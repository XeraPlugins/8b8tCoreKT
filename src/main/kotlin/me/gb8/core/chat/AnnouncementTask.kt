/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.chat

import me.gb8.core.Main
import me.gb8.core.chat.ChatInfo
import me.gb8.core.chat.ChatSection
import me.gb8.core.util.FoliaCompat
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.concurrent.ThreadLocalRandom

class AnnouncementTask : Runnable {

    private val random = ThreadLocalRandom.current()

    override fun run() {
        val plugin = Main.instance
        val chatSection = plugin.getSectionByName("ChatControl") as? ChatSection ?: return

        for (p in Bukkit.getOnlinePlayers()) {
            val info = chatSection.getInfo(p)
            if (info == null || info.hideAnnouncements) continue

            FoliaCompat.schedule(p, plugin) {
                if (!p.isOnline) return@schedule
                val loc = me.gb8.core.Localization.getLocalization(p.locale().toLanguageTag())
                val announcements = loc.getComponentList("announcements")
                if (announcements.isEmpty()) return@schedule
                val announcement = announcements[random.nextInt(announcements.size)]
                p.sendMessage(announcement)
            }
        }
    }
}
