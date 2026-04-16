/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.deathmessages

import me.gb8.core.util.GlobalUtils.sendDeathMessage
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DeathMessageListener : Listener {

    private val deathTracker = ConcurrentHashMap<UUID, Long>()
    private val COOLDOWN_MS = 30_000L

    @EventHandler(priority = EventPriority.HIGHEST)
    fun handleDeath(event: PlayerDeathEvent) {
        event.deathMessage(null)
        
        val deceased = event.entity
        val deceasedId = deceased.uniqueId
        val now = System.currentTimeMillis()

        if (now - deathTracker.getOrDefault(deceasedId, 0L) < COOLDOWN_MS) return
        if (deceased.lastDamageCause == null) return
        if (hasProtectionTotem(deceased)) return

        deathTracker[deceasedId] = now

        val attacker = deceased.killer
        val cause = DeathCause.resolve(deceased.lastDamageCause?.cause ?: return)

        if (cause == DeathCause.UNKNOWN) return

        if (attacker != null) {
            val weapon = getMainHandItem(attacker)
            val weaponLabel = formatWeaponName(weapon)
            var messageKey = DeathCause.PLAYER.configPath
            
            if (attacker.uniqueId == deceasedId) {
                messageKey = DeathCause.END_CRYSTAL.configPath
            }
            
            sendDeathMessage(messageKey, deceased.name, attacker.name, weaponLabel)
        } else if (cause != DeathCause.ENTITY_ATTACK) {
            sendDeathMessage(cause.configPath, deceased.name, "", "")
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun handleEntityDamage(event: EntityDamageByEntityEvent) {
        val target = event.entity as? Player ?: return
        val targetId = target.uniqueId
        val now = System.currentTimeMillis()

        if (now - deathTracker.getOrDefault(targetId, 0L) < COOLDOWN_MS) return
        if (hasProtectionTotem(target)) return
        if (target.health - event.finalDamage > 0) return

        var killer: Entity = event.damager

        if (event.damager is Projectile) {
            val shooter = (event.damager as Projectile).shooter
            if (shooter is Entity) killer = shooter
        }

        var cause = DeathCause.resolve(killer.type)

        if (cause == DeathCause.WITHER) {
            cause = DeathCause.WITHER_BOSS
        }

        when (killer) {
            is Player -> {
                if (cause == DeathCause.END_CRYSTAL || cause == DeathCause.UNKNOWN) return

                val weapon = getMainHandItem(killer)
                val weaponLabel = formatWeaponName(weapon)

                deathTracker[targetId] = now
                sendDeathMessage(DeathCause.PLAYER.configPath, target.name, killer.name, weaponLabel)
            }
            else -> {
                if (cause == DeathCause.END_CRYSTAL || cause == DeathCause.UNKNOWN) return

                deathTracker[targetId] = now
                sendDeathMessage(cause.configPath, target.name, killer.name, "")
            }
        }
    }

    private fun hasProtectionTotem(player: Player): Boolean {
        val inv = player.inventory
        return inv.itemInMainHand.type == Material.TOTEM_OF_UNDYING ||
               inv.itemInOffHand.type == Material.TOTEM_OF_UNDYING
    }

    private fun getMainHandItem(player: Player): ItemStack {
        return player.inventory.itemInMainHand
    }

    private fun formatWeaponName(item: ItemStack?): String {
        if (item == null) return ""
        
        val meta = item.itemMeta
        return when {
            meta != null && meta.hasDisplayName() -> {
                val display = meta.displayName()
                if (display != null) MiniMessage.miniMessage().serialize(display)
                else item.type.name.lowercase().replace("_", " ")
            }
            else -> item.type.name.lowercase().replace("_", " ")
        }
    }
}
