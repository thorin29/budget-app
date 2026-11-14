package com.budget.app.data

import com.budget.app.data.remote.Projection

/** What paying an amount today does to the projection. Pure, so it is unit-tested. */
object PlanLogic {
    enum class Verdict { FINE, INTO_CUSHION, BELOW_ZERO }

    data class Outcome(val safeCents: Long, val lowCents: Long, val verdict: Verdict)

    /**
     * A payment today comes off every day ahead, so the low point and what is
     * safe both drop by the same amount.
     */
    fun ifPaid(p: Projection, payCents: Long): Outcome {
        val low = p.lowPoint.balanceCents - payCents
        val safe = p.safeToPayCents - payCents
        val verdict = when {
            low < 0 -> Verdict.BELOW_ZERO
            safe < 0 -> Verdict.INTO_CUSHION
            else -> Verdict.FINE
        }
        return Outcome(safeCents = maxOf(safe, 0L), lowCents = low, verdict = verdict)
    }

    /** Closing balance per day, opening day first, less a payment made today. */
    fun series(p: Projection, payCents: Long): List<Long> {
        val days = p.days.map { it.balanceCents - payCents }
        return if (days.isEmpty()) emptyList() else listOf(p.openingBalanceCents - payCents) + days
    }

    /** The slider's top end: twice what is safe, at least $500, in whole $10 steps. */
    fun sliderMaxCents(p: Projection): Long {
        val raw = maxOf(p.safeToPayCents * 2, 50_000L)
        return ((raw + 999) / 1_000) * 1_000
    }
}
