/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.util

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

object GradientAnimator {

    fun applyAnimation(baseGradient: String?, animationType: String?, speed: Int, tick: Long): String? {
        if (baseGradient == null || animationType.isNullOrEmpty() || 
            animationType.equals("none", ignoreCase = true) || 
            !baseGradient.contains(":") || 
            baseGradient.lowercase().contains("tobias:")) {
            return baseGradient
        }

        val effectiveSpeed = minOf(5.0, speed.toDouble())
        val normalizedSpeed = effectiveSpeed / 5.0
        val t = tick * normalizedSpeed

        val phase = when (animationType.lowercase()) {
            "wave" -> waveAnimation(t)
            "pulse" -> pulseAnimation(t)
            "smooth" -> smoothAnimation(t)
            "saturate" -> saturateAnimation(t)
            "bounce" -> bounceAnimation(t)
            "billboard" -> billboardAnimation(t)
            "sweep" -> sweepAnimation(t)
            "shimmer" -> shimmerAnimation(t)
            else -> return baseGradient
        }

        val clampedPhase = phase.coerceIn(0.0, 1.0)
        val rounded = (clampedPhase * 100.0).toLong() / 100.0
        return "$baseGradient:$rounded"
    }

    private fun waveAnimation(t: Double): Double = (sin(t * 0.15) + 1.0) / 2.0

    private fun pulseAnimation(t: Double): Double = (sin(t * 0.05) + 1.0) / 2.0

    private fun smoothAnimation(t: Double): Double {
        var st = (t * 0.06) % 2.0
        if (st > 1.0) st = 2.0 - st
        return if (st < 0.5) 2 * st * st else 1 - (-2 * st + 2).pow(2.0) / 2
    }

    private fun saturateAnimation(t: Double): Double = (sin(t * 0.12) + sin(t * 0.24)) / 4.0 + 0.5

    private fun bounceAnimation(t: Double): Double = abs(sin(t * 0.2))

    private fun billboardAnimation(t: Double): Double = (((t * 0.05) % 1.0) * 5).toLong() / 4.0

    private fun sweepAnimation(t: Double): Double {
        var sw = (t * 0.07) % 2.0
        if (sw > 1.0) sw = 2.0 - sw
        return sw * sw * (3 - 2 * sw)
    }

    private fun shimmerAnimation(t: Double): Double = minOf(1.0, maxOf(0.0, (t * 0.15) % 3.0 - 1.0))

    fun getAnimationTick(): Long = System.currentTimeMillis() / 50
}