/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.dupe

import me.gb8.core.Main
import me.gb8.core.Section
import me.gb8.core.dupe.DupeCommand

import org.bukkit.configuration.ConfigurationSection
import kotlin.jvm.JvmName

class DupeSection(override val plugin: Main) : Section {
    private var config: ConfigurationSection? = null
    override val name: String = "Dupe"

    @JvmName("retrievePlugin")
    fun getPlugin(): Main = plugin

    override fun enable() {
        config = plugin.getSectionConfig(this)
        plugin.register(FrameDupe(plugin))
        plugin.register(ZombieDupe(plugin))
        plugin.register(DonkeyDupe(plugin))
    }

    override fun disable() {}

    override fun reloadConfig() {
        config = plugin.getSectionConfig(this)
    }
}
