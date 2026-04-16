/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.util

import java.io.IOException
import java.util.logging.FileHandler
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

class MapCreationLogger {
    companion object {
        val logger: Logger = Logger.getLogger("MapCreationLogger")
        private var fileHandler: FileHandler? = null

        init {
            try {
                java.nio.file.Files.createDirectories(java.nio.file.Paths.get("critical-logs-do-not-delete"))

                val handler = FileHandler("critical-logs-do-not-delete/map_creation.log", true)
                handler.formatter = SimpleFormatter()
                fileHandler = handler
                logger.addHandler(handler)
            } catch (ignored: IOException) {
            }
        }
    }
}
