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
import org.bukkit.entity.Arrow
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.entity.Zombie
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

class ZombieDupe(private val plugin: Main) : Listener {

    private val cooldowns: Cache<UUID, Long> = CacheBuilder.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build()

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityHitByArrow(event: EntityDamageByEntityEvent) {
        val zombie = event.entity as? Zombie ?: return
        val arrow = event.damager as? Arrow ?: return
        val player = arrow.shooter as? Player ?: return

        if (!plugin.config.getBoolean("ZombieDupe.enabled", true)) return

        val votersOnly = plugin.config.getBoolean("ZombieDupe.votersOnly", false)
        if (votersOnly && !player.hasPermission("8b8tcore.dupe.zombie")) return

        val isGliding = player.isGliding
        val isJumpFalling = player.hasPotionEffect(PotionEffectType.JUMP_BOOST) &&
            player.location.block.type == Material.AIR &&
            player.velocity.y < 0

        if (isGliding || isJumpFalling) {
            event.damage = 0.0
            arrow.remove()

            val probability = plugin.config.getInt("ZombieDupe.probabilityPercentage", 100)
            if (probability < 100) {
                if (ThreadLocalRandom.current().nextInt(100) >= probability) return
            }

            val cooldownTime = plugin.config.getLong("ZombieDupe.dupeCooldown", 200L)
            val chunkUUID = getChunkUUID(zombie.location.chunk)

            val lastDupe = cooldowns.getIfPresent(chunkUUID)
            if (lastDupe != null && System.currentTimeMillis() - lastDupe < cooldownTime) {
                sendPrefixedLocalizedMessage(player, "framedupe_cooldown")
                return
            }

            val maxItems = plugin.config.getInt("ZombieDupe.limitItemsPerChunk", 18)
            if (countItemsInChunk(zombie.location.chunk) >= maxItems) {
                sendPrefixedLocalizedMessage(player, "framedupe_items_limit")
                return
            }

            val itemInHand = zombie.equipment?.itemInMainHand
            if (itemInHand != null && itemInHand.type != Material.AIR) {
                zombie.world.dropItemNaturally(zombie.location, itemInHand.clone())

                val fleshChance = plugin.config.getInt("ZombieDupe.rottenFleshDropPercentage", 50)
                if (ThreadLocalRandom.current().nextInt(100) < fleshChance) {
                    zombie.world.dropItemNaturally(zombie.location, ItemStack(Material.ROTTEN_FLESH))
                }

                cooldowns.put(chunkUUID, System.currentTimeMillis())
            }
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
