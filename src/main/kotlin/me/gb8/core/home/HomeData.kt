/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.home

class HomeData {
    private val homes = mutableListOf<Home>()

    constructor()

    constructor(homesList: List<Home>) {
        homes.addAll(homesList)
    }

    fun addHome(home: Home) {
        homes.add(home)
    }

    fun deleteHome(home: Home) {
        homes.remove(home)
    }

    fun stream(): Sequence<Home> = homes.asSequence()

    fun hasHomes(): Boolean = homes.isNotEmpty()

    fun getHomes(): List<Home> = homes.toList()
}
