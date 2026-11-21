package com.budget.app

import com.budget.app.data.AlertKind
import com.budget.app.data.AlertRules
import com.budget.app.data.EntryStatuses
import com.budget.app.data.Tone
import com.budget.app.data.remote.AlertDto
import com.budget.app.data.remote.MonthEntry
import com.budget.app.data.remote.PendingChargeDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

// Names and amounts are invented.
class AlertRulesTest {
    private val alerts = listOf(
        AlertDto(key = "billDue:a:2026-11-23", kind = "billDue", title = "Electric is due today"),
        AlertDto(key = "over:g:2026-11", kind = "budgetOver", title = "Eating out is \$40 over"),
        AlertDto(key = "large:t1", kind = "largeCharge", title = "\$300.00 at Example Store", cents = 30_000),
        AlertDto(key = "large:t2", kind = "largeCharge", title = "\$117.43 at Example Store", cents = 11_743),
        AlertDto(key = "weekly:2026-W47", kind = "weekly", title = "This week"),
    )
    private val all = AlertKind.entries.map { it.key }.toSet()

    @Test fun nothingWhenNothingIsOn() {
        assertTrue(AlertRules.pick(alerts, emptySet(), emptySet(), LocalDateTime.of(2026, 11, 23, 12, 0), quietHours = true).isEmpty())
    }

    @Test fun keepsTheKindsTurnedOnAndSkipsWhatWasPosted() {
        val now = LocalDateTime.of(2026, 11, 23, 12, 0) // a Monday
        val picked = AlertRules.pick(alerts, all, setOf("over:g:2026-11"), now, quietHours = true).map { it.key }
        assertEquals(listOf("billDue:a:2026-11-23", "large:t1"), picked)
    }

    @Test fun quietHoursHoldEverything() {
        assertTrue(AlertRules.pick(alerts, all, emptySet(), LocalDateTime.of(2026, 11, 23, 22, 0), quietHours = true).isEmpty())
        assertTrue(AlertRules.pick(alerts, all, emptySet(), LocalDateTime.of(2026, 11, 23, 22, 0), quietHours = false).isNotEmpty())
    }

    @Test fun weeklyOnlySundayEvening() {
        val sunday = LocalDateTime.of(2026, 11, 22, 18, 30)
        assertTrue(AlertRules.pick(alerts, setOf("weekly"), emptySet(), sunday, quietHours = true).any { it.kind == "weekly" })
        assertTrue(AlertRules.pick(alerts, setOf("weekly"), emptySet(), sunday.minusHours(2), quietHours = true).isEmpty())
    }

    @Test fun remembersTheNewestKeys() {
        assertEquals(listOf("b", "c", "d"), AlertRules.remember(listOf("a", "b", "c"), listOf("c", "d"), keep = 3))
    }

    private fun bill(dueDay: Int, pending: PendingChargeDto? = null) =
        MonthEntry(lineItemId = "x", name = "Car loan", year = 2026, month = 11, kind = "BILL", dueDay = dueDay, budgetedCents = 41_000, pendingCharge = pending)

    @Test fun pendingAndTheTwoDayGrace() {
        val today = LocalDate.of(2026, 11, 21)
        assertEquals("Pending at the bank", EntryStatuses.of(bill(21, PendingChargeDto(txnId = "t", cents = 41_000, postedOn = "2026-11-20", pending = true)), today).text)
        assertEquals("Due yesterday", EntryStatuses.of(bill(20), today).text)
        assertEquals("Due 2 days ago", EntryStatuses.of(bill(19), today).text)
        assertEquals(Tone.DANGER, EntryStatuses.of(bill(18), today).tone)
    }
}
