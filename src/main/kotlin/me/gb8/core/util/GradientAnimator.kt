/*
 * 8b8tCore
 * Copyright (c) 2026 8b8tTeam
 * 
 * Do not send issue requests or pull requests.
 * Zero warranty. Zero support.
 */

package me.gb8.core.util

object GradientAnimator {

    fun applyAnimation(baseGradient: String?, animationType: String?, speed: Int, tick: Long): String? {
        if (baseGradient == null || animationType == null || animationType.equals("none", ignoreCase = true) || animationType.isEmpty() || !baseGradient.contains(":") || baseGradient.lowercase().contains("tobias:")) {
            return baseGradient
        }

        var phase: Double
        val effectiveSpeed = minOf(5.0, speed.toDouble())
        val normalizedSpeed = effectiveSpeed / 5.0
        val t = tick * normalizedSpeed

        phase = when (animationType.lowercase()) {
            "wave" -> (Math.sin(t * 0.15) + 1.0) / 2.0
            "pulse" -> (Math.sin(t * 0.05) + 1.0) / 2.0
            "smooth" -> {
                var st = (t * 0.06) % 2.0
                if (st > 1.0) st = 2.0 - st
                if (st < 0.5) 2 * st * st else 1 - Math.pow(-2 * st + 2, 2.0) / 2
            }
            "saturate" -> (Math.sin(t * 0.12) + Math.sin(t * 0.24)) / 4.0 + 0.5
            "bounce" -> Math.abs(Math.sin(t * 0.2))
            "billboard" -> Math.floor(((t * 0.05) % 1.0) * 5) / 4.0
            "sweep" -> {
                var sw = (t * 0.07) % 2.0
                if (sw > 1.0) sw = 2.0 - sw
                sw * sw * (3 - 2 * sw)
            }
            "shimmer" -> minOf(1.0, maxOf(0.0, (t * 0.15) % 3.0 - 1.0))
            else -> return baseGradient
        }

        phase = maxOf(0.0, minOf(1.0, phase))
        val rounded = Math.round(phase * 100.0) / 100.0
        return "$baseGradient:$rounded"
    }

    fun getAnimationTick(): Long = System.currentTimeMillis() / 50
}
