package com.budget.app.ui.insights

import androidx.compose.foundation.background
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

/** The headline: everything left in the envelopes (or how far over), colored by how things stand. Used on Today and Insights. */
@Composable
fun EnvelopeHero(v: EnvelopesResponse, modifier: Modifier = Modifier) {
    val c = Budget.colors
    val (bg, fg) = when (v.standing) {
        "ok" -> c.accent to c.onAccent
        "low" -> c.warnSoft to c.warn
        else -> c.dangerSoft to c.danger
    }
    // Servers before 0.41 only send the flexible figure.
    val left = v.allLeftCents ?: v.flexibleLeftCents
    Column(
        modifier.fillMaxWidth().background(bg, RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("${if (left < 0) "Over" else "Left to spend"} ${until(v)}", color = fg.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(Money.whole(kotlin.math.abs(left)), color = fg, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
        Text(
            if (left > 0) {
                "About ${Money.whole(v.perDayCents)} a day for ${v.period.daysLeft} more day${if (v.period.daysLeft == 1) "" else "s"} · ${Money.whole(maxOf(0L, v.flexibleLeftCents))} of it flexible"
            } else {
                "Across every envelope"
            },
            color = fg.copy(alpha = 0.92f), fontSize = 14.sp,
        )
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
        Step(5, "Flexible", v.flexibleCents, "What's left, shared by the flexible envelopes")
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
                if (e.gaveCents > 0) "Gave ${Money.whole(e.gaveCents)} to cover another envelope" else null,
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
        "ESSENTIAL" -> "Essentials" to "Protected. What isn't used stays in the envelope."
        "PLANNED" -> "Planned" to "Giving, sports and other spending you plan for."
        else -> "Flexible" to "One shared pot. An envelope that runs over takes from the others."
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
        Text("What's left in every envelope, added up.", color = c.muted, fontSize = 12.sp)
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
