/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.home

import org.bukkit.Location

data class Home(
    val name: String,
    val worldName: String,
    val location: Location
)
