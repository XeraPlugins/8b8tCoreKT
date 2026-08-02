/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 *
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.coordinate

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound
import com.github.retrooper.packetevents.protocol.player.ClientVersion
import com.github.retrooper.packetevents.protocol.player.User
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk
import com.github.retrooper.packetevents.protocol.world.chunk.Column
import com.github.retrooper.packetevents.protocol.world.chunk.HeightmapType
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity

@Suppress("OVERRIDE_DEPRECATION")
internal class OffsetColumn(
    private val source: Column,
    private val offset: SpoofOffset,
    private val user: User
) : Column(0, 0, false, emptyArray<BaseChunk>(), null) {
    private val translatedTileEntities by lazy(LazyThreadSafetyMode.NONE) {
        source.tileEntities.map { original ->
            TileEntity(original.packedByte, original.yShort, original.type, original.nbt.copy()).also {
                it.x = original.x - offset.x
                it.z = original.z - offset.z
            }
        }.toTypedArray()
    }

    override fun getX(): Int = source.x - offset.chunkX
    override fun getZ(): Int = source.z - offset.chunkZ
    override fun isFullChunk(): Boolean = source.isFullChunk
    override fun getChunks(): Array<BaseChunk> = source.chunks
    override fun hasHeightMaps(): Boolean = source.hasHeightMaps()

    @Suppress("DEPRECATION")
    override fun getHeightMaps(): NBTCompound? = source.heightMaps

    override fun getHeightmaps(): MutableMap<HeightmapType, LongArray> = source.heightmaps
    override fun hasBiomeData(): Boolean = source.hasBiomeData()
    override fun getBiomeDataInts(): IntArray = source.biomeDataInts
    override fun getBiomeDataBytes(): ByteArray = source.biomeDataBytes

    override fun getTileEntities(): Array<TileEntity> {
        return if (user.clientVersion.isOlderThan(ClientVersion.V_1_18)) translatedTileEntities else source.tileEntities
    }
}
