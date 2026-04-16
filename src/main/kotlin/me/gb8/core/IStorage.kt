/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core

interface IStorage<T, F> {
    fun save(data: T, id: F)
    fun load(id: F): T
    fun delete(id: F)
}
