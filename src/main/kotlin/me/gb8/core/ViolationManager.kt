/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

open class ViolationManager @JvmOverloads constructor(
    private val addAmount: Int,
    private val removeAmount: Int = addAmount,
    protected val plugin: Main? = null
) {
    private val map = ConcurrentHashMap<UUID, Int>()

    fun decrementAll() {
        for (uuid in map.keys) {
            map.computeIfPresent(uuid) { _, `val` ->
                val newVal = `val` - removeAmount
                if (newVal <= 0) null else newVal
            }
        }
    }

    fun increment(uuid: UUID) {
        map.compute(uuid) { _, `val` -> `val`?.let { it + addAmount } ?: addAmount }
    }

    fun getVLS(uuid: UUID): Int {
        return map.getOrDefault(uuid, 0)
    }

    fun remove(uuid: UUID) {
        map.remove(uuid)
    }
}
