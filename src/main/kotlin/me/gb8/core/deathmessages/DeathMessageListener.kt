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
import java.util.concurrent.ConcurrentHashMap

@JvmInline
value class PlayerId(val value: java.util.UUID)

class DeathMessageListener : Listener {

    private val deathTracker = ConcurrentHashMap<PlayerId, Long>()
    private val COOLDOWN_MS = 30_000L

    companion object {
        private val miniMessage = MiniMessage.miniMessage()
        private val TOTEM_MATERIAL = Material.TOTEM_OF_UNDYING
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun handleDeath(event: PlayerDeathEvent) {
        event.deathMessage(null)

        val deceased = event.entity
        val deceasedId = PlayerId(deceased.uniqueId)
        val now = System.currentTimeMillis()

        if (now - deathTracker.getOrDefault(deceasedId, 0L) < COOLDOWN_MS) return
        if (deceased.lastDamageCause == null) return
        if (hasProtectionTotem(deceased)) return

        deathTracker[deceasedId] = now

        val attacker = deceased.killer
        val cause = DeathCause.resolve(deceased.lastDamageCause?.cause ?: return)

        if (cause == DeathCause.UNKNOWN) return

        attacker?.let { atk ->
            val weaponLabel = formatWeaponName(getMainHandItem(atk))
            val messageKey = if (atk.uniqueId == deceased.uniqueId) DeathCause.END_CRYSTAL.configPath else DeathCause.PLAYER.configPath
            sendDeathMessage(messageKey, deceased.name, atk.name, weaponLabel)
        } ?: run {
            if (cause != DeathCause.ENTITY_ATTACK) {
                sendDeathMessage(cause.configPath, deceased.name, "", "")
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun handleEntityDamage(event: EntityDamageByEntityEvent) {
        val target = event.entity as? Player ?: return
        val targetId = PlayerId(target.uniqueId)
        val now = System.currentTimeMillis()

        if (now - deathTracker.getOrDefault(targetId, 0L) < COOLDOWN_MS) return
        if (hasProtectionTotem(target)) return
        if (target.health - event.finalDamage > 0) return

        val killer: Entity? = (event.damager as? Projectile)?.shooter as? Entity ?: event.damager

        var cause = killer?.type?.let { DeathCause.resolve(it) } ?: DeathCause.UNKNOWN

        if (cause == DeathCause.WITHER) cause = DeathCause.WITHER_BOSS

        when (killer) {
            is Player -> handlePlayerKill(target, targetId, now, cause)
            else -> killer?.let { handleEntityKill(target, targetId, it, now, cause) }
        }
    }

    private fun handlePlayerKill(target: Player, targetId: PlayerId, now: Long, cause: DeathCause) {
        if (cause == DeathCause.END_CRYSTAL || cause == DeathCause.UNKNOWN) return
        val killer = target.killer ?: return

        deathTracker[targetId] = now
        val weaponLabel = formatWeaponName(getMainHandItem(killer))
        sendDeathMessage(DeathCause.PLAYER.configPath, target.name, killer.name, weaponLabel)
    }

    private fun handleEntityKill(target: Player, targetId: PlayerId, killer: Entity, now: Long, cause: DeathCause) {
        if (cause == DeathCause.END_CRYSTAL || cause == DeathCause.UNKNOWN) return

        deathTracker[targetId] = now
        sendDeathMessage(cause.configPath, target.name, killer.name, "")
    }

    private fun hasProtectionTotem(player: Player): Boolean {
        val inv = player.inventory
        return inv.itemInMainHand.type == TOTEM_MATERIAL || inv.itemInOffHand.type == TOTEM_MATERIAL
    }

    private fun getMainHandItem(player: Player): ItemStack = player.inventory.itemInMainHand

    private fun formatWeaponName(item: ItemStack?): String {
        return item?.let { stack ->
            stack.itemMeta?.takeIf { it.hasDisplayName() }?.let { meta ->
                meta.displayName()?.let { display ->
                    miniMessage.serialize(display)
                }
            } ?: stack.type.name.lowercase().replace("_", " ")
        } ?: ""
    }
}