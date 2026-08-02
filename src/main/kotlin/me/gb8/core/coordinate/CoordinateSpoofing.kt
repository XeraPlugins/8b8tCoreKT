/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 *
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.coordinate

import me.gb8.core.Main
import org.bukkit.entity.Player

internal interface CoordinateSpoofingBackend {
    fun enable()
    fun disable()
    fun isBedrock(player: Player): Boolean
    fun applyLoadedPreference(player: Player, enabled: Boolean)
    fun setEnabled(player: Player, enabled: Boolean)
}

object CoordinateSpoofing {
    val isAvailable: Boolean get() = Main.instance.coordinateSpoofingService != null

    private fun service(): CoordinateSpoofingBackend =
        checkNotNull(Main.instance.coordinateSpoofingService) { "PacketEvents is not available" }

    fun isBedrock(player: Player): Boolean = service().isBedrock(player)

    fun applyLoadedPreference(player: Player, enabled: Boolean) {
        Main.instance.coordinateSpoofingService?.applyLoadedPreference(player, enabled)
    }

    fun enable(player: Player) = service().setEnabled(player, true)

    fun disable(player: Player) = service().setEnabled(player, false)
}
