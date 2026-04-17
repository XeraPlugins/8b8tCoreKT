/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.home

import com.google.gson.*
import me.gb8.core.IStorage
import me.gb8.core.home.Home
import me.gb8.core.home.HomeData
import me.gb8.core.util.GlobalUtils
import org.bukkit.Bukkit
import org.bukkit.Location
import java.io.File
import java.util.logging.Level
import java.util.UUID

class HomeJsonStorage(private val dataDir: File) : IStorage<HomeData, UUID> {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    override fun save(data: HomeData, id: UUID) {
        val file = File(dataDir, "$id.json")

        if (data.getHomes().isEmpty()) {
            if (file.exists()) delete(id)
            return
        }

        runCatching {
            val obj = JsonObject()
            val arr = JsonArray()
            data.getHomes().forEach { h ->
                val homeObj = JsonObject().apply {
                    addProperty("name", h.name)
                    addProperty("world", h.worldName)
                    addProperty("yaw", h.location.yaw)
                    addProperty("pitch", h.location.pitch)
                    addProperty("x", h.location.x)
                    addProperty("y", h.location.y)
                    addProperty("z", h.location.z)
                }
                arr.add(homeObj)
            }
            obj.add("homes", arr)

            file.writer().use { writer ->
                gson.toJson(obj, writer)
            }
        }.onFailure { t ->
            GlobalUtils.log(Level.SEVERE, "Failed to save Homes for&r&a %s&r&c. Please see the stacktrace below for more info", id)
            t.printStackTrace()
        }
    }

    override fun load(id: UUID): HomeData {
        return runCatching {
            val file = File(dataDir, "$id.json")
            if (!file.exists()) return@runCatching HomeData()

            file.reader().use { reader ->
                val obj = gson.fromJson(reader, JsonObject::class.java)
                val homesArr = obj.get("homes").asJsonArray

                homesArr.mapNotNull { element ->
                    val homeObj = element.asJsonObject
                    val name = homeObj.get("name").asString ?: "Home"
                    val worldName = homeObj.get("world").asString ?: "world"

                    Location(
                        Bukkit.getWorld(worldName),
                        homeObj.get("x").asDouble,
                        homeObj.get("y").asDouble,
                        homeObj.get("z").asDouble,
                        homeObj.get("yaw").asFloat,
                        homeObj.get("pitch").asFloat
                    ).let { location ->
                        Home(name, worldName, location)
                    }
                }.let { HomeData(it.toList()) }
            }
        }.getOrElse { t ->
            GlobalUtils.log(Level.SEVERE, "&cFailed to parse&r&a %s&r&c. This is most likely due to malformed json", id)
            t.printStackTrace()
            HomeData()
        }
    }

    override fun delete(id: UUID) {
        val file = File(dataDir, "$id.json")
        if (file.delete()) {
            GlobalUtils.log(Level.INFO, "&3Deleted homes file for&r&a %s&r", id)
        } else {
            GlobalUtils.log(Level.WARNING, "&cFailed to delete homes file for&r&a %s&r", id)
        }
    }
}