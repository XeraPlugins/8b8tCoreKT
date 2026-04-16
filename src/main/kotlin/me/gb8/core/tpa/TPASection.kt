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
        val requesterUUID = requester.uniqueId
        val requestedUUID = requested.uniqueId
        
        lastRequest[requestedUUID] = requesterUUID
        val requestList = requests.getOrPut(requestedUUID) { CopyOnWriteArrayList() }
        if (!requestList.contains(requesterUUID)) requestList.add(requesterUUID)

        Bukkit.getAsyncScheduler().runDelayed(plugin, {
            @Suppress("NAME_SHADOWING")
            val requestList = requests[requestedUUID]
            if (requestList == null || !requestList.contains(requesterUUID)) return@runDelayed
            
            sendTimeoutMessage(requesterUUID, requestedUUID)
            
            requestList.remove(requesterUUID)
            if (requestList.isEmpty()) {
                requests.remove(requestedUUID)
                lastRequest.remove(requestedUUID)
            } else {
                lastRequest[requestedUUID] = requestList[0]
            }
        }, requestTimeoutMinutes.toLong(), TimeUnit.MINUTES)
    }

    fun registerHereRequest(requester: Player, requested: Player) {
        val requesterUUID = requester.uniqueId
        val requestedUUID = requested.uniqueId
        
        lastRequest[requestedUUID] = requesterUUID
        val hereList = hereRequests.getOrPut(requestedUUID) { CopyOnWriteArrayList() }
        if (!hereList.contains(requesterUUID)) hereList.add(requesterUUID)

        Bukkit.getAsyncScheduler().runDelayed(plugin, {
            val requestList = hereRequests[requestedUUID]
            if (requestList == null || !requestList.contains(requesterUUID)) return@runDelayed
            
            sendTimeoutMessage(requesterUUID, requestedUUID)
            
            requestList.remove(requesterUUID)
            if (requestList.isEmpty()) {
                hereRequests.remove(requestedUUID)
                lastRequest.remove(requestedUUID)
            } else {
                lastRequest[requestedUUID] = requestList[0]
            }
        }, requestTimeoutMinutes.toLong(), TimeUnit.MINUTES)
    }

    private fun sendTimeoutMessage(requesterUUID: UUID, requestedUUID: UUID) {
        val requester = Bukkit.getPlayer(requesterUUID)
        val requested = Bukkit.getPlayer(requestedUUID)
        
        if (requested != null && requested.isOnline) {
            FoliaCompat.schedule(requested, plugin) {
                val req = Bukkit.getPlayer(requesterUUID)
                if (req != null) {
                    sendPrefixedLocalizedMessage(requested, "tpa_request_timeout_to", req.name)
                }
            }
        }
        
        if (requester != null && requester.isOnline) {
            FoliaCompat.schedule(requester, plugin) {
                val reqd = Bukkit.getPlayer(requestedUUID)
                if (reqd != null) {
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
        val list = requests[requested.uniqueId]
        return list != null && list.contains(requester.uniqueId)
    }

    fun hasHereRequested(requester: Player, requested: Player): Boolean {
        val list = hereRequests[requested.uniqueId]
        return list != null && list.contains(requester.uniqueId)
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
        val players = ArrayList<Player>(uuids.size)
        for (uuid in uuids) {
            val p = Bukkit.getPlayer(uuid)
            if (p != null) players.add(p)
        }
        return players
    }

    fun togglePlayer(player: Player) {
        val uuid = player.uniqueId
        if (toggledPlayers.remove(uuid) == null) {
            toggledPlayers[uuid] = true
        }
        removeAllInRequests(player)
    }

    fun checkToggle(player: Player): Boolean {
        return toggledPlayers.containsKey(player.uniqueId)
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
        val list = blockedPlayers[requested.uniqueId]
        return list != null && list.contains(requester.uniqueId)
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
