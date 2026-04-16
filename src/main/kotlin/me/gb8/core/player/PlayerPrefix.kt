/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.player

import me.gb8.core.player.PrefixManager
import me.gb8.core.database.GeneralDatabase
import me.gb8.core.util.FoliaCompat
import me.gb8.core.util.GlobalUtils
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.permissions.PermissionAttachment
import org.bukkit.plugin.java.JavaPlugin

import me.gb8.core.util.GlobalUtils.executeCommand

class PlayerPrefix(private val plugin: JavaPlugin) : Listener {
    private val prefixManager = PrefixManager()
    private val database = GeneralDatabase.getInstance()
    private val miniMessage = MiniMessage.miniMessage()

    fun reloadConfig() {
    }

    fun handlePlayerJoin(player: Player) {
        if (player.name.startsWith(".")) {
            val attachment = player.addAttachment(plugin)
            attachment.setPermission("nocheatplus.shortcut.bypass", true)
        }

        val prefixFuture = prefixManager.getPrefixAsync(player)
        val nicknameFuture = database.getNicknameAsync(player.name)

        prefixFuture.thenAcceptBoth(nicknameFuture) { tag, _ ->
            if (!player.isOnline) return@thenAcceptBoth
            FoliaCompat.schedule(player, plugin) {
                setupTag(player, tag)
                GlobalUtils.updateDisplayNameAsync(player)
            }
        }.exceptionally { ex ->
            plugin.logger.warning("Error setting up player prefix/nickname for ${player.name}: ${ex.message}")
            null
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        handlePlayerJoin(event.player)
    }

    private fun setupTag(player: Player, tag: String) {
        val name = player.displayName()
        player.playerListName(if (tag.isEmpty()) name else miniMessage.deserialize(tag).append(name))
    }
}
