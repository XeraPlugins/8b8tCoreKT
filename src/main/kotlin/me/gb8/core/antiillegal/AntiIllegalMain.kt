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
import io.papermc.paper.event.block.BlockPreDispenseEvent
import io.papermc.paper.persistence.PersistentDataContainerView
import me.gb8.core.listeners.*
import me.gb8.core.util.GlobalUtils
import me.gb8.core.Localization
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.event.Cancellable
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import java.util.logging.Level

class AntiIllegalMain(override val plugin: Main) : Section {
    var informPlayer = true

    private var config: ConfigurationSection? = null
    val checks = mutableListOf<Check>()
    val effectCheck = PlayerEffectCheck()
    private lateinit var itemSizeCheck: ItemSizeCheck
    private lateinit var illegalItemCheck: IllegalItemCheck
    private lateinit var nameCheck: NameCheck

    companion object {
        var debug = false
    }

    override fun enable() {
        config = plugin.getSectionConfig(this)
        val cfg = config ?: return

        if (checks.isEmpty()) {
            itemSizeCheck = ItemSizeCheck(plugin.config.getInt("NbtBanItemChecker.maxItemSizeAllowed", 48000))
            illegalItemCheck = IllegalItemCheck(cfg.getStringList("IllegalItems"))
            nameCheck = NameCheck(cfg.getInt("MaxItemNameLength", 255))
            checks.addAll(listOf(
                OverStackCheck(),
                DurabilityCheck(),
                AttributeCheck(),
                EnchantCheck(),
                PotionCheck(),
                BookCheck(),
                itemSizeCheck,
                LegacyTextCheck(),
                illegalItemCheck,
                IllegalDataCheck(),
                AntiPrefilledContainers(),
                ContainerContentCheck(this),
                effectCheck,
                nameCheck
            ))
        }

        listOf(
            PlayerListeners(this),
            MiscListeners(this),
            InventoryListeners(this),
            AttackListener(plugin),
            PlayerEffectListener(plugin, this),
            EntityEffectListener(plugin)
        ).forEach { plugin.register(it) }

        plugin.register(IllegalBlocksCleaner(plugin, cfg))
    }

    override fun disable() {}

    override fun reloadConfig() {
        config = plugin.getSectionConfig(this)
        val cfg = config ?: return
        if (::itemSizeCheck.isInitialized) {
            itemSizeCheck.updateMaxSize(plugin.config.getInt("NbtBanItemChecker.maxItemSizeAllowed", 48000))
        }
        if (::illegalItemCheck.isInitialized) {
            illegalItemCheck.updateIllegalItems(cfg.getStringList("IllegalItems"))
        }
        if (::nameCheck.isInitialized) {
            nameCheck.updateMaxNameLength(cfg.getInt("MaxItemNameLength", 255))
        }
    }

    override val name: String = "AntiIllegal"

    fun checkFixItem(item: ItemStack?, cancellable: Cancellable?): Boolean {
        item?.takeIf { it.type != Material.AIR } ?: return false

        var wasIllegal = false

        for (check in checks) {
            if (!check.shouldCheck(item) || !check.check(item)) continue

            wasIllegal = true
            logDebug(item, check)
            notifyPlayerIfNeeded(item, check, cancellable)
            check.fix(item)

            if (item.type == Material.AIR || item.amount <= 0) {
                (cancellable as? BlockPreDispenseEvent)?.isCancelled = true
                break
            }
        }

        if (wasIllegal && item.amount > 0 && item.hasItemMeta()) {
            item.itemMeta = item.itemMeta
        }

        return wasIllegal
    }

    private fun logDebug(item: ItemStack, check: Check) {
        if (debug) {
            GlobalUtils.log(Level.INFO, "&cItem %s flagged by %s", item.type.toString(), check::class.simpleName)
        }
    }

    private fun notifyPlayerIfNeeded(item: ItemStack, check: Check, cancellable: Cancellable?) {
        if (!informPlayer) return
        if (cancellable !is BlockPlaceEvent && cancellable !is PlayerInteractEvent) return

        val player = when (cancellable) {
            is BlockPlaceEvent -> cancellable.player
            is PlayerInteractEvent -> cancellable.player
            else -> null
        } ?: return

        runCatching {
            val checkName = getCheckName(item, check)
            val loc = Localization.getLocalization(player.locale().toString())
            val msg = loc.get("antiillegal_flagged_placement").format(checkName)
            GlobalUtils.sendMessage(player, "${Main.prefix} >> $msg")
            if (debug) GlobalUtils.log(Level.INFO, "Sent message to %s: %s", player.name, msg)
        }.onFailure { t ->
            if (debug) GlobalUtils.log(Level.WARNING, "Failed to send localized message: %s", t.message)
        }
    }

    private fun getCheckName(item: ItemStack, check: Check): String {
        if (check !is ContainerContentCheck) return check::class.simpleName ?: "Unknown"

        val pdc: PersistentDataContainerView = item.persistentDataContainer
        val key = NamespacedKey(plugin, "last_failed_check")
        return pdc.get(key, PersistentDataType.STRING) ?: check::class.simpleName ?: "Unknown"
    }
}
