/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 *
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.dialog

import me.gb8.core.Main
import org.bukkit.World
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

object DialogDatapackInstaller {
    private const val RESOURCE_ROOT = "8b8t_dialogs"
    private const val PACK_DIRECTORY = "8b8tcore-dialogs"
    private val files = listOf(
        "pack.mcmeta",
        "data/8b8t/dialog/settings_menu.json",
        "data/minecraft/tags/dialog/quick_actions.json",
        "data/minecraft/tags/dialog/pause_screen_additions.json"
    )
    fun installBeforeWorldLoad(plugin: Main): Boolean {
        val propertiesPath = Path.of("server.properties").toAbsolutePath().normalize()
        val levelName = if (Files.isRegularFile(propertiesPath)) {
            Properties().apply {
                Files.newInputStream(propertiesPath).use(::load)
            }.getProperty("level-name", "world")
        } else {
            "world"
        }
        val worldContainer = plugin.server.worldContainer.toPath()
            .toAbsolutePath()
            .normalize()
        val worldRoot = worldContainer
            .resolve(levelName)
            .normalize()
        require(worldRoot.startsWith(worldContainer)) { "Invalid level-name path: $levelName" }
        return installAt(plugin, worldRoot)
    }

    fun install(plugin: Main): Boolean {
        val world = plugin.server.worlds.firstOrNull { it.environment == World.Environment.NORMAL }
            ?: plugin.server.worlds.firstOrNull()
            ?: run {
                plugin.logger.warning("Could not install the settings dialog datapack because no world is loaded.")
                return false
            }
        val worldFolder = world.worldFolder.toPath().toAbsolutePath().normalize()
        val worldRoot = generateSequence(worldFolder) { it.parent }
            .firstOrNull { Files.exists(it.resolve("level.dat")) }
            ?: worldFolder
        return installAt(plugin, worldRoot)
    }

    private fun installAt(plugin: Main, worldRoot: Path): Boolean {
        val targetRoot = worldRoot
            .resolve("datapacks")
            .resolve(PACK_DIRECTORY)

        var changed = false
        for (relativePath in files) {
            val resourcePath = "$RESOURCE_ROOT/$relativePath"
            val contents = plugin.getResource(resourcePath)?.use { it.readBytes() }
                ?: error("Missing bundled dialog datapack resource: $resourcePath")
            val destination = targetRoot.resolve(relativePath).normalize()
            require(destination.startsWith(targetRoot)) { "Invalid dialog datapack path: $relativePath" }

            if (Files.exists(destination) && Files.readAllBytes(destination).contentEquals(contents)) continue

            Files.createDirectories(destination.parent)
            val temporary = Files.createTempFile(destination.parent, ".8b8t-dialog-", ".tmp")
            try {
                Files.write(temporary, contents)
                try {
                    Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
            changed = true
        }
        return changed
    }
}
