/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.vote

data class VoteEntry(
    var count: Int = 0,
    var timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired(expirationDays: Int): Boolean {
        if (expirationDays <= 0) return false
        val expirationTime = timestamp + (expirationDays * 24L * 60L * 60L * 1000L)
        return System.currentTimeMillis() > expirationTime
    }

    fun addVote() {
        count++
    }

    fun decrementVote() {
        if (count > 0) count--
    }
}