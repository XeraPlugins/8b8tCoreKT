/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import me.gb8.core.Main
import me.gb8.core.command.BaseCommand
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.util.UUID
import me.gb8.core.util.GlobalUtils.sendMessage

class JihadCommand(private val plugin: Main) : BaseCommand(
    "jihad",
    "/jihad",
    "8b8tcore.command.jihad",
    "Gives TNT and flint & steel for explosive fun"
) {
    private val cooldowns = HashMap<UUID, Long>()
    
    init {
        // No-op init
    }

    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player ?: run {
            sendMessage(sender, "&cThis command can only be used by players!")
            return
        }

        val playerId = player.uniqueId
        val currentTime = System.currentTimeMillis()

        if (cooldowns.containsKey(playerId)) {
            val lastUsed = cooldowns[playerId] ?: return
            val timeLeft = (lastUsed + COOLDOWN_TIME) - currentTime

            if (timeLeft > 0) {
                val secondsLeft = timeLeft / 1000
                sendMessage(player, "&cYou must wait &e$secondsLeft &cseconds before using this command again!")
                return
            }
        }

        val tnt = ItemStack(Material.TNT, 64)
        val tntMeta = tnt.itemMeta
        tntMeta.displayName(Component.text("WINST0N").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
        tnt.itemMeta = tntMeta
        
        val lighter = ItemStack(Material.FLINT_AND_STEEL, 1)
        val lighterMeta = lighter.itemMeta
        lighterMeta.displayName(Component.text("John's ALLAHU AKBAR").color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))
        lighterMeta.addEnchant(Enchantment.UNBREAKING, 3, false)
        lighter.itemMeta = lighterMeta
        
        player.inventory.addItem(tnt, lighter)
        cooldowns[playerId] = currentTime

        sendMessage(player, "&a&lALLAHU AKBAR! &r&7You have received explosive materials!")
        sendMessage(player, "&eUse &6/jihad &eagain in 15 seconds!")
    }

    companion object {
        private const val COOLDOWN_TIME: Long = 15000
    }
}
