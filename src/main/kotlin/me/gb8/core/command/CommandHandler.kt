/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command

import me.gb8.core.command.commands.*
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.PluginCommand
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player

class CommandHandler(private val main: CommandSection) : TabExecutor {
    private val commands = mutableMapOf<String, BaseCommand>()
    val commandMap: Map<String, BaseCommand> get() = commands

    fun registerCommands() {
        addCommand(BaseCmd(main))
        addCommand(DiscordCommand(main))
        addCommand(HelpCommand())
        addCommand(OpenInv())
        addCommand(SayCommand())
        addCommand(SpawnCommand())
        addCommand(SpeedCommand())
        addCommand(UptimeCommand(main.plugin))
        addCommand(WorldSwitcher(main.plugin))
        addCommand(TpsinfoCommand(main.plugin))

        addCommand(ToggleJoinMessagesCommand(main.plugin))
        addCommand(TogglePrefixCommand(main.plugin))
        addCommand(ToggleDeathMessageCommand(main.plugin))
        addCommand(ToggleAnnouncementsCommand(main.plugin))
        addCommand(ToggleAchievementsCommand())
        addCommand(RenameCommand())
        addCommand(SignCommand())
        addCommand(ShadowMuteCommand(main.plugin))
        addCommand(VanishCommand(main.plugin))
        addCommand(ClearEntitiesCommand())
        addCommand(GmCreativeCommand())
        addCommand(GmSpectatorCommand())
        addCommand(GmSurvivalCommand())
        addCommand(TableCommand())
        addCommand(JoinDateCommand(main.plugin))
        addCommand(KillCommand(main.plugin))
        addCommand(LastSeenCommand(main.plugin))
        addCommand(ToggleLeaderboardCommand(main.plugin))
        addCommand(DpsCommand())
        addCommand(CosmeticsCommand(main.plugin))
    }

    private fun addCommand(command: BaseCommand) {
        commands[command.name.lowercase()] = command
        main.plugin.getCommand(command.name)?.let { cmd ->
            cmd.setExecutor(this)
            cmd.setTabCompleter(this)
            if (command.permissions.isNotEmpty()) {
                cmd.setPermission(command.permissions[0])
            }
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
                command.subCommands.map { it.split("::")[0] }
                    .filter { it.startsWith(args[0].lowercase()) }
            }
            args.size > 1 -> {
                Bukkit.getOnlinePlayers().map { it.name }
                    .filter { it.lowercase().startsWith(args.last().lowercase()) }
            }
            else -> Bukkit.getOnlinePlayers().map { it.name }
        }
    }
}
