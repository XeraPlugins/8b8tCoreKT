/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.tpa

import me.gb8.core.Main
import me.gb8.core.Section
import me.gb8.core.tpa.commands.*
import me.gb8.core.util.FoliaCompat
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage

class TPASection(override val plugin: Main) : Section {
    val main: Main get() = plugin
    private val lastRequest = ConcurrentHashMap<UUID, UUID>()
    private val requests = ConcurrentHashMap<UUID, CopyOnWriteArrayList<UUID>>()
    private val hereRequests = ConcurrentHashMap<UUID, CopyOnWriteArrayList<UUID>>()
    private val toggledPlayers = ConcurrentHashMap<UUID, Boolean>()
    private val blockedPlayers = ConcurrentHashMap<UUID, CopyOnWriteArrayList<UUID>>()
    
    private var config: ConfigurationSection? = null
    private var requestTimeoutMinutes = 5

    override fun enable() {
        config = plugin.getSectionConfig(this)
        requestTimeoutMinutes = config?.getInt("RequestTimeoutInMinutes", 5) ?: 5
        plugin.register(LeaveListener(this))
        plugin.getCommand("tpa")?.setExecutor(TPACommands.TPACommand(this))
        plugin.getCommand("tpahere")?.setExecutor(TPACommands.TPAHereCommand(this))
        plugin.getCommand("tpayes")?.setExecutor(TPACommands.TPAAcceptCommand(this))
        plugin.getCommand("tpano")?.setExecutor(TPACommands.TPADenyCommand(this))
        plugin.getCommand("tpacancel")?.setExecutor(TPACommands.TPACancelCommand(this))
        plugin.getCommand("tpatoggle")?.setExecutor(TPACommands.TPAToggleCommand(this))
    }

    override fun disable() {
        lastRequest.clear()
        requests.clear()
        hereRequests.clear()
        toggledPlayers.clear()
        blockedPlayers.clear()
    }

    override fun reloadConfig() {
        config = plugin.getSectionConfig(this)
        requestTimeoutMinutes = config?.getInt("RequestTimeoutInMinutes", 5) ?: 5
    }

    override val name: String = "TPA"

    fun registerRequest(requester: Player, requested: Player) {
        registerRequestInternal(requester, requested, requests)
    }

    fun registerHereRequest(requester: Player, requested: Player) {
        registerRequestInternal(requester, requested, hereRequests)
    }

    private fun registerRequestInternal(
        requester: Player,
        requested: Player,
        requestMap: ConcurrentHashMap<UUID, CopyOnWriteArrayList<UUID>>
    ) {
        val requesterUUID = requester.uniqueId
        val requestedUUID = requested.uniqueId

        lastRequest[requestedUUID] = requesterUUID
        val requestList = requestMap.getOrPut(requestedUUID) { CopyOnWriteArrayList() }
        if (!requestList.contains(requesterUUID)) requestList.add(requesterUUID)

        Bukkit.getAsyncScheduler().runDelayed(plugin, {
            val currentList = requestMap[requestedUUID]
            if (currentList == null || !currentList.contains(requesterUUID)) return@runDelayed

            sendTimeoutMessage(requesterUUID, requestedUUID)

            currentList.remove(requesterUUID)
            if (currentList.isEmpty()) {
                requestMap.remove(requestedUUID)
                lastRequest.remove(requestedUUID)
            } else {
                lastRequest[requestedUUID] = currentList[0]
            }
        }, requestTimeoutMinutes.toLong(), TimeUnit.MINUTES)
    }

    private fun sendTimeoutMessage(requesterUUID: UUID, requestedUUID: UUID) {
        Bukkit.getPlayer(requestedUUID)?.takeIf { it.isOnline }?.let { requested ->
            FoliaCompat.schedule(requested, plugin) {
                Bukkit.getPlayer(requesterUUID)?.let { req ->
                    sendPrefixedLocalizedMessage(requested, "tpa_request_timeout_to", req.name)
                }
            }
        }

        Bukkit.getPlayer(requesterUUID)?.takeIf { it.isOnline }?.let { requester ->
            FoliaCompat.schedule(requester, plugin) {
                Bukkit.getPlayer(requestedUUID)?.let { reqd ->
                    sendPrefixedLocalizedMessage(requester, "tpa_request_timeout_from", reqd.name)
                }
            }
        }
    }

    fun getLastRequest(requested: Player): Player? {
        val uuid = lastRequest[requested.uniqueId]
        return uuid?.let { Bukkit.getPlayer(it) }
    }

    fun hasRequested(requester: Player, requested: Player): Boolean {
        return requests[requested.uniqueId]?.contains(requester.uniqueId) == true
    }

    fun hasHereRequested(requester: Player, requested: Player): Boolean {
        return hereRequests[requested.uniqueId]?.contains(requester.uniqueId) == true
    }

    fun removeRequest(requester: Player, requested: Player) {
        removeFromMap(requests, requester.uniqueId, requested.uniqueId)
    }

    fun removeHereRequest(requester: Player, requested: Player) {
        removeFromMap(hereRequests, requester.uniqueId, requested.uniqueId)
    }

    private fun removeFromMap(map: ConcurrentHashMap<UUID, CopyOnWriteArrayList<UUID>>, requesterUUID: UUID, requestedUUID: UUID) {
        val list = map[requestedUUID] ?: return
        
        val wasFirst = list.isNotEmpty() && list[0] == requesterUUID
        list.remove(requesterUUID)
        
        if (list.isEmpty()) {
            map.remove(requestedUUID)
            lastRequest.remove(requestedUUID)
        } else if (wasFirst) {
            lastRequest[requestedUUID] = list[0]
        }
    }

    fun removeAllInRequests(player: Player) {
        val playerUUID = player.uniqueId
        
        notifyAndRemove(requests, playerUUID, "tpa_request_denied_from", player.name)
        notifyAndRemove(hereRequests, playerUUID, "tpa_request_denied_from", player.name)
        lastRequest.remove(playerUUID)
    }

    private fun notifyAndRemove(map: ConcurrentHashMap<UUID, CopyOnWriteArrayList<UUID>>, playerUUID: UUID, messageKey: String, playerName: String) {
        val requesters = map.remove(playerUUID) ?: return
        
        for (requesterUUID in requesters) {
            val requester = Bukkit.getPlayer(requesterUUID)
            if (requester != null && requester.isOnline) {
                FoliaCompat.schedule(requester, plugin) {
                    sendPrefixedLocalizedMessage(requester, messageKey, playerName)
                }
            }
        }
    }

    fun getRequests(to: Player): List<Player> {
        return uuidsToPlayers(requests.getOrDefault(to.uniqueId, CopyOnWriteArrayList()))
    }

    fun getHereRequests(to: Player): List<Player> {
        return uuidsToPlayers(hereRequests.getOrDefault(to.uniqueId, CopyOnWriteArrayList()))
    }

    private fun uuidsToPlayers(uuids: List<UUID>): List<Player> {
        return uuids.mapNotNull { Bukkit.getPlayer(it) }
    }

    fun togglePlayer(player: Player) {
        val uuid = player.uniqueId
        toggledPlayers[uuid] = toggledPlayers[uuid] != true
        removeAllInRequests(player)
    }

    fun checkToggle(player: Player): Boolean {
        return toggledPlayers[player.uniqueId] == true
    }

    fun addBlockedPlayer(requested: Player, requester: Player) {
        val blockedList = blockedPlayers.getOrPut(requested.uniqueId) { CopyOnWriteArrayList() }
        if (!blockedList.contains(requester.uniqueId)) blockedList.add(requester.uniqueId)
        removeRequest(requester, requested)
        removeHereRequest(requester, requested)
    }

    fun removeBlockedPlayer(requested: Player, requester: Player) {
        val list = blockedPlayers[requested.uniqueId]
        list?.remove(requester.uniqueId)
    }

    fun checkBlocked(requested: Player, requester: Player): Boolean {
        return blockedPlayers[requested.uniqueId]?.contains(requester.uniqueId) == true
    }

    fun cleanupPlayer(uuid: UUID) {
        lastRequest.remove(uuid)
        requests.remove(uuid)
        hereRequests.remove(uuid)
        toggledPlayers.remove(uuid)
        blockedPlayers.remove(uuid)
        requests.values.forEach { it.remove(uuid) }
        hereRequests.values.forEach { it.remove(uuid) }
        blockedPlayers.values.forEach { it.remove(uuid) }
    }
}
