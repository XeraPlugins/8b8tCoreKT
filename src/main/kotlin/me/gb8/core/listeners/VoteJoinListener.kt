/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import me.gb8.core.vote.PlayerName
import me.gb8.core.vote.VoteSection
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class VoteJoinListener(private val main: VoteSection) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player: Player = event.player
        val username = player.name.lowercase()

        main.checkAndMigrateLegacyPlayer(player.name)

        if (main.toReward.containsKey(PlayerName(username))) {
            if (!main.hasVoterRoleExpired(username)) {
                main.getToRewardEntry(player).ifPresent { voteCount ->
                    if (voteCount > 0) {
                        main.rewardOfflineVotes(player, voteCount)
                    }
                }
            }
        }
    }
}
