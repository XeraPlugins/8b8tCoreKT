/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command

import me.gb8.core.command.commands.*
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor

class CommandHandler(private val main: CommandSection) : TabExecutor {
    private val commands = mutableMapOf<String, BaseCommand>()
    val commandMap: Map<String, BaseCommand> get() = commands

    fun registerCommands() {
        val cosmeticsCommand = CosmeticsCommand(main.plugin)
        listOf(
            BaseCmd(main),
            DiscordCommand(main),
            HelpCommand(),
            SettingsCommand(main.plugin, cosmeticsCommand),
            CoordinateSpoofCommand(main.plugin),
            OpenInv(),
            SayCommand(),
            SpawnCommand(),
            SpeedCommand(),
            UptimeCommand(main.plugin),
            WorldSwitcher(main.plugin),
            TpsinfoCommand(main.plugin),
            ToggleJoinMessagesCommand(main.plugin),
            TogglePrefixCommand(main.plugin),
            ToggleDeathMessageCommand(main.plugin),
            ToggleAnnouncementsCommand(main.plugin),
            ToggleAchievementsCommand(),
            RenameCommand(),
            SignCommand(),
            ShadowMuteCommand(main.plugin),
            VanishCommand(main.plugin),
            ClearEntitiesCommand(),
            GmCreativeCommand(),
            GmSpectatorCommand(),
            GmSurvivalCommand(),
            TableCommand(),
            JihadCommand(main.plugin),
            JoinDateCommand(main.plugin),
            KillCommand(main.plugin),
            LastSeenCommand(main.plugin),
            ToggleLeaderboardCommand(main.plugin),
            DpsCommand(),
            cosmeticsCommand,
            ReloadConfigCommand(main.plugin)
        ).forEach(::addCommand)
    }

    private fun addCommand(command: BaseCommand) {
        commands[command.name.lowercase()] = command
        main.plugin.getCommand(command.name)?.let { cmd ->
            cmd.setExecutor(this)
            cmd.setTabCompleter(this)
            command.permissions.firstOrNull()?.let(cmd::setPermission)
        }
    }

    private fun hasPermission(sender: CommandSender, command: BaseCommand): Boolean {
        return command.permissions.any { permission ->
            sender.hasPermission(permission) || sender.isOp || sender.hasPermission("*")
        }
    }

    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<String>): Boolean {
        val command = commands[cmd.name.lowercase()] ?: return true

        if (hasPermission(sender, command)) {
            command.execute(sender, args)
        } else {
            command.sendNoPermission(sender)
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, cmd: Command, alias: String, args: Array<String>): List<String> {
        val command = commands[cmd.name.lowercase()] ?: return emptyList()

        if (!hasPermission(sender, command)) return emptyList()

        return when {
            command is BaseTabCommand -> command.onTab(sender, args)
            command.subCommands != null && args.size == 1 -> {
                val prefix = args[0].lowercase()
                command.subCommands.map { it.split("::")[0] }
                    .filter { it.startsWith(prefix, ignoreCase = true) }
            }
            args.size > 1 -> {
                val prefix = args.last()
                main.plugin.visibleOnlinePlayers(sender).map { it.name }
                    .filter { it.startsWith(prefix, ignoreCase = true) }
            }
            else -> main.plugin.visibleOnlinePlayers(sender).map { it.name }
        }
    }
}
