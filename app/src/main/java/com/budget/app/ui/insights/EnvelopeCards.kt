package com.budget.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.data.Dates
import com.budget.app.data.EnvelopeLogic
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
    return if (v.period.byPayday) "until payday, $next" else "until $next"
}

/** The headline: flexible money left, colored by how things stand. Used on Today and Insights. */
@Composable
fun EnvelopeHero(v: EnvelopesResponse, modifier: Modifier = Modifier) {
    val c = Budget.colors
    val (bg, fg) = when (v.standing) {
        "ok" -> c.accent to c.onAccent
        "low" -> c.warnSoft to c.warn
        else -> c.dangerSoft to c.danger
    }
    Column(
        modifier.fillMaxWidth().background(bg, RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Flexible money left ${until(v)}", color = fg.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(Money.whole(maxOf(0L, v.flexibleLeftCents)), color = fg, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
        Text(
            when {
                v.flexibleLeftCents > 0 -> "About ${Money.whole(v.perDayCents)} a day for ${v.period.daysLeft} more day${if (v.period.daysLeft == 1) "" else "s"}"
                v.shortCents > 0 -> "${Money.whole(v.shortCents)} past what this pay period had"
                else -> "Nothing left to spend"
            },
            color = fg.copy(alpha = 0.92f), fontSize = 14.sp,
        )
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
            Text(if (v.period.byPayday) "Where each paycheck goes" else "Where each month's pay goes", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${Money.whole(v.incomeCents)} in", color = c.muted, fontSize = 13.sp)
        }
        Step(1, "Bills", v.billsCents, "An equal share of the plan's bills, set aside each $each")
        Step(2, "Essentials", v.essentialCents, "Food, fuel, medical and the like")
        Step(3, "Planned", v.plannedCents, "Giving and save-ups")
        val owed = d.balanceCents
        val done = d.doneOn
        val debtNote = if (owed == null) {
            if (d.automatic) "From the plan's payments" else "Set by you"
        } else if (done == null) {
            "${Money.whole(owed)} owed. Nothing is going to it yet."
        } else {
            "${Money.whole(owed)} owed · clears ${Dates.monthYear(done) ?: done} at this rate"
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
        "PLANNED" -> "Planned" to "Giving, and save-ups for costs that come in lumps."
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

/** Type an amount, pick an envelope, and see what the purchase would do. Nothing is saved. */
@Composable
fun AffordCard(v: EnvelopesResponse) {
    val c = Budget.colors
    val spendable = v.groups.filter { !it.saveUp }
    if (spendable.isEmpty()) return
    var amount by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf((spendable.firstOrNull { it.tier == "FLEXIBLE" } ?: spendable.first()).id) }
    val cents = EnvelopeLogic.parseDollars(amount)
    Panel {
        Text("Can I afford it?", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Nothing is saved. It shows what a purchase would do to this pay period.", color = c.muted, fontSize = 12.sp)
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            prefix = { Text("$") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            spendable.forEach { g ->
                val on = g.id == picked
                Text(
                    g.name,
                    color = if (on) c.onAccent else c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.background(if (on) c.accent else c.raised, RoundedCornerShape(50)).clickable { picked = g.id }.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        if (cents != null) {
            val before = v.groups.associateBy { it.id }
            val r = EnvelopeLogic.share(v.groups, picked, cents)
            val target = r.rows.firstOrNull { it.id == picked }
            val givers = r.rows.filter { it.gaveCents > (before[it.id]?.gaveCents ?: 0) }
            val more = r.shortCents - v.shortCents
            val (text, fg, bg) = when {
                more > 0 -> Triple("No. It's ${Money.whole(more)} more than the flexible money left.", c.danger, c.dangerSoft)
                givers.isNotEmpty() -> Triple("Yes, but other envelopes give up money for it.", c.warn, c.warnSoft)
                else -> Triple("Yes. It fits in the envelope.", if (c.dark) Color(0xFF7BD9A3) else OK, if (c.dark) Color(0xFF0F3324) else Color(0xFFDCFAE6))
            }
            Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp).fillMaxWidth().background(bg, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp))
            if (target != null) {
                Row(Modifier.padding(top = 8.dp)) {
                    Text(target.name, color = c.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(
                        "${Money.whole(before[picked]?.leftCents ?: 0)} left → " + if (target.overCents > 0) "${Money.whole(target.overCents)} over" else "${Money.whole(target.leftCents)} left",
                        color = if (target.overCents > 0) c.danger else c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            givers.forEach { g ->
                Row(Modifier.padding(top = 4.dp)) {
                    Text("${g.name} gives up", color = c.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(Money.whole(g.gaveCents - (before[g.id]?.gaveCents ?: 0)), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.padding(top = 8.dp)) {
                Text("Flexible money left after", color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(Money.whole(maxOf(0L, r.flexibleLeftCents)), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Text("Bills, essentials and the debt payment are unchanged.", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
