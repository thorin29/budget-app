package com.budget.app.data

import com.budget.app.data.remote.InsightsResponse

/** The Sunday summary: the week's everyday spending, the month's pace, and what's coming. Pure, so it is unit-tested. */
object WeeklyLogic {
    fun message(s: InsightsResponse): ReminderMessage? {
        val week = s.weekCents ?: return null
        val lines = buildList {
            s.limit?.takeIf { it.limitCents > 0 }?.let { l ->
                val t = l.thisMonth
                add(
                    if (t.leftCents >= 0) "${Money.whole(t.spentCents)} of the ${Money.whole(l.limitCents)} monthly limit spent, ${Money.whole(t.leftCents)} left."
                    else "${Money.whole(-t.leftCents)} over the ${Money.whole(l.limitCents)} monthly limit.",
                )
            }
            s.pace?.let { add("On pace for ${Money.whole(it.projectedCents)} this month. ${PaceLogic.verdict(it)}.") }
            s.leftAfterBillsCents?.let { add("${Money.whole(it)} left after this month's bills.") }
            s.upcoming.firstOrNull()?.let { add("Coming up: ${it.merchant} ${Money.format(it.cents)} around ${Dates.short(it.expectedOn) ?: it.expectedOn}.") }
            s.suggestions.firstOrNull { it.kind == "price-up" || it.kind == "new-repeat" }?.let { add(it.title + ".") }
        }
        return ReminderMessage("This week: ${Money.whole(week)} everyday spending", lines.joinToString(" "), lines)
    }
}
