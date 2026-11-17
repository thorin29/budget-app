package com.budget.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.data.Dates
import com.budget.app.data.Money
import com.budget.app.data.PaceLogic
import com.budget.app.data.remote.InsightsResponse
import com.budget.app.data.remote.LimitDto
import com.budget.app.data.remote.PaceDto
import com.budget.app.data.remote.PayoffDto
import com.budget.app.data.remote.SuggestionDto
import com.budget.app.data.remote.UpcomingDto
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon
import com.budget.app.ui.today.ExtrasUi
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT = DateTimeFormatter.ofPattern("MMM", Locale.US)
private fun monthShort(year: Int, month: Int) = YearMonth.of(year, month).format(SHORT)

/** The Insights tab, as on the web: totals, pace, payoff, suggestions, charts, categories and more. */
@Composable
fun InsightsScreen(extras: ExtrasUi, onMonth: (String?) -> Unit = {}, onOpenCategory: (String) -> Unit = {}) {
    val c = Budget.colors
    val s = extras.insights
    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.statusBarsPadding().padding(start = 4.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Insights", color = c.ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, modifier = Modifier.weight(1f))
                if (s != null && s.year > 0) MonthPicker(s.year, s.month, onMonth)
            }
        }
        if (s == null) {
            item { Text("Insights show once the server is reachable.", color = c.muted, modifier = Modifier.padding(4.dp)) }
            return@LazyColumn
        }

        s.limit?.let { l -> item { LimitCard(l) } }
        item { Totals(s) }
        s.pace?.let { p -> item { PaceCard(p, s.leftAfterBillsCents, s.weekCents) } }
        if (s.payoff.isNotEmpty()) item { PayoffCard(s.payoff) }
        if (s.upcoming.isNotEmpty()) item { ComingUpCard(s.upcoming) }

        if (s.suggestions.isNotEmpty()) {
            item {
                val keep = s.suggestions.sumOf { it.monthlyCents }
                Row(Modifier.padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text("Suggestions", color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (keep > 0) Text("up to ${Money.whole(keep)} a month", color = c.muted, fontSize = 13.sp)
                }
            }
            items(s.suggestions, key = { "${it.kind}-${it.title}" }) { SuggestionRow(it, onOpenCategory) }
            item {
                Text("From the last six complete months of bank history. Tap one for the details.", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }

        if (s.categories.isNotEmpty()) item { CategoriesCard(s, onOpenCategory) }
        if (s.changes.isNotEmpty()) item { ChangesCard(s) }
        if (s.merchants.isNotEmpty()) item { MerchantsCard(s) }

        val shown = s.months.dropWhile { it.everydayCents == 0L && it.billsCents == 0L && it.inCents == 0L }
        if (shown.size >= 2) {
            item {
                Card("Everyday spending, by month") {
                    val rest = s.pace?.let { maxOf(0L, it.projectedCents - it.soFarCents) } ?: 0L
                    BarChart(
                        values = shown.map { it.everydayCents },
                        labels = shown.map { monthShort(it.year, it.month) },
                        extraOnLast = rest,
                    )
                    if (rest > 0) Text("The light part is what a usual month still spends from here.", color = c.muted, fontSize = 12.sp)
                }
            }
            item {
                Card("In minus out, by month") {
                    BarChart(values = shown.map { it.netCents }, labels = shown.map { monthShort(it.year, it.month) })
                    val total = shown.sumOf { it.netCents }
                    Text(
                        "Over these ${shown.size} months, money ${if (total >= 0) "in" else "out"} ran ${Money.whole(kotlin.math.abs(total))} ahead.",
                        color = c.muted, fontSize = 12.sp,
                    )
                }
            }
        }

        if (s.managed.isNotEmpty()) {
            item {
                Card("Managed for others") {
                    s.managed.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("This month: ${Money.format(m.monthInCents)} collected · ${Money.format(m.monthOutCents)} paid out", color = c.muted, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Money.format(kotlin.math.abs(m.heldCents)), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(if (m.heldCents >= 0) "held for others" else "fronted by you", color = c.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthPicker(year: Int, month: Int, onMonth: (String?) -> Unit) {
    val c = Budget.colors
    val shown = YearMonth.of(year, month)
    val now = YearMonth.now()
    Row(
        Modifier.background(c.surface, RoundedCornerShape(12.dp)).border(1.dp, c.line, RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clickable(role = Role.Button) { onMonth(shown.minusMonths(1).toString()) }, contentAlignment = Alignment.Center) {
            PhIcon(Ph.CARET_LEFT, "Previous month", c.ink, size = 18.dp)
        }
        Text(shown.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        val canNext = shown.isBefore(now)
        Box(
            Modifier.size(40.dp).clickable(enabled = canNext, role = Role.Button) {
                val next = shown.plusMonths(1)
                onMonth(if (next == now) null else next.toString())
            },
            contentAlignment = Alignment.Center,
        ) { PhIcon(Ph.CARET_RIGHT, "Next month", if (canNext) c.ink else c.line, size = 18.dp) }
    }
}

@Composable
private fun Card(title: String?, content: @Composable ColumnScope.() -> Unit) {
    val c = Budget.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(20.dp))
            .border(1.dp, c.line, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) Text(title, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun Totals(s: InsightsResponse) {
    val c = Budget.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.fillMaxWidth().background(c.accent, RoundedCornerShape(20.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Everyday spending", color = c.onAccent.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(Money.format(s.everydayCents), color = c.onAccent, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            val diff = s.everydayCents - s.everydayAverageCents
            val vsAverage = when {
                s.everydayAverageCents <= 0 -> "No earlier months to compare"
                kotlin.math.abs(diff) < maxOf(1_000L, s.everydayAverageCents * 3 / 100) -> "About your ${Money.whole(s.everydayAverageCents)} average"
                else -> "${Money.whole(kotlin.math.abs(diff))} ${if (diff > 0) "more" else "less"} than your ${Money.whole(s.everydayAverageCents)} average"
            }
            Text(vsAverage, color = c.onAccent.copy(alpha = 0.9f), fontSize = 13.sp)
            s.lastYear?.let { Text("${Money.whole(it.everydayCents)} in ${monthShort(s.year, s.month)} ${s.year - 1}", color = c.onAccent.copy(alpha = 0.9f), fontSize = 13.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallTotal("Bills paid", Money.format(s.billsCents), Modifier.weight(1f))
            SmallTotal("Money in", Money.format(s.inCents), Modifier.weight(1f))
            SmallTotal("In minus out", Money.format(s.netCents), Modifier.weight(1f), if (s.netCents < 0) c.danger else c.ink)
        }
    }
}

/** The big picture: what everyday spending can be each month, against what it has been. */
@Composable
private fun LimitCard(a: LimitDto) {
    val c = Budget.colors
    val t = a.thisMonth
    Card("Spending limit") {
        if (a.limitCents > 0) {
            Text("Keep everyday spending, cards and debit together, to about", color = c.muted, fontSize = 13.sp)
            Text("${Money.whole(a.limitCents)} a month", color = c.ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        } else {
            Text("The plan's bills and 0% payoff already take more than its income, so nothing is left for everyday spending.", color = c.danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "${Money.whole(a.incomeCents)} income${if (a.incomeSource == "bank") " (recent deposits)" else ""} − ${Money.whole(a.fixedBillsCents)} bills" +
                (if (a.payoffCents > 0) " − ${Money.whole(a.payoffCents)} 0% payoff" else "") + ", averaged over the next 12 months",
            color = c.muted, fontSize = 12.sp,
        )
        if (a.averageCents > 0) {
            val over = a.overCents > 0
            Text(
                if (over) "The last three months averaged ${Money.whole(a.averageCents)}: about ${Money.whole(a.overCents)} a month over (${Money.whole(a.overCents * 12)} a year)."
                else "The last three months averaged ${Money.whole(a.averageCents)}, about ${Money.whole(-a.overCents)} a month under." +
                    "",
                color = if (over) c.danger else c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().background(if (over) c.dangerSoft else c.accentSoft, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            )
            if (a.averageOnCardsCents > 0) Text("${Money.whole(a.averageOnCardsCents)} of it went on cards.", color = c.muted, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("This month: ${Money.whole(t.spentCents)} of ${Money.whole(a.limitCents)}", color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        val scale = maxOf(a.limitCents, t.projectedCents ?: 0L, t.spentCents, 1L).toFloat()
        BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp)) {
            val w = maxWidth
            Box(Modifier.fillMaxWidth().height(10.dp).align(Alignment.Center).background(c.raised, RoundedCornerShape(5.dp)))
            t.projectedCents?.let { p -> Box(Modifier.width(w * (p / scale).coerceIn(0f, 1f)).height(10.dp).align(Alignment.CenterStart).background(c.accentSoft, RoundedCornerShape(5.dp))) }
            Box(Modifier.width(w * (t.spentCents / scale).coerceIn(0f, 1f)).height(10.dp).align(Alignment.CenterStart).background(if (t.spentCents > a.limitCents) c.danger else c.accent, RoundedCornerShape(5.dp)))
            if (a.limitCents > 0) Box(Modifier.offset(x = w * (a.limitCents / scale).coerceIn(0f, 1f) - 1.dp).width(2.dp).fillMaxHeight().background(c.ink))
        }
        Text(
            if (t.leftCents >= 0) "${Money.whole(t.leftCents)} left · about ${Money.whole(t.perWeekLeftCents)} a week for the next ${t.daysLeft} days"
            else "${Money.whole(-t.leftCents)} over the limit already",
            color = if (t.leftCents < 0) c.danger else c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        )
        Text(
            "The line is the limit" + (t.projectedCents?.let { "; at your usual pace the month ends near ${Money.whole(it)}" } ?: "") + ".",
            color = c.muted, fontSize = 12.sp,
        )
        if (a.cardPaymentNames.isNotEmpty()) {
            Text(
                "Card payments (${a.cardPaymentNames.joinToString(", ")}) aren't counted as bills here: they pay for this spending.",
                color = c.muted, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SmallTotal(label: String, value: String, modifier: Modifier, tint: Color = Budget.colors.ink) {
    val c = Budget.colors
    Column(
        modifier.background(c.surface, RoundedCornerShape(16.dp)).border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(value, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PaceCard(p: PaceDto, leftAfterBills: Long?, week: Long?) {
    val c = Budget.colors
    val (spent, projected, usual) = PaceLogic.bar(p)
    Card(null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pace", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Day ${p.day} of ${p.daysInMonth}", color = c.muted, fontSize = 13.sp)
        }
        Text("On pace for ${Money.whole(p.projectedCents)}. ${PaceLogic.verdict(p)}.", color = c.ink, fontSize = 14.sp)
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(16.dp).semantics {
                contentDescription = "Spent ${Money.whole(p.soFarCents)} so far. A usual month has spent ${Money.whole(p.usualByNowCents)} by now."
            },
        ) {
            val w = maxWidth
            Box(Modifier.fillMaxWidth().height(10.dp).align(Alignment.Center).background(c.raised, RoundedCornerShape(5.dp)))
            Box(Modifier.width(w * projected).height(10.dp).align(Alignment.CenterStart).background(c.accentSoft, RoundedCornerShape(5.dp)))
            Box(Modifier.width(w * spent).height(10.dp).align(Alignment.CenterStart).background(c.accent, RoundedCornerShape(5.dp)))
            Box(Modifier.offset(x = (w * usual) - 1.dp).width(2.dp).fillMaxHeight().background(c.ink))
        }
        Text(
            "${Money.whole(p.soFarCents)} so far · a usual month is at ${Money.whole(p.usualByNowCents)} by now (the line) · usual month ${Money.whole(p.usualMonthCents)}",
            color = c.muted, fontSize = 12.sp,
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        leftAfterBills?.let {
            Fact("Left after this month's bills", Money.whole(it), "in the bills account, once bills and paychecks land", if (it < 0) c.danger else c.ink)
        }
        val rest = maxOf(0L, p.projectedCents - p.soFarCents)
        Fact(
            "Still to come this month", Money.whole(rest),
            "everyday spending at a usual pace" + if (p.restOnCardsCents > 0) ", about ${Money.whole(p.restOnCardsCents)} of it on cards (paid next month)" else "",
        )
        week?.let { Fact("Last 7 days", Money.whole(it), "everyday spending") }
    }
}

@Composable
private fun Fact(label: String, value: String, note: String, tint: Color = Budget.colors.ink) {
    val c = Budget.colors
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(label, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(note, color = c.muted, fontSize = 12.sp)
        }
        Text(value, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PayoffCard(rows: List<PayoffDto>) {
    val c = Budget.colors
    Card("0% payoff") {
        rows.forEach { r ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(if (r.onTrack) "On track" else "Behind plan", color = if (r.onTrack) c.accent else c.warn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                val start = r.startCents
                if (start != null && start > 0) {
                    val share = ((r.paidCents ?: 0L).toFloat() / start).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(10.dp).background(c.raised, RoundedCornerShape(5.dp))) {
                        Box(Modifier.fillMaxWidth(share).height(10.dp).background(c.accent, RoundedCornerShape(5.dp)))
                    }
                    Text("${Money.whole(r.paidCents ?: 0L)} of ${Money.whole(start)} paid down", color = c.ink, fontSize = 13.sp)
                }
                Text(
                    "${Money.whole(r.balanceCents)} left · ${r.paymentsLeft} payment${if (r.paymentsLeft == 1) "" else "s"} of ${Money.whole(r.monthlyCents)} clear it by ${Dates.short(r.endsOn) ?: r.endsOn}, ${r.endsOn.take(4)}" +
                        if (!r.onTrack && r.planMonthlyCents != null) " (the plan was ${Money.whole(r.planMonthlyCents)} a month)" else "",
                    color = c.muted, fontSize = 12.sp,
                )
            }
        }
    }
}

/** Yearly charges expected soon. Plan or hide them on the web. */
@Composable
private fun ComingUpCard(items: List<UpcomingDto>) {
    val c = Budget.colors
    Card("Coming up") {
        Text("Yearly charges due in the next 45 days, from bank history. Add them as bills on the web.", color = c.muted, fontSize = 13.sp)
        items.forEach { u ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(u.merchant, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Around ${Dates.short(u.expectedOn) ?: u.expectedOn}" + if (u.years >= 2) " · ${u.years} years running" else "",
                        color = c.muted, fontSize = 13.sp,
                    )
                }
                Text(Money.format(u.cents), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SuggestionRow(s: SuggestionDto, onOpenCategory: (String) -> Unit) {
    val c = Budget.colors
    var open by rememberSaveable(s.kind, s.title) { mutableStateOf(false) }
    val (icon, tint) = when (s.kind) {
        "rising", "spike", "price-up", "new-repeat" -> Ph.TREND_UP to c.warn
        "interest", "fees" -> Ph.WARNING to c.danger
        "payoff" -> Ph.CHECK_CIRCLE to c.accent
        "repeating" -> Ph.SYNC to c.accent
        else -> Ph.LIGHTBULB to c.accent
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(18.dp))
            .border(1.dp, c.line, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button) { open = !open }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            PhIcon(icon, null, tint, size = 20.dp)
            Spacer(Modifier.width(12.dp))
            Text(s.title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PhIcon(if (open) Ph.CARET_DOWN else Ph.CARET_RIGHT, null, c.muted, size = 18.dp)
        }
        if (open) {
            Text(s.detail, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 32.dp))
            if (s.months.isNotEmpty()) {
                Box(Modifier.padding(start = 32.dp)) {
                    BarChart(values = s.months.map { it.cents }, labels = s.months.map { it.label }, height = 72, highlightLast = 3)
                }
            }
            s.items.forEach { it ->
                Row(Modifier.padding(start = 32.dp)) {
                    Text(it.name, color = c.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        (it.fromCents?.let { f -> "${Money.format(f)} → " } ?: "") + Money.format(it.cents),
                        color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            s.categoryId?.let { id ->
                Text(
                    "See the charges",
                    color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 32.dp).clickable(role = Role.Button) { onOpenCategory(id) },
                )
            }
        }
    }
}

/** Where it went: this month by category, with the usual and last year. Tap one for its charges. */
@Composable
private fun CategoriesCard(s: InsightsResponse, onOpenCategory: (String) -> Unit) {
    val c = Budget.colors
    val max = (s.categories.maxOfOrNull { maxOf(it.cents, it.averageCents) } ?: 1L).coerceAtLeast(1L)
    Card("Where it went") {
        Text("Everyday spending · the line is the 3-month average · tap one for its charges", color = c.muted, fontSize = 12.sp)
        s.categories.forEach { cat ->
            Column(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenCategory(cat.id ?: "none") }.padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row {
                    Text(cat.name, color = if (cat.id == null) c.muted else c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(Money.format(cat.cents), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                BoxWithConstraints(Modifier.fillMaxWidth().height(12.dp)) {
                    val w = maxWidth
                    Box(Modifier.fillMaxWidth().height(6.dp).align(Alignment.Center).background(c.raised, RoundedCornerShape(3.dp)))
                    Box(Modifier.width(w * (cat.cents.toFloat() / max).coerceIn(0f, 1f)).height(6.dp).align(Alignment.CenterStart).background(c.accent, RoundedCornerShape(3.dp)))
                    if (cat.averageCents > 0) {
                        Box(Modifier.offset(x = w * (cat.averageCents.toFloat() / max).coerceIn(0f, 1f) - 1.dp).width(2.dp).fillMaxHeight().background(c.ink))
                    }
                }
                Text(
                    "Usually ${Money.whole(cat.averageCents)}" + (cat.lastYearCents?.let { " · ${Money.whole(it)} in ${monthShort(s.year, s.month)} ${s.year - 1}" } ?: ""),
                    color = c.muted, fontSize = 12.sp,
                )
            }
        }
        if (s.uncategorizedCount > 0) {
            Text("${s.uncategorizedCount} charge${if (s.uncategorizedCount == 1) "" else "s"} without a category. Sort them on the web.", color = c.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ChangesCard(s: InsightsResponse) {
    val c = Budget.colors
    Card("What changed") {
        s.changes.forEach { ch ->
            val up = ch.cents > ch.averageCents
            Row(verticalAlignment = Alignment.Top) {
                Text(if (up) "▲" else "▼", color = if (up) c.warn else c.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("${ch.name} ${if (up) "up" else "down"} ${Money.whole(kotlin.math.abs(ch.cents - ch.averageCents))}", color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("${Money.format(ch.cents)} this month against a ${Money.whole(ch.averageCents)} average", color = c.muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MerchantsCard(s: InsightsResponse) {
    val c = Budget.colors
    Card("Top merchants") {
        s.merchants.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.merchant + if (m.count > 1) "  ×${m.count}" else "", color = c.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(Money.format(m.cents), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Plain vertical bars with a value and label under each. Negative values hang
 * below a zero line. [extraOnLast] adds a lighter part on top of the last bar
 * (what's still to come). The last [highlightLast] bars are drawn solid.
 */
@Composable
fun BarChart(values: List<Long>, labels: List<String>, height: Int = 120, extraOnLast: Long = 0, highlightLast: Int = 1) {
    val c = Budget.colors
    if (values.isEmpty()) return
    val tops = values.mapIndexed { i, v -> if (i == values.lastIndex) v + extraOnLast else v }
    val maxUp = (tops.maxOrNull() ?: 0L).coerceAtLeast(0L)
    val maxDown = (-(values.minOrNull() ?: 0L)).coerceAtLeast(0L)
    val range = (maxUp + maxDown).coerceAtLeast(1L).toFloat()
    val upShare = maxUp / range
    Column(Modifier.fillMaxWidth().semantics { contentDescription = labels.zip(values).joinToString(", ") { (l, v) -> "$l ${Money.whole(v)}" } }) {
        Row(Modifier.fillMaxWidth().height(height.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            values.forEachIndexed { i, v ->
                val solid = i >= values.size - highlightLast
                val color = if (v < 0) c.danger else c.accent
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    // Above the zero line.
                    Column(Modifier.fillMaxWidth().weight(upShare.coerceAtLeast(0.0001f)), verticalArrangement = Arrangement.Bottom) {
                        if (i == values.lastIndex && extraOnLast > 0) {
                            Box(Modifier.fillMaxWidth().height((height * extraOnLast / range).dp).background(c.accentSoft, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                        }
                        if (v > 0) {
                            Box(
                                Modifier.fillMaxWidth().height((height * v / range).dp.coerceAtLeast(2.dp))
                                    .background(color.copy(alpha = if (solid) 1f else 0.45f), if (i == values.lastIndex && extraOnLast > 0) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                            )
                        }
                    }
                    // Below it.
                    if (maxDown > 0) {
                        Column(Modifier.fillMaxWidth().weight((1f - upShare).coerceAtLeast(0.0001f))) {
                            if (v < 0) {
                                Box(
                                    Modifier.fillMaxWidth().height((height * -v / range).dp.coerceAtLeast(2.dp))
                                        .background(color.copy(alpha = if (solid) 1f else 0.45f), RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)),
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            values.forEachIndexed { i, v ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(labels.getOrElse(i) { "" }, color = c.muted, fontSize = 10.sp, maxLines = 1)
                    Text(Money.whole(v).replace("$", ""), color = c.muted, fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
