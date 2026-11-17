package com.budget.app

import org.junit.Test
import com.budget.app.data.MonthPlanLogic
import com.budget.app.data.PlanAlerts
import com.budget.app.data.remote.*

class MonthPlanLogicTest {
    private fun g(id: String, tier: String, manual: Long?, spent: Long, usual: Long = 0) =
        PlanGroupDto(id = id, name = id, tier = tier, manualCents = manual, usualCents = usual, spentCents = spent)
    private val base = MonthPlanResponse(
        month = "2026-11", daysLeft = 29, daysInMonth = 30,
        income = PlanIncomeTotalsDto(totalCents = 500_000), bills = PlanBillsDto(totalCents = 142_000), debt = PlanDebtDto(cents = 28_000),
        safeCents = 90_000,
        groups = listOf(
            g("groceries", "ESSENTIAL", 160_000, 20_000), g("fuel", "ESSENTIAL", 50_000, 6_000), g("household", "ESSENTIAL", 30_000, 0),
            g("eating", "FLEXIBLE", 40_000, 0), g("shopping", "FLEXIBLE", 30_000, 0), g("fun", "FLEXIBLE", 20_000, 0),
            g("repairs", "UNPLANNED", null, 0, 30_000),
        ),
    )
    private fun room(o: MonthPlanLogic.Out, id: String) = o.groups.first { it.id == id }.roomCents

    @Test fun matchesTheServerExample() {
        val day2 = MonthPlanLogic.whatIf(base, emptyList())
        check(day2.poolCents == 330_000L && day2.stillToComeCents == 214_000L && day2.safeCents == 90_000L) { "$day2" }
        val asks = MonthPlanLogic.whatIf(base, listOf("eating" to 8_000L, "eating" to 2_000L, "shopping" to 25_000L))
        check(asks.safeCents == 55_000L && room(asks, "eating") == 30_000L && room(asks, "shopping") == 5_000L && room(asks, "fun") == 20_000L) { "$asks" }
        val repair = MonthPlanLogic.whatIf(base, listOf("eating" to 10_000L, "shopping" to 25_000L, "repairs" to 60_000L))
        check(repair.safeCents == -5_000L && room(repair, "fun") == 0L && room(repair, "eating") == 0L) { "$repair" }
    }

    @Test fun alertsOnTheMonthTotalOnly() {
        check(PlanAlerts.level(base) == null)
        check(PlanAlerts.level(base.copy(safeCents = 15_000)) == "low")
        check(PlanAlerts.level(base.copy(safeCents = -100)) == "over")
        check(PlanAlerts.level(base.copy(safeCents = -100, closed = true)) == null)
        check(PlanAlerts.words(base.copy(safeCents = -7_100))!!.first == "Over by $71 this month")
    }
}
