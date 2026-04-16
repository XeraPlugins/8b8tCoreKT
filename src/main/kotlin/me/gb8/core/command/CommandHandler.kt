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
    val commands = mutableListOf<BaseCommand>()

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
        commands.add(command)
        val cmd: PluginCommand? = main.plugin.getCommand(command.name)
        cmd?.let {
            it.setExecutor(this)
            it.setTabCompleter(this)
            if (command.permissions.isNotEmpty()) {
                it.setPermission(command.permissions[0])
            }
        }
    }

    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<String>): Boolean {
        for (command in commands) {
            if (command.name.equals(cmd.name, ignoreCase = true)) {
                var hasPermission = false
                for (permission in command.permissions) {
                    if (sender.hasPermission(permission) || sender.isOp || sender.hasPermission("*")) {
                        hasPermission = true
                        break
                    }
                }

                if (hasPermission) {
                    command.execute(sender, args)
                } else {
                    command.sendNoPermission(sender)
                }
                break
            }
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, cmd: Command, alias: String, args: Array<String>): List<String> {
        for (command in commands) {
            if (command.name.equals(cmd.name, ignoreCase = true)) {
                var hasPermission = false
                for (permission in command.permissions) {
                    if (sender.hasPermission(permission) || sender.isOp || sender.hasPermission("*")) {
                        hasPermission = true
                        break
                    }
                }
                if (!hasPermission) return emptyList()

                if (command is BaseTabCommand) return command.onTab(sender, args)
                if (command.subCommands != null && args.size == 1) {
                    return command.subCommands.map { it.split("::")[0] }
                        .filter { it.startsWith(args[0].lowercase()) }
                } else if (args.size > 1) {
                    return Bukkit.getOnlinePlayers().map { it.name }
                        .filter { it.lowercase().startsWith(args[args.size - 1].lowercase()) }
                } else {
                    return Bukkit.getOnlinePlayers().map { it.name }
                }
            }
        }
        return listOf("")
    }
}
