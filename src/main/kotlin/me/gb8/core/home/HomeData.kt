/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.home

import java.util.ArrayList
import java.util.Collections
import java.util.List
import java.util.stream.Stream

class HomeData {
    private val homes: MutableList<Home> = ArrayList()

    constructor()

    constructor(homesList: List<Home>) {
        this.homes.addAll(homesList)
    }

    fun addHome(home: Home) {
        homes.add(home)
    }

    fun deleteHome(home: Home) {
        homes.remove(home)
    }

    fun stream(): Stream<Home> = homes.stream()

    fun hasHomes(): Boolean = homes.isNotEmpty()

    @Suppress("UNCHECKED_CAST")
    fun getHomes(): java.util.List<Home> {
        val list = homes.toList()
        return list as java.util.List<Home>
    }
}
