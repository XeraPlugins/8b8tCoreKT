/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command

import me.gb8.core.Main
import me.gb8.core.Section
import org.bukkit.configuration.ConfigurationSection

class CommandSection(override val plugin: Main) : Section {
    var commandHandler: CommandHandler? = null
    var config: ConfigurationSection? = null

    override fun enable() {
        commandHandler = CommandHandler(this)
        config = plugin.getSectionConfig(this)
        commandHandler?.registerCommands()
    }

    override fun disable() {}

    override val name: String = "Commands"

    override fun reloadConfig() {
        config = plugin.getSectionConfig(this)
    }
}
