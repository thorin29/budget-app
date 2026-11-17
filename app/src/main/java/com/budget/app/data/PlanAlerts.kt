package com.budget.app.data

import com.budget.app.data.remote.MonthPlanResponse

/** The words of the spending plan alert. Pure, so it can be tested. */
object PlanAlerts {
    /** Under this much safe to spend, the month is running low. */
    const val LOW_CENTS = 20_000L

    /** "low" or "over" when the month's total calls for an alert; null otherwise. Never per budget. */
    fun level(v: MonthPlanResponse): String? = when {
        v.closed -> null
        v.safeCents < 0 -> "over"
        v.safeCents < LOW_CENTS -> "low"
        else -> null
    }

    fun words(v: MonthPlanResponse): Pair<String, String>? = when (level(v)) {
        "over" -> "Over by ${Money.whole(-v.safeCents)} this month" to
            "After bills and the groceries, fuel and other essentials still to come, the month has nothing left for extras. Anything more lands on a card that can't be paid in full."
        "low" -> "${Money.whole(v.safeCents)} safe to spend this month" to
            "That's what's left for extras for the rest of the month, after bills and the essentials still to come."
        else -> null
    }
}
