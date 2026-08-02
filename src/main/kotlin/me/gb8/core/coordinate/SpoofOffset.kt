/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 *
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.coordinate

import org.bukkit.Location
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.floor
import kotlin.math.roundToLong

internal data class SpoofOffset(val x: Int, val z: Int) {
    init {
        require(x % ALIGNMENT == 0 && z % ALIGNMENT == 0) {
            "Coordinate offsets must be aligned to $ALIGNMENT blocks"
        }
    }

    val chunkX: Int get() = x shr 4
    val chunkZ: Int get() = z shr 4
    val isZero: Boolean get() = x == 0 && z == 0

    operator fun unaryMinus() = SpoofOffset(-x, -z)

    companion object {
        const val ALIGNMENT = 16
        val ZERO = SpoofOffset(0, 0)
    }
}

internal data class ScalableSpoofOffset(val x: Int, val z: Int) {
    fun inWorld(location: Location): SpoofOffset {
        val scale = location.world?.coordinateScale ?: 1.0
        return atScale(scale)
    }

    fun atScale(scale: Double): SpoofOffset {
        require(scale.isFinite() && scale > 0.0) { "Invalid coordinate scale: $scale" }
        return SpoofOffset(align(x / scale), align(z / scale))
    }

    private fun align(value: Double): Int =
        (value / SpoofOffset.ALIGNMENT).roundToLong().times(SpoofOffset.ALIGNMENT).toInt()
}

internal class SpoofOffsetGenerator {
    private val sessionKey = ByteArray(32).also(SecureRandom()::nextBytes)

    fun generate(playerId: UUID, location: Location): ScalableSpoofOffset {
        val world = requireNotNull(location.world)
        return generate(playerId, world.name, world.coordinateScale, location.x, location.z)
    }

    fun generate(
        playerId: UUID,
        worldName: String,
        scale: Double,
        x: Double,
        z: Double
    ): ScalableSpoofOffset {
        require(scale.isFinite() && scale > 0.0) { "Invalid coordinate scale for $worldName: $scale" }

        val realX = floor(x).toLong()
        val realZ = floor(z).toLong()
        val fixedBound = maxOf(1L, (WORLD_LIMIT / scale).toLong())
        val shiftX = alignedRandom(playerId, worldName, "grid-x", 0, (BUCKET_SIZE - SpoofOffset.ALIGNMENT).toLong())
        val shiftZ = alignedRandom(playerId, worldName, "grid-z", 0, (BUCKET_SIZE - SpoofOffset.ALIGNMENT).toLong())
        val bucketX = Math.floorDiv(realX - shiftX, BUCKET_SIZE.toLong())
        val bucketZ = Math.floorDiv(realZ - shiftZ, BUCKET_SIZE.toLong())
        val originX = shiftX + bucketX * BUCKET_SIZE
        val originZ = shiftZ + bucketZ * BUCKET_SIZE

        val fixedX = originX - displayBase(playerId, worldName, "display-x", bucketX, bucketZ, originX, fixedBound)
        val fixedZ = originZ - displayBase(playerId, worldName, "display-z", bucketX, bucketZ, originZ, fixedBound)
        check(kotlin.math.abs(fixedX) <= fixedBound && kotlin.math.abs(fixedZ) <= fixedBound)

        val scalableX = (fixedX * scale).roundToLong()
        val scalableZ = (fixedZ * scale).roundToLong()
        check(scalableX in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        check(scalableZ in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        return ScalableSpoofOffset(scalableX.toInt(), scalableZ.toInt())
    }

    private fun displayBase(
        playerId: UUID,
        worldName: String,
        domain: String,
        bucketX: Long,
        bucketZ: Long,
        origin: Long,
        fixedBound: Long
    ): Long {
        val minimum = maxOf(-WORLD_LIMIT + BORDER_MARGIN, origin - fixedBound)
        val maximum = minOf(WORLD_LIMIT - BORDER_MARGIN - BUCKET_SIZE, origin + fixedBound)
        return alignedRandom(playerId, worldName, domain, minimum, maximum, bucketX, bucketZ)
    }

    private fun alignedRandom(
        playerId: UUID,
        worldName: String,
        domain: String,
        minimum: Long,
        maximum: Long,
        vararg extra: Long
    ): Long {
        val first = ceilAligned(minimum)
        val last = floorAligned(maximum)
        check(first <= last) { "No aligned coordinate offset in [$minimum, $maximum]" }
        val count = Math.floorDiv(last - first, SpoofOffset.ALIGNMENT) + 1
        val index = java.lang.Long.remainderUnsigned(hash(playerId, worldName, domain, extra), count)
        return first + index * SpoofOffset.ALIGNMENT
    }

    private fun hash(playerId: UUID, worldName: String, domain: String, extra: LongArray): Long {
        val worldBytes = worldName.toByteArray(StandardCharsets.UTF_8)
        val domainBytes = domain.toByteArray(StandardCharsets.UTF_8)
        val input = ByteBuffer.allocate(1 + 16 + 4 + worldBytes.size + 4 + domainBytes.size + extra.size * 8)
        input.put(FORMAT_VERSION)
        input.putLong(playerId.mostSignificantBits)
        input.putLong(playerId.leastSignificantBits)
        input.putInt(worldBytes.size).put(worldBytes)
        input.putInt(domainBytes.size).put(domainBytes)
        extra.forEach(input::putLong)

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(sessionKey, "HmacSHA256"))
        return ByteBuffer.wrap(mac.doFinal(input.array())).long
    }

    private fun floorAligned(value: Long): Long =
        Math.floorDiv(value, SpoofOffset.ALIGNMENT) * SpoofOffset.ALIGNMENT

    private fun ceilAligned(value: Long): Long {
        val floor = floorAligned(value)
        return if (floor == value) value else floor + SpoofOffset.ALIGNMENT
    }

    private companion object {
        const val WORLD_LIMIT = 30_000_000L
        const val BORDER_MARGIN = 2_000_000L
        const val BUCKET_SIZE = 1_500_000
        const val FORMAT_VERSION: Byte = 2
    }
}
