/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.command.commands

import me.gb8.core.Localization
import me.gb8.core.Main
import me.gb8.core.command.BaseCommand
import me.gb8.core.util.FoliaCompat
import me.gb8.core.util.GlobalUtils
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.CompletableFuture
import me.gb8.core.util.GlobalUtils.sendPrefixedLocalizedMessage
import me.gb8.core.util.GlobalUtils.translateChars

class TpsinfoCommand(private val plugin: Main) : BaseCommand("tpsinfo", "/tpsinfo", "8b8tcore.tpsinfo", "Show TPS information") {

    override fun execute(sender: CommandSender, args: Array<String>) {
        val player = sender as? Player ?: run {
            sender.sendMessage("This command can only be used by players.")
            return
        }

        if (!sender.hasPermission("8b8tcore.tpsinfo") && !sender.isOp) {
            sendPrefixedLocalizedMessage(player, "tps_failed")
            return
        }

        FoliaCompat.schedule(player, plugin) {
            val tps = GlobalUtils.getCurrentRegionTps()
            val mspt = GlobalUtils.getCurrentRegionMspt()
            val onlinePlayerCount = Bukkit.getOnlinePlayers().size

            val loc = Localization.getLocalization("en")
            val tpsMsg = loc.getStringList("TpsMessage").joinToString("\n")
            val strTps = formatTps(tps)
            val strMspt = String.format("%s%.2f", GlobalUtils.getMSPTColor(mspt), mspt)

            getLowestRegionTPS().thenAccept { lowestTPS ->
                FoliaCompat.schedule(player, plugin) l@{
                    if (!player.isOnline) return@l
                    val strLowest = formatTps(lowestTPS)
                    player.sendMessage(translateChars(String.format(tpsMsg, strTps, strMspt, strLowest, onlinePlayerCount)))
                }
            }
        }
    }

    private fun formatTps(tps: Double): String {
        return if (tps >= 20.0) {
            String.format("%s20.00", GlobalUtils.getTPSColor(tps))
        } else {
            String.format("%s%.2f", GlobalUtils.getTPSColor(tps), tps)
        }
    }

    private fun getLowestRegionTPS(): CompletableFuture<Double> {
        val futures = mutableListOf<CompletableFuture<Double>>()
        for (player in Bukkit.getOnlinePlayers()) {
            val uuid = player.uniqueId

            val future = CompletableFuture<Double>()
            FoliaCompat.schedule(player, plugin) {
                val p = Bukkit.getPlayer(uuid)
                if (p != null && p.isOnline) {
                    GlobalUtils.getRegionTps(p.location).thenAccept(future::complete)
                } else {
                    future.complete(-1.0)
                }
            }

            futures.add(future)
        }

        val allFutures = futures.toTypedArray()
        return CompletableFuture.allOf(*allFutures)
                .thenApply {
                    var lowestTPS = Double.MAX_VALUE
                    for (future in futures) {
                        val regionTPS = future.getNow(-1.0)
                        if (regionTPS > 0 && regionTPS < lowestTPS) {
                            lowestTPS = regionTPS
                        }
                    }
                    if (lowestTPS == Double.MAX_VALUE) 20.0 else lowestTPS
                }
    }
}
