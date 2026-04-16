/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.util.FoliaCompat
import me.gb8.core.util.GlobalUtils
import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage
import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.Chunk
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
        
        if (entity is Item) {
            val stack = entity.itemStack
            if (isIllegalItem(stack)) {
                entity.remove()
                return
            }
        } 
        
        if (entity is ItemFrame) {
            val stack = entity.item
            if (isIllegalItem(stack)) {
                entity.setItem(ItemStack(Material.AIR))
                FoliaCompat.schedule(entity, me.gb8.core.Main.instance) { entity.remove() }
            }
            return
        }

        val name = entity.customName()
        if (name != null) {
            val depth = GlobalUtils.getComponentDepth(name)
            val content = GlobalUtils.getStringContent(name)
            if (depth > 20 || content.length > 500) {
                entity.customName(null)
                FoliaCompat.schedule(entity, me.gb8.core.Main.getInstance()) { entity.remove() }
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
        val chunk = event.chunk
        for (state in chunk.tileEntities) {
            if (state is CreatureSpawner) {
                if (state.spawnCount > 100 || state.requiredPlayerRange > 100) {
                    state.block.type = Material.AIR
                }
            } else if (state is Beehive) {
                if (state.entityCount > 5) {
                    state.block.type = Material.AIR
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSpawnerSpawn(event: SpawnerSpawnEvent) {
        val entity = event.entity
        if (entity is FallingBlock || entity.type == EntityType.FALLING_BLOCK) {
            event.isCancelled = true
            event.spawner?.block?.type = Material.AIR
        } else if (entity is Item || entity is ItemFrame) {
            event.isCancelled = true
            event.spawner?.block?.type = Material.AIR
        }
    }

    private fun isIllegalItem(item: ItemStack?): Boolean {
        if (item == null || item.type == Material.AIR) return false
        
        try {
            if (isBundle(item.type)) {
                if (checkBundleRecursion(item, 0)) {
                    return true
                }
            }
            
            if (item.hasItemMeta()) {
                val size = calculateItemSize(item)
                if (size > maxItemSizeBytes) {
                    return true
                }
            }
        } catch (e: Exception) {
            return false
        }
        
        return false
    }

    private fun sanitizeInventory(player: Player) {
        val contents = player.inventory.contents
        var modified = false

        for (i in contents.indices) {
            val item = contents[i] ?: continue
            
            if (item.type == Material.AIR) continue

            if (!item.hasItemMeta()) continue

            val itemSize = calculateItemSize(item)

            if (itemSize > maxItemSizeBytes) {
                val itemName = getItemName(item)
                logger.warn("NBT Patch: Prevented overloaded NBT item from {} | Size: {} bytes | Type: {}", 
                    player.name, itemSize, itemName)

                player.inventory.setItem(i, null)
                
                sendPrefixedLocalizedMessage(player, "nbtPatch_deleted_item", itemName)
                modified = true
                continue
            }

            if (isBundle(item.type)) {
                if (checkBundleRecursion(item, 0)) {
                    val itemName = getItemName(item)
                    logger.warn("NBT Patch: Prevented bundle crash from {} | Type: {}", player.name, itemName)
                    player.inventory.setItem(i, null)
                    sendPrefixedLocalizedMessage(player, "nbtPatch_deleted_item", itemName)
                    modified = true
                }
            }
        }
        
        if (modified) player.updateInventory()
    }

    private fun calculateItemSize(item: ItemStack): Int {
        return GlobalUtils.calculateItemSize(item)
    }

    
    fun getItemName(itemStack: ItemStack?): String {
        if (itemStack == null) return "Unknown"
        try {
            val meta = itemStack.itemMeta
            if (meta != null && meta.hasDisplayName()) {
                val displayName = meta.displayName
                return displayName.ifEmpty { itemStack.type.name }
            }
        } catch (_: Exception) { }
        return itemStack.type.name
    }
    
    private fun checkBundleRecursion(item: ItemStack?, depth: Int): Boolean {
        if (item == null || !isBundle(item.type)) return false
        if (depth >= 1) return true
        try {
            if (item.hasItemMeta() && item.itemMeta is BundleMeta) {
                val bundleMeta = item.itemMeta as BundleMeta
                for (inner in bundleMeta.items) {
                    if (inner.type == Material.AIR) continue
                    if (isBundle(inner.type)) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {
            return true
        }
        return false
    }

    private fun isBundle(type: Material): Boolean {
        return type.name.endsWith("BUNDLE") || type.name.contains("SHULKER")
    }
}
