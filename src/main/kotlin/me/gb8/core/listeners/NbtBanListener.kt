/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.Main
import me.gb8.core.util.FoliaCompat
import me.gb8.core.util.GlobalUtils
import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage
import org.bukkit.Material
import org.bukkit.block.Beehive
import org.bukkit.block.CreatureSpawner
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.FallingBlock
import org.bukkit.entity.Item
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.SpawnerSpawnEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BundleMeta
import org.bukkit.plugin.java.JavaPlugin
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent
import org.apache.logging.log4j.LogManager

class NbtBanListener(private val plugin: JavaPlugin) : Listener {
    private val maxItemSizeBytes = plugin.config.getInt("NbtBanItemChecker.maxItemSizeAllowed", 48000)
    private val logger = LogManager.getLogger()

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        sanitizeInventory(event.player)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        sanitizeInventory(event.player)
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onEntityAdd(event: EntityAddToWorldEvent) {
        val entity = event.entity

        when (entity) {
            is Item -> {
                if (isIllegalItem(entity.itemStack)) {
                    entity.remove()
                    return
                }
            }
            is ItemFrame -> {
                if (isIllegalItem(entity.item)) {
                    entity.setItem(ItemStack(Material.AIR))
                    FoliaCompat.schedule(entity, Main.instance) { entity.remove() }
                }
                return
            }
            else -> {
                entity.customName()?.let { name ->
                    val depth = GlobalUtils.getComponentDepth(name)
                    val content = GlobalUtils.getStringContent(name)
                    if (depth > 20 || content.length > 500) {
                        entity.customName(null)
                        FoliaCompat.schedule(entity, Main.instance) { entity.remove() }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPlace(event: org.bukkit.event.block.BlockPlaceEvent) {
        val item = event.itemInHand
        if (isIllegalItem(item)) {
            event.isCancelled = true
            event.player.inventory.setItemInMainHand(null)
            sendPrefixedLocalizedMessage(event.player, "nbtPatch_deleted_item", getItemName(item))
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkLoad(event: ChunkLoadEvent) {
        event.chunk.tileEntities.forEach { state ->
            when (state) {
                is CreatureSpawner -> {
                    if (state.spawnCount > 100 || state.requiredPlayerRange > 100) {
                        state.block.type = Material.AIR
                    }
                }
                is Beehive -> {
                    if (state.entityCount > 5) {
                        state.block.type = Material.AIR
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSpawnerSpawn(event: SpawnerSpawnEvent) {
        val entity = event.entity
        when {
            entity is FallingBlock || entity.type == EntityType.FALLING_BLOCK -> {
                event.isCancelled = true
                event.spawner?.block?.type = Material.AIR
            }
            entity is Item || entity is ItemFrame -> {
                event.isCancelled = true
                event.spawner?.block?.type = Material.AIR
            }
        }
    }

    private fun isIllegalItem(item: ItemStack?): Boolean {
        return item?.takeIf { it.type != Material.AIR }?.let { stack ->
            runCatching {
                when {
                    isBundle(stack.type) && checkBundleRecursion(stack, 0) -> true
                    stack.hasItemMeta() && calculateItemSize(stack) > maxItemSizeBytes -> true
                    else -> false
                }
            }.getOrDefault(false)
        } ?: false
    }

    private fun sanitizeInventory(player: Player) {
        val contents = player.inventory.contents
        var modified = false

        contents.forEachIndexed { index, item ->
            item?.takeIf { it.type != Material.AIR && it.hasItemMeta() }?.let { stack ->
                when {
                    calculateItemSize(stack) > maxItemSizeBytes -> {
                        val itemName = getItemName(stack)
                        logger.warn("NBT Patch: Prevented overloaded NBT item from {} | Size: {} bytes | Type: {}",
                            player.name, calculateItemSize(stack), itemName)
                        player.inventory.setItem(index, null)
                        sendPrefixedLocalizedMessage(player, "nbtPatch_deleted_item", itemName)
                        modified = true
                    }
                    isBundle(stack.type) && checkBundleRecursion(stack, 0) -> {
                        val itemName = getItemName(stack)
                        logger.warn("NBT Patch: Prevented bundle crash from {} | Type: {}", player.name, itemName)
                        player.inventory.setItem(index, null)
                        sendPrefixedLocalizedMessage(player, "nbtPatch_deleted_item", itemName)
                        modified = true
                    }
                }
            }
        }

        if (modified) player.updateInventory()
    }

    private fun calculateItemSize(item: ItemStack): Int {
        return GlobalUtils.calculateItemSize(item)
    }

    fun getItemName(itemStack: ItemStack?): String {
        return itemStack?.let { stack ->
            stack.itemMeta?.displayName?.takeIf { it.isNotEmpty() } ?: stack.type.name
        } ?: "Unknown"
    }

    private fun checkBundleRecursion(item: ItemStack?, depth: Int): Boolean {
        if (item == null || !isBundle(item.type)) return false
        if (depth >= 1) return true

        return try {
            (item.itemMeta as? BundleMeta)?.items?.any { inner ->
                inner.type != Material.AIR && isBundle(inner.type)
            } ?: false
        } catch (_: Exception) {
            true
        }
    }

    private fun isBundle(type: Material): Boolean =
        type.name.endsWith("BUNDLE") || type.name.contains("SHULKER")
}