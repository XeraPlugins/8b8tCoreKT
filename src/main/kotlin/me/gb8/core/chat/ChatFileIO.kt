/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.chat

import com.google.gson.GsonBuilder
import me.gb8.core.IStorage
import me.gb8.core.chat.ChatInfo
import me.gb8.core.chat.ChatSection
import me.gb8.core.util.GlobalUtils
import org.bukkit.entity.Player
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.UUID
import java.util.logging.Level

class ChatFileIO(private val dataDir: File, private val cm: ChatSection) : IStorage<ChatInfo, Player> {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    override fun save(data: ChatInfo, id: Player) {
        val file = File(dataDir, id.uniqueId.toString() + ".json")

        if (data.shouldNotSave()) {
            if (file.exists()) delete(id)
            return
        }

        try {
            val obj = com.google.gson.JsonObject()
            obj.addProperty("togglechat", data.isToggledChat)
            obj.addProperty("togglejoinmessages", data.isJoinMessages)
            val arr = com.google.gson.JsonArray()
            data.ignoring.forEach { u -> arr.add(u.toString()) }
            obj.add("ignores", arr)

            FileWriter(file, false).use { fw ->
                gson.toJson(obj, fw)
            }
        } catch (t: Throwable) {
            GlobalUtils.log(Level.SEVERE, "Failed to save ChatInfo for&r&a %s. Please see the stacktrace below for more info", id.uniqueId.toString())
            t.printStackTrace()
        }
    }

    @Suppress("NAMED_PARAMETER_SHADOWING")
    override fun load(id: Player): ChatInfo {
        val file = File(dataDir, id.uniqueId.toString() + ".json")
        if (file.exists()) {
            try {
                FileReader(file).use { reader ->
                    val obj = gson.fromJson(reader, com.google.gson.JsonObject::class.java)

                    val toggleChat = obj.has("togglechat") && obj["togglechat"].asBoolean
                    val toggleJoinMessages = obj.has("togglejoinmessages") && obj["togglejoinmessages"].asBoolean
                    val ignores: Set<UUID> = if (obj.has("ignores")) parse(obj["ignores"].asJsonArray) else emptySet()

                    return ChatInfo(id, cm, ignores, toggleChat, toggleJoinMessages)
                }
            } catch (t: Throwable) {
                GlobalUtils.log(Level.SEVERE, "Failed to parse %s. This is most likely due to malformed json", id.uniqueId.toString())
                t.printStackTrace()
            }
        }
        return ChatInfo(id, cm)
    }

    
    private fun parse(arr: com.google.gson.JsonArray): Set<UUID> {
        return arr.mapTo(mutableSetOf()) { UUID.fromString(it.asString) }
    }

    @Suppress("NAMED_PARAMETER_SHADOWING")
    override fun delete(id: Player) {
        val file = File(dataDir, id.uniqueId.toString() + ".json")
        file.delete()
        GlobalUtils.log(Level.INFO, "Deleted ChatInfo file for %s", id.name)
    }
}
