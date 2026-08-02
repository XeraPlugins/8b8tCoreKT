/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 *
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.event.player.PlayerCustomClickEvent
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import me.gb8.core.Main
import me.gb8.core.chat.ChatSection
import me.gb8.core.command.BaseTabCommand
import me.gb8.core.database.GeneralDatabase
import me.gb8.core.home.HomeManager
import me.gb8.core.tpa.TPASection
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

class SettingsCommand(
    private val plugin: Main,
    private val cosmeticsCommand: CosmeticsCommand
) : BaseTabCommand(
    "settings",
    "/settings [player|homes|hotspots|tpa|info|tables|menu|misc]",
    "8b8tcore.command.settings",
    "Open the player settings and commands menu",
    PAGES.map { "$it::Open this settings page" }.toTypedArray()
), Listener {
    private val database = GeneralDatabase.getInstance()

    init {
        plugin.register(this)
    }

    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player ?: run {
            sender.sendMessage("This command can only be used by players.")
            return
        }

        if ('.' in player.name) {
            player.sendMessage(
                Component.text("This settings menu is for Java Edition players only.", NamedTextColor.RED)
            )
            return
        }

        if (args.firstOrNull().equals("toggle", ignoreCase = true)) {
            handleToggle(player, args)
            return
        }
        if (args.firstOrNull().equals("option", ignoreCase = true)) {
            handleMenuOption(player, args)
            return
        }
        if (args.firstOrNull().equals("action", ignoreCase = true)) {
            handleMenuAction(player, args)
            return
        }
        if (args.firstOrNull().equals("sign", ignoreCase = true)) {
            player.scheduler.runDelayed(plugin, {
                if (!player.isOnline) return@runDelayed
                player.closeDialog()
                player.performCommand("sign")
            }, null, 1L)
            return
        }

        val page = Page.from(args.firstOrNull())
        if (page == null) {
            player.sendMessage(Component.text(usage, NamedTextColor.RED))
            return
        }
        if (
            page == Page.NICKNAME &&
            (
                plugin.getCommand("cosmetics")?.testPermissionSilent(player) == false ||
                    !player.hasPermission("8b8tcore.command.nick")
                )
        ) {
            player.sendMessage(Component.text("Your rank cannot set a nickname.", NamedTextColor.RED))
            return
        }
        if (
            page == Page.KILL &&
            ((plugin.getSectionByName("ChatControl") as? ChatSection)
                ?.getInfo(player)?.menuNoConfirmKill == true)
        ) {
            player.scheduler.runDelayed(plugin, {
                if (!player.isOnline) return@runDelayed
                player.closeDialog()
                player.performCommand("kill")
            }, null, 1L)
            return
        }
        if (
            page != Page.ROOT &&
            page != Page.NICKNAME &&
            page != Page.KILL &&
            entriesFor(player, page).isEmpty()
        ) {
            player.closeDialog()
            player.sendMessage(
                Component.text("No options available.", NamedTextColor.RED)
            )
            return
        }

        if (player.protocolVersion >= DIALOG_PROTOCOL) {
            val dialog = if (page == Page.ROOT) registeredRootDialog() else null
            player.showDialog(dialog ?: createDialog(player, page))
        } else {
            showChatFallback(player, page)
        }
    }

    override fun onTab(sender: CommandSender, args: Array<String>): List<String> {
        if (args.size != 1) return emptyList()
        return PAGES.filter { it.startsWith(args[0], ignoreCase = true) }
    }

    private fun createDialog(player: Player, page: Page): Dialog {
        if (page == Page.NICKNAME) return createNicknameDialog(player)
        if (page == Page.KILL) return createKillDialog()

        val entries = entriesFor(player, page)
        val columns = if (page == Page.HOMES) {
            ((plugin.getSectionByName("ChatControl") as? ChatSection)
                ?.getInfo(player)?.menuHomeColumns ?: 4).coerceIn(1, 7)
        } else {
            page.columns
        }
        val buttonWidth = when {
            columns == 1 -> 300
            columns == 2 -> 150
            page == Page.HOMES -> 100
            else -> 150
        }
        val actions = entries.map { createButton(it, buttonWidth) }
        val exitAction = if (page == Page.ROOT) null else createButton(
            MenuEntry("← Main Menu", "/settings", "Return to the main settings menu"),
            200
        )

        return Dialog.create { factory ->
            factory.empty()
                .base(
                    DialogBase.builder(
                        Component.text(page.title, NamedTextColor.AQUA)
                            .decorate(TextDecoration.BOLD)
                    )
                        .externalTitle(Component.text("8b8t Settings", NamedTextColor.AQUA))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(
                            if (page.keepOpenAfterAction) {
                                DialogBase.DialogAfterAction.NONE
                            } else {
                                DialogBase.DialogAfterAction.CLOSE
                            }
                        )
                        .body(createBody(player, page))
                        .build()
                )
                .type(
                    DialogType.multiAction(actions)
                        .columns(columns)
                        .exitAction(exitAction)
                        .build()
                )
        }
    }

    private fun registeredRootDialog(): Dialog? {
        return RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.DIALOG)
            .get(ROOT_DIALOG_KEY)
    }

    private fun createBody(player: Player, page: Page): List<DialogBody> {
        val body = mutableListOf<DialogBody>(
            DialogBody.plainMessage(
                Component.text(page.description, NamedTextColor.GRAY),
                310
            )
        )
        if (page == Page.PLAYER) {
            val joinDate = if (player.firstPlayed > 0L) {
                JOIN_DATE_FORMATTER.format(Instant.ofEpochMilli(player.firstPlayed))
            } else {
                "Unknown"
            }
            body += DialogBody.plainMessage(
                Component.text("Join Date: ", NamedTextColor.GRAY)
                    .append(Component.text(joinDate, NamedTextColor.AQUA)),
                310
            )
        }
        return body
    }

    private fun createNicknameDialog(player: Player): Dialog {
        val info = (plugin.getSectionByName("ChatControl") as? ChatSection)?.getInfo(player)
        val initialNickname = info?.nickname?.takeIf(String::isNotBlank) ?: player.name
        val submit = ActionButton.builder(Component.text("Save Nickname", NamedTextColor.GREEN))
            .tooltip(Component.text("Save this nickname using your existing rank permissions", NamedTextColor.GRAY))
            .width(150)
            .action(DialogAction.customClick(NICKNAME_ACTION, null))
            .build()
        val back = createButton(
            MenuEntry("← Item Tools", "/settings items", "Return without changing your nickname"),
            150
        )

        return Dialog.create { factory ->
            factory.empty()
                .base(
                    DialogBase.builder(
                        Component.text("Set Nickname", NamedTextColor.AQUA)
                            .decorate(TextDecoration.BOLD)
                    )
                        .externalTitle(Component.text("Set Nickname", NamedTextColor.AQUA))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(
                            listOf(
                                DialogBody.plainMessage(
                                    Component.text(
                                        "Enter a nickname.",
                                        NamedTextColor.GRAY
                                    ),
                                    310
                                )
                            )
                        )
                        .inputs(
                            listOf(
                                DialogInput.text(
                                    "nickname",
                                    Component.text("Nickname", NamedTextColor.WHITE)
                                )
                                    .width(240)
                                    .initial(initialNickname)
                                    .maxLength(16)
                                    .build()
                            )
                        )
                        .build()
                )
                .type(DialogType.confirmation(submit, back))
        }
    }

    private fun createKillDialog(): Dialog {
        val confirm = createButton(
            MenuEntry("Confirm", "/kill", "Kill your player"),
            150
        )
        val cancel = createButton(
            MenuEntry("Cancel", "/settings player", "Return to Player settings"),
            150
        )

        return Dialog.create { factory ->
            factory.empty()
                .base(
                    DialogBase.builder(
                        Component.text("Self Kill", NamedTextColor.RED)
                            .decorate(TextDecoration.BOLD)
                    )
                        .externalTitle(Component.text("Self Kill", NamedTextColor.RED))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(
                            listOf(
                                DialogBody.plainMessage(
                                    Component.text("Are you sure?", NamedTextColor.GRAY),
                                    200
                                )
                            )
                        )
                        .build()
                )
                .type(DialogType.confirmation(confirm, cancel))
        }
    }

    @EventHandler
    fun onDialogAction(event: PlayerCustomClickEvent) {
        if (event.identifier != NICKNAME_ACTION) return
        val connection = event.commonConnection as? PlayerGameConnection ?: return
        val response = event.dialogResponseView ?: return
        cosmeticsCommand.setNicknameFromDialog(
            connection.player,
            response.getText("nickname") ?: ""
        )
    }

    private fun createButton(entry: MenuEntry, width: Int): ActionButton {
        val click = when (entry.mode) {
            ActionMode.RUN -> ClickEvent.runCommand(entry.command)
            ActionMode.SUGGEST -> ClickEvent.suggestCommand(entry.command)
        }

        val label = Component.text(entry.label, entry.labelColor).let { base ->
            when (entry.state) {
                true -> base.append(Component.text(": ON", NamedTextColor.GREEN))
                false -> base.append(Component.text(": OFF", NamedTextColor.RED))
                null -> if (entry.valueText != null) {
                    base.append(Component.text(": ${entry.valueText}", NamedTextColor.AQUA))
                } else if (entry.actionId != null) {
                    base.append(Component.text(": LOADING", NamedTextColor.YELLOW))
                } else {
                    base
                }
            }
        }

        return ActionButton.builder(label)
            .tooltip(Component.text(entry.tooltip, NamedTextColor.GRAY))
            .width(width)
            .action(DialogAction.staticAction(click))
            .build()
    }

    private fun showChatFallback(player: Player, page: Page) {
        player.sendMessage(Component.empty())
        player.sendMessage(
            Component.text("8b8t Settings - ${page.title}", NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD)
        )
        player.sendMessage(Component.text(page.description, NamedTextColor.GRAY))

        entriesFor(player, page).forEach { entry ->
            val click = when (entry.mode) {
                ActionMode.RUN -> ClickEvent.runCommand(entry.command)
                ActionMode.SUGGEST -> ClickEvent.suggestCommand(entry.command)
            }
            player.sendMessage(
                Component.text("[${entry.label}]", NamedTextColor.GREEN)
                    .clickEvent(click)
                    .hoverEvent(Component.text(entry.tooltip, NamedTextColor.GRAY))
            )
        }

        if (page != Page.ROOT) {
            player.sendMessage(
                Component.text("[Back]", NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.runCommand("/settings"))
            )
        }
    }

    private fun entriesFor(player: Player, page: Page): List<MenuEntry> {
        val entries = when (page) {
            Page.ROOT -> rootEntries(player)
            Page.PLAYER -> playerEntries(player)
            Page.HOMES -> homeEntries(player)
            Page.HOTSPOTS -> HOTSPOT_ENTRIES
            Page.TPA -> tpaEntries(player)
            Page.INFO -> infoEntries()
            Page.ITEMS -> ITEM_ENTRIES
            Page.TABLES -> tableEntries(player)
            Page.CLOSE_ACTIONS -> closeActionEntries(player)
            Page.MISC -> miscEntries(player)
            Page.NICKNAME -> emptyList()
            Page.KILL -> emptyList()
        }
        return entries.filter { entry -> isEntryAvailable(player, entry) }
    }

    private fun isEntryAvailable(player: Player, entry: MenuEntry): Boolean {
        return (
            entry.permissionCommand == null ||
                plugin.getCommand(entry.permissionCommand)?.testPermissionSilent(player) != false
            ) && (entry.requiredPermission == null || player.hasPermission(entry.requiredPermission))
    }

    private fun handleToggle(player: Player, args: Array<String>) {
        val actionId = args.getOrNull(1)?.lowercase()
        val entry = TOGGLE_ENTRIES.firstOrNull { it.actionId == actionId }
        if (entry == null || !isEntryAvailable(player, entry)) {
            player.sendMessage(Component.text("That setting is not available to you.", NamedTextColor.RED))
            return
        }

        val playerId = player.uniqueId
        if (!PENDING_TOGGLE_REFRESH.add(playerId)) return
        val refreshPage = when (args.getOrNull(2)?.lowercase()) {
            Page.TPA.id -> Page.TPA
            else -> Page.PLAYER
        }
        player.performCommand(entry.command.removePrefix("/"))
        player.scheduler.runDelayed(plugin, {
            try {
                if (player.isOnline) {
                    player.showDialog(createDialog(player, refreshPage))
                }
            } finally {
                PENDING_TOGGLE_REFRESH.remove(playerId)
            }
        }, { PENDING_TOGGLE_REFRESH.remove(playerId) }, 5L)
    }

    private fun handleMenuOption(player: Player, args: Array<String>) {
        val info = (plugin.getSectionByName("ChatControl") as? ChatSection)?.getInfo(player)
        if (info == null || !info.dataLoaded) {
            player.sendMessage(Component.text("Settings are still loading.", NamedTextColor.RED))
            return
        }

        val optionId = args.getOrNull(1)?.lowercase()
        val playerId = player.uniqueId
        if (!PENDING_MENU_OPTION.add(playerId)) return
        val refreshPage: Page
        val column: String
        val value: Any
        lateinit var rollback: () -> Unit
        when (optionId) {
            "close_tpa_accept" -> {
                val previous = info.menuCloseTpaAccept
                info.menuCloseTpaAccept = !info.menuCloseTpaAccept
                rollback = { info.menuCloseTpaAccept = previous }
                column = "menuCloseTpaAccept"
                value = info.menuCloseTpaAccept
                refreshPage = Page.CLOSE_ACTIONS
            }
            "close_help" -> {
                val previous = info.menuCloseHelp
                info.menuCloseHelp = !info.menuCloseHelp
                rollback = { info.menuCloseHelp = previous }
                column = "menuCloseHelp"
                value = info.menuCloseHelp
                refreshPage = Page.CLOSE_ACTIONS
            }
            "close_vote" -> {
                val previous = info.menuCloseVote
                info.menuCloseVote = !info.menuCloseVote
                rollback = { info.menuCloseVote = previous }
                column = "menuCloseVote"
                value = info.menuCloseVote
                refreshPage = Page.CLOSE_ACTIONS
            }
            "close_uptime" -> {
                val previous = info.menuCloseUptime
                info.menuCloseUptime = !info.menuCloseUptime
                rollback = { info.menuCloseUptime = previous }
                column = "menuCloseUptime"
                value = info.menuCloseUptime
                refreshPage = Page.CLOSE_ACTIONS
            }
            "close_discord" -> {
                val previous = info.menuCloseDiscord
                info.menuCloseDiscord = !info.menuCloseDiscord
                rollback = { info.menuCloseDiscord = previous }
                column = "menuCloseDiscord"
                value = info.menuCloseDiscord
                refreshPage = Page.CLOSE_ACTIONS
            }
            "no_confirm_kill" -> {
                val previous = info.menuNoConfirmKill
                info.menuNoConfirmKill = !info.menuNoConfirmKill
                rollback = { info.menuNoConfirmKill = previous }
                column = "menuNoConfirmKill"
                value = info.menuNoConfirmKill
                refreshPage = Page.MISC
            }
            "home_columns" -> {
                val previous = info.menuHomeColumns
                info.menuHomeColumns = if (info.menuHomeColumns >= 7) 1 else info.menuHomeColumns + 1
                rollback = { info.menuHomeColumns = previous }
                column = "menuHomeColumns"
                value = info.menuHomeColumns
                refreshPage = Page.MISC
            }
            else -> {
                PENDING_MENU_OPTION.remove(playerId)
                player.sendMessage(Component.text("Unknown menu option.", NamedTextColor.RED))
                return
            }
        }

        database.upsertPlayer(player.name, column, value).whenComplete { _, error ->
            player.scheduler.run(plugin, {
                PENDING_MENU_OPTION.remove(playerId)
                if (!player.isOnline) return@run
                if (error != null) {
                    rollback()
                    player.sendMessage(Component.text("Failed to save that menu option.", NamedTextColor.RED))
                }
                player.showDialog(createDialog(player, refreshPage))
            }, { PENDING_MENU_OPTION.remove(playerId) })
        }
    }

    private fun handleMenuAction(player: Player, args: Array<String>) {
        val info = (plugin.getSectionByName("ChatControl") as? ChatSection)?.getInfo(player)
        val action = when (args.getOrNull(1)?.lowercase()) {
            "tpa_accept" -> MenuAction("tpayes", null, info?.menuCloseTpaAccept ?: true)
            "tpa_deny" -> MenuAction("tpano", null, true)
            "tpa_cancel" -> MenuAction("tpacancel", null, true)
            "help" -> MenuAction("help", "help", info?.menuCloseHelp ?: true)
            "vote" -> MenuAction("vote", null, info?.menuCloseVote ?: true)
            "uptime" -> MenuAction("uptime", "uptime", info?.menuCloseUptime ?: true)
            "tps" -> MenuAction("tpsinfo", "tpsinfo", true)
            "discord" -> MenuAction("discord", "discord", info?.menuCloseDiscord ?: true)
            else -> null
        }
        if (action == null) {
            player.sendMessage(Component.text("Unknown menu action.", NamedTextColor.RED))
            return
        }
        if (
            action.permissionCommand != null &&
            plugin.getCommand(action.permissionCommand)?.testPermissionSilent(player) == false
        ) {
            player.sendMessage(Component.text("That action is not available to you.", NamedTextColor.RED))
            return
        }

        val playerId = player.uniqueId
        if (!PENDING_MENU_ACTION.add(playerId)) return
        player.performCommand(action.command)
        player.scheduler.runDelayed(plugin, {
            try {
                if (player.isOnline && action.closeMenu) player.closeDialog()
            } finally {
                PENDING_MENU_ACTION.remove(playerId)
            }
        }, { PENDING_MENU_ACTION.remove(playerId) }, 1L)
    }

    private fun rootEntries(player: Player): List<MenuEntry> {
        return ROOT_ENTRIES
    }

    private fun playerEntries(player: Player): List<MenuEntry> {
        val info = (plugin.getSectionByName("ChatControl") as? ChatSection)?.getInfo(player)
        val loadedInfo = info?.takeIf { it.dataLoaded }
        val tpa = plugin.getSectionByName("TPA") as? TPASection
        return TOGGLE_ENTRIES
            .filterNot { entry ->
                entry.actionId == "coordinate_spoof" && '.' in player.name
            }
            .map { entry ->
            if (entry.actionId == null) return@map entry
            val state = when (entry.actionId) {
                "chat" -> info?.let { !it.toggledChat }
                "tpa" -> tpa?.let { !it.checkToggle(player) }
                "join" -> info?.joinMessages
                "prefix" -> loadedInfo?.let { !it.hidePrefix }
                "death" -> loadedInfo?.let { !it.hideDeathMessages }
                "announcements" -> loadedInfo?.let { !it.hideAnnouncements }
                "achievements" -> loadedInfo?.let { !it.hideBadges }
                "custom_tab" -> loadedInfo?.let { !it.useVanillaLeaderboard }
                "vanilla_tab" -> loadedInfo?.useVanillaLeaderboard
                "phantoms" -> loadedInfo?.let { !it.preventPhantomSpawn }
                "coordinate_spoof" -> loadedInfo?.coordinateSpoofing
                else -> null
            }
            entry.copy(
                command = "/settings toggle ${entry.actionId}",
                state = state
            )
        }
    }

    private fun tpaEntries(player: Player): List<MenuEntry> {
        val tpa = plugin.getSectionByName("TPA") as? TPASection
        return TPA_ENTRIES.map { entry ->
            if (entry.actionId != "tpa") return@map entry
            entry.copy(
                command = "/settings toggle tpa ${Page.TPA.id}",
                state = tpa?.let { !it.checkToggle(player) }
            )
        }
    }

    private fun infoEntries(): List<MenuEntry> = INFO_ENTRIES

    private fun tableEntries(player: Player): List<MenuEntry> {
        return TABLE_ENTRIES
    }

    private fun closeActionEntries(player: Player): List<MenuEntry> {
        val info = (plugin.getSectionByName("ChatControl") as? ChatSection)?.getInfo(player)
        return CLOSE_ACTION_ENTRIES.map { entry ->
            val state = when (entry.actionId) {
                "close_tpa_accept" -> info?.menuCloseTpaAccept
                "close_help" -> info?.menuCloseHelp
                "close_vote" -> info?.menuCloseVote
                "close_uptime" -> info?.menuCloseUptime
                "close_discord" -> info?.menuCloseDiscord
                else -> null
            }
            entry.copy(state = state)
        }
    }

    private fun miscEntries(player: Player): List<MenuEntry> {
        val info = (plugin.getSectionByName("ChatControl") as? ChatSection)?.getInfo(player)
        return MISC_ENTRIES.map { entry ->
            when (entry.actionId) {
                "no_confirm_kill" -> entry.copy(state = info?.menuNoConfirmKill)
                "home_columns" -> entry.copy(
                    valueText = info?.menuHomeColumns?.coerceIn(1, 7)?.toString() ?: "4"
                )
                else -> entry
            }
        }
    }

    private fun homeEntries(player: Player): List<MenuEntry> {
        val homeManager = plugin.getSectionByName("Home") as? HomeManager
        val homes = homeManager?.getHomes(player.uniqueId)?.getHomes()
            ?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .orEmpty()
        val homeButtons = homes.map { home ->
            MenuEntry(
                "⌂ ${home.name}",
                "/home ${home.name}",
                "Teleport to ${home.name}"
            )
        }
        return homeButtons.ifEmpty {
            listOf(
                MenuEntry(
                    "No Homes Saved",
                    "/settings homes",
                    "Create homes with /sethome <name>"
                )
            )
        }
    }

    private enum class ActionMode {
        RUN,
        SUGGEST
    }

    private data class MenuAction(
        val command: String,
        val permissionCommand: String?,
        val closeMenu: Boolean
    )

    private data class MenuEntry(
        val label: String,
        val command: String,
        val tooltip: String,
        val mode: ActionMode = ActionMode.RUN,
        val permissionCommand: String? = null,
        val requiredPermission: String? = null,
        val actionId: String? = null,
        val state: Boolean? = null,
        val valueText: String? = null,
        val labelColor: NamedTextColor = NamedTextColor.WHITE
    )

    private enum class Page(
        val id: String,
        val title: String,
        val description: String,
        val columns: Int = 2,
        val keepOpenAfterAction: Boolean = false
    ) {
        ROOT(
            "",
            "8b8t Settings",
            "Select a category.",
            columns = 1
        ),
        PLAYER(
            "player",
            "Player",
            "Manage your player settings.",
            keepOpenAfterAction = true
        ),
        HOMES(
            "homes",
            "Homes",
            "Select a home.",
            columns = 4
        ),
        HOTSPOTS(
            "hotspots",
            "Hotspots",
            "Manage your hotspot.",
            columns = 1
        ),
        TPA(
            "tpa",
            "TPA Controls",
            "Manage teleport requests.",
            keepOpenAfterAction = true
        ),
        INFO(
            "info",
            "Server Information",
            "Server information.",
            keepOpenAfterAction = true
        ),
        ITEMS(
            "items",
            "Rank & Item Tools",
            "Nickname and item tools available through your current rank."
        ),
        TABLES(
            "tables",
            "Crafter Tables",
            "Select a table."
        ),
        CLOSE_ACTIONS(
            "menu",
            "Menu",
            "Choose menu behavior.",
            keepOpenAfterAction = true
        ),
        MISC(
            "misc",
            "Misc",
            "Menu options.",
            keepOpenAfterAction = true
        ),
        NICKNAME(
            "nickname",
            "Set Nickname",
            "Enter and save your visible nickname."
        ),
        KILL(
            "kill",
            "Self Kill",
            "Confirm self kill."
        );

        companion object {
            fun from(input: String?): Page? {
                if (input.isNullOrBlank() || input.equals("main", ignoreCase = true)) return ROOT
                return when (input.lowercase()) {
                    "teleport" -> TPA
                    "utilities" -> INFO
                    "close" -> CLOSE_ACTIONS
                    else -> entries.firstOrNull { it.id.equals(input, ignoreCase = true) }
                }
            }
        }
    }

    companion object {
        private const val DIALOG_PROTOCOL = 771
        private val ROOT_DIALOG_KEY = Key.key("8b8t:settings_menu")
        private val NICKNAME_ACTION = Key.key("8b8tcore:nickname")
        private val PENDING_TOGGLE_REFRESH = ConcurrentHashMap.newKeySet<java.util.UUID>()
        private val PENDING_MENU_OPTION = ConcurrentHashMap.newKeySet<java.util.UUID>()
        private val PENDING_MENU_ACTION = ConcurrentHashMap.newKeySet<java.util.UUID>()
        private val JOIN_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        private val PAGES = Page.entries
            .filter { it != Page.ROOT && it != Page.NICKNAME && it != Page.KILL && it != Page.ITEMS }
            .map(Page::id)

        private val ROOT_ENTRIES = listOf(
            MenuEntry("Player", "/settings player", "Player settings"),
            MenuEntry("Homes", "/settings homes", "Saved homes"),
            MenuEntry("Hotspots", "/settings hotspots", "Create or delete your public hotspot"),
            MenuEntry("TPA Controls", "/settings tpa", "Manage incoming and pending TPA requests"),
            MenuEntry("Server Information", "/settings info", "Help, voting, uptime, TPS, and Discord"),
            MenuEntry(
                "Crafter Tables",
                "/settings tables",
                "Open a crafting table",
                permissionCommand = "table"
            ),
            MenuEntry("Menu", "/settings menu", "Choose menu behavior"),
            MenuEntry("Misc", "/settings misc", "More menu options")
        )

        private val TOGGLE_ENTRIES = listOf(
            MenuEntry(
                "Public Chat",
                "/togglechat",
                "Show or hide public chat",
                actionId = "chat"
            ),
            MenuEntry(
                "TPA Requests",
                "/tpatoggle",
                "Enable or disable incoming TPA requests",
                actionId = "tpa"
            ),
            MenuEntry(
                "Join Messages",
                "/togglejoinmessages",
                "Show or hide player join and leave messages",
                permissionCommand = "togglejoinmessages",
                actionId = "join"
            ),
            MenuEntry(
                "Rank Prefix",
                "/toggleprefix",
                "Show or hide your rank prefix",
                permissionCommand = "toggleprefix",
                actionId = "prefix"
            ),
            MenuEntry(
                "Death Messages",
                "/deathmessage",
                "Show or hide death messages",
                permissionCommand = "deathmessage",
                actionId = "death"
            ),
            MenuEntry(
                "Announcements",
                "/announcements",
                "Show or hide server announcements",
                permissionCommand = "announcements",
                actionId = "announcements"
            ),
            MenuEntry(
                "Achievements",
                "/achievements",
                "Show or hide player advancement announcements",
                permissionCommand = "achievements",
                actionId = "achievements"
            ),
            MenuEntry(
                "Custom Tab List",
                "/toggleleaderboard custom",
                "Use the 8b8t custom tab list",
                permissionCommand = "toggleleaderboard",
                actionId = "custom_tab"
            ),
            MenuEntry(
                "Vanilla Tab List",
                "/toggleleaderboard vanilla",
                "Use the vanilla tab list",
                permissionCommand = "toggleleaderboard",
                actionId = "vanilla_tab"
            ),
            MenuEntry(
                "Phantom Spawns",
                "/dps",
                "Enable or disable phantom spawning for yourself",
                permissionCommand = "dps",
                actionId = "phantoms"
            ),
            MenuEntry(
                "Coordinate Spoofing (beta)",
                "/coordinatespoof",
                "Show offset coordinates instead of your real coordinates",
                permissionCommand = "coordinatespoof",
                actionId = "coordinate_spoof"
            ),
            MenuEntry(
                "Sign Held Item",
                "/settings sign",
                "Sign the item in your main hand",
                permissionCommand = "sign"
            ),
            MenuEntry(
                "Self Kill",
                "/settings kill",
                "Open the self-kill confirmation",
                permissionCommand = "kill"
            )
        )

        private val HOTSPOT_ENTRIES = listOf(
            MenuEntry(
                "Create Hotspot",
                "/hotspot create",
                "Create a public hotspot if your rank permits it",
                permissionCommand = "hotspot"
            ),
            MenuEntry(
                "Delete Hotspot",
                "/hotspot delete",
                "Delete your active hotspot",
                permissionCommand = "hotspot"
            )
        )

        private val TPA_ENTRIES = listOf(
            MenuEntry("Accept TPA", "/settings action tpa_accept", "Accept your latest teleport request"),
            MenuEntry("Deny TPA", "/settings action tpa_deny", "Deny your latest teleport request"),
            MenuEntry("Cancel TPA", "/settings action tpa_cancel", "Cancel your outgoing teleport request"),
            MenuEntry(
                "TPA Requests",
                "/tpatoggle",
                "Enable or disable incoming TPA requests",
                actionId = "tpa"
            )
        )

        private val INFO_ENTRIES = listOf(
            MenuEntry("Help", "/settings action help", "Show the server help menu", permissionCommand = "help"),
            MenuEntry("Vote", "/settings action vote", "Show voting links and vote status"),
            MenuEntry(
                "Uptime",
                "/settings action uptime",
                "Show how long the server has been online",
                permissionCommand = "uptime"
            ),
            MenuEntry(
                "TPS Information",
                "/settings action tps",
                "Show server performance information",
                permissionCommand = "tpsinfo"
            ),
            MenuEntry(
                "Discord",
                "/settings action discord",
                "Show the official 8b8t Discord link",
                permissionCommand = "discord"
            )
        )

        private val CLOSE_ACTION_ENTRIES = listOf(
            MenuEntry(
                "TPA Accept",
                "/settings option close_tpa_accept",
                "Close after accepting a TPA",
                actionId = "close_tpa_accept"
            ),
            MenuEntry(
                "Help",
                "/settings option close_help",
                "Close after opening Help",
                actionId = "close_help"
            ),
            MenuEntry(
                "Vote",
                "/settings option close_vote",
                "Close after opening Vote",
                actionId = "close_vote"
            ),
            MenuEntry(
                "Uptime",
                "/settings option close_uptime",
                "Close after viewing Uptime",
                actionId = "close_uptime"
            ),
            MenuEntry(
                "Discord",
                "/settings option close_discord",
                "Close after opening Discord",
                actionId = "close_discord"
            )
        )

        private val MISC_ENTRIES = listOf(
            MenuEntry(
                "No Confirm Kill",
                "/settings option no_confirm_kill",
                "Skip the self-kill confirmation",
                actionId = "no_confirm_kill"
            ),
            MenuEntry(
                "Home Column Size",
                "/settings option home_columns",
                "Change the home list column count",
                actionId = "home_columns"
            )
        )

        private val ITEM_ENTRIES = listOf(
            MenuEntry(
                "Set Nickname",
                "/settings nickname",
                "Enter and save your visible nickname",
                permissionCommand = "cosmetics",
                requiredPermission = "8b8tcore.command.nick"
            ),
            MenuEntry(
                "Clear Nickname",
                "/cosmetics nick clear",
                "Reset your nickname and nickname styling",
                permissionCommand = "cosmetics",
                requiredPermission = "8b8tcore.command.nick"
            ),
            MenuEntry(
                "Rename Held Item",
                "/rename ",
                "Enter a new name for the item in your hand",
                ActionMode.SUGGEST,
                "rename"
            ),
            MenuEntry("Sign Held Item", "/sign", "Sign the item in your main hand", permissionCommand = "sign")
        )

        private val TABLE_ENTRIES = listOf(
            MenuEntry("Crafting", "/table crafting", "Open a crafting table", permissionCommand = "table"),
            MenuEntry("Cartography", "/table cartography", "Open a cartography table", permissionCommand = "table"),
            MenuEntry("Stonecutter", "/table stonecutter", "Open a stonecutter", permissionCommand = "table"),
            MenuEntry("Enchanting", "/table enchanting", "Open an enchanting table", permissionCommand = "table"),
            MenuEntry("Anvil", "/table anvil", "Open an anvil", permissionCommand = "table"),
            MenuEntry("Grindstone", "/table grindstone", "Open a grindstone", permissionCommand = "table"),
            MenuEntry("Loom", "/table loom", "Open a loom", permissionCommand = "table"),
            MenuEntry("Smithing", "/table smithing", "Open a smithing table", permissionCommand = "table")
        )
    }
}
