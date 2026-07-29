/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.patch.PatchSection
import org.bukkit.entity.Entity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPlaceEvent
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.event.hanging.HangingPlaceEvent
import org.bukkit.event.vehicle.VehicleCreateEvent

class EntitySpawnListener(private val main: PatchSection) : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntitySpawn(event: EntitySpawnEvent) {
        if (exceedsLimit(event.entity)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityPlace(event: EntityPlaceEvent) {
        if (exceedsLimit(event.entity)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onHangingPlace(event: HangingPlaceEvent) {
        if (exceedsLimit(event.entity)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onVehicleCreate(event: VehicleCreateEvent) {
        if (exceedsLimit(event.vehicle)) event.isCancelled = true
    }

    private fun exceedsLimit(entity: Entity): Boolean {
        if (entity.isExemptFromEntityLimit()) return false

        val type = entity.type
        val entityPerChunk = main.getEntityPerChunk() ?: return false
        val max = entityPerChunk[type] ?: return false

        val currentCount = 1 + entity.location.chunk.entities.count {
            it.uniqueId != entity.uniqueId &&
                it.type == type &&
                !it.isExemptFromEntityLimit()
        }

        return currentCount > max
    }
}
