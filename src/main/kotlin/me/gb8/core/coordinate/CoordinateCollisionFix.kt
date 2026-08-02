/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 *
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.coordinate

import me.gb8.core.Main
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.data.type.Speleothem
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.util.BoundingBox
import java.lang.reflect.Field
import java.lang.reflect.Modifier

internal class CoordinateCollisionFix(private val plugin: Main) : Listener {
    fun enable() {
        runCatching {
            val emptyShape = Class.forName(SHAPES_CLASS).getMethod("empty").invoke(null)
            replaceShape(Class.forName(BAMBOO_CLASS).getDeclaredField("SHAPE_COLLISION"), emptyShape)

            val speleothem = Class.forName(SPELEOTHEM_CLASS)
            SPELEOTHEM_SHAPES.forEach { name ->
                replaceShape(speleothem.getDeclaredField(name), emptyShape)
            }
        }.onFailure {
            plugin.logger.log(
                java.util.logging.Level.SEVERE,
                "Could not disable bamboo and speleothem collision for coordinate spoofing",
                it
            )
        }
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val block = event.blockPlaced
        val box = when (block.type) {
            Material.BAMBOO -> BAMBOO_BOX
            Material.POINTED_DRIPSTONE, Material.SULFUR_SPIKE -> speleothemBox(block.blockData as Speleothem)
            else -> return
        }
        if (event.player.boundingBox.overlaps(box.clone().shift(block.location))) {
            event.setBuild(false)
        }
    }

    private fun speleothemBox(data: Speleothem): BoundingBox = when (data.thickness) {
        Speleothem.Thickness.TIP_MERGE -> TIP_MERGE_BOX
        Speleothem.Thickness.TIP -> if (data.verticalDirection == BlockFace.DOWN) TIP_DOWN_BOX else TIP_UP_BOX
        Speleothem.Thickness.FRUSTUM -> FRUSTUM_BOX
        Speleothem.Thickness.MIDDLE -> MIDDLE_BOX
        Speleothem.Thickness.BASE -> BASE_BOX
    }

    private fun replaceShape(field: Field, emptyShape: Any) {
        check(Modifier.isStatic(field.modifiers)) { "${field.name} is not static" }
        StaticFinalField.set(field, emptyShape)
    }

    private object StaticFinalField {
        private val unsafeClass = Class.forName("sun.misc.Unsafe")
        private val unsafe = unsafeClass.getDeclaredField("theUnsafe").run {
            trySetAccessible()
            get(null)
        }
        private val staticFieldBase = unsafeClass.getMethod("staticFieldBase", Field::class.java)
        private val staticFieldOffset = unsafeClass.getMethod("staticFieldOffset", Field::class.java)
        private val putObject = unsafeClass.getMethod(
            "putObject",
            Any::class.java,
            java.lang.Long.TYPE,
            Any::class.java
        )

        fun set(field: Field, value: Any) {
            val base = staticFieldBase.invoke(unsafe, field)
            val offset = staticFieldOffset.invoke(unsafe, field)
            putObject.invoke(unsafe, base, offset, value)
        }
    }

    private companion object {
        const val SHAPES_CLASS = "net.minecraft.world.phys.shapes.Shapes"
        const val BAMBOO_CLASS = "net.minecraft.world.level.block.BambooStalkBlock"
        const val SPELEOTHEM_CLASS = "net.minecraft.world.level.block.SpeleothemBlock"
        val SPELEOTHEM_SHAPES = listOf(
            "SHAPE_TIP_MERGE",
            "SHAPE_TIP_UP",
            "SHAPE_TIP_DOWN",
            "SHAPE_FRUSTUM",
            "SHAPE_MIDDLE",
            "SHAPE_BASE"
        )

        val BAMBOO_BOX = box(6.5, 0.0, 6.5, 9.5, 16.0, 9.5)
        val TIP_MERGE_BOX = box(5.0, 0.0, 5.0, 11.0, 16.0, 11.0)
        val TIP_UP_BOX = box(5.0, 0.0, 5.0, 11.0, 11.0, 11.0)
        val TIP_DOWN_BOX = box(5.0, 5.0, 5.0, 11.0, 16.0, 11.0)
        val FRUSTUM_BOX = box(4.0, 0.0, 4.0, 12.0, 16.0, 12.0)
        val MIDDLE_BOX = box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0)
        val BASE_BOX = box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0)

        fun box(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double) =
            BoundingBox(minX / 16.0, minY / 16.0, minZ / 16.0, maxX / 16.0, maxY / 16.0, maxZ / 16.0)
    }
}
