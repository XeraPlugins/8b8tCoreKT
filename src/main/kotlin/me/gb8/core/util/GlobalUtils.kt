/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.util

import me.gb8.core.Localization
import me.gb8.core.Main
import me.gb8.core.database.GeneralDatabase
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Statistic
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.inventory.meta.BookMeta
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.nio.file.Files
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.lang.reflect.Method
import java.util.logging.Level
import java.util.Random

object GlobalUtils {
    
    private val PREFIX = Main.prefix
    private val miniMessage = MiniMessage.miniMessage()
    private val componentCache = ConcurrentHashMap<String, TextComponent>()
    private val miniMessageFormatCache = ConcurrentHashMap<String, String>()
    private var database: GeneralDatabase? = null
    private val HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})")

    private var getCurrentRegionMethod: Method? = null
    private var getDataMethod: Method? = null
    private var getRegionSchedulingHandleMethod: Method? = null
    private var getTickReport15sMethod: Method? = null
    private var tpsDataMethod: Method? = null
    private var msptDataMethod: Method? = null
    private var segmentAllMethod: Method? = null
    private var averageMethod: Method? = null

    private val LUMINOL: Boolean
    private val LUMINOL_GET_GLOBAL_TPS: Method?
    private val LUMINOL_GET_GLOBAL_MSPT: Method?

    init {
        var luminol = false
        var getGlobalTps: Method? = null
        var getGlobalMspt: Method? = null
        try {
            val regionizedServer = Class.forName("me.earthme.luminol.server.RegionizedServer")
            val tickDataClass = Class.forName("ca.spottedleaf.moonrise.common.time.TickData\$TickReportData")

            getGlobalTps = regionizedServer.getMethod("getGlobalTickData")
            getGlobalMspt = tickDataClass.getMethod("getTickReport15s", Long::class.javaPrimitiveType)
            luminol = true
        } catch (_: Exception) {
        }
        LUMINOL = luminol
        LUMINOL_GET_GLOBAL_TPS = getGlobalTps
        LUMINOL_GET_GLOBAL_MSPT = getGlobalMspt
    }

    fun getCurrentRegionTps(): Double {
        if (LUMINOL && LUMINOL_GET_GLOBAL_TPS != null) {
            try {
                val tickData = LUMINOL_GET_GLOBAL_TPS.invoke(null)
                val report = tickData.javaClass.getMethod("getTickReport15s", Long::class.javaPrimitiveType).invoke(tickData, System.nanoTime())
                val tpsSeg = report.javaClass.getMethod("tpsData").invoke(report)
                val segAll = tpsSeg.javaClass.getMethod("segmentAll").invoke(tpsSeg)
                return segAll.javaClass.getMethod("average").invoke(segAll) as Double
            } catch (_: Exception) {
            }
        }
        try {
            if (getCurrentRegionMethod == null) {
                val trs = Class.forName("io.papermc.paper.threadedregions.TickRegionScheduler")
                getCurrentRegionMethod = trs.getMethod("getCurrentRegion")
            }
            val region = getCurrentRegionMethod?.invoke(null)
            if (region != null) {
                return getTpsFromRegionObject(region)
            }
        } catch (t: Throwable) {
            if (!loggedMsptError) {
                Main.instance.logger.log(Level.WARNING, "Failed to get current region TPS", t)
                loggedMsptError = true
            }
        }
        return -1.0
    }

    fun getCurrentRegionMspt(): Double {
        if (LUMINOL && LUMINOL_GET_GLOBAL_TPS != null) {
            try {
                val tickData = LUMINOL_GET_GLOBAL_TPS.invoke(null)
                val msptMethod = LUMINOL_GET_GLOBAL_MSPT ?: return -1.0
                val report = msptMethod.invoke(tickData, System.nanoTime()) ?: return -1.0
                val timeData = report.javaClass.getMethod("timePerTickData").invoke(report)
                val segAll = timeData.javaClass.getMethod("segmentAll").invoke(timeData)
                val nsPerTick = segAll.javaClass.getMethod("average").invoke(segAll) as Double
                return nsPerTick / 1.0E6
            } catch (_: Exception) {
            }
        }
        try {
            if (getCurrentRegionMethod == null) {
                val trs = Class.forName("io.papermc.paper.threadedregions.TickRegionScheduler")
                getCurrentRegionMethod = trs.getMethod("getCurrentRegion")
            }
            val region = getCurrentRegionMethod?.invoke(null)
            if (region != null) {
                return getMsptFromRegionObject(region)
            }
        } catch (_: Throwable) {
        }
        return -1.0
    }

    fun calculateItemSize(item: ItemStack?): Int {
        if (item == null) return 0
        return try {
            ByteArrayOutputStream().use { baos ->
                BukkitObjectOutputStream(baos).use { boos ->
                    boos.writeObject(item)
                    boos.flush()
                    baos.size()
                }
            }
        } catch (_: Throwable) {
            0
        }
    }

    fun info(format: String) {
        log(Level.INFO, format)
    }

    fun log(level: Level, format: String, vararg args: Any?) {
        val element = Thread.currentThread().stackTrace[2]
        var message = String.format(format, *args)
        message = getStringContent(translateChars(message))
        Main.instance.logger.log(level,
            String.format("%s%c%s", message, Character.MIN_VALUE, element.className))
    }

    fun translateChars(input: String?): TextComponent {
        if (input == null) return Component.empty()
        val cached = componentCache[input]
        if (cached != null) return cached

        val formatted = convertToMiniMessageFormat(input) ?: return Component.empty()
        val component = miniMessage.deserialize(formatted) as TextComponent
        
        if (input.length < 512 && !input.contains("<gradient")) {
            componentCache[input] = component
        }
        return component
    }

    fun isTeleportRestricted(player: Player): Boolean {
        if (player.isOp || player.hasPermission("8b8tcore.teleport.bypass")) return false

        if (player.getStatistic(Statistic.PLAY_ONE_MINUTE) >= 7200000) return false

        var ranked = false
        for (info in player.effectivePermissions) {
            if (info.permission.startsWith("8b8tcore.prefix.") && info.value) {
                ranked = true
                break
            }
        }
        if (ranked) return false

        var maxDistanceFromSpawn = Main.instance.config.getInt("TPAHOMERADIUS.default", 50000)

        for (info in player.effectivePermissions) {
            if (!info.value) continue
            val perm = info.permission
            if (perm.startsWith("tpa.spawn.") || perm.startsWith("home.spawn.")) {
                try {
                    val sub = if (perm.startsWith("tpa.spawn.")) perm.substring(10) else perm.substring(11)
                    val dist = sub.toInt()
                    maxDistanceFromSpawn = minOf(maxDistanceFromSpawn, dist)
                } catch (_: NumberFormatException) {
                }
            }
        }

        if (player.world.environment == World.Environment.NETHER) {
            maxDistanceFromSpawn /= 8
        }

        val loc = player.location
        return loc.blockX < maxDistanceFromSpawn && loc.blockX > -maxDistanceFromSpawn &&
               loc.blockZ < maxDistanceFromSpawn && loc.blockZ > -maxDistanceFromSpawn
    }

    fun getTeleportRestrictionRange(player: Player): Int {
        var range = Main.instance.config.getInt("TPAHOMERADIUS.default", 50000)
        for (info in player.effectivePermissions) {
            if (!info.value) continue
            val perm = info.permission
            if (perm.startsWith("tpa.spawn.") || perm.startsWith("home.spawn.")) {
                try {
                    val sub = if (perm.startsWith("tpa.spawn.")) perm.substring(10) else perm.substring(11)
                    val dist = sub.toInt()
                    range = minOf(range, dist)
                } catch (_: NumberFormatException) {
                }
            }
        }
        if (player.world.environment == World.Environment.NETHER) range /= 8
        return range
    }

    fun convertToMiniMessageFormat(input: String?): String? {
        if (input == null) return null
        if (input.indexOf('&') == -1) return input
        
        val cached = miniMessageFormatCache[input]
        if (cached != null) return cached

        val sb = StringBuilder(input.length + 32)
        val chars = input.toCharArray()
        var i = 0
        while (i < chars.size) {
            val c = chars[i]
            if (c == '&' && i + 1 < chars.size) {
                val next = chars[++i]
                when (next) {
                    '0' -> sb.append("<black>")
                    '1' -> sb.append("<dark_blue>")
                    '2' -> sb.append("<dark_green>")
                    '3' -> sb.append("<dark_aqua>")
                    '4' -> sb.append("<dark_red>")
                    '5' -> sb.append("<dark_purple>")
                    '6' -> sb.append("<gold>")
                    '7' -> sb.append("<gray>")
                    '8' -> sb.append("<dark_gray>")
                    '9' -> sb.append("<blue>")
                    'a' -> sb.append("<green>")
                    'b' -> sb.append("<aqua>")
                    'c' -> sb.append("<red>")
                    'd' -> sb.append("<light_purple>")
                    'e' -> sb.append("<yellow>")
                    'f' -> sb.append("<white>")
                    'l' -> sb.append("<bold>")
                    'm' -> sb.append("<strikethrough>")
                    'n' -> sb.append("<underlined>")
                    'o' -> sb.append("<italic>")
                    'k' -> sb.append("<obfuscated>")
                    'r' -> sb.append("<reset>")
                    '#' -> {
                        if (i + 6 < chars.size) {
                            sb.append("<#")
                            sb.append(chars, i + 1, 6)
                            sb.append(">")
                            i += 6
                        } else sb.append("&#")
                    }
                    else -> sb.append('&').append(next)
                }
            } else sb.append(c)
            i++
        }
        val result = sb.toString()
        if (input.length < 256) miniMessageFormatCache[input] = result
        return result
    }

    fun getTPSColor(tps: Double): String {
        return if (tps >= 18.0) {
            "<green>"
        } else {
            if (tps >= 13.0) "<yellow>" else "<red>"
        }
    }

    fun getMSPTColor(mspt: Double): String {
        return if (mspt < 60.0) {
            "<green>"
        } else if (mspt <= 100.0) {
            "<yellow>"
        } else {
            "<red>"
        }
    }

    fun sendMessage(obj: CommandSender, message: String, vararg args: Any?) {
        sendOptionalPrefixMessage(obj, message, true, *args)
    }

    fun sendOptionalPrefixMessage(obj: CommandSender, msg: String, prefix: Boolean, vararg args: Any?) {
        var message = if (prefix)
            String.format("%s &7>>&r %s", PREFIX, msg)
        else msg
        message = String.format(message, *args)
        obj.sendMessage(translateChars(message))
    }

    fun sendPrefixedLocalizedMessage(player: Player, key: String, vararg args: Any?) {
        sendLocalizedMessage(player, key, true, *args)
    }

    
    fun sendLocalizedMessage(player: Player, key: String, prefix: Boolean, vararg args: Any?) {
        val loc = Localization.getLocalization(player.locale().language)
        var msg = String.format(loc.get(key), *args)
        if (prefix)
            msg = PREFIX + " &r&7>>&r " + msg
        player.sendMessage(translateChars(msg))
    }

    
    fun sendLocalizedAmpersandMessage(player: Player, key: String, prefix: Boolean, vararg args: Any?) {
        val loc = Localization.getLocalization(player.locale().language)
        var msg = String.format(loc.get(key), *args)
        if (prefix)
            msg = PREFIX + " &r&7>>&r " + msg
        player.sendMessage(translateChars(msg))
    }

    fun sendDeathMessage(key: String, victim: String, killer: String, weapon: String) {
        try {
            val locEnglish = Localization.getLocalization("en")
            val deathListMessages = locEnglish.getStringList(key).map { translateChars(it) }

            var msgIndex = 0
            if (deathListMessages.size > 1) {
                msgIndex = java.util.concurrent.ThreadLocalRandom.current().nextInt(deathListMessages.size)
            }
            val finalMsgIndex = msgIndex

            Bukkit.getGlobalRegionScheduler().runDelayed(Main.instance, {
                try {
                    if (database == null) {
                        database = GeneralDatabase.getInstance()
                    }
                    for (p in Bukkit.getOnlinePlayers()) {
                        database?.getPlayerHideDeathMessagesAsync(p.name)?.thenAccept { hideDeathMessages ->
                            if (hideDeathMessages || !p.isOnline) {
                                return@thenAccept
                            }
                            FoliaCompat.schedule(p, Main.instance) {
                                if (!p.isOnline) return@schedule
                                val loc = Localization.getLocalization(p.locale().language)
                                val rawMessages = loc.getStringList(key)
                                if (rawMessages.isEmpty()) return@schedule
                                
                                val rawMsg = rawMessages[finalMsgIndex]
                                val formatted = rawMsg.replace("%victim%", victim)
                                        .replace("%killer%", killer)
                                        .replace("%kill-weapon%", weapon)
                                
                                p.sendMessage(translateChars(formatted))
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Main.instance.logger.warning("Failed to send death message: " + t.message)
                }
            }, 1L)
        } catch (_: Throwable) {
        }

    }

    fun sendPrefixedComponent(target: CommandSender, component: Component) {
        target.sendMessage(translateChars(String.format("%s &7>>&r ", PREFIX)).append(component))
    }

    fun unpackResource(resourceName: String, file: File) {
        if (file.exists())
            return
        try {
            Main::class.java.classLoader.getResourceAsStream(resourceName).use { `is` ->
                if (`is` == null)
                    throw NullPointerException(String.format("Resource %s is not present in the jar", resourceName))
                Files.copy(`is`, file.toPath())
            }
        } catch (t: Throwable) {
            log(Level.SEVERE,
                    "&cFailed to extract resource from jar due to &r&3 %s&r&c! Please see the stacktrace below for more info",
                    t.message)
            t.printStackTrace()
        }
    }

    fun executeCommand(command: String, vararg args: Any?) {
        Bukkit.dispatchCommand(Bukkit.getServer().consoleSender, String.format(command, *args))
    }

    fun removeElytra(player: Player) {
        Bukkit.getRegionScheduler().run(Main.instance, player.location) {
            try {
                val chestPlate = player.inventory.chestplate
                if (chestPlate == null)
                    return@run
                if (chestPlate.type == Material.AIR)
                    return@run
                if (chestPlate.type == Material.ELYTRA) {
                    val inventory = player.inventory
                    if (inventory.firstEmpty() == -1) {
                        player.world.dropItemNaturally(player.location, chestPlate)
                    } else
                        inventory.setItem(inventory.firstEmpty(), chestPlate)
                    val buffer = inventory.armorContents
                    buffer[2] = null
                    inventory.armorContents = buffer
                }
            } catch (e: Exception) {
                Main.instance.logger
                        .warning("Failed to remove elytra from " + player.name + ": " + e.message)
            }
        }
    }

    fun getStringContent(component: Component?): String {
        if (component == null) return ""
        try {
            if (getComponentDepth(component) > 50) return "Too many extra components"
            val serializer = PlainTextComponentSerializer.plainText()
            return serializer.serialize(component)
        } catch (_: Throwable) {
            return "Error serializing component"
        }
    }

    fun getComponentDepth(component: Component?): Int {
        if (component == null) return 0
        return getComponentDepth(component, 0)
    }

    private fun getComponentDepth(component: Component?, currentDepth: Int): Int {
        if (component == null || currentDepth > 50) return currentDepth
        var maxDepth = currentDepth + 1

        for (child in component.children()) {
            maxDepth = maxOf(maxDepth, getComponentDepth(child, currentDepth + 1))
            if (maxDepth > 50) return maxDepth
        }

        if (component is net.kyori.adventure.text.TranslatableComponent) {
            for (arg in component.arguments()) {
                if (arg is Component) {
                    maxDepth = maxOf(maxDepth, getComponentDepth(arg, currentDepth + 1))
                }
                if (maxDepth > 50) return maxDepth
            }
        }
        
        component.hoverEvent()?.let { hover ->
            if (hover.action() == net.kyori.adventure.text.event.HoverEvent.Action.SHOW_TEXT) {
                maxDepth = maxOf(maxDepth, getComponentDepth(hover.value() as Component, currentDepth + 1))
            }
        }
        
        return maxDepth
    }

    fun getTpsNearEntity(entity: Entity): CompletableFuture<Double> {
        val future = CompletableFuture<Double>()
        try {
            val regionTpsArr = Bukkit.getRegionTPS(entity.location)
            if (regionTpsArr != null && regionTpsArr.size > 0) {
                future.complete(regionTpsArr[0])
                return future
            }
        } catch (_: Throwable) {
        }

        FoliaCompat.schedule(entity, Main.instance) {
            val regionTps = getCurrentRegionTps()
            future.complete(regionTps)
        }
        return future
    }

    fun getTpsNearEntitySync(entity: Entity): DoubleArray {
        try {
            val tps = Bukkit.getRegionTPS(entity.location)
            if (tps != null && tps.size > 0) {
                return tps
            }
        } catch (_: Throwable) {
        }
        try {
            val globalTps = Bukkit.getTPS()
            return doubleArrayOf(globalTps[0], globalTps[0], globalTps[0], globalTps[0], globalTps[0])
        } catch (_: Throwable) {
        }
        return doubleArrayOf(20.0, 20.0, 20.0, 20.0, 20.0)
    }

    fun getRegionTps(location: Location): CompletableFuture<Double> {
        val future = CompletableFuture<Double>()
        try {
            val regionTpsArr = Bukkit.getRegionTPS(location)
            if (regionTpsArr != null && regionTpsArr.size > 0) {
                future.complete(regionTpsArr[0])
                return future
            }
        } catch (_: Throwable) {
        }

        Bukkit.getRegionScheduler().run(Main.instance, location) {
            val regionTps = getCurrentRegionTps()
            future.complete(regionTps)
        }
        return future
    }

    private var loggedMsptError = false

    @Volatile private var cachedGetData: Method? = null
    @Volatile private var cachedRegionClass: Class<*>? = null
    @Volatile private var cachedGetHandle: Method? = null
    @Volatile private var cachedTickDataClass: Class<*>? = null

    private fun getCachedGetData(region: Any): Method {
        val cls = region.javaClass
        if (cls != cachedRegionClass) {
            val m = cls.getMethod("getData")
            cachedGetData = m
            cachedRegionClass = cls
            return m
        }
        return cachedGetData ?: throw IllegalStateException("Cached method should not be null")
    }

    private fun getCachedGetHandle(tickData: Any): Method {
        val cls = tickData.javaClass
        if (cls != cachedTickDataClass) {
            val m = cls.getMethod("getRegionSchedulingHandle")
            cachedGetHandle = m
            cachedTickDataClass = cls
            return m
        }
        return cachedGetHandle ?: throw IllegalStateException("Cached method should not be null")
    }

    private fun getTpsFromRegionObject(region: Any): Double {
        try {
            val tickData = getCachedGetData(region).invoke(region)
            val handle = getCachedGetHandle(tickData).invoke(tickData)

            var report: Any? = null
            val reportMethods = arrayOf("getTickReport1s", "getTickReport5s", "getTickReport15s")
            for (mName in reportMethods) {
                try {
                    val m = handle.javaClass.getMethod(mName, Long::class.javaPrimitiveType)
                    report = m.invoke(handle, System.nanoTime())
                    if (report != null)
                        break
                } catch (_: Exception) {
                }
            }

            if (report != null) {
                val mTpsData = report.javaClass.getMethod("tpsData")
                val segmentedAvg = mTpsData.invoke(report)
                return getAverageFromSegmented(segmentedAvg)
            }
        } catch (_: Exception) {
        }
        return -1.0
    }

    private fun getMsptFromRegionObject(region: Any): Double {
        try {
            val tickData = getCachedGetData(region).invoke(region)
            val handle = getCachedGetHandle(tickData).invoke(tickData)

            var report: Any? = null
            val reportMethods = arrayOf("getTickReport1s", "getTickReport5s", "getTickReport15s")
            for (mName in reportMethods) {
                try {
                    val m = handle.javaClass.getMethod(mName, Long::class.javaPrimitiveType)
                    report = m.invoke(handle, System.nanoTime())
                    if (report != null)
                        break
                } catch (_: Exception) {
                }
            }

            if (report != null) {
                var segmentedAvg: Any? = null
                val msptMethodNames = arrayOf("msptData", "tickTimeData", "mspt", "tickTimes")
                for (mName in msptMethodNames) {
                    try {
                        val m = report.javaClass.getMethod(mName)
                        segmentedAvg = m.invoke(report)
                        if (segmentedAvg != null)
                            break
                    } catch (_: Exception) {
                    }
                }

                if (segmentedAvg != null) {
                    return getAverageFromSegmented(segmentedAvg)
                }
            }
        } catch (_: Exception) {
        }

        val tps = getCurrentRegionTps()
        if (tps > 0)
            return 1000.0 / minOf(tps, 20.0)
        return -1.0
    }

    private fun getAverageFromSegmented(segmentedAvg: Any): Double {
        try {
            return segmentedAvg.javaClass.getMethod("average").invoke(segmentedAvg) as Double
        } catch (e: Exception) {
            val segAll = segmentedAvg.javaClass.getMethod("segmentAll").invoke(segmentedAvg)
            return segAll.javaClass.getMethod("average").invoke(segAll) as Double
        }
    }

    fun getTotalTps(): Double {
        val seenRegions = HashSet<Int>()
        var total = 0.0
        for (player in Bukkit.getOnlinePlayers()) {
            val regionTPS = Bukkit.getRegionTPS(player.location)
            if (regionTPS != null) {
                val regionId = System.identityHashCode(regionTPS)
                if (seenRegions.add(regionId)) {
                    total += regionTPS[0]
                }
            }
        }
        return total
    }

    fun formatLocation(location: Location): String {
        return "${location.world.name} ${location.blockX}, ${location.blockY}, ${location.blockZ}"
    }

    fun getChunkId(block: Block): UUID {
        val x = block.chunk.x
        val z = block.chunk.z
        return UUID.nameUUIDFromBytes((x.toString() + ":" + z).toByteArray())
    }

    fun getItemCountInChunk(block: Block): Int {
        return block.chunk.entities.filterIsInstance<Item>().size
    }

    fun updateDisplayName(player: Player) {
        updateDisplayNameAsync(player).join()
    }

    fun updateDisplayNameAsync(player: Player): CompletableFuture<Void> {
        if (database == null)
            database = GeneralDatabase.getInstance()
        
        val username = player.name
        val db = database ?: return CompletableFuture.completedFuture(null)
        
        return CompletableFuture.allOf(
            db.getNicknameAsync(username),
            db.getCustomGradientAsync(username),
            db.getGradientAnimationAsync(username),
            db.getGradientSpeedAsync(username),
            db.getPlayerDataAsync(username, "nameDecorations")
        ).thenAcceptAsync {
            if (!player.isOnline) return@thenAcceptAsync
            
            val nick = db.getNicknameAsync(username).join()
            val customGradient = db.getCustomGradientAsync(username).join()
            val anim = db.getGradientAnimationAsync(username).join()
            val speed = db.getGradientSpeedAsync(username).join()
            val decorationsStr = db.getPlayerDataAsync(username, "nameDecorations").join()
            
            FoliaCompat.schedule(player, Main.instance) {
                if (!player.isOnline) return@schedule
                player.displayName(parseDisplayName(player.name, nick, customGradient, anim, speed, decorationsStr))
            }
        }
    }

    fun parseDisplayName(playerName: String, nick: String?, customGradient: String?, anim: String?, speed: Int, decorationsStr: String?): Component {
        return parseDisplayName(playerName, nick, customGradient, anim, speed, decorationsStr, GradientAnimator.getAnimationTick())
    }

    fun parseDisplayName(playerName: String, nick: String?, customGradient: String?, anim: String?, speed: Int, decorationsStr: String?, tick: Long): Component {
        val baseName: String
        if (nick.isNullOrEmpty() || nick == playerName) {
            baseName = playerName
        } else {
            if (nick.indexOf('&') == -1 && nick.indexOf('§') == -1 && nick.indexOf('<') == -1) {
                baseName = nick.trim()
            } else {
                baseName = PlainTextComponentSerializer.plainText()
                        .serialize(miniMessage.deserialize(convertToMiniMessageFormat(nick) ?: nick)).trim()
            }
        }

        if (customGradient.isNullOrEmpty()) {
            return renderSimpleName(baseName, decorationsStr)
        }

        var workingGradient = customGradient.trim()
        if (workingGradient.lowercase().startsWith("<gradient:") && workingGradient.endsWith(">")) {
            workingGradient = workingGradient.substring(10, workingGradient.length - 1)
        } else if (workingGradient.lowercase().startsWith("<color:") && workingGradient.endsWith(">")) {
            workingGradient = workingGradient.substring(7, workingGradient.length - 1)
        }

        if (workingGradient.lowercase().contains("tobias:")) {
            try {
                val lower = workingGradient.lowercase()
                val tIndex = lower.indexOf("tobias:")
                var format = workingGradient.substring(tIndex + 7).trim()
                if (format.endsWith(">")) format = format.substring(0, format.length - 1)
                
                val parts = format.split(";")
                var finalComp = Component.empty()
                var currentIndex = 0
                for (part in parts) {
                    if (!part.contains(":")) continue
                    val split = part.split(":")
                    var len = split[0].toInt()
                    val decorations = if (split.size > 1) split[1] else ""
                    val colorHex = if (split.size > 2) split[2] else ""

                    if (currentIndex + len > baseName.length) len = baseName.length - currentIndex
                    if (len <= 0) break
                    val sub = baseName.substring(currentIndex, currentIndex + len)
                    currentIndex += len

                    var segment = Component.text(sub)
                    if (colorHex.isNotEmpty() && colorHex.startsWith("#")) {
                        val textColor = TextColor.fromHexString(colorHex)
                        if (textColor != null) segment = segment.color(textColor)
                    }
                    if (decorations.isNotEmpty() && decorations.lowercase() != "none") {
                        for (dec in decorations.split("/")) {
                            val textDecoration = TextDecoration.NAMES.value(dec.lowercase().trim())
                            if (textDecoration != null) segment = segment.decoration(textDecoration, true)
                        }
                    }
                    finalComp = finalComp.append(segment)
                }
                return finalComp
            } catch (e: Exception) {
                return renderSimpleName(baseName, decorationsStr)
            }
        }

        val isGradient = workingGradient.contains(":") &&
                workingGradient.indexOf('#') != workingGradient.lastIndexOf('#')

        val finalGradient: String?
        if (isGradient) {
            finalGradient = GradientAnimator.applyAnimation(workingGradient, anim, speed, tick)
        } else {
            finalGradient = workingGradient
        }

        val result = StringBuilder()
        if (!decorationsStr.isNullOrEmpty()) {
            for (decoration in decorationsStr.split(",")) {
                result.append("<").append(decoration.trim()).append(">")
            }
        }

        if (isGradient) {
            result.append("<gradient:").append(finalGradient).append(">")
                    .append(baseName)
                    .append("</gradient>")
        } else {
            val color = if (finalGradient?.contains(":") == true) finalGradient.split(":")[0] else finalGradient
            result.append("<color:").append(color).append(">")
                    .append(baseName)
                    .append("</color>")
        }

        if (!decorationsStr.isNullOrEmpty()) {
            val decorations = decorationsStr.split(",")
            for (i in decorations.indices.reversed()) {
                result.append("</").append(decorations[i].trim()).append(">")
            }
        }

        return miniMessage.deserialize(result.toString())
    }

    private fun renderSimpleName(name: String, decorationsStr: String?): Component {
        if (decorationsStr.isNullOrEmpty()) return Component.text(name)
        val sb = StringBuilder()
        for (dec in decorationsStr.split(",")) sb.append("<").append(dec.trim()).append(">")
        sb.append(name)
        val decs = decorationsStr.split(",")
        for (i in decs.indices.reversed()) sb.append("</").append(decs[i].trim()).append(">")
        return miniMessage.deserialize(convertToMiniMessageFormat(sb.toString()) ?: sb.toString())
    }
}
