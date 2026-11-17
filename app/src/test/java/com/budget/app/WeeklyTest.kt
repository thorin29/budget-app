package com.budget.app

import com.budget.app.data.WeeklyLogic
import com.budget.app.data.remote.InsightsResponse
import com.budget.app.data.remote.PaceDto
import com.budget.app.data.remote.UpcomingDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Merchants and amounts are invented.
class WeeklyTest {
    @Test fun summarizesTheWeek() {
        val s = InsightsResponse(
            weekCents = 54_000,
            leftAfterBillsCents = 232_000,
            pace = PaceDto(day = 15, daysInMonth = 30, soFarCents = 81_200, usualByNowCents = 40_100, usualMonthCents = 59_700, projectedCents = 100_900),
            upcoming = listOf(UpcomingDto(merchant = "Example Club", expectedOn = "2026-12-01", cents = 13_900, years = 2)),
        )
        val m = WeeklyLogic.message(s)!!
        assertEquals("This week: \$540 everyday spending", m.title)
        assertEquals(3, m.lines.size)
        assertEquals("\$2,320 left after this month's bills.", m.lines[1])
    }

    @Test fun nothingWithoutAWeek() {
        assertNull(WeeklyLogic.message(InsightsResponse()))
    }
}
