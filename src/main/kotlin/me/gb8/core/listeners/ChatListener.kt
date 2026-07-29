/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import io.papermc.paper.event.player.AsyncChatEvent
import me.gb8.core.Main
import me.gb8.core.chat.ChatInfo
import me.gb8.core.chat.ChatSection
import me.gb8.core.player.PrefixManager
import me.gb8.core.util.FoliaCompat
import me.gb8.core.util.GlobalUtils
import me.gb8.core.util.GlobalUtils.getStringContent
import me.gb8.core.util.GlobalUtils.log
import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Statistic
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import kotlin.math.abs

class ChatListener(private val manager: ChatSection, private val tlds: Set<String>) : Listener {
    private val prefixManager = PrefixManager()
    private val miniMessage = MiniMessage.miniMessage()
    private val playerMessages = ConcurrentHashMap<UUID, MutableList<String>>()
    private val prefixCache = ConcurrentHashMap<String, Component>()

    @EventHandler
    fun onChat(event: AsyncChatEvent) {
        event.isCancelled = true

        val sender = event.player
        val message = GlobalUtils.getStringContent(event.message())
        FoliaCompat.schedule(sender, manager.plugin) {
            if (sender.isOnline) processChat(sender, message)
        }
    }

    private fun processChat(sender: Player, ogMessage: String) {
        val senderUUID = sender.uniqueId
        val senderName = sender.name
        val ci = manager.getInfo(sender) ?: return

        val cooldown = manager.config?.getInt("Cooldown") ?: 5
        if (ci.chatLock && !sender.isOp && !sender.hasPermission("*")) {
            sendPrefixedLocalizedMessage(sender, "chat_cooldown", cooldown)
            return
        }

        ci.chatLock = true
        Main.executorService.schedule({ ci.chatLock = false }, cooldown.toLong(), TimeUnit.SECONDS)

        val filteredMessage = filterLongWords(ogMessage)

        val messages = playerMessages.computeIfAbsent(senderUUID) { mutableListOf() }
        synchronized(messages) {
            if (!sender.isOp && !sender.hasPermission("*")) {
                for (oldMessage in messages) {
                    if (ogMessage.length < 20) continue
                    if (isSimilar(filterLongWords(oldMessage), filteredMessage)) {
                        sendPrefixedLocalizedMessage(sender, "spam_alert")
                        return
                    }
                }
            }
            if (messages.size >= MESSAGE_HISTORY) messages.removeAt(0)
            messages.add(ogMessage)
        }
        val messageWords = tokenize(ogMessage)

        val blocked = blockedCheck(ogMessage)
        val domain = domainCheck(ogMessage)
        val muted = ci.mutedUntil > Instant.now().epochSecond
        if (blocked || muted || domain) {
            val senderComp = getSenderComponent(sender, ci)
            sender.sendMessage(senderComp.append(formatBody(messageWords, sender.name, setOf(sender.name.lowercase()))))
            if (!blocked && !domain) return
            log(Level.INFO, "&3Prevented&r&a %s&r&3 from sending a message (banned words/link)", senderName)
            return
        }

        val senderComponent = getSenderComponent(sender, ci)

        Bukkit.getLogger().info("$senderName: $ogMessage")

        Bukkit.getGlobalRegionScheduler().run(manager.plugin) {
            val onlineNames = Bukkit.getOnlinePlayers().mapTo(mutableSetOf()) { it.name.lowercase() }

            for (recipient in Bukkit.getOnlinePlayers()) {
                FoliaCompat.schedule(recipient, manager.plugin) {
                    if (!recipient.isOnline) return@schedule
                    val recipientInfo = manager.getInfo(recipient)
                    if (recipientInfo == null || recipientInfo.isIgnoring(senderUUID) || recipientInfo.isToggledChat) {
                        return@schedule
                    }
                    val body = formatBody(messageWords, recipient.name, onlineNames)
                    recipient.sendMessage(senderComponent.append(body))
                }
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        playerMessages.remove(event.player.uniqueId)
    }

    private fun isSimilar(m1: String, m2: String): Boolean {
        val l1 = m1.length
        val l2 = m2.length
        if (abs(l1 - l2) > maxOf(l1, l2) * (1.0 - SIMILARITY_THRESHOLD)) return false
        return similarityPercentage(m1, m2) >= SIMILARITY_THRESHOLD
    }

    private fun messageColor(message: String): TextColor {
        return if (message.startsWith(">")) NamedTextColor.GREEN else NamedTextColor.WHITE
    }

    private fun tokenize(message: String): MessageWords = MessageWords(
        messageColor(message),
        message.split(" ").map { word -> MessageWord(word, word.lowercase()) }
    )

    private fun formatBody(message: MessageWords, recipientName: String, onlineNames: Set<String>): Component {
        val baseColor = message.color
        val recipientNameLowercase = recipientName.lowercase()
        var body = Component.empty()
        val words = message.words
        for (i in words.indices) {
            val word = words[i]
            var color = baseColor

            if (word.lowercase == recipientNameLowercase || word.lowercase in KEYWORDS) {
                color = NamedTextColor.YELLOW
            } else if (word.lowercase in onlineNames) {
                color = baseColor
            }

            body = body.append(Component.text(word.original + if (i == words.size - 1) "" else " ").color(color))
        }
        return body
    }

    private fun domainCheck(message: String): Boolean {
        val config = manager.config ?: return false
        if (!config.getBoolean("PreventLinks")) return false
        var msg = message.lowercase().replace("dot", ".").replace("d0t", ".")
        if (msg.indexOf('.') == -1) return false
        val split = msg.trim().split("\\.".toRegex())
        if (split.size == 2) {
            var possibleTLD = split[1]
            if (possibleTLD.contains("/")) possibleTLD = possibleTLD.substring(0, possibleTLD.indexOf("/"))
            return possibleTLD in tlds
        } else {
            for (word in split) {
                if (word.contains("/")) {
                    if (word.substring(0, word.indexOf("/")) in tlds) return true
                }
                if (word in tlds) return true
            }
        }
        return false
    }

    private fun blockedCheck(message: String): Boolean {
        val messageLowercase = message.lowercase()
        for (blockedWord in manager.blockedWordsLowercase) {
            if (messageLowercase.contains(blockedWord)) return true
        }
        return false
    }

    private fun similarityPercentage(str1: String, str2: String): Double {
        val maxLength = maxOf(str1.length, str2.length)
        if (maxLength == 0) return 1.0

        val distance = levenshteinDistance(str1, str2)
        return 1.0 - (distance.toDouble() / maxLength)
    }

    companion object {
        fun filterLongWords(message: String): String {
            val words = message.split("\\s+".toRegex())
            val result = StringBuilder()

            for (word in words) {
                if (word.length <= 12) {
                    result.append(word).append(" ")
                }
            }

            return result.toString().trim()
        }

        private const val SIMILARITY_THRESHOLD = 0.8
        private const val MESSAGE_HISTORY = 3
        private val KEYWORDS = setOf("@here", "@everyone", "here", "everyone")
    }

    private data class MessageWord(val original: String, val lowercase: String)
    private data class MessageWords(val color: TextColor, val words: List<MessageWord>)

    private fun levenshteinDistance(str1: String, str2: String): Int {
        var n = str1.length
        var m = str2.length
        if (n < m) return levenshteinDistance(str2, str1)
        if (m == 0) return n
        if (m <= 64) return myersDistance(str1, str2)

        val curr = IntArray(m + 1)
        for (j in 0..m) curr[j] = j

        for (i in 1..n) {
            var prev = curr[0]
            curr[0] = i
            for (j in 1..m) {
                val temp = curr[j]
                curr[j] = if (str1[i - 1] == str2[j - 1]) prev else minOf(minOf(curr[j - 1], curr[j]), prev) + 1
                prev = temp
            }
        }
        return curr[m]
    }

    private fun myersDistance(str1: String, str2: String): Int {
        val n = str1.length
        val m = str2.length
        val peq = LongArray(256)
        for (i in 0 until m) peq[str2[i].code and 0xFF] = peq[str2[i].code and 0xFF] or (1L shl i)
        var pv = -1L
        var mv: Long = 0
        var dist = m
        for (i in 0 until n) {
            val eq = peq[str1[i].code and 0xFF]
            val xv = eq or mv
            val xh = ((eq and pv) + pv).xor(pv) or eq
            var ph = mv or (xh xor pv).inv()
            var mh = pv and xh
            if ((ph and (1L shl (m - 1))) != 0L) dist++
            if ((mh and (1L shl (m - 1))) != 0L) dist--
            ph = (ph shl 1) or 1L
            mh = mh shl 1
            pv = mh or (ph or xv).inv()
            mv = ph and xv
        }
        return dist
    }

    
    fun getSenderComponent(player: Player, ci: ChatInfo): Component {
        val hoverText = Component.text()
            .append(player.displayName())
            .append(Component.text("\n\n").color(NamedTextColor.GRAY))
            .append(Component.text("Lang: ").color(TextColor.fromHexString("#FFD700")))
            .append(Component.text(player.locale().toString() + "\n").color(NamedTextColor.LIGHT_PURPLE))
            .append(Component.text("Experience: ").color(TextColor.fromHexString("#FFD700")))
            .append(Component.text(player.level.toString() + "\n").color(NamedTextColor.GREEN))
            .append(Component.text("Distance Walked: ").color(TextColor.fromHexString("#FFD700")))
            .append(Component.text("%.2f km\n".format((player.getStatistic(Statistic.WALK_ONE_CM) + player.getStatistic(Statistic.SPRINT_ONE_CM)) / 100000.0)).color(NamedTextColor.BLUE))
            .append(Component.text("Player Kills: ").color(TextColor.fromHexString("#FFD700")))
            .append(Component.text(player.getStatistic(Statistic.PLAYER_KILLS).toString() + "\n").color(NamedTextColor.RED))
            .append(Component.text("Player Deaths: ").color(TextColor.fromHexString("#FFD700")))
            .append(Component.text(player.getStatistic(Statistic.DEATHS).toString() + "\n").color(NamedTextColor.RED))
            .append(Component.text("Time Played: ").color(TextColor.fromHexString("#FFD700")))
            .append(Component.text("%.2f hours\n".format(player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20.0 / 3600.0)).color(NamedTextColor.YELLOW))
            .append(Component.text("\n(Click to send a direct message)").color(NamedTextColor.GRAY))
            .build()

        val prefixComponent = prefixCache.computeIfAbsent(prefixManager.getPrefix(ci)) { miniMessage.deserialize(it) }
        return prefixComponent
            .append(Component.text("<").color(TextColor.color(170, 170, 170)))
            .append(player.displayName().hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hoverText)).clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand("/msg " + player.name + " ")))
            .append(Component.text("> ").color(TextColor.color(170, 170, 170)))
    }
}
