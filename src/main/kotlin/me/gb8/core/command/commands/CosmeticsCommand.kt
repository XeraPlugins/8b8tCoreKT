/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import me.gb8.core.Main
import me.gb8.core.command.BaseTabCommand
import me.gb8.core.player.PrefixManager
import me.gb8.core.database.GeneralDatabase
import me.gb8.core.util.FoliaCompat
import me.gb8.core.util.GlobalUtils
import me.gb8.core.vote.VoteSection
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.util.ArrayList
import java.util.Collections
import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage
import me.gb8.core.util.GlobalUtils.sendMessage

class CosmeticsCommand(private val plugin: Main) : BaseTabCommand("cosmetics", "/cosmetics <type> <action> [args]", "8b8tcore.command.cosmetics") {
    private val prefixManager = PrefixManager()
    private val database = GeneralDatabase.getInstance()
    private val miniMessage = MiniMessage.miniMessage()

    companion object {
        private val VALID_STYLES = setOf("bold", "italic", "underlined", "strikethrough")
        private val VALID_ANIMATIONS = setOf("wave", "pulse", "smooth", "saturate", "bounce", "billboard", "sweep", "shimmer", "none")
    }

    private fun hasNickPermission(player: Player): Boolean {
        if (player.hasPermission("8b8tcore.command.nc")) return true

        val hasRank = prefixManager.hasRank(player)
        val hasVoterRole = (Main.instance.getSectionByName("Vote") as? VoteSection)
            ?.let { !it.hasVoterRoleExpired(player.name) } ?: false

        return hasRank || hasVoterRole
    }

    private fun parseColorsAndStyles(args: Array<String>, startIndex: Int = 2): Pair<String?, List<String>> {
        val styles = mutableListOf<String>()
        var colors: String? = null

        for (i in startIndex until args.size) {
            val arg = args[i].lowercase()
            when {
                arg.startsWith("#") -> colors = arg
                arg in VALID_STYLES -> styles.add(arg)
            }
        }

        return colors to styles
    }

    private fun parseColorsAndStylesItem(args: Array<String>): Pair<String?, List<String>> {
        val styles = mutableListOf<String>()
        var colors: String? = null

        for (i in 2 until args.size) {
            val arg = args[i].lowercase()
            when {
                arg.startsWith("#") -> colors = args[i]
                arg in VALID_STYLES -> styles.add(arg)
            }
        }

        return colors to styles
    }

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (sender !is Player) {
            sendMessage(sender, "&cOnly players can use this command.")
            return
        }

        if (args.size < 2) {
            sendHelp(sender)
            return
        }

        val type = args[0].lowercase()
        val action = args[1].lowercase()

        when (type) {
            "title" -> handleTitle(sender, action, args)
            "nick" -> handleNick(sender, action, args)
            "item" -> handleItem(sender, action, args)
            "clear" -> {
                handleTitle(sender, "clear", args)
                handleNick(sender, "clear", args)
            }
            else -> sendHelp(sender)
        }
    }

    private fun handleTitle(player: Player, action: String, args: Array<String>) {
        when (action) {
            "clear" -> {
                database.updateSelectedRank(player.name, "")
                database.updatePrefixGradient(player.name, "")
                database.updatePrefixAnimation(player.name, "none")
                database.updatePrefixSpeed(player.name, 5)
                database.updatePrefixDecorations(player.name, "")

                val chatSection = Main.instance.getSectionByName("ChatControl") as? me.gb8.core.chat.ChatSection
                if (chatSection != null) {
                    val info = chatSection.getInfo(player)
                    if (info != null) {
                        info.selectedRank = null
                        info.customGradient = null
                        info.prefixAnimation = "none"
                        info.prefixSpeed = 5
                        info.prefixDecorations = null
                        info.clearAnimatedNameCache()
                    }
                }

                refreshPlayer(player)
                sendPrefixedLocalizedMessage(player, "title_cleared")
            }
            "color" -> {
                if (!player.hasPermission("8b8tcore.prefix.custom")) {
                    sendPrefixedLocalizedMessage(player, "title_no_permission_for_rank")
                    return
                }

                val (colors, styles) = parseColorsAndStyles(args)

                if (colors == null) {
                    sendMessage(player, "&cUsage: /cosmetics title color <#hex:#hex...> [styles]")
                    return
                }

                if (!colors.matches(Regex("^#[0-9A-Fa-f]{6}(:#[0-9A-Fa-f]{6})*$"))) {
                    sendPrefixedLocalizedMessage(player, "gradient_invalid", colors)
                    return
                }

                val decorations = styles.joinToString(",")
                database.updatePrefixGradient(player.name, colors)
                database.updatePrefixDecorations(player.name, decorations).thenRun { refreshPlayer(player) }

                var preview = colors
                if (styles.isNotEmpty()) {
                    preview += " with styles: " + styles.joinToString(", ")
                }
                sendMessage(player, "&aTitle gradient set: &r$preview")
            }
            "animation" -> {
                if (!player.hasPermission("8b8tcore.prefix.custom")) {
                    sendPrefixedLocalizedMessage(player, "title_no_permission_for_rank")
                    return
                }
                if (args.size < 3) {
                    sendMessage(player, "&cUsage: /cosmetics title animation <wave/pulse/smooth/saturate/bounce/billboard/sweep/shimmer/none>")
                    return
                }
                val anim = args[2].lowercase()
                if (anim !in VALID_ANIMATIONS) {
                    sendMessage(player, "&cInvalid animation! Use: wave, pulse, smooth, saturate, bounce, billboard, sweep, shimmer, or none")
                    return
                }
                database.updatePrefixAnimation(player.name, anim).thenRun { refreshPlayer(player) }
                sendMessage(player, "&aTitle animation set to: &e$anim")
            }
            "speed" -> {
                if (!player.hasPermission("8b8tcore.prefix.custom")) {
                    sendPrefixedLocalizedMessage(player, "title_no_permission_for_rank")
                    return
                }
                if (args.size < 3) {
                    sendMessage(player, "&cUsage: /cosmetics title speed <1-5>")
                    return
                }
                try {
                    val speed = args[2].toInt()
                    if (speed < 1 || speed > 5) {
                        sendMessage(player, "&cSpeed must be between 1 and 5!")
                        return
                    }
                    database.updatePrefixSpeed(player.name, speed).thenRun { refreshPlayer(player) }
                    sendMessage(player, "&aTitle animation speed set to: &e$speed")
                } catch (e: NumberFormatException) {
                    sendMessage(player, "&cInvalid number!")
                }
            }
            "set" -> {
                if (args.size < 3) {
                    sendMessage(player, "&cUsage: /cosmetics title set <rank>")
                    return
                }
                val rankInput = args[2]
                selectRank(player, rankInput)
            }
            else -> sendHelp(player)
        }
    }

    private fun selectRank(player: Player, input: String) {
        val available = prefixManager.getAvailableRanks(player)
        var targetRank: String? = null
        val inputLower = input.lowercase()

        for (rank in available) {
            val friendly = prefixManager.getRankDisplayName(rank).lowercase()
            val permission = rank.lowercase()
            val stripped = rank.replace("8b8tcore.prefix.", "").lowercase()

            if (inputLower == friendly || inputLower == permission || inputLower == stripped) {
                targetRank = rank
                break
            }
        }

        if (targetRank != null) {
            database.updateSelectedRank(player.name, targetRank).thenRun { refreshPlayer(player) }
            sendPrefixedLocalizedMessage(player, "title_success", prefixManager.getRankDisplayName(targetRank))
        } else {
            sendPrefixedLocalizedMessage(player, "title_no_permission_for_rank")
        }
    }

    private fun handleNick(player: Player, action: String, args: Array<String>) {
        when (action) {
            "clear" -> {
                database.insertNickname(player.name, "")
                database.updateCustomGradient(player.name, "")
                database.updateGradientAnimation(player.name, "none")
                database.updateGradientSpeed(player.name, 5)
                database.updateNameDecorations(player.name, "")

                val chatSection = Main.instance.getSectionByName("ChatControl") as? me.gb8.core.chat.ChatSection
                if (chatSection != null) {
                    val info = chatSection.getInfo(player)
                    if (info != null) {
                        info.nickname = null
                        info.nameGradient = null
                        info.nameAnimation = "none"
                        info.nameSpeed = 5
                        info.nameDecorations = null
                        info.clearAnimatedNameCache()
                    }
                }

                FoliaCompat.schedule(player, Main.instance) {
                    player.displayName(miniMessage.deserialize(player.name))
                    refreshPlayer(player)
                }
                sendPrefixedLocalizedMessage(player, "nick_reset")
            }
            "color" -> {
                if (!hasNickPermission(player)) {
                    sendPrefixedLocalizedMessage(player, "nc_no_permission")
                    return
                }

                if (args.size > 2 && args[2].equals("tobias", ignoreCase = true)) {
                    handleTobiasNick(player, args)
                    return
                }

                val (colors, styles) = parseColorsAndStyles(args)

                if (colors == null) {
                    sendMessage(player, "&cUsage: /cosmetics nick color <#hex:#hex...> [styles]")
                    return
                }

                if (!colors.matches(Regex("^#[0-9A-Fa-f]{6}(:#[0-9A-Fa-f]{6})*$"))) {
                    sendPrefixedLocalizedMessage(player, "gradient_invalid", colors)
                    return
                }

                val finalColors = colors
                database.getNicknameAsync(player.name).thenAccept { currentNick ->
                    val current = currentNick ?: player.name
                    database.insertNickname(player.name, current)

                    val decorations = styles.joinToString(",")
                    database.updateCustomGradient(player.name, finalColors)
                    database.updateNameDecorations(player.name, decorations).thenRun {
                        GlobalUtils.updateDisplayNameAsync(player).thenRun { refreshPlayer(player) }
                    }
                }

                var preview = colors
                if (styles.isNotEmpty()) {
                    preview += " with styles: " + styles.joinToString(", ")
                }
                sendMessage(player, "&aNickname gradient set: &r$preview")
            }
            "animation" -> {
                if (!hasNickPermission(player)) {
                    sendPrefixedLocalizedMessage(player, "nc_no_permission")
                    return
                }
                if (args.size < 3) {
                    sendMessage(player, "&cUsage: /cosmetics nick animation <wave/pulse/smooth/saturate/bounce/billboard/sweep/shimmer/none>")
                    return
                }
                val anim = args[2].lowercase()
                if (anim !in VALID_ANIMATIONS) {
                    sendMessage(player, "&cInvalid animation! Use: wave, pulse, smooth, saturate, bounce, billboard, sweep, shimmer, or none")
                    return
                }
                database.updateGradientAnimation(player.name, anim).thenRun {
                    GlobalUtils.updateDisplayNameAsync(player).thenRun { refreshPlayer(player) }
                }
                sendMessage(player, "&aNickname animation set to: &e$anim")
            }
            "speed" -> {
                if (!hasNickPermission(player)) {
                    sendPrefixedLocalizedMessage(player, "nc_no_permission")
                    return
                }
                if (args.size < 3) {
                    sendMessage(player, "&cUsage: /cosmetics nick speed <1-5>")
                    return
                }
                try {
                    val speed = args[2].toInt()
                    if (speed < 1 || speed > 5) {
                        sendMessage(player, "&cSpeed must be between 1 and 5!")
                        return
                    }
                    database.updateGradientSpeed(player.name, speed).thenRun {
                        GlobalUtils.updateDisplayNameAsync(player).thenRun { refreshPlayer(player) }
                    }
                    sendMessage(player, "&aNickname animation speed set to: &e$speed")
                } catch (e: NumberFormatException) {
                    sendMessage(player, "&cInvalid number!")
                }
            }
            else -> {
                if (action == "set") {
                    if (args.size < 3) {
                        sendHelp(player)
                        return
                    }
                    setNickname(player, args, 2)
                } else {
                    setNickname(player, args, 1)
                }
            }
        }
    }

    private fun handleTobiasNick(player: Player, args: Array<String>) {
        if (!player.hasPermission("8b8tcore.command.nc")) {
            sendPrefixedLocalizedMessage(player, "nc_no_permission")
            return
        }

        if (args.size < 4) {
            sendMessage(player, "&cUsage: /cosmetics nick color tobias <length:decorations:#color> ...")
            sendMessage(player, "&7Example: /cosmetics nick color tobias 6:bold/italic:#FF0000 8:none:#00FF00")
            return
        }

        database.getNicknameAsync(player.name).thenAccept { currentNick ->
            val effectiveNick = currentNick ?: player.name

            val mmFormat = GlobalUtils.convertToMiniMessageFormat(effectiveNick) ?: effectiveNick
            val plainNick = PlainTextComponentSerializer.plainText()
                .serialize(miniMessage.deserialize(mmFormat)).trim()
            val nickLength = plainNick.length

            var totalLength = 0
            val parts = ArrayList<String>()
            for (i in 3 until args.size) {
                val arg = args[i].lowercase()
                if (!arg.matches(Regex("^\\d+:[a-z/]+:#[0-9a-f]{6}$"))) {
                    sendMessage(player, "&cInvalid part format: $arg. Expected length:decorations:#color")
                    return@thenAccept
                }
                val split = arg.split(":")
                try {
                    val len = split[0].toInt()
                    totalLength += len
                    parts.add(arg)
                } catch (e: NumberFormatException) {
                    sendMessage(player, "&cInvalid length in part: $arg")
                    return@thenAccept
                }
            }

            if (totalLength != nickLength) {
                sendMessage(player, "&cTotal length of parts ($totalLength) does not match nickname length ($nickLength)")
                return@thenAccept
            }

            val tobiasFormat = "tobias:" + parts.joinToString(";")
            database.updateCustomGradient(player.name, tobiasFormat).thenRun {
                GlobalUtils.updateDisplayNameAsync(player).thenRun { refreshPlayer(player) }
            }
            sendMessage(player, "&aNickname set to special Tobias format!")
        }
    }

    private fun setNickname(player: Player, args: Array<String>, startIndex: Int) {
        if (!player.hasPermission("8b8tcore.command.nick")) {
            sendPrefixedLocalizedMessage(player, "nick_no_permission")
            return
        }

        val raw = args.drop(startIndex).joinToString(" ").trim()
        val mmFormat = GlobalUtils.convertToMiniMessageFormat(raw) ?: raw
        val plain = PlainTextComponentSerializer.plainText()
            .serialize(miniMessage.deserialize(mmFormat)).trim()

        if (plain.length > 16) {
            sendPrefixedLocalizedMessage(player, "nick_too_large", "16")
            return
        }

        database.insertNickname(player.name, plain).thenRun {
            GlobalUtils.updateDisplayNameAsync(player).thenRun { refreshPlayer(player) }
            sendPrefixedLocalizedMessage(player, "nick_success", plain)
        }
    }

    private fun handleItem(player: Player, action: String, args: Array<String>) {
        if (action == "color") {
            if (!player.hasPermission("8b8tcore.command.ic")) {
                sendPrefixedLocalizedMessage(player, "ic_no_permission")
                return
            }

            val item = player.inventory.itemInMainHand
            if (item.type == Material.AIR) {
                sendPrefixedLocalizedMessage(player, "ic_no_item")
                return
            }

            if (args.size < 3) {
                sendMessage(player, "&cUsage: /cosmetics item color <#hex:#hex...> [styles]")
                return
            }

            val (colors, styles) = parseColorsAndStylesItem(args)

            if (colors == null) {
                sendMessage(player, "&cYou must provide a color (e.g. #FF0000).")
                return
            }

            val meta = item.itemMeta
            val name = getCleanItemName(item)
            val escapedName = name.replace("<", "\\<").replace(">", "\\>")

            val mm = buildString {
                styles.forEach { style -> append("<$style>") }

                val isGradient = colors.contains(":") && colors.indexOf('#') != colors.lastIndexOf('#')

                val styledName = if (isGradient) {
                    "<gradient:$colors>$escapedName</gradient>"
                } else {
                    "<color:${colors.split(":")[0]}>$escapedName</color>"
                }

                append(styledName)
                styles.reversed().forEach { style -> append("</$style>") }
            }

            meta.displayName(miniMessage.deserialize(mm).decoration(TextDecoration.ITALIC, false))
            item.itemMeta = meta
            val displayName = meta.displayName()
            if (displayName != null) {
                sendPrefixedLocalizedMessage(player, "ic_success", miniMessage.serialize(displayName))
            }
        } else {
            sendHelp(player)
        }
    }

    
    private fun getCleanItemName(item: ItemStack): String {
        if (item.hasItemMeta() && item.itemMeta.hasDisplayName()) {
            val displayName: String = item.itemMeta.displayName
            return PlainTextComponentSerializer.plainText().serialize(net.kyori.adventure.text.Component.text(displayName))
        }
        return item.type.name.replace("_", " ").lowercase()
    }

    private fun sendHelp(player: Player) {
        sendMessage(player, "&6--- Cosmetics Help ---")

        val colorHelp = "<gold>/cosmetics <yellow><title|nick|item> <gold>color <red><colors> <aqua>[styles]</red>"
        val colorHover = "<gray>Colors: <white>#RRGGBB <gray>or <white>#RRGGBB:#RRGGBB <gray>(Gradient)\n" +
                "<gray>Styles: <white>bold, italic, underlined, strikethrough"
        GlobalUtils.sendPrefixedComponent(player, miniMessage.deserialize(colorHelp)
            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(miniMessage.deserialize(colorHover))))

        val tobiasHelp = "<gold>/cosmetics <yellow>nick <gold>color <red>tobias <aqua><parts...>"
        val tobiasHover = "<gray>Formatting.\n<gray>Format: <white>length:decorations:#color\n<gray>Example: <white>6:bold:#FF0000 8:none:#00FF00"
        GlobalUtils.sendPrefixedComponent(player, miniMessage.deserialize(tobiasHelp)
            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(miniMessage.deserialize(tobiasHover))))

        val animHelp = "<gold>/cosmetics <yellow><title|nick> <gold>animation <red><type>"
        val animHover = "<gray>Types: <white>wave, pulse, smooth, saturate, bounce, billboard, sweep, shimmer, none"
        GlobalUtils.sendPrefixedComponent(player, miniMessage.deserialize(animHelp)
            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(miniMessage.deserialize(animHover))))

        sendMessage(player, "&6/cosmetics <nick|title> speed &e<1-5>")
        sendMessage(player, "&6/cosmetics <nick|title> clear")
        sendMessage(player, "&6/cosmetics title set &e<rank>")
        sendMessage(player, "&6/cosmetics nick &e<name>")
        sendMessage(player, "&3----------------------")
    }

    private fun refreshPlayer(player: Player) {
        val plugin = Main.instance
        val chatSection = plugin.getSectionByName("ChatControl")
        if (chatSection is me.gb8.core.chat.ChatSection) {
            val info = chatSection.getInfo(player)
            if (info != null) {
                info.clearAnimatedNameCache()
                chatSection.loadAllDataAsync(info)
            }
        }

        FoliaCompat.scheduleDelayed(player, plugin, {
            if (!player.isOnline) return@scheduleDelayed
            val section = plugin.getSectionByName("TabList")
            if (section is me.gb8.core.tablist.TabSection) {
                section.setTab(player, true)
            }
        }, 5L)
    }

    override fun onTab(sender: CommandSender, args: Array<String>): List<String> {
        val player = sender as? Player ?: return emptyList()

        if (args.size == 1) {
            val lastArg = args[0].lowercase()
            return listOf("title", "nick", "item")
                .filter { s -> s.startsWith(lastArg) }
        }

        val type = args[0].lowercase()
        if (args.size == 2) {
            val lastArg = args[1].lowercase()
            return when (type) {
                "title" -> listOf("clear", "color", "animation", "speed", "set")
                    .filter { s -> s.startsWith(lastArg) }
                "nick" -> listOf("clear", "color", "animation", "speed", "set")
                    .filter { s -> s.startsWith(lastArg) }
                "item" -> listOf("color")
                    .filter { s -> s.startsWith(lastArg) }
                else -> emptyList()
            }
        }

        if (type == "title") {
            if (args[1] == "set" && args.size == 3) {
                val lastArg = args[2].lowercase()
                return prefixManager.getAvailableRanks(player)
                    .filter { rank -> rank.lowercase().contains(lastArg) ||
                        prefixManager.getRankDisplayName(rank).lowercase().startsWith(lastArg) }
            }
        }

        if (args.size >= 3) {
            val lastArg = args[args.size - 1].lowercase()

            if ((type == "title" && args[1] == "color") ||
                (type == "nick" && args[1] == "color") ||
                (type == "item" && args[1] == "color")) {

                var hasColor = false
                for (i in 2 until args.size - 1) {
                    if (args[i].startsWith("#")) {
                        hasColor = true
                        break
                    }
                }

                val suggestions = ArrayList<String>()
                val hexes = listOf("#FFFFFF", "#FF0000", "#00FF00", "#0000FF",
                    "#FF0000:#00FF00", "#00FFFF:#FF00FF")
                suggestions.addAll(hexes)
                if (type == "nick" && args[1] == "color" && args.size == 3) {
                    suggestions.add("tobias")
                }

                if (hasColor) {
                    val allStyles = listOf("bold", "italic", "underlined", "strikethrough")
                    val usedStyles = ArrayList<String>()
                    for (i in 2 until args.size - 1) {
                        val arg = args[i].lowercase()
                        if (allStyles.contains(arg)) {
                            usedStyles.add(arg)
                        }
                    }
                    allStyles
                        .filter { style -> !usedStyles.contains(style) }
                        .forEach { suggestions.add(it) }
                }

                return suggestions
                    .filter { s -> s.lowercase().startsWith(lastArg) }
            }

            if ((type == "title" && args[1] == "animation") ||
                (type == "nick" && args[1] == "animation")) {
                return listOf("wave", "pulse", "smooth", "saturate", "bounce", "billboard", "sweep", "shimmer", "none")
                    .filter { a -> a.startsWith(lastArg) }
            }

            if ((type == "title" && args[1] == "speed") ||
                (type == "nick" && args[1] == "speed")) {
                return listOf("1", "2", "3", "4", "5")
                    .filter { s -> s.startsWith(lastArg) }
            }
        }

        return emptyList()
    }
}
