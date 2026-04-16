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
import java.io.FileReader
import java.io.FileWriter
import java.util.ArrayList
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

        try {
            val obj = JsonObject()
            val arr = JsonArray()
            data.getHomes().forEach { h ->
                val homeObj = JsonObject()
                homeObj.addProperty("name", h.name)
                homeObj.addProperty("world", h.worldName)
                homeObj.addProperty("yaw", h.location.yaw)
                homeObj.addProperty("pitch", h.location.pitch)
                homeObj.addProperty("x", h.location.x)
                homeObj.addProperty("y", h.location.y)
                homeObj.addProperty("z", h.location.z)
                arr.add(homeObj)
            }
            obj.add("homes", arr)

            FileWriter(file, false).use { fw ->
                gson.toJson(obj, fw)
            }
        } catch (t: Throwable) {
            GlobalUtils.log(Level.SEVERE, "Failed to save Homes for&r&a %s&r&c. Please see the stacktrace below for more info", id)
            t.printStackTrace()
        }
    }

    
    override fun load(id: UUID): HomeData {
        return try {
            val file = File(dataDir, "$id.json")
            val buf = ArrayList<Home>()
            if (file.exists()) {
                FileReader(file).use { reader ->
                    val obj = gson.fromJson(reader, JsonObject::class.java)
                    val homesArr = obj.get("homes").asJsonArray
                    for (element in homesArr) {
                        val homeObj = element.asJsonObject
                        val name = homeObj.get("name").asString ?: "Home"
                        val worldName = homeObj.get("world").asString ?: "world"
                        val location = Location(
                            Bukkit.getWorld(worldName),
                            homeObj.get("x").asDouble,
                            homeObj.get("y").asDouble,
                            homeObj.get("z").asDouble,
                            homeObj.get("yaw").asFloat,
                            homeObj.get("pitch").asFloat
                        )
                        buf.add(Home(name, worldName, location))
                    }
                }
            }
            @Suppress("USELESS_CAST")
            HomeData(buf as java.util.List<Home>)
        } catch (t: Throwable) {
            GlobalUtils.log(Level.SEVERE, "&cFailed to parse&r&a %s&r&c. This is most likely due to malformed json", id)
            t.printStackTrace()
            return HomeData()
        }
    }

    override fun delete(id: UUID) {
        val file = File(dataDir, "$id.json")
        file.delete()
        GlobalUtils.log(Level.INFO, "&3Deleted homes file for&r&a %s&r", id)
    }
}
