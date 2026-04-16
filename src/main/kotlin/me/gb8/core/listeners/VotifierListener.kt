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
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class VotifierListener(private val voteSection: VoteSection) : Listener {

    @EventHandler
    fun onVote(event: VotifierEvent) {
        val username = event.vote.username.lowercase()
        val player = Bukkit.getPlayerExact(username)
        val voteAccepted = voteSection.registerVote(username)

        if (!voteAccepted) {
            if (player != null && player.isOnline) {
                val remainingDays = voteSection.getRemainingVoterDays(username)
                player.sendMessage("§cYou already have the voter role! It expires in $remainingDays days.")
            }
            return
        }

        if (player != null && player.isOnline) {
            voteSection.rewardPlayer(player)
        } else {
            voteSection.announceVote(username)
        }
    }
}
