package com.budget.app.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object Dates {
    private val SHORT = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

    /** "2026-11-21" or "2026-11-21T00:00:00.000Z" → "Nov 21". */
    fun short(iso: String?): String? {
        if (iso.isNullOrBlank() || iso.length < 10) return null
        return runCatching { LocalDate.parse(iso.substring(0, 10)).format(SHORT) }.getOrNull()
    }

    fun monthTitle(year: Int, month: Int): String = YearMonth.of(year, month).format(MONTH)

    /** The due date of [dueDay] in that month, clamped to the month's length. */
    fun dueDate(year: Int, month: Int, dueDay: Int?): LocalDate? {
        if (dueDay == null) return null
        val ym = YearMonth.of(year, month)
        return ym.atDay(dueDay.coerceIn(1, ym.lengthOfMonth()))
    }

    fun isValidDay(text: String): Boolean =
        Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(text) && runCatching { LocalDate.parse(text) }.isSuccess
}
