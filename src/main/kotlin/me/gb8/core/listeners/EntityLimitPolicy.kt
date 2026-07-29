/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 *
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import org.bukkit.entity.ChestedHorse
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

internal fun Entity.isExemptFromEntityLimit(): Boolean {
    return this is Player || this is ChestedHorse && isCarryingChest
}

internal fun Entity.isSubjectToEntityCleanup(): Boolean {
    return this is LivingEntity && !isExemptFromEntityLimit()
}
