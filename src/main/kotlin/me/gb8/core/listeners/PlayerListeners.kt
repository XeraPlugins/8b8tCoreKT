/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.antiillegal.AntiIllegalMain
import me.gb8.core.antiillegal.Utils.checkStand
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.entity.ThrownPotion
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityResurrectEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.*
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory

class PlayerListeners(private val main: AntiIllegalMain) : Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player

        val effectCheck = main.effectCheck
        effectCheck.fixPlayerEffects(player)

        val inv = player.inventory
        main.checkFixItem(inv.itemInMainHand, null)
        main.checkFixItem(inv.itemInOffHand, null)

        for (armor in inv.armorContents) {
            if (armor != null) main.checkFixItem(armor, null)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEnderChestOpen(event: InventoryOpenEvent) {
        if (event.inventory.type == InventoryType.ENDER_CHEST) {
            for (item in event.inventory.contents) {
                if (item != null) main.checkFixItem(item, null)
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onDropItem(event: PlayerDropItemEvent) {
        main.checkFixItem(event.itemDrop.itemStack, null)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onOffhand(event: PlayerSwapHandItemsEvent) {
        main.checkFixItem(event.mainHandItem, event)
        main.checkFixItem(event.offHandItem, event)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPickup(event: PlayerAttemptPickupItemEvent) {
        main.checkFixItem(event.item.itemStack, null)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val inv = event.player.inventory
        val usedItem = if (event.hand == EquipmentSlot.OFF_HAND) inv.itemInOffHand else inv.itemInMainHand
        val otherItem = if (event.hand == EquipmentSlot.OFF_HAND) inv.itemInMainHand else inv.itemInOffHand

        if (main.checkFixItem(usedItem, event)) {
            if (usedItem?.type != org.bukkit.Material.ENDER_PEARL) {
                event.isCancelled = true
            }
        }
        main.checkFixItem(otherItem, event)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onResurrect(event: EntityResurrectEvent) {
        val player = event.entity as? Player ?: return
        val inv = player.inventory
        main.checkFixItem(inv.itemInMainHand, event)
        main.checkFixItem(inv.itemInOffHand, event)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onEntityInteract(event: PlayerInteractEntityEvent) {
        val inv = event.player.inventory
        val usedItem = if (event.hand == EquipmentSlot.OFF_HAND) inv.itemInOffHand else inv.itemInMainHand
        val otherItem = if (event.hand == EquipmentSlot.OFF_HAND) inv.itemInMainHand else inv.itemInOffHand

        if (main.checkFixItem(usedItem, event)) {
            event.isCancelled = true
        }
        main.checkFixItem(otherItem, event)
        val clicked = event.rightClicked
        if (clicked is ArmorStand) {
            checkStand(clicked, main)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onArmorStandManipulate(event: PlayerArmorStandManipulateEvent) {
        main.checkFixItem(event.playerItem, event)
        main.checkFixItem(event.armorStandItem, event)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onProjectileLaunch(event: ProjectileLaunchEvent) {
        val potion = event.entity as? ThrownPotion ?: return
        val shooter = potion.shooter
        if (shooter is Player) {
            main.checkFixItem(potion.item, event)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onDeath(event: PlayerDeathEvent) {
        for (item in event.drops) {
            main.checkFixItem(item, null)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        for (item in event.player.inventory.contents) {
            if (item != null) main.checkFixItem(item, null)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        main.checkFixItem(player.inventory.itemInOffHand, event)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onAttack(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        val inv = player.inventory
        val mainHandIllegal = main.checkFixItem(inv.itemInMainHand, event)
        val offHandIllegal = main.checkFixItem(inv.itemInOffHand, event)
        if (mainHandIllegal || offHandIllegal) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (main.checkFixItem(event.itemInHand, event)) {
            event.isCancelled = true
        }
    }
}
