/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import me.gb8.core.command.BaseTabCommand
import org.bukkit.command.CommandSender
import org.bukkit.entity.EntityType
import me.gb8.core.util.GlobalUtils.sendMessage

class SpawnCommand : BaseTabCommand(
    "spawn",
    "/spawn <EntityType> <amount>",
    "8b8tcore.command.spawnshit"
) {
    private val entityTypes: List<String>

    init {
        entityTypes = getEntityTypes()
    }

    override fun execute(sender: CommandSender, args: Array<String>) {
        getSenderAsPlayer(sender)?.let { player ->
            if (args.size >= 1 && args.size <= 2) {
                try {
                    if (entityTypes.contains(args[0])) {
                        val count = if (args.size == 2) args[1].toInt() else 1
                        val entityType = EntityType.valueOf(args[0].uppercase())
                        for (i in 0 until count) player.world.spawnEntity(player.location, entityType)
                    } else sendMessage(sender, "&cInvalid entity&r&a %s", args[0])
                } catch (e: NumberFormatException) {
                    sendErrorMessage(sender, "Invalid argument type the argument " + args[0] + " must be a number")
                }
            } else sendErrorMessage(sender, usage)
        } ?: sendMessage(sender, "&c%s", PLAYER_ONLY)
    }

    private fun getEntityTypes(): List<String> {
        return EntityType.entries.map { it.name.lowercase() }
    }

    override fun onTab(sender: CommandSender, args: Array<String>): List<String> {
        return if (args.isEmpty()) entityTypes
        else entityTypes.filter { it.startsWith(args[0].lowercase()) }
    }
}
