package com.illyism.transcribe.domain

import java.util.Locale
import kotlin.math.ceil

object CostEstimator {
    fun estimate(durationMs: Long, usdPerMinute: Double): Double =
        (durationMs.coerceAtLeast(0L) / 60_000.0) * usdPerMinute.coerceAtLeast(0.0)

    fun estimateAll(durationMs: Iterable<Long>, usdPerMinute: Double): Double =
        durationMs.sumOf { estimate(it, usdPerMinute) }

    /** Conservative display without pretending billing is exact. */
    fun formatEstimate(usd: Double): String {
        if (usd <= 0.0) return "$0.00"
        if (usd < 0.01) return "< $0.01"
        val roundedUp = ceil(usd * 100.0) / 100.0
        return "~$" + String.format(Locale.US, "%.2f", roundedUp)
    }
}
