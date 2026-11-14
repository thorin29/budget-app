package com.budget.app.data

import com.budget.app.data.remote.MonthEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How loudly a status reads: plain, needs attention soon, or late. */
enum class Tone { MUTED, WARN, DANGER }

data class EntryStatus(val text: String, val tone: Tone)

/** The short line under an entry's name. Pure, so it is unit-tested. */
object EntryStatuses {
    fun of(e: MonthEntry, today: LocalDate = LocalDate.now()): EntryStatus {
        val due = Dates.dueDate(e.year, e.month, e.dueDay)
        val dueText = due?.let { Dates.short(it.toString()) }
        if (e.isIncome) {
            return if (e.settled) EntryStatus(Dates.short(e.paidOn)?.let { "Received $it" } ?: "Received", Tone.MUTED)
            else EntryStatus(dueText?.let { "Expected $it" } ?: "Expected", Tone.MUTED)
        }
        if (e.settled) return EntryStatus(Dates.short(e.paidOn)?.let { "Paid $it" } ?: "Paid", Tone.MUTED)
        if (e.carriedFrom != null) return EntryStatus("Still owed from ${Dates.monthName(e.year, e.month)}", Tone.DANGER)
        if (due == null || dueText == null) return EntryStatus("Not paid yet", Tone.MUTED)
        val days = ChronoUnit.DAYS.between(today, due)
        return when {
            days < 0 -> EntryStatus("Overdue · was due $dueText", Tone.DANGER)
            days == 0L -> EntryStatus("Due today", Tone.WARN)
            days <= 3 -> EntryStatus("Due $dueText", Tone.WARN)
            else -> EntryStatus("Due $dueText", Tone.MUTED)
        }
    }
}
