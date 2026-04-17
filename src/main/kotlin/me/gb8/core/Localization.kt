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

    companion object {
        private val localizationMap = ConcurrentHashMap<String, Localization>()
        private val localeCache = ConcurrentHashMap<String, Localization>()

        private val DEFAULT_PREFIX = "&8[&98b&78t&8]"

        fun loadLocalizations(dataFolder: File) {
            localizationMap.clear()
            localeCache.clear()

            val localeDir = File(dataFolder, "Localization")
            if (!localeDir.exists() && !localeDir.mkdirs()) {
                GlobalUtils.log(Level.SEVERE, "Could not create localization directory: %s", localeDir.absolutePath)
                return
            }

            listOf("ar", "en", "es", "fr", "he", "hi", "it", "ja", "nl", "pt", "ru", "tr", "zh").forEach { locale ->
                val file = File(localeDir, "$locale.yml")
                GlobalUtils.unpackResource("Localization/$locale.yml", file)
            }

            localeDir.listFiles { f -> f.isFile && f.name.endsWith(".yml") }?.forEach { ymlFile ->
                runCatching {
                    val cfg = YamlConfiguration.loadConfiguration(ymlFile)
                    val key = ymlFile.nameWithoutExtension
                    localizationMap[key] = Localization(cfg)
                }.onFailure { e ->
                    GlobalUtils.log(Level.SEVERE, "Failed to load localization file %s: %s", ymlFile.name, e.message)
                    e.printStackTrace()
                }
            }
        }

        fun getLocalization(locale: String): Localization {
            return localeCache.getOrPut(locale) {
                if (localizationMap.isEmpty()) {
                    Localization(YamlConfiguration.loadConfiguration(File("")))
                } else {
                    localizationMap[locale] ?: run {
                        val base = locale.split("[_-]".toRegex()).first()
                        localizationMap[base] ?: localizationMap["en"] ?: Localization(YamlConfiguration.loadConfiguration(File("")))
                    }
                }
            }
        }
    }

    private val cachedPrefix: String by lazy {
        config.getString("prefix", DEFAULT_PREFIX) ?: DEFAULT_PREFIX
    }

    fun getPrefix(): String = cachedPrefix

    fun get(key: String): String {
        return stringCache.getOrPut(key) {
            val raw = config.getString(key) ?: "Unknown key $key"
            val withPrefix = raw.replace("%prefix%", cachedPrefix)
            GlobalUtils.convertToMiniMessageFormat(withPrefix) ?: withPrefix
        }
    }

    fun getStringList(key: String): List<String> {
        return listCache.getOrPut(key) {
            config.getStringList(key).mapNotNull { line ->
                val withPrefix = line.replace("%prefix%", cachedPrefix)
                GlobalUtils.convertToMiniMessageFormat(withPrefix)
            }
        }
    }

    fun getComponentList(key: String): List<net.kyori.adventure.text.TextComponent> {
        return getStringList(key).map { GlobalUtils.translateChars(it) }
    }

    fun getWithPlaceholders(key: String, vararg replacements: String): String {
        val raw = rawCache.getOrPut(key) {
            config.getString(key) ?: "Unknown key $key"
        }

        var result = raw
        replacements.forEachIndexed { index, replacement ->
            if (index % 2 == 0 && index + 1 < replacements.size) {
                result = result.replace(replacement, replacements[index + 1])
            }
        }

        val withPrefix = result.replace("%prefix%", cachedPrefix)
        return GlobalUtils.convertToMiniMessageFormat(withPrefix) ?: withPrefix
    }
}