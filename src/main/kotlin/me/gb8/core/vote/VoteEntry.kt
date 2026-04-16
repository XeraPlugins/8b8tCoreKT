/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.vote

class VoteEntry {
    var count: Int = 0
    var timestamp: Long = 0

    constructor()

    constructor(count: Int, timestamp: Long) {
        this.count = count
        this.timestamp = timestamp
    }

    constructor(count: Int) {
        this.count = count
        this.timestamp = System.currentTimeMillis()
    }

    fun isExpired(expirationDays: Int): Boolean {
        if (expirationDays <= 0) return false
        val expirationTime = timestamp + (expirationDays * 24L * 60L * 60L * 1000L)
        return System.currentTimeMillis() > expirationTime
    }

    fun addVote() {
        this.count++
    }

    fun decrementVote() {
        if (this.count > 0) {
            this.count--
        }
    }
}
