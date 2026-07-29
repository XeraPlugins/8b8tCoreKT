/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.listeners

import com.vexsoftware.votifier.model.VotifierEvent
import me.gb8.core.vote.VoteSection
import me.gb8.core.util.FoliaCompat
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class VotifierListener(private val voteSection: VoteSection) : Listener {

    @EventHandler
    fun onVote(event: VotifierEvent) {
        val username = event.vote.username.lowercase()
        Bukkit.getGlobalRegionScheduler().run(voteSection.plugin) {
            val player = Bukkit.getPlayerExact(username)
            voteSection.registerVote(username).whenComplete { voteAccepted, error ->
                if (error != null) {
                    voteSection.plugin.logger.warning("Failed to register vote for $username: ${error.cause?.message ?: error.message}")
                    return@whenComplete
                }

                if (!voteAccepted) {
                    if (player != null && player.isOnline) {
                        FoliaCompat.schedule(player, voteSection.plugin) {
                            val remainingDays = voteSection.getRemainingVoterDays(username)
                            player.sendMessage("§cYou already have the voter role! It expires in $remainingDays days.")
                        }
                    }
                    return@whenComplete
                }

                if (player != null && player.isOnline) {
                    FoliaCompat.schedule(player, voteSection.plugin) {
                        if (player.isOnline) voteSection.rewardPlayer(player) else voteSection.announceVote(username)
                    }
                } else {
                    voteSection.announceVote(username)
                }
            }
        }
    }
}
