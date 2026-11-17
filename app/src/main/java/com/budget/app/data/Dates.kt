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

    /** "November". */
    fun monthName(year: Int, month: Int): String = YearMonth.of(year, month).format(MONTH_NAME)

    /** "Saturday, Nov 14". */
    fun weekdayDate(day: LocalDate): String = day.format(WEEKDAY)

    /** "Today, Nov 14" for today, else "Nov 3". */
    fun dayLabel(iso: String, today: LocalDate = LocalDate.now()): String {
        val s = short(iso) ?: iso
        return if (iso.take(10) == today.toString()) "Today, $s" else s
    }

    private val MONTH_NAME = DateTimeFormatter.ofPattern("MMMM", Locale.US)
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)

    /** The due date of [dueDay] in that month, clamped to the month's length. */
    fun dueDate(year: Int, month: Int, dueDay: Int?): LocalDate? {
        if (dueDay == null) return null
        val ym = YearMonth.of(year, month)
        return ym.atDay(dueDay.coerceIn(1, ym.lengthOfMonth()))
    }

    /** An instant ("2026-11-17T14:17:00.000Z") as "8:17 AM" today, "Yesterday 8:17 AM", or "Nov 14, 8:17 AM", in the phone's zone. */
    fun stamp(iso: String?, now: java.time.ZonedDateTime = java.time.ZonedDateTime.now()): String? {
        if (iso.isNullOrBlank()) return null
        val at = runCatching { java.time.Instant.parse(iso).atZone(now.zone) }.getOrNull() ?: return null
        val time = at.format(TIME)
        val days = java.time.temporal.ChronoUnit.DAYS.between(at.toLocalDate(), now.toLocalDate())
        return when (days) {
            0L -> time
            1L -> "yesterday $time"
            else -> "${at.format(SHORT)}, $time"
        }
    }

    private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun isValidDay(text: String): Boolean =
        Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(text) && runCatching { LocalDate.parse(text) }.isSuccess
}
