/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.util

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.function.Consumer
import java.util.logging.Level
import java.util.logging.Logger

class FoliaCompat {
    companion object {
        private val LUMINOL: Boolean
        private val LOGGER: Logger?
        private val LUMINOL_SCHEDULE: Method?
        private val LUMINOL_SCHEDULE_FIXED: Method?
        private val FOLIA_RUN_DELAYED: Method?
        private val FOLIA_RUN: Method?
        private val FOLIA_RUN_FIXED: Method?
        private val GET_SCHEDULER: Method?
        private val GET_HANDLE: Method?
        private val GET_BUKKIT_ENTITY: Method?
        private val TASK_SCHEDULER_FIELD: Field?

        init {
            var logger: Logger? = null
            var scheduleMethod: Method? = null
            var scheduleFixedMethod: Method? = null
            var runDelayedMethod: Method? = null
            var runMethod: Method? = null
            var runFixedMethod: Method? = null
            var getScheduler: Method? = null
            var getHandle: Method? = null
            var getBukkitEntity: Method? = null
            var taskSchedulerField: Field? = null

            runCatching {
                val newLogger = Logger.getLogger(FoliaCompat::class.java.name)
                newLogger.level = Level.WARNING
                logger = newLogger
            }

            LUMINOL = runCatching {
                val minecraftEntityClass = Class.forName("net.minecraft.world.entity.Entity")
                minecraftEntityClass.getMethod("getBukkitEntity")

                val taskSchedulerClass = Class.forName("io.papermc.paper.threadedregions.EntityScheduler")
                scheduleMethod = taskSchedulerClass.getMethod("schedule", Runnable::class.java, Runnable::class.java, Long::class.javaPrimitiveType)
                scheduleFixedMethod = taskSchedulerClass.getMethod("scheduleAtFixedRate", Runnable::class.java, Runnable::class.java, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)

                getHandle = Class.forName("org.bukkit.craftbukkit.entity.CraftEntity").getMethod("getHandle")
                getBukkitEntity = minecraftEntityClass.getMethod("getBukkitEntity")
                taskSchedulerField = Class.forName("org.bukkit.craftbukkit.entity.CraftEntity").getField("taskScheduler")
                true
            }.getOrDefault(false)

            runCatching {
                val taskSchedulerClass = Class.forName("io.papermc.paper.threadedregions.EntityScheduler")
                runDelayedMethod = taskSchedulerClass.getMethod("runDelayed", Plugin::class.java, Consumer::class.java, Runnable::class.java, Long::class.javaPrimitiveType)
                runMethod = taskSchedulerClass.getMethod("run", Plugin::class.java, Consumer::class.java, Runnable::class.java)
                runFixedMethod = taskSchedulerClass.getMethod("runAtFixedRate", Plugin::class.java, Consumer::class.java, Runnable::class.java, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)
                getScheduler = Entity::class.java.getMethod("getScheduler")
            }


            LOGGER = logger
            LUMINOL_SCHEDULE = scheduleMethod
            LUMINOL_SCHEDULE_FIXED = scheduleFixedMethod
            FOLIA_RUN_DELAYED = runDelayedMethod
            FOLIA_RUN = runMethod
            FOLIA_RUN_FIXED = runFixedMethod
            GET_SCHEDULER = getScheduler
            GET_HANDLE = getHandle
            GET_BUKKIT_ENTITY = getBukkitEntity
            TASK_SCHEDULER_FIELD = taskSchedulerField
        }

        private fun getLuminolScheduler(entity: Entity): Any? {
            if (GET_HANDLE == null || GET_BUKKIT_ENTITY == null || TASK_SCHEDULER_FIELD == null) return null
            return runCatching {
                val minecraftEntity = GET_HANDLE?.invoke(entity)
                val bukkitEntity = GET_BUKKIT_ENTITY?.invoke(minecraftEntity)
                TASK_SCHEDULER_FIELD?.get(bukkitEntity)
            }.onFailure { e -> LOGGER?.log(Level.WARNING, "Failed to get Luminol scheduler: ${e.message}") }
                .getOrNull()
        }

        @JvmStatic
        fun schedule(entity: Entity, plugin: Plugin, task: Runnable) {
            if (LUMINOL && LUMINOL_SCHEDULE != null) {
                getLuminolScheduler(entity)?.let { scheduler ->
                    runCatching {
                        LUMINOL_SCHEDULE?.invoke(scheduler, task, null, 1L)
                    }.onFailure { e -> LOGGER?.log(Level.WARNING, "Luminol schedule failed, falling back: ${e.message}") }
                        .onSuccess { return }
                }
            }
            if (FOLIA_RUN != null && GET_SCHEDULER != null) {
                runCatching {
                    val scheduler = GET_SCHEDULER?.invoke(entity)
                    FOLIA_RUN?.invoke(scheduler, plugin, Runnable { task.run() }, null)
                }.onFailure { e -> LOGGER?.log(Level.WARNING, "Folia schedule failed, falling back: ${e.message}") }
                    .onSuccess { return }
            }
            val loc = entity.location
            Bukkit.getRegionScheduler().run(plugin, loc) {
                if (entity.isValid && Bukkit.isOwnedByCurrentRegion(entity)) {
                    task.run()
                }
            }
        }

        @JvmStatic
        fun scheduleDelayed(entity: Entity, plugin: Plugin, task: Runnable, delay: Long) {
            if (LUMINOL && LUMINOL_SCHEDULE != null) {
                getLuminolScheduler(entity)?.let { scheduler ->
                    runCatching {
                        LUMINOL_SCHEDULE?.invoke(scheduler, Runnable {}, task, delay)
                    }.onFailure { e -> LOGGER?.log(Level.WARNING, "Luminol scheduleDelayed failed, falling back: ${e.message}") }
                        .onSuccess { return }
                }
            }
            if (FOLIA_RUN_DELAYED != null && GET_SCHEDULER != null) {
                runCatching {
                    GET_SCHEDULER?.invoke(entity)?.let { scheduler ->
                        FOLIA_RUN_DELAYED?.invoke(scheduler, plugin, Runnable { task.run() }, Runnable {}, delay)
                    }
                }.onFailure { e -> LOGGER?.log(Level.WARNING, "Folia scheduleDelayed failed, falling back: ${e.message}") }
                    .onSuccess { return }
            }
            val loc = entity.location
            Bukkit.getRegionScheduler().runDelayed(plugin, loc, {
                if (entity.isValid && Bukkit.isOwnedByCurrentRegion(entity)) {
                    task.run()
                }
            }, delay)
        }

        @JvmStatic
        fun scheduleAtFixedRate(entity: Entity, plugin: Plugin, task: Runnable, initialDelay: Long, period: Long) {
            if (LUMINOL && LUMINOL_SCHEDULE_FIXED != null) {
                getLuminolScheduler(entity)?.let { scheduler ->
                    runCatching {
                        LUMINOL_SCHEDULE_FIXED?.invoke(scheduler, task, null, initialDelay, period)
                    }.onFailure { e -> LOGGER?.log(Level.WARNING, "Luminol scheduleAtFixedRate failed, falling back: ${e.message}") }
                        .onSuccess { return }
                }
            }
            if (FOLIA_RUN_FIXED != null && GET_SCHEDULER != null) {
                runCatching {
                    GET_SCHEDULER?.invoke(entity)?.let { scheduler ->
                        FOLIA_RUN_FIXED?.invoke(scheduler, plugin, Runnable { task.run() }, null, initialDelay, period)
                    }
                }.onFailure { e -> LOGGER?.log(Level.WARNING, "Folia scheduleAtFixedRate failed, falling back: ${e.message}") }
                    .onSuccess { return }
            }
            val loc = entity.location
            fun scheduleNext() {
                Bukkit.getRegionScheduler().runDelayed(plugin, loc, {
                    if (entity.isValid && Bukkit.isOwnedByCurrentRegion(entity)) {
                        task.run()
                        scheduleNext()
                    }
                }, period)
            }
            if (initialDelay > 0) {
                Bukkit.getRegionScheduler().runDelayed(plugin, loc, { scheduleNext() }, initialDelay)
            } else {
                scheduleNext()
            }
        }
    }
}
