package com.budget.app.data

import com.budget.app.data.remote.MonthEntry
import com.budget.app.data.remote.MonthView
import java.time.LocalDate

/** When bill reminders come: off, the morning a bill is due, or a day or two before. Set per phone. */
enum class ReminderLead(val key: String, val label: String, val days: Int) {
    OFF("off", "Off", -1),
    DAY_OF("0", "The day it's due", 0),
    ONE_DAY("1", "1 day before", 1),
    TWO_DAYS("2", "2 days before", 2);

    companion object {
        fun from(key: String?): ReminderLead = entries.firstOrNull { it.key == key } ?: OFF
    }
}

data class ReminderMessage(val title: String, val body: String, val lines: List<String>)

/** Which bills to remind about, and what the notification says. Pure, so it is unit-tested. */
object ReminderLogic {
    /**
     * Unpaid bills (not income) due exactly [lead] days after [today]. Pass the
     * months that could hold them: this one, and next if the lead reaches into it.
     */
    fun dueOn(views: List<MonthView>, today: LocalDate, lead: ReminderLead): List<MonthEntry> {
        if (lead == ReminderLead.OFF) return emptyList()
        val target = today.plusDays(lead.days.toLong())
        return views
            .flatMap { v -> v.halves.flatMap { it.entries } + v.surplusItems }
            .filter { !it.settled && !it.isIncome }
            .filter { Dates.dueDate(it.year, it.month, it.dueDay) == target }
            .distinctBy { "${it.lineItemId}:${it.year}:${it.month}" }
            .sortedBy { it.name.lowercase() }
    }

    fun message(due: List<MonthEntry>, lead: ReminderLead): ReminderMessage? {
        if (due.isEmpty()) return null
        val whenText = when (lead.days) {
            0 -> "today"
            1 -> "tomorrow"
            else -> "in ${lead.days} days"
        }
        val lines = due.map { "${it.name} · ${Money.format(it.budgetedCents)}" }
        return if (due.size == 1) {
            val e = due.first()
            ReminderMessage("${e.name} is due $whenText", "${Money.format(e.budgetedCents)} · tap to record it", lines)
        } else {
            ReminderMessage(
                "${due.size} bills due $whenText",
                due.joinToString(", ") { "${it.name} ${Money.format(it.budgetedCents)}" },
                lines,
            )
        }
    }
}
