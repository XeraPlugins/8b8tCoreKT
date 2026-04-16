/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command

abstract class BaseTabCommand : BaseCommand {

    constructor(name: String, usage: String, permission: String) : super(name, usage, permission, null, null)

    constructor(name: String, usage: String, permission: String, description: String?) : super(name, usage, arrayOf(permission), description, null)

    constructor(name: String, usage: String, permission: String, description: String?, subCommands: Array<String>?) : super(name, usage, arrayOf(permission), description, subCommands)

    constructor(name: String, usage: String, permissions: Array<String>, description: String?, subCommands: Array<String>?) : super(name, usage, permissions, description, subCommands)

    abstract fun onTab(sender: org.bukkit.command.CommandSender, args: Array<String>): List<String>
}
