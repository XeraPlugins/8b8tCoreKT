/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.dupe

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import me.gb8.core.Main
import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage
import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.entity.Item
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

class FrameDupe(private val plugin: Main) : Listener {

    private val cooldowns: Cache<UUID, Long> = CacheBuilder.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build()

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onFrameInteract(event: EntityDamageByEntityEvent) {
        val itemFrame = event.entity as? ItemFrame ?: return
        val player = event.damager as? Player ?: return

        if (!plugin.config.getBoolean("FrameDupe.enabled", true)) return

        val votersOnly = plugin.config.getBoolean("FrameDupe.votersOnly", false)
        if (votersOnly && !player.hasPermission("8b8tcore.dupe.frame")) return

        val probability = plugin.config.getInt("FrameDupe.probabilityPercentage", 100)
        if (probability < 100) {
            if (ThreadLocalRandom.current().nextInt(100) >= probability) {
                return
            }
        }

        val cooldownTime = plugin.config.getLong("FrameDupe.dupeCooldown", 200L)
        val chunkUUID = getChunkUUID(itemFrame.location.chunk)

        val lastDupe = cooldowns.getIfPresent(chunkUUID)
        if (lastDupe != null && System.currentTimeMillis() - lastDupe < cooldownTime) {
            sendPrefixedLocalizedMessage(player, "framedupe_cooldown")
            return
        }

        val maxItems = plugin.config.getInt("FrameDupe.limitItemsPerChunk", 18)
        if (countItemsInChunk(itemFrame.location.chunk) >= maxItems) {
            sendPrefixedLocalizedMessage(player, "framedupe_items_limit")
            return
        }

        val itemStack = itemFrame.item
        if (itemStack.type != Material.AIR) {
            itemFrame.world.dropItemNaturally(itemFrame.location, itemStack.clone())
            cooldowns.put(chunkUUID, System.currentTimeMillis())
        }
    }

    private fun countItemsInChunk(chunk: Chunk): Int {
        var count = 0
        for (entity in chunk.entities) {
            if (entity is Item) {
                count++
            }
        }
        return count
    }

    private fun getChunkUUID(chunk: Chunk): UUID {
        val x = chunk.x
        val z = chunk.z
        return UUID(chunk.world.uid.mostSignificantBits, (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL))
    }
}
