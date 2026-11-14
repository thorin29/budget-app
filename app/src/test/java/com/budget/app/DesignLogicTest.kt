package com.budget.app

import com.budget.app.data.Accent
import com.budget.app.data.AmountInput
import com.budget.app.data.BillIconKey
import com.budget.app.data.BillIcons
import com.budget.app.data.EntryStatuses
import com.budget.app.data.MonthLogic
import com.budget.app.data.PlanLogic
import com.budget.app.data.ThemeMode
import com.budget.app.data.Tone
import com.budget.app.data.remote.HalfInfo
import com.budget.app.data.remote.HalfSummary
import com.budget.app.data.remote.LowPoint
import com.budget.app.data.remote.MonthEntry
import com.budget.app.data.remote.MonthView
import com.budget.app.data.remote.Projection
import com.budget.app.data.remote.ProjectionDay
import com.budget.app.data.remote.YearMonthRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

private fun entry(
    id: String,
    name: String,
    dueDay: Int? = null,
    actual: Long? = null,
    kind: String = "BILL",
    category: String? = null,
    carried: Boolean = false,
    budgeted: Long = 10_00,
) = MonthEntry(
    lineItemId = id, name = name, year = 2026, month = 11, kind = kind, categoryName = category,
    dueDay = dueDay, budgetedCents = budgeted, actualCents = actual, paidOn = actual?.let { "2026-11-03" },
    carriedFrom = if (carried) YearMonthRef(2026, 10) else null,
)

class BillIconsTest {
    @Test fun picksFromName() {
        assertEquals(BillIconKey.ELECTRIC, BillIcons.forItem("Electric", null, false))
        assertEquals(BillIconKey.WATER, BillIcons.forItem("City water & sewer", null, false))
        assertEquals(BillIconKey.STREAMING, BillIcons.forItem("Netflix", null, false))
    }

    @Test fun insuranceBeatsCar() {
        assertEquals(BillIconKey.INSURANCE, BillIcons.forItem("Car insurance", null, false))
        assertEquals(BillIconKey.CAR, BillIcons.forItem("Car payment", null, false))
    }

    @Test fun cardIsNotCar() {
        assertEquals(BillIconKey.CARD, BillIcons.forItem("Credit card", null, false))
    }

    @Test fun fallsBackToCategoryThenReceipt() {
        assertEquals(BillIconKey.HOME, BillIcons.forItem("Big monthly one", "Mortgage", false))
        assertEquals(BillIconKey.OTHER, BillIcons.forItem("Something", "Misc", false))
        assertEquals(BillIconKey.INCOME, BillIcons.forItem("Anything", null, true))
    }
}

class AmountInputTest {
    @Test fun firstDigitReplacesThePlannedAmount() {
        assertEquals("5", AmountInput.press("172.40", '5', fresh = true))
        assertEquals("1725", AmountInput.press("172", '5', fresh = false))
    }

    @Test fun decimals() {
        assertEquals("0.", AmountInput.press("", '.', fresh = false))
        assertEquals("12.", AmountInput.press("12.", '.', fresh = false))
        assertEquals("12.34", AmountInput.press("12.34", '9', fresh = false))
        assertEquals(1234L, AmountInput.cents("12.34"))
        assertEquals(1200L, AmountInput.cents("12."))
        assertNull(AmountInput.cents(""))
    }

    @Test fun deleteAndLeadingZero() {
        assertEquals("17", AmountInput.press("172", AmountInput.DELETE, fresh = false))
        assertEquals("", AmountInput.press("172.40", AmountInput.DELETE, fresh = true))
        assertEquals("7", AmountInput.press("0", '7', fresh = false))
    }

    @Test fun capsWholeDigits() {
        assertEquals("1234567", AmountInput.press("1234567", '8', fresh = false))
    }

    @Test fun groupsThousands() {
        assertEquals("1,234.5", AmountInput.display("1234.5"))
        assertEquals("0", AmountInput.display(""))
        assertEquals("999", AmountInput.display("999"))
    }
}

class EntryStatusTest {
    private val today = LocalDate.of(2026, 11, 14)

    @Test fun dueDates() {
        assertEquals("Due today", EntryStatuses.of(entry("a", "A", dueDay = 14), today).text)
        assertEquals(Tone.WARN, EntryStatuses.of(entry("a", "A", dueDay = 16), today).tone)
        assertEquals(Tone.MUTED, EntryStatuses.of(entry("a", "A", dueDay = 25), today).tone)
        assertEquals(Tone.DANGER, EntryStatuses.of(entry("a", "A", dueDay = 2), today).tone)
    }

    @Test fun paidCarriedIncome() {
        assertEquals("Paid Nov 3", EntryStatuses.of(entry("a", "A", actual = 500), today).text)
        assertEquals("Still owed from November", EntryStatuses.of(entry("a", "A", carried = true), today).text)
        assertEquals("Expected Nov 15", EntryStatuses.of(entry("p", "Pay", dueDay = 15, kind = "INCOME"), today).text)
    }
}

class MonthLogicTest {
    private val view = MonthView(
        year = 2026, month = 11,
        carried = listOf(entry("old", "Gym", carried = true)),
        halves = listOf(
            HalfSummary(HalfInfo(0, "1st half"), listOf(entry("b", "Water", dueDay = 18), entry("a", "Electric", dueDay = 14), entry("c", "Phone", dueDay = 3, actual = 85_00))),
        ),
        income = listOf(entry("p", "Pay", dueDay = 15, kind = "INCOME")),
        totalBudgetedCents = 200_00,
        totalPaidCents = 50_00,
    )

    @Test fun dueSoonPutsDebtFirstThenByDay() {
        assertEquals(listOf("Gym", "Electric", "Water"), MonthLogic.dueSoon(view).map { it.name })
    }

    @Test fun everythingIncludesPaidAndIncomeOnce() {
        assertEquals(listOf("Gym", "Water", "Electric", "Phone", "Pay"), MonthLogic.everything(view).map { it.name })
    }

    @Test fun paidShare() {
        assertEquals(0.25f, MonthLogic.paidShare(view), 0.0001f)
        assertEquals(0f, MonthLogic.paidShare(view.copy(totalBudgetedCents = 0)), 0f)
    }
}

class PlanLogicTest {
    private val p = Projection(
        safeToPayCents = 412_00,
        lowPoint = LowPoint("2026-11-28", 608_00),
        openingBalanceCents = 2_340_00,
        bufferCents = 196_00,
        days = listOf(ProjectionDay("2026-11-15", balanceCents = 2_000_00), ProjectionDay("2026-11-28", balanceCents = 608_00)),
    )

    @Test fun verdicts() {
        assertEquals(PlanLogic.Verdict.FINE, PlanLogic.ifPaid(p, 100_00).verdict)
        assertEquals(PlanLogic.Verdict.INTO_CUSHION, PlanLogic.ifPaid(p, 500_00).verdict)
        assertEquals(PlanLogic.Verdict.BELOW_ZERO, PlanLogic.ifPaid(p, 700_00).verdict)
        assertEquals(0L, PlanLogic.ifPaid(p, 500_00).safeCents)
        assertEquals(508_00L, PlanLogic.ifPaid(p, 100_00).lowCents)
    }

    @Test fun seriesStartsWithOpeningBalance() {
        assertEquals(listOf(2_240_00L, 1_900_00L, 508_00L), PlanLogic.series(p, 100_00))
    }

    @Test fun sliderMax() {
        assertEquals(830_00L, PlanLogic.sliderMaxCents(p))
        assertEquals(500_00L, PlanLogic.sliderMaxCents(p.copy(safeToPayCents = 0)))
    }
}

class AppearanceTest {
    @Test fun defaults() {
        assertEquals(Accent.COBALT, Accent.from(null))
        assertEquals(Accent.COBALT, Accent.from("nonsense"))
        assertEquals(Accent.GRAPHITE, Accent.from("graphite"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from(null))
        assertEquals(ThemeMode.DARK, ThemeMode.from("dark"))
    }
}

class ReminderLogicTest {
    private val view = MonthView(
        year = 2026, month = 11,
        halves = listOf(
            HalfSummary(
                HalfInfo(1, "2nd half"),
                listOf(
                    entry("w", "Water", dueDay = 18, budgeted = 64_12),
                    entry("e", "Electric", dueDay = 18, budgeted = 172_40),
                    entry("p", "Phone", dueDay = 18, actual = 85_00),
                    entry("i", "Internet", dueDay = 22),
                ),
            ),
        ),
        income = listOf(entry("pay", "Pay", dueDay = 18, kind = "INCOME")),
    )
    private val dec = MonthView(year = 2026, month = 12, halves = listOf(HalfSummary(HalfInfo(0, "1st"), listOf(entry("m", "Mortgage", dueDay = 1).copy(year = 2026, month = 12)))))

    @Test fun picksUnpaidBillsDueOnTheLeadDay() {
        val due = com.budget.app.data.ReminderLogic.dueOn(listOf(view), LocalDate.of(2026, 11, 17), com.budget.app.data.ReminderLead.ONE_DAY)
        assertEquals(listOf("Electric", "Water"), due.map { it.name })
    }

    @Test fun offAndOtherDaysAreEmpty() {
        assertEquals(0, com.budget.app.data.ReminderLogic.dueOn(listOf(view), LocalDate.of(2026, 11, 17), com.budget.app.data.ReminderLead.OFF).size)
        assertEquals(0, com.budget.app.data.ReminderLogic.dueOn(listOf(view), LocalDate.of(2026, 11, 15), com.budget.app.data.ReminderLead.DAY_OF).size)
    }

    @Test fun reachesIntoNextMonth() {
        val due = com.budget.app.data.ReminderLogic.dueOn(listOf(view, dec), LocalDate.of(2026, 11, 29), com.budget.app.data.ReminderLead.TWO_DAYS)
        assertEquals(listOf("Mortgage"), due.map { it.name })
    }

    @Test fun messages() {
        val one = com.budget.app.data.ReminderLogic.message(listOf(entry("e", "Electric", budgeted = 172_40)), com.budget.app.data.ReminderLead.DAY_OF)!!
        assertEquals("Electric is due today", one.title)
        assertEquals("$172.40 · tap to record it", one.body)
        val two = com.budget.app.data.ReminderLogic.message(
            listOf(entry("e", "Electric", budgeted = 172_40), entry("w", "Water", budgeted = 64_12)),
            com.budget.app.data.ReminderLead.ONE_DAY,
        )!!
        assertEquals("2 bills due tomorrow", two.title)
        assertEquals("Electric $172.40, Water $64.12", two.body)
        assertNull(com.budget.app.data.ReminderLogic.message(emptyList(), com.budget.app.data.ReminderLead.ONE_DAY))
    }
}
