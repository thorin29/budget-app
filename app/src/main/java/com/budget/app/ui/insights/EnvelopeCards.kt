package com.budget.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.data.Dates
import com.budget.app.data.Money
import com.budget.app.data.remote.EnvelopeDto
import com.budget.app.data.remote.EnvelopesResponse
import com.budget.app.ui.common.Panel
import com.budget.app.ui.common.Pill
import com.budget.app.ui.theme.Budget

/**
 * Spending envelopes (server 0.40+): the flexible money left this pay period,
 * where each paycheck goes, every envelope, and "Can I afford it?". Limits and
 * the envelopes themselves are changed on the web.
 */

private val OK = Color(0xFF1F8A4C)

private fun until(v: EnvelopesResponse): String {
    val next = Dates.short(v.period.next) ?: v.period.next
    return if (v.period.byPayday && v.period.view != "month") "until payday, $next" else "until $next"
}

/**
 * The headline: a donut of what's left in the spending plan by kind (with any
 * overage as a red slice), the total in the middle, and each kind in words.
 * Used on Today and Insights.
 */
@Composable
fun EnvelopeHero(v: EnvelopesResponse, modifier: Modifier = Modifier) {
    val c = Budget.colors
    // Servers before 0.41 only send the flexible figure.
    val left = v.allLeftCents ?: v.flexibleLeftCents
    val kinds = listOf(
        Triple("ESSENTIAL", "Essentials", "groceries, vehicles, health, school") to OK,
        Triple("PLANNED", "Planned", "giving, sports") to Color(0xFF6E56CF),
        Triple("FLEXIBLE", "Flexible", "eating out, shopping, fun") to c.accent,
    )
    val parts = kinds.map { (k, color) -> Triple(k, maxOf(0L, v.byTier[k.first]?.leftCents ?: 0L), color) }
    val over = v.allOverCents
    val slices = parts.map { it.second to it.third } + if (over > 0) listOf(over to c.danger) else emptyList()
    val total = slices.sumOf { it.first }.coerceAtLeast(1)
    Column(
        modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(24.dp)).border(1.dp, c.line, RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("${if (left < 0) "Over" else "Left in your spending plan"} ${until(v)}", color = c.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                val track = c.raised
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 18.dp.toPx())
                    val inset = 9.dp.toPx()
                    val arc = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arc, style = stroke)
                    if (v.byTier.isNotEmpty() || over > 0) {
                        var start = -90f
                        slices.forEach { (cents, color) ->
                            val sweep = 360f * cents / total
                            if (sweep > 1f) drawArc(color, start, sweep - 1.5f, false, topLeft = Offset(inset, inset), size = arc, style = stroke)
                            start += sweep
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Money.whole(kotlin.math.abs(left)), color = if (left < 0) c.danger else c.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if (left < 0) "over" else "left", color = c.muted, fontSize = 12.sp)
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                parts.forEach { (k, cents, color) ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 5.dp).size(10.dp).background(color, RoundedCornerShape(5.dp)))
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(k.second, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(k.third, color = c.muted, fontSize = 11.sp)
                        }
                        Text(Money.whole(cents), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (over > 0) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 5.dp).size(10.dp).background(c.danger, RoundedCornerShape(5.dp)))
                        Text("Over, nothing to cover it", color = c.danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        Text(Money.whole(over), color = c.danger, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (left > 0) Text("About ${Money.whole(v.perDayCents)} a day for ${v.period.daysLeft} more day${if (v.period.daysLeft == 1) "" else "s"}", color = c.muted, fontSize = 13.sp)
    }
}

/** Pay period or calendar month. */
@Composable
fun EnvelopeViewToggle(byMonth: Boolean, onChange: (Boolean) -> Unit) {
    val c = Budget.colors
    Row(Modifier.background(c.raised, RoundedCornerShape(14.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(false to "This pay period", true to "This month").forEach { (month, label) ->
            val on = month == byMonth
            Text(
                label,
                color = if (on) c.ink else c.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.background(if (on) c.surface else Color.Transparent, RoundedCornerShape(10.dp)).clickable { onChange(month) }.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

/** The sentence under the headline, when something needs attention. */
@Composable
fun EnvelopeMessage(v: EnvelopesResponse) {
    val c = Budget.colors
    val text = v.message ?: return
    val calm = v.standing == "ok" || v.standing == "low"
    Text(
        text,
        color = if (calm) c.warn else c.danger, fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().background(if (calm) c.warnSoft else c.dangerSoft, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
fun EnvelopeLeftover(v: EnvelopesResponse) {
    val c = Budget.colors
    val last = v.lastPeriod?.takeIf { it.leftoverCents >= 100 } ?: return
    Text(
        "${Money.whole(last.leftoverCents)} of flexible money wasn't spent last pay period. It can go to debt.",
        color = if (c.dark) Color(0xFF7BD9A3) else OK, fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().background(if (c.dark) Color(0xFF0F3324) else Color(0xFFDCFAE6), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun Step(n: Int, label: String, cents: Long, note: String) {
    val c = Budget.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(22.dp).background(c.accentSoft, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Text("$n", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Row {
                Text(label, color = c.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(Money.whole(cents), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Text(note, color = c.muted, fontSize = 12.sp)
        }
    }
}

/** The order each paycheck is handed out in. */
@Composable
fun PaycheckStepsCard(v: EnvelopesResponse) {
    val c = Budget.colors
    val d = v.debt
    val each = if (v.period.byPayday) "payday" else "month"
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (v.period.view == "month" || !v.period.byPayday) "This month's pay" else "This paycheck", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${Money.whole(v.incomeCents)} in", color = c.muted, fontSize = 13.sp)
        }
        Step(1, "Bills", v.billsCents, if (v.basis == "due") "Bills due by ${Dates.short(v.period.next) ?: v.period.next}" else "A share of the plan's bills")
        Step(2, "Essentials", v.essentialCents, "Food, fuel, medical and the like")
        Step(3, "Planned", v.plannedCents, "Giving, sports and other planned spending")
        val owed = d.balanceCents
        val done = d.doneOn
        val debtNote = if (owed == null) {
            if (d.automatic) "From the plan's payments" else "Set by you"
        } else if (done == null) {
            "${Money.whole(owed)} owed. Nothing is going to it yet."
        } else {
            "${Money.whole(owed)} owed${if (d.source == "minimum") " · about the minimum" else ""} · clears ${Dates.monthYear(done) ?: done} at this rate"
        }
        Step(4, "Debt payoff", d.perPeriodCents, debtNote)
        Step(5, "Flexible", v.flexibleCents, "What's left, shared by the flexible budgets")
        val ends = d.endsOn
        val needed = d.neededCents
        if (ends != null && done != null && done > ends && needed != null) {
            Text(
                "The 0% ends ${Dates.monthYear(ends) ?: ends}. Clearing it by then takes ${Money.whole(needed)} each $each.",
                color = c.warn, fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth().background(c.warnSoft, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        if (v.planShortCents > 0) {
            Text(
                "These steps need ${Money.whole(v.planShortCents)} more than the income, before any flexible spending.",
                color = c.danger, fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth().background(c.dangerSoft, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun EnvelopeRow(e: EnvelopeDto, daysShare: Float, onOpenCategory: (String) -> Unit) {
    val c = Budget.colors
    var open by rememberSaveable(e.id) { mutableStateOf(false) }
    val have = e.limitCents + (e.carriedCents ?: 0)
    val used = if (have > 0) e.spentCents.toFloat() / have else if (e.spentCents > 0) 1f else 0f
    val target = e.saveUpTargetCents
    val fast = !e.saveUp && e.overCents == 0L && used > minOf(1f, daysShare + 0.2f)
    val barColor = when {
        e.overCents > 0 -> c.danger
        fast -> c.warn
        e.saveUp || e.tier != "FLEXIBLE" -> OK
        else -> c.accent
    }
    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            when {
                e.overCents > 0 -> Pill("${Money.whole(e.overCents)} over", c.danger, c.dangerSoft)
                e.saveUp -> Text("${Money.whole(e.leftCents)} saved" + (target?.let { " of ${Money.whole(it)}" } ?: ""), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                else -> Text("${Money.whole(e.leftCents)} left of ${Money.whole(have)}", color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        val fill = if (e.saveUp && target != null && target > 0) e.leftCents.toFloat() / target else used
        Box(Modifier.fillMaxWidth().height(8.dp).background(c.raised, RoundedCornerShape(4.dp))) {
            Box(Modifier.fillMaxWidth(fill.coerceIn(0f, 1f)).height(8.dp).background(barColor, RoundedCornerShape(4.dp)))
        }
        val carriedIn = if (!e.saveUp) maxOf(0L, e.carriedCents ?: 0) else 0L
        val note = when {
            e.saveUp -> "${Money.whole(e.limitCents)} set aside each payday"
            e.overCents > 0 -> "${Money.whole(e.spentCents)} spent of ${Money.whole(have)}"
            else -> listOfNotNull(
                if (e.gaveCents > 0) "Gave ${Money.whole(e.gaveCents)} to cover another budget" else null,
                if (carriedIn > 0) "Includes ${Money.whole(carriedIn)} not used earlier" else null,
                if (fast) "Going faster than the pay period" else null,
            ).joinToString(" · ")
        }
        if (note.isNotEmpty()) Text(note, color = c.muted, fontSize = 12.sp)
        if (open) {
            Column(Modifier.fillMaxWidth().background(c.raised, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (e.categories.isEmpty()) Text("Nothing spent from it since payday.", color = c.muted, fontSize = 12.sp)
                e.categories.forEach { cat ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenCategory(cat.id ?: "none") }) {
                        Text(cat.name, color = c.ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(Money.whole(cat.spentCents), color = c.ink, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** One kind of envelope: Essentials, Planned or Flexible. */
@Composable
fun EnvelopeTierCard(v: EnvelopesResponse, tier: String, onOpenCategory: (String) -> Unit) {
    val c = Budget.colors
    val rows = v.groups.filter { it.tier == tier }
    if (rows.isEmpty()) return
    val (title, note) = when (tier) {
        "ESSENTIAL" -> "Essentials" to "Protected. What isn't used stays in the budget."
        "PLANNED" -> "Planned" to "Giving, sports and other spending you plan for."
        else -> "Flexible" to "One shared pot. A budget that runs over takes from the others."
    }
    val daysShare = if (v.period.days > 0) (v.period.days - v.period.daysLeft).toFloat() / v.period.days else 0f
    Panel {
        Text(title, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(note, color = c.muted, fontSize = 12.sp)
        rows.forEach { e -> EnvelopeRow(e, daysShare, onOpenCategory) }
        if (tier == "FLEXIBLE") {
            Row(Modifier.padding(top = 4.dp)) {
                Text("Flexible left", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(Money.whole(maxOf(0L, v.flexibleLeftCents)), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** What's left in every envelope, added up, and what's over. */
@Composable
fun EnvelopeSummaryCard(v: EnvelopesResponse) {
    val c = Budget.colors
    if (v.byTier.isEmpty()) return
    Panel {
        Text("Summary", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("What's left in every budget, added up.", color = c.muted, fontSize = 12.sp)
        listOf("ESSENTIAL" to "Essentials", "PLANNED" to "Planned", "FLEXIBLE" to "Flexible").forEach { (tier, label) ->
            val t = v.byTier[tier] ?: return@forEach
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = c.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("${Money.whole(t.leftCents)} left", color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (t.overCents > 0) Text("  ${Money.whole(t.overCents)} over", color = c.danger, fontSize = 13.sp)
            }
        }
        if (v.allOverCents > 0) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Over, with nothing left to cover it", color = c.danger, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(Money.whole(v.allOverCents), color = c.danger, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        val total = v.allLeftCents ?: 0
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text(if (total < 0) "Over in total" else "Left in total", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(Money.whole(kotlin.math.abs(total)), color = if (total < 0) c.danger else c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** What to do with the paycheck: the debt payment, the bills to hold, each card, and what stays in checking. */
@Composable
fun PaydayPlanCard(v: EnvelopesResponse) {
    val c = Budget.colors
    val p = v.payday ?: return
    val sent = p.cards.sumOf { it.sendCents }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (p.isPayday) "Today's payday plan" else "Payday plan", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${Money.whole(p.checkingCents)} in checking", color = c.muted, fontSize = 13.sp)
        }
        Text(if (p.isPayday) "What to do with today's paycheck." else "With what's in checking now, until payday ${Dates.short(v.period.next) ?: ""}.", color = c.muted, fontSize = 12.sp)
        var n = 1
        if (p.debtCents > 0) {
            PlanLine("${n++}. Send to ${p.debtName ?: "the 0% card"}", p.debtCents, p.debtDueOn?.let { "Due ${Dates.short(it) ?: it}" })
        }
        PlanLine(
            "${n++}. Hold for bills before payday", p.holdCents,
            if (p.bills.isEmpty()) "Nothing else is due before payday." else p.bills.joinToString(" · ") { "${it.name} ${Money.whole(it.cents)} (${Dates.short(it.dueOn) ?: it.dueOn})" },
        )
        PlanLine("$n. Send to the cards", sent, null)
        if (p.forCardsCents < 0) {
            Text(
                "Checking is ${Money.whole(-p.forCardsCents)} short of the bills and debt payment, before any card payment.",
                color = c.danger, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().background(c.dangerSoft, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        p.cards.filter { it.owedCents > 0 }.forEach { card ->
            val (text, tint) = when (card.result) {
                "zero" -> "pays it to zero" to OK
                "statement" -> "covers the statement" to c.ink
                else -> "short of the statement" to c.danger
            }
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 4.dp)) {
                Text("${card.name} · due ${Dates.short(card.dueOn) ?: card.dueOn}", color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${Money.whole(card.sendCents)} $text", color = tint, fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text("Left in checking until payday", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(Money.whole(p.afterCents), color = if (p.afterCents < 0) c.danger else c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        if (p.leftOnCardsCents > 0) Text("${Money.whole(p.leftOnCardsCents)} stays on the cards until the next paycheck.", color = c.muted, fontSize = 12.sp)
    }
}

@Composable
private fun PlanLine(label: String, cents: Long, note: String?) {
    val c = Budget.colors
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row {
            Text(label, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(Money.whole(cents), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        if (note != null) Text(note, color = c.muted, fontSize = 12.sp)
    }
}

/** Money in against money out for each recent pay period: a pair of bars each. */
@Composable
fun PeriodHistoryCard(v: EnvelopesResponse) {
    val c = Budget.colors
    val rows = v.history.filter { it.inCents != 0L || it.outCents != 0L }
    if (rows.size < 2) return
    val max = rows.maxOf { maxOf(it.inCents, it.outCents) }.coerceAtLeast(1)
    val done = rows.filter { !it.current }
    val overCount = done.count { it.outCents > it.inCents }
    Panel {
        Text(if (v.period.byPayday && v.period.view != "month") "Each pay period: in and out" else "Each month: in and out", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            (if (done.isNotEmpty()) "Out was more than in for $overCount of the last ${done.size}. " else "") + "Gray is money in; colored is money out.",
            color = c.muted, fontSize = 12.sp,
        )
        Row(Modifier.fillMaxWidth().height(120.dp).padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            rows.forEach { h ->
                val over = h.outCents > h.inCents
                Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.width(9.dp).fillMaxHeight((h.inCents.toFloat() / max).coerceIn(0.02f, 1f)).background(c.muted.copy(alpha = 0.35f), RoundedCornerShape(3.dp)))
                    Box(Modifier.width(9.dp).fillMaxHeight((h.outCents.toFloat() / max).coerceIn(0.02f, 1f)).background((if (over) c.danger else c.accent).copy(alpha = if (h.current) 0.5f else 1f), RoundedCornerShape(3.dp)))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            rows.forEach { h -> Text(Dates.short(h.from) ?: "", color = c.muted, fontSize = 10.sp) }
        }
    }
}
