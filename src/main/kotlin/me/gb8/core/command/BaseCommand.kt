/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command

import me.gb8.core.Main
import me.gb8.core.util.GlobalUtils
import me.gb8.core.util.GlobalUtils.sendMessage
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

abstract class BaseCommand(
    val name: String,
    val usage: String,
    val permissions: Array<String>,
    val description: String?,
    val subCommands: Array<String>?
) {
    protected val CONSOLE_ONLY = "This command is console only"
    protected val PLAYER_ONLY = "This command is player only"
    protected val PREFIX = Main.prefix

    constructor(name: String, usage: String, permission: String) : this(name, usage, arrayOf(permission), null, null)

    constructor(name: String, usage: String, permission: String, description: String?) : this(name, usage, arrayOf(permission), description, null)

    constructor(name: String, usage: String, permission: Array<String>, description: String?) : this(name, usage, permission, description, null)

    constructor(name: String, usage: String, permission: String, description: String?, subCommands: Array<String>?) : this(name, usage, arrayOf(permission), description, subCommands)

    fun sendNoPermission(sender: CommandSender) {
        sendMessage(sender, "&cYou are lacking the permission&r&a %s", permissions.joinToString(", "))
    }

    fun sendErrorMessage(sender: CommandSender, message: String) {
        sendMessage(sender, "&c%s", message)
    }

    fun getSenderAsPlayer(sender: CommandSender): Player? {
        return sender as? Player
    }

    fun getPermission(): String? {
        return if (permissions.size > 1 || permissions.size == 1) permissions[0] else null
    }

    abstract fun execute(sender: CommandSender, args: Array<String>)
}
