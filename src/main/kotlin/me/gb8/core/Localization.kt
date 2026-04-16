/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core

import me.gb8.core.util.GlobalUtils
import org.bukkit.configuration.Configuration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

class Localization(private val config: Configuration) {

    private val stringCache = ConcurrentHashMap<String, String>()
    private val listCache = ConcurrentHashMap<String, List<String>>()
    private val rawCache = ConcurrentHashMap<String, String>()
    private var prefix: String? = null

    companion object {
        private val localizationMap = ConcurrentHashMap<String, Localization>()
        private val localeCache = ConcurrentHashMap<String, Localization>()

        fun loadLocalizations(dataFolder: File) {
            localizationMap.clear()
            localeCache.clear()

            val localeDir = File(dataFolder, "Localization")
            if (!localeDir.exists() && !localeDir.mkdirs()) {
                GlobalUtils.log(Level.SEVERE, "Could not create localization directory: %s", localeDir.absolutePath)
                return
            }

            val locales = arrayOf("ar", "en", "es", "fr", "he", "hi", "it", "ja", "nl", "pt", "ru", "tr", "zh")
            for (locale in locales) {
                val file = File(localeDir, "$locale.yml")
                GlobalUtils.unpackResource("Localization/$locale.yml", file)
            }

            val ymlFiles = localeDir.listFiles { f -> f.isFile && f.name.endsWith(".yml") }
            if (ymlFiles == null) return

            for (ymlFile in ymlFiles) {
                try {
                    val cfg = YamlConfiguration.loadConfiguration(ymlFile)
                    val key = ymlFile.nameWithoutExtension
                    localizationMap[key] = Localization(cfg)
                } catch (e: Exception) {
                    GlobalUtils.log(Level.SEVERE, "Failed to load localization file %s: %s", ymlFile.name, e.message)
                    e.printStackTrace()
                }
            }
        }

        fun getLocalization(locale: String): Localization {
            localeCache[locale]?.let { return it }

            val loc: Localization = if (localizationMap.isEmpty()) {
                Localization(YamlConfiguration.loadConfiguration(File("")))
            } else {
                localizationMap[locale] ?: run {
                    val base = locale.split("[_-]".toRegex())[0]
                    localizationMap[base] ?: localizationMap["en"] ?: Localization(YamlConfiguration.loadConfiguration(File("")))
                }
            }

            localeCache[locale] = loc
            return loc
        }
    }

    fun getPrefix(): String {
        if (prefix == null) prefix = config.getString("prefix", "&8[&98b&78t&8]")
        return prefix ?: "&8[&98b&78t&8]"
    }

    fun get(key: String): String {
        return stringCache.getOrPut(key) {
            val `val` = config.getString(key) ?: "Unknown key $key"
            val withPrefix = `val`.replace("%prefix%", getPrefix())
            GlobalUtils.convertToMiniMessageFormat(withPrefix) ?: withPrefix
        }
    }

    fun getStringList(key: String): List<String> {
        return listCache.getOrPut(key) {
            val list = config.getStringList(key)
            list.mapNotNull { line ->
                val withPrefix = line.replace("%prefix%", getPrefix())
                GlobalUtils.convertToMiniMessageFormat(withPrefix)
            }
        }
    }

    fun getComponentList(key: String): List<net.kyori.adventure.text.TextComponent> {
        return getStringList(key).map { GlobalUtils.translateChars(it) }
    }

    fun getWithPlaceholders(key: String, vararg replacements: String): String {
        val `val` = rawCache.getOrPut(key) {
            config.getString(key) ?: "Unknown key $key"
        }

        var result = `val`
        for (i in replacements.indices step 2) {
            if (i + 1 < replacements.size) {
                result = result.replace(replacements[i], replacements[i + 1])
            }
        }

        val withPrefix = result.replace("%prefix%", getPrefix())
        return GlobalUtils.convertToMiniMessageFormat(withPrefix) ?: withPrefix
    }
}
