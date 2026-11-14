package com.budget.app.data

import com.budget.app.data.remote.MonthEntry
import com.budget.app.data.remote.MonthView

/** Lists the screens build from a month. Pure, so it is unit-tested. */
object MonthLogic {
    /** Bills still to pay: anything carried from earlier first, then by due day. */
    fun dueSoon(view: MonthView): List<MonthEntry> {
        val carried = view.carried.filter { !it.settled }
        val thisMonth = (view.halves.flatMap { it.entries } + view.surplusItems)
            .filter { !it.settled && !it.isIncome }
            .sortedWith(compareBy<MonthEntry>({ it.dueDay ?: 99 }, { it.name.lowercase() }))
        return carried + thisMonth
    }

    /** Every entry that can be recorded, each once, in the order the month screen shows them. */
    fun everything(view: MonthView): List<MonthEntry> =
        (view.carried + view.halves.flatMap { it.entries } + view.surplusItems + view.income)
            .distinctBy { "${it.lineItemId}:${it.year}:${it.month}" }

    /** 0–1 share of the month's bills paid so far. */
    fun paidShare(view: MonthView): Float {
        val total = view.totalBudgetedCents
        if (total <= 0) return 0f
        return (view.totalPaidCents.toFloat() / total).coerceIn(0f, 1f)
    }
}
