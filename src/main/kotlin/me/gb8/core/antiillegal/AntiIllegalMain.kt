/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.antiillegal

import me.gb8.core.Main
import me.gb8.core.Section
import me.gb8.core.antiillegal.Check
import me.gb8.core.antiillegal.*
import me.gb8.core.listeners.*
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.event.Cancellable
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import io.papermc.paper.persistence.PersistentDataContainerView
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import java.util.logging.Level

class AntiIllegalMain(override val plugin: Main) : Section {
    var informPlayer = true

    private var config: ConfigurationSection? = null
    val checks = mutableListOf<Check>()
    val effectCheck = PlayerEffectCheck()

    companion object {
        var debug = false
    }

    override fun enable() {
        if (config == null) config = plugin.getSectionConfig(this)
        val cfg = config ?: return

        if (checks.isEmpty()) {
            checks.addAll(listOf(
                OverStackCheck(),
                DurabilityCheck(),
                AttributeCheck(),
                EnchantCheck(),
                PotionCheck(),
                BookCheck(),
                LegacyTextCheck(),
                IllegalItemCheck(),
                IllegalDataCheck(),
                AntiPrefilledContainers(),
                ContainerContentCheck(this),
                effectCheck
            ))
        }

        checks.add(NameCheck(cfg))

        plugin.register(
            PlayerListeners(this),
            MiscListeners(this),
            InventoryListeners(this),
            AttackListener(plugin),
            PlayerEffectListener(plugin, this),
            EntityEffectListener(plugin)
        )

        if (cfg.getBoolean("EnableIllegalBlocksCleaner", true)) {
            plugin.register(IllegalBlocksCleaner(plugin, cfg))
        }
    }

    override fun disable() {}

    override fun reloadConfig() {
        config = plugin.getSectionConfig(this)
    }

    override val name: String = "AntiIllegal"

    fun checkFixItem(item: ItemStack?, cancellable: Cancellable?): Boolean {
        if (item == null || item.type == Material.AIR) return false

        val isInventoryOpen = cancellable is InventoryOpenEvent
        var wasIllegal = false

        for (check in checks) {
            if (check is AntiPrefilledContainers) {
                if (!isInventoryOpen) continue
            }

            if (!check.shouldCheck(item)) continue
            if (!check.check(item)) continue

            wasIllegal = true
            if (debug) me.gb8.core.util.GlobalUtils.log(Level.INFO, "&cItem %s flagged by %s", item.type.toString(), check::class.simpleName)
            if (informPlayer && (cancellable is BlockPlaceEvent || cancellable is PlayerInteractEvent)) {
                val player: org.bukkit.entity.Player = when (cancellable) {
                    is BlockPlaceEvent -> cancellable.player
                    is PlayerInteractEvent -> cancellable.player
                    else -> null
                } ?: continue
                try {
                    var checkName = check::class.simpleName
                    if (check is me.gb8.core.antiillegal.ContainerContentCheck) {
                        val pdc: PersistentDataContainerView = item.persistentDataContainer
                        val key = NamespacedKey(plugin, "last_failed_check")
                        if (pdc.has(key, PersistentDataType.STRING)) {
                            checkName = pdc.get(key, PersistentDataType.STRING) ?: checkName
                        }
                    }
                    
                    val loc = me.gb8.core.Localization.getLocalization(player.locale.toString())
                    val msg = String.format(loc.get("antiillegal_flagged_placement"), checkName)
                    val prefixMsg = me.gb8.core.util.GlobalUtils.translateChars(Main.prefix + " >> " + msg)
                    player.sendMessage(prefixMsg)
                    if (debug) me.gb8.core.util.GlobalUtils.log(Level.INFO, "Sent message to %s: %s", player.name, msg)
                } catch (t: Throwable) {
                    if (debug) me.gb8.core.util.GlobalUtils.log(Level.WARNING, "Failed to send localized message: %s", t.message)
                }
            }
            check.fix(item)

            if (item.hasItemMeta()) {
                item.itemMeta = item.itemMeta
            }

            if (item.type == Material.AIR || item.amount <= 0) {
                if (cancellable is io.papermc.paper.event.block.BlockPreDispenseEvent) {
                    cancellable.isCancelled = true
                }
            }
        }
        return wasIllegal
    }
}
