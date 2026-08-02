/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 *
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.coordinate

import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.component.ComponentTypes
import com.github.retrooper.packetevents.protocol.component.builtin.item.LodestoneTracker
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.item.ItemStack
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes
import com.github.retrooper.packetevents.protocol.nbt.NBTInt
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon
import com.github.retrooper.packetevents.protocol.particle.data.ParticleItemStackData
import com.github.retrooper.packetevents.protocol.particle.data.ParticleTrailData
import com.github.retrooper.packetevents.protocol.particle.data.ParticleVibrationData
import com.github.retrooper.packetevents.protocol.player.DiggingAction
import com.github.retrooper.packetevents.protocol.player.User
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag
import com.github.retrooper.packetevents.protocol.world.Location
import com.github.retrooper.packetevents.protocol.world.WorldBlockPosition
import com.github.retrooper.packetevents.protocol.world.waypoint.AzimuthWaypointInfo
import com.github.retrooper.packetevents.protocol.world.waypoint.ChunkWaypointInfo
import com.github.retrooper.packetevents.protocol.world.waypoint.EmptyWaypointInfo
import com.github.retrooper.packetevents.protocol.world.waypoint.TrackedWaypoint
import com.github.retrooper.packetevents.protocol.world.waypoint.Vec3iWaypointInfo
import com.github.retrooper.packetevents.util.Vector2i
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.util.Vector3i
import com.github.retrooper.packetevents.wrapper.play.client.*
import com.github.retrooper.packetevents.wrapper.play.server.*
import java.nio.charset.StandardCharsets
import java.util.Optional

internal object CoordinatePacketTranslator {
    private val incomingTypes: Set<PacketTypeCommon> = setOf(
        PacketType.Play.Client.CLICK_WINDOW,
        PacketType.Play.Client.CREATIVE_INVENTORY_ACTION,
        PacketType.Play.Client.GENERATE_STRUCTURE,
        PacketType.Play.Client.PICK_ITEM_FROM_BLOCK,
        PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT,
        PacketType.Play.Client.PLAYER_DIGGING,
        PacketType.Play.Client.PLAYER_POSITION,
        PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION,
        PacketType.Play.Client.UPDATE_STRUCTURE_BLOCK,
        PacketType.Play.Client.SET_TEST_BLOCK,
        PacketType.Play.Client.TEST_INSTANCE_BLOCK_ACTION,
        PacketType.Play.Client.UPDATE_COMMAND_BLOCK,
        PacketType.Play.Client.UPDATE_JIGSAW_BLOCK,
        PacketType.Play.Client.UPDATE_SIGN,
        PacketType.Play.Client.VEHICLE_MOVE
    )

    private val outgoingTypes: Set<PacketTypeCommon> = setOf(
        PacketType.Play.Server.ACKNOWLEDGE_PLAYER_DIGGING,
        PacketType.Play.Server.BLOCK_ACTION,
        PacketType.Play.Server.BLOCK_BREAK_ANIMATION,
        PacketType.Play.Server.BLOCK_CHANGE,
        PacketType.Play.Server.BLOCK_ENTITY_DATA,
        PacketType.Play.Server.CHUNK_DATA,
        PacketType.Play.Server.EFFECT,
        PacketType.Play.Server.ENTITY_EQUIPMENT,
        PacketType.Play.Server.ENTITY_METADATA,
        PacketType.Play.Server.ENTITY_POSITION_SYNC,
        PacketType.Play.Server.ENTITY_TELEPORT,
        PacketType.Play.Server.EXPLOSION,
        PacketType.Play.Server.FACE_PLAYER,
        PacketType.Play.Server.GAME_TEST_HIGHLIGHT_POS,
        PacketType.Play.Server.JOIN_GAME,
        PacketType.Play.Server.UPDATE_LIGHT,
        PacketType.Play.Server.MOVE_MINECART,
        PacketType.Play.Server.MULTI_BLOCK_CHANGE,
        PacketType.Play.Server.OPEN_SIGN_EDITOR,
        PacketType.Play.Server.PARTICLE,
        PacketType.Play.Server.PLAYER_POSITION_AND_LOOK,
        PacketType.Play.Server.PLUGIN_MESSAGE,
        PacketType.Play.Server.RESPAWN,
        PacketType.Play.Server.SET_CURSOR_ITEM,
        PacketType.Play.Server.SET_PLAYER_INVENTORY,
        PacketType.Play.Server.SET_SLOT,
        PacketType.Play.Server.SOUND_EFFECT,
        PacketType.Play.Server.SPAWN_ENTITY,
        PacketType.Play.Server.SPAWN_EXPERIENCE_ORB,
        PacketType.Play.Server.SPAWN_LIVING_ENTITY,
        PacketType.Play.Server.SPAWN_PAINTING,
        PacketType.Play.Server.SPAWN_PLAYER,
        PacketType.Play.Server.SPAWN_POSITION,
        PacketType.Play.Server.UNLOAD_CHUNK,
        PacketType.Play.Server.UPDATE_VIEW_POSITION,
        PacketType.Play.Server.VEHICLE_MOVE,
        PacketType.Play.Server.WAYPOINT,
        PacketType.Play.Server.WINDOW_ITEMS
    )

    fun handlesIncoming(type: PacketTypeCommon): Boolean = type in incomingTypes
    fun handlesOutgoing(type: PacketTypeCommon): Boolean = type in outgoingTypes

    fun translateIncoming(event: PacketReceiveEvent, offset: SpoofOffset) {
        when (event.packetType) {
            PacketType.Play.Client.CLICK_WINDOW -> translateClickWindow(WrapperPlayClientClickWindow(event), -offset)
            PacketType.Play.Client.CREATIVE_INVENTORY_ACTION -> {
                val packet = WrapperPlayClientCreativeInventoryAction(event)
                offsetItem(packet.itemStack, -offset)?.let(packet::setItemStack)
            }
            PacketType.Play.Client.GENERATE_STRUCTURE -> WrapperPlayClientGenerateStructure(event).run {
                blockPosition = translate(blockPosition, -offset)
            }
            PacketType.Play.Client.PICK_ITEM_FROM_BLOCK -> WrapperPlayClientPickItemFromBlock(event).run {
                blockPos = translate(blockPos, -offset)
            }
            PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT -> WrapperPlayClientPlayerBlockPlacement(event).run {
                blockPosition = translate(blockPosition, -offset)
            }
            PacketType.Play.Client.PLAYER_DIGGING -> WrapperPlayClientPlayerDigging(event).run {
                if (action !in POSITIONLESS_DIG_ACTIONS) blockPosition = translate(blockPosition, -offset)
            }
            PacketType.Play.Client.PLAYER_POSITION,
            PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION -> WrapperPlayClientPlayerFlying(event).run {
                location = translate(location, -offset)
            }
            PacketType.Play.Client.UPDATE_STRUCTURE_BLOCK -> WrapperPlayClientSetStructureBlock(event).run {
                position = translate(position, -offset)
            }
            PacketType.Play.Client.SET_TEST_BLOCK -> WrapperPlayClientSetTestBlock(event).run {
                position = translate(position, -offset)
            }
            PacketType.Play.Client.TEST_INSTANCE_BLOCK_ACTION -> WrapperPlayClientTestInstanceBlockAction(event).run {
                position = translate(position, -offset)
            }
            PacketType.Play.Client.UPDATE_COMMAND_BLOCK -> WrapperPlayClientUpdateCommandBlock(event).run {
                position = translate(position, -offset)
            }
            PacketType.Play.Client.UPDATE_JIGSAW_BLOCK -> WrapperPlayClientUpdateJigsawBlock(event).run {
                position = translate(position, -offset)
            }
            PacketType.Play.Client.UPDATE_SIGN -> WrapperPlayClientUpdateSign(event).run {
                blockPosition = translate(blockPosition, -offset)
            }
            PacketType.Play.Client.VEHICLE_MOVE -> WrapperPlayClientVehicleMove(event).run {
                position = translate(position, -offset)
            }
        }
    }

    fun translateOutgoing(event: PacketSendEvent, offset: SpoofOffset) {
        when (event.packetType) {
            PacketType.Play.Server.ACKNOWLEDGE_PLAYER_DIGGING -> WrapperPlayServerAcknowledgePlayerDigging(event).run {
                blockPosition = translate(blockPosition, offset)
            }
            PacketType.Play.Server.BLOCK_ACTION -> WrapperPlayServerBlockAction(event).run {
                blockPosition = translate(blockPosition, offset)
            }
            PacketType.Play.Server.BLOCK_BREAK_ANIMATION -> WrapperPlayServerBlockBreakAnimation(event).run {
                blockPosition = translate(blockPosition, offset)
            }
            PacketType.Play.Server.BLOCK_CHANGE -> WrapperPlayServerBlockChange(event).run {
                blockPosition = translate(blockPosition, offset)
            }
            PacketType.Play.Server.BLOCK_ENTITY_DATA -> translateBlockEntity(WrapperPlayServerBlockEntityData(event), offset)
            PacketType.Play.Server.CHUNK_DATA -> WrapperPlayServerChunkData(event).run {
                if (column !is OffsetColumn) column = OffsetColumn(column, offset, event.user)
            }
            PacketType.Play.Server.EFFECT -> WrapperPlayServerEffect(event).run {
                position = translate(position, offset)
            }
            PacketType.Play.Server.ENTITY_EQUIPMENT -> WrapperPlayServerEntityEquipment(event).equipment.forEach {
                offsetItem(it.item, offset)?.let(it::setItem)
            }
            PacketType.Play.Server.ENTITY_METADATA -> WrapperPlayServerEntityMetadata(event).entityMetadata.forEach {
                translateMetadata(it, offset)
            }
            PacketType.Play.Server.ENTITY_POSITION_SYNC -> WrapperPlayServerEntityPositionSync(event).values.run {
                position = translate(position, offset)
            }
            PacketType.Play.Server.ENTITY_TELEPORT -> WrapperPlayServerEntityTeleport(event).run {
                position = translate(position, offset)
            }
            PacketType.Play.Server.EXPLOSION -> WrapperPlayServerExplosion(event).run {
                position = translate(position, offset)
                @Suppress("UNNECESSARY_SAFE_CALL")
                records?.let { records = it.map { record -> translate(record, offset) } }
            }
            PacketType.Play.Server.FACE_PLAYER -> WrapperPlayServerFacePlayer(event).run {
                targetPosition = translate(targetPosition, offset)
            }
            PacketType.Play.Server.GAME_TEST_HIGHLIGHT_POS -> WrapperPlayServerGameTestHighlightPos(event).run {
                absolutePos = translate(absolutePos, offset)
            }
            PacketType.Play.Server.JOIN_GAME -> WrapperPlayServerJoinGame(event).run {
                lastDeathPosition?.let { lastDeathPosition = translate(it, offset) }
            }
            PacketType.Play.Server.UPDATE_LIGHT -> WrapperPlayServerUpdateLight(event).run {
                x -= offset.chunkX
                z -= offset.chunkZ
            }
            PacketType.Play.Server.MOVE_MINECART -> WrapperPlayServerMoveMinecart(event).lerpSteps.forEach {
                it.position = translate(it.position, offset)
            }
            PacketType.Play.Server.MULTI_BLOCK_CHANGE -> WrapperPlayServerMultiBlockChange(event).run {
                chunkPosition = translateChunk(chunkPosition, offset)
            }
            PacketType.Play.Server.OPEN_SIGN_EDITOR -> WrapperPlayServerOpenSignEditor(event).run {
                position = translate(position, offset)
            }
            PacketType.Play.Server.PARTICLE -> translateParticle(WrapperPlayServerParticle(event), offset)
            PacketType.Play.Server.PLAYER_POSITION_AND_LOOK -> WrapperPlayServerPlayerPositionAndLook(event).run {
                if (!isRelativeFlag(RelativeFlag.X)) x -= offset.x
                if (!isRelativeFlag(RelativeFlag.Z)) z -= offset.z
            }
            PacketType.Play.Server.PLUGIN_MESSAGE -> translatePluginMessage(WrapperPlayServerPluginMessage(event), offset)
            PacketType.Play.Server.RESPAWN -> WrapperPlayServerRespawn(event).run {
                lastDeathPosition?.let { lastDeathPosition = translate(it, offset) }
            }
            PacketType.Play.Server.SET_CURSOR_ITEM -> WrapperPlayServerSetCursorItem(event).run {
                offsetItem(stack, offset)?.let(::setStack)
            }
            PacketType.Play.Server.SET_PLAYER_INVENTORY -> WrapperPlayServerSetPlayerInventory(event).run {
                offsetItem(stack, offset)?.let(::setStack)
            }
            PacketType.Play.Server.SET_SLOT -> WrapperPlayServerSetSlot(event).run {
                offsetItem(item, offset)?.let(::setItem)
            }
            PacketType.Play.Server.SOUND_EFFECT -> WrapperPlayServerSoundEffect(event).run {
                position = translate(position, offset)
            }
            PacketType.Play.Server.SPAWN_ENTITY -> WrapperPlayServerSpawnEntity(event).run {
                position = translate(position, offset)
            }
            PacketType.Play.Server.SPAWN_EXPERIENCE_ORB -> WrapperPlayServerSpawnExperienceOrb(event).run {
                x -= offset.x
                z -= offset.z
            }
            PacketType.Play.Server.SPAWN_LIVING_ENTITY -> WrapperPlayServerSpawnLivingEntity(event).run {
                position = translate(position, offset)
            }
            PacketType.Play.Server.SPAWN_PAINTING -> WrapperPlayServerSpawnPainting(event).run {
                position = translate(position, offset)
            }
            PacketType.Play.Server.SPAWN_PLAYER -> WrapperPlayServerSpawnPlayer(event).run {
                position = translate(position, offset)
            }
            PacketType.Play.Server.SPAWN_POSITION -> WrapperPlayServerSpawnPosition(event).run {
                position = translate(position, offset)
            }
            PacketType.Play.Server.UNLOAD_CHUNK -> WrapperPlayServerUnloadChunk(event).run {
                chunkX -= offset.chunkX
                chunkZ -= offset.chunkZ
            }
            PacketType.Play.Server.UPDATE_VIEW_POSITION -> WrapperPlayServerUpdateViewPosition(event).run {
                chunkX -= offset.chunkX
                chunkZ -= offset.chunkZ
            }
            PacketType.Play.Server.VEHICLE_MOVE -> WrapperPlayServerVehicleMove(event).run {
                position = translate(position, offset)
            }
            PacketType.Play.Server.WAYPOINT -> translateWaypoint(WrapperPlayServerWaypoint(event), offset)
            PacketType.Play.Server.WINDOW_ITEMS -> translateWindowItems(WrapperPlayServerWindowItems(event), offset)
        }
    }

    private fun translateClickWindow(packet: WrapperPlayClientClickWindow, offset: SpoofOffset) {
        packet.slots.ifPresent { slots ->
            packet.slots = Optional.of(slots.mapValues { (_, item) -> offsetItem(item, offset) ?: item })
        }
        offsetItem(packet.carriedItemStack, offset)?.let(packet::setCarriedItemStack)
    }

    private fun translateWindowItems(packet: WrapperPlayServerWindowItems, offset: SpoofOffset) {
        packet.items = packet.items.map { offsetItem(it, offset) ?: it }
        packet.carriedItem.ifPresent { item -> offsetItem(item, offset)?.let(packet::setCarriedItem) }
    }

    private fun translateBlockEntity(packet: WrapperPlayServerBlockEntityData, offset: SpoofOffset) {
        packet.position = translate(packet.position, offset)
        val nbt = packet.nbt ?: return
        val x = nbt.getNumberTagOrNull("x") ?: return
        val z = nbt.getNumberTagOrNull("z") ?: return
        nbt.setTag("x", NBTInt(x.asInt - offset.x))
        nbt.setTag("z", NBTInt(z.asInt - offset.z))
    }

    private fun translateMetadata(data: EntityData<*>, offset: SpoofOffset) {
        val value = data.value ?: return
        @Suppress("UNCHECKED_CAST")
        (data as EntityData<Any>).value = when (value) {
            is Optional<*> -> if (value.isPresent) Optional.of(translateMetadataValue(value.get(), offset)) else value
            else -> translateMetadataValue(value, offset)
        }
    }

    private fun translateMetadataValue(value: Any, offset: SpoofOffset): Any = when (value) {
        is Vector3i -> translate(value, offset)
        is ItemStack -> offsetItem(value, offset) ?: value
        else -> value
    }

    private fun translateParticle(packet: WrapperPlayServerParticle, offset: SpoofOffset) {
        packet.position = translate(packet.position, offset)
        when (val data = packet.particle.data) {
            is ParticleVibrationData -> {
                if (data.startingPosition != Vector3i.zero()) {
                    data.startingPosition = translate(data.startingPosition, offset)
                }
                data.blockPosition.ifPresent { data.setBlockPosition(translate(it, offset)) }
            }
            is ParticleItemStackData -> offsetItem(data.itemStack, offset)?.let(data::setItemStack)
            is ParticleTrailData -> data.target = translate(data.target, offset)
        }
    }

    private fun translateWaypoint(packet: WrapperPlayServerWaypoint, offset: SpoofOffset) {
        val waypoint = packet.waypoint
        val translated = when (val info = waypoint.info) {
            is Vec3iWaypointInfo -> Vec3iWaypointInfo(translate(info.position, offset))
            is ChunkWaypointInfo -> ChunkWaypointInfo(info.chunkX - offset.chunkX, info.chunkZ - offset.chunkZ)
            is AzimuthWaypointInfo, is EmptyWaypointInfo -> return
            else -> return
        }
        packet.waypoint = TrackedWaypoint(waypoint.identifier, waypoint.icon, translated)
    }

    private fun translatePluginMessage(packet: WrapperPlayServerPluginMessage, offset: SpoofOffset) {
        if (packet.channelName != "worldedit:cui") return
        val parts = String(packet.data, StandardCharsets.ISO_8859_1).split('|').toMutableList()
        when (parts.firstOrNull()) {
            "cyl" -> {
                parts[1] = (parts[1].toInt() - offset.x).toString()
                parts[3] = (parts[3].toInt() - offset.z).toString()
            }
            "e" -> {
                if (parts[1] != "0") return
                parts[2] = (parts[2].toInt() - offset.x).toString()
                parts[4] = (parts[4].toInt() - offset.z).toString()
            }
            "p" -> {
                parts[2] = (parts[2].toInt() - offset.x).toString()
                parts[4] = (parts[4].toInt() - offset.z).toString()
            }
            "p2" -> {
                parts[2] = (parts[2].toInt() - offset.x).toString()
                parts[3] = (parts[3].toInt() - offset.z).toString()
            }
            else -> return
        }
        packet.data = parts.joinToString("|").toByteArray(StandardCharsets.ISO_8859_1)
    }

    private fun offsetItem(item: ItemStack?, offset: SpoofOffset): ItemStack? {
        if (item == null || item.type != ItemTypes.COMPASS) return null

        item.nbt?.getCompoundTagOrNull("LodestonePos")?.let { position ->
            val x = position.getNumberTagOrNull("X") ?: return@let
            val z = position.getNumberTagOrNull("Z") ?: return@let
            position.setTag("X", NBTInt(x.asInt - offset.x))
            position.setTag("Z", NBTInt(z.asInt - offset.z))
            return item
        }

        val lodestone: LodestoneTracker = item.getComponent(ComponentTypes.LODESTONE_TRACKER).orElse(null) ?: return null
        lodestone.target = lodestone.target?.let { translate(it, offset) }
        return item
    }

    private fun translate(vector: Vector3d, offset: SpoofOffset) =
        Vector3d(vector.x - offset.x, vector.y, vector.z - offset.z)

    private fun translate(vector: Vector3f, offset: SpoofOffset) =
        Vector3f(vector.x - offset.x, vector.y, vector.z - offset.z)

    private fun translate(vector: Vector3i, offset: SpoofOffset) =
        Vector3i(vector.x - offset.x, vector.y, vector.z - offset.z)

    private fun translate(vector: Vector2i, offset: SpoofOffset) =
        Vector2i(vector.x - offset.chunkX, vector.z - offset.chunkZ)

    private fun translateChunk(vector: Vector3i, offset: SpoofOffset) =
        Vector3i(vector.x - offset.chunkX, vector.y, vector.z - offset.chunkZ)

    private fun translate(location: Location, offset: SpoofOffset): Location {
        location.position = translate(location.position, offset)
        return location
    }

    private fun translate(position: WorldBlockPosition, offset: SpoofOffset) =
        WorldBlockPosition(position.world, translate(position.blockPosition, offset))

    private val POSITIONLESS_DIG_ACTIONS = setOf(
        DiggingAction.DROP_ITEM_STACK,
        DiggingAction.DROP_ITEM,
        DiggingAction.RELEASE_USE_ITEM,
        DiggingAction.SWAP_ITEM_WITH_OFFHAND
    )
}
