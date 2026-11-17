package com.budget.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.budget.app.data.MonthPlanLogic
import com.budget.app.data.Money
import com.budget.app.data.PlanAlerts
import com.budget.app.data.remote.CashTimingResponse
import com.budget.app.data.remote.MonthPlanResponse
import com.budget.app.data.remote.PlanGroupDto
import com.budget.app.data.remote.PlanTxnDto
import com.budget.app.ui.common.Panel
import com.budget.app.ui.common.Pill
import com.budget.app.ui.theme.Budget
import kotlin.math.abs

/**
 * The month's spending plan (server 0.43+): what's safe to spend on extras,
 * how it adds up (each line opens to its items), "Can we afford it?", and
 * every budget down to its transactions. Budgets and guardrails are changed on
 * the web.
 */

private val OK = Color(0xFF1F8A4C)

private fun monthName(ym: String): String = Dates.monthYear("$ym-01") ?: ym

private fun day(iso: String): String = Dates.short(iso) ?: iso

/** The headline, on Today and Insights. */
@Composable
fun PlanHero(v: MonthPlanResponse, modifier: Modifier = Modifier) {
    val c = Budget.colors
    val over = v.safeCents < 0
    val low = PlanAlerts.level(v) == "low"
    Column(
        modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(24.dp)).border(1.dp, c.line, RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (v.closed) {
            Text("${monthName(v.month)}, after bills", color = c.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (v.poolLeftCents < 0) "Over by ${Money.whole(-v.poolLeftCents)}" else "${Money.whole(v.poolLeftCents)} not spent",
                color = if (v.poolLeftCents < 0) c.danger else c.ink, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
            )
        } else {
            Text(if (over) "This month is" else "Safe to spend on extras", color = c.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (over) "Over by ${Money.whole(-v.safeCents)}" else Money.whole(v.safeCents),
                color = if (over) c.danger else if (low) c.warn else c.ink, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold,
            )
            if (v.groups.any { it.tier == "FLEXIBLE" }) {
                Text("Extras' guardrails have ${Money.whole(v.groups.sumOf { it.roomCents ?: 0L })} of room left in all.", color = c.ink, fontSize = 14.sp)
            }
            Text(
                "For the rest of the month (${v.daysLeft} day${if (v.daysLeft == 1) "" else "s"}), after every bill and the groceries, fuel and other essentials still to come.",
                color = c.muted, fontSize = 13.sp,
            )
        }
        v.message?.let { m ->
            Text(
                m,
                color = if (over || v.shortCents > 0) c.danger else c.warn, fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp).fillMaxWidth().background(if (over || v.shortCents > 0) c.dangerSoft else c.warnSoft, RoundedCornerShape(12.dp)).padding(10.dp),
            )
        }
    }
}

@Composable
private fun TxnRows(rows: List<PlanTxnDto>, onMakeBill: ((String, String) -> Unit)? = null) {
    val c = Budget.colors
    if (rows.isEmpty()) Text("Nothing yet.", color = c.muted, fontSize = 12.sp)
    rows.forEach { t ->
        var asked by remember(t.id) { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${day(t.date)}  ${t.name}${if (t.pending) " · pending" else ""}${if (t.split) " · part of an order" else ""}",
                color = c.ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (onMakeBill != null && t.cents > 0 && !t.split && !t.pending) {
                Text(
                    if (asked) "Added" else "Make it a bill",
                    color = if (asked) OK else c.accentText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp).clickable(enabled = !asked) {
                        asked = true
                        onMakeBill(t.id, t.name)
                    },
                )
            }
            Text(Money.format(t.cents), color = c.ink, fontSize = 12.sp)
        }
    }
}

/** A line of the sum that opens to what's behind it. */
@Composable
private fun StepRow(sign: String, label: String, cents: Long, note: String? = null, strong: Boolean = false, content: (@Composable () -> Unit)? = null) {
    val c = Budget.colors
    var open by rememberSaveable(label) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().then(if (content != null) Modifier.clickable { open = !open } else Modifier),
            verticalAlignment = Alignment.Top,
        ) {
            Text(sign, color = c.muted, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label + if (content != null) (if (open) "  ▴" else "  ▾") else "",
                    color = c.ink, fontSize = if (strong) 15.sp else 14.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
                )
                note?.let { Text(it, color = c.muted, fontSize = 12.sp) }
            }
            Text(Money.whole(cents), color = c.ink, fontSize = if (strong) 16.sp else 14.sp, fontWeight = if (strong) FontWeight.ExtraBold else FontWeight.SemiBold)
        }
        if (open && content != null) {
            Column(Modifier.padding(start = 18.dp, top = 6.dp).fillMaxWidth().background(c.raised, RoundedCornerShape(12.dp)).padding(10.dp)) { content() }
        }
    }
}

@Composable
fun PlanAddsUpCard(v: MonthPlanResponse, onMakeBill: ((String, String) -> Unit)? = null) {
    val c = Budget.colors
    Panel {
        Text("How it adds up", color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("Tap a line to see what's in it.", color = c.muted, fontSize = 12.sp)
        StepRow("", "Money in", v.income.totalCents, "${Money.whole(v.income.receivedCents)} received so far; the rest is expected") {
            v.income.items.forEach { i ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text("${day(i.dueOn)}  ${i.name} · ${if (i.received) "received" else "expected"}", color = c.ink, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(Money.format(i.cents), color = c.ink, fontSize = 12.sp)
                }
            }
        }
        val overdue = v.bills.items.any { it.status == "overdue" }
        StepRow("−", "Bills", v.bills.totalCents, "Every bill this month, whole. ${Money.whole(v.bills.paidCents)} paid${if (overdue) " · some are overdue" else ""}") {
            v.bills.items.forEach { b ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${day(b.dueOn)}  ${b.name}${b.from?.let { " · from ${monthName(it)}" } ?: ""}",
                        color = c.ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    when (b.status) {
                        "paid" -> Pill("Paid", OK, OK.copy(alpha = 0.14f))
                        "overdue" -> Pill("Overdue", c.danger, c.dangerSoft)
                        "skipped" -> Pill("Not this month", c.muted, c.surface)
                        else -> Pill("Due", c.muted, c.surface)
                    }
                    Text(Money.format(b.cents), color = c.ink, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
            if (v.bills.other.isNotEmpty()) {
                Text("Charges in bill categories that aren't on the bill list (${Money.format(v.bills.otherCents)})", color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                TxnRows(v.bills.other, onMakeBill)
            }
        }
        val d = v.debt
        if (d.cents > 0 || (d.balanceCents ?: 0) > 0) {
            val note = when (d.source) {
                "paid" -> "Paid this month"
                "set" -> "The amount you set"
                "plan" -> "The bill for that card"
                "minimum" -> "About the minimum (1% of the balance)"
                else -> null
            }
            StepRow("−", "Debt payment${d.name?.let { ": $it" } ?: ""}", d.cents, note) {
                d.dueOn?.let { Text("Due ${day(it)}", color = c.ink, fontSize = 12.sp) }
                d.balanceCents?.let { Text("${Money.format(it)} owed${d.endsOn?.let { e -> "; the 0% offer ends ${Dates.monthYear(e) ?: e}" } ?: ""}", color = c.ink, fontSize = 12.sp) }
                d.doneIn?.let { Text("At ${Money.whole(d.cents)} a month it's paid off in ${monthName(it)}.", color = c.ink, fontSize = 12.sp) }
                d.targetCents?.let { t ->
                    Text(
                        "Paying it off before the offer ends takes ${Money.whole(t)} a month." + if (d.cents >= t) " This month is on track." else "",
                        color = if (d.cents >= t) OK else c.warn, fontSize = 12.sp,
                    )
                }
            }
        }
        StepRow("=", "The pool for everything else", v.poolCents, "Groceries, fuel, clothes, eating out, repairs and the rest", strong = true)
        StepRow("−", if (v.closed) "Spent" else "Spent so far", v.spentCents, "On cards and from checking, by transaction date") {
            listOf("ESSENTIAL" to "Essentials", "FLEXIBLE" to "Extras", "UNPLANNED" to "Unplanned", "SORT" to "Needs sorting").forEach { (t, label) ->
                val cents = v.groups.filter { it.tier == t }.sumOf { it.spentCents }
                if (t != "SORT" || cents != 0L) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(label, color = c.ink, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(Money.format(cents), color = c.ink, fontSize = 12.sp)
                }
            }
            Text("Each budget below opens to its transactions.", color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
        StepRow("=", "Left in the pool", v.poolLeftCents, strong = true)
        if (!v.closed) {
            StepRow("−", "Essentials still to come", v.stillToComeCents, "What groceries, fuel and the like still need this month") {
                v.groups.filter { it.tier == "ESSENTIAL" }.forEach { g ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("${g.name} (${Money.whole(g.spentCents)} of ${Money.whole(g.limitCents ?: 0)} spent)", color = c.ink, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(Money.format(g.leftCents ?: 0), color = c.ink, fontSize = 12.sp)
                    }
                }
            }
            StepRow("=", if (v.safeCents < 0) "Over by" else "Safe to spend on extras", abs(v.safeCents), strong = true)
        }
    }
}

/** "Can we afford it?": one or more purchases, worked out on the phone. */
@Composable
fun WhatIfCard(v: MonthPlanResponse) {
    val c = Budget.colors
    val choices = v.groups.filter { it.tier != "SORT" }
    if (choices.isEmpty()) return
    val first = (choices.firstOrNull { it.tier == "FLEXIBLE" } ?: choices.first()).id
    val amounts = remember(v.month) { mutableStateListOf("") }
    val budgets = remember(v.month) { mutableStateListOf(first) }
    Panel {
        Text("Can we afford it?", color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("Add one or more purchases to see what they'd do before saying yes.", color = c.muted, fontSize = 12.sp)
        amounts.indices.forEach { i ->
            var menu by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amounts[i],
                    onValueChange = { amounts[i] = it },
                    placeholder = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(110.dp),
                )
                Box(Modifier.weight(1f)) {
                    Text(
                        (choices.firstOrNull { it.id == budgets[i] }?.name ?: "Budget") + "  ▾",
                        color = c.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().border(1.dp, c.line, RoundedCornerShape(10.dp)).clickable { menu = true }.padding(horizontal = 12.dp, vertical = 14.dp),
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        choices.forEach { g ->
                            DropdownMenuItem(text = { Text(g.name) }, onClick = {
                                budgets[i] = g.id
                                menu = false
                            })
                        }
                    }
                }
                if (amounts.size > 1) Text("×", color = c.muted, fontSize = 20.sp, modifier = Modifier.clickable {
                    amounts.removeAt(i)
                    budgets.removeAt(i)
                }.padding(6.dp))
            }
        }
        Text(
            "Add another", color = c.accentText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp).clickable {
                amounts.add("")
                budgets.add(first)
            }.padding(vertical = 4.dp),
        )
        val purchases = amounts.indices.mapNotNull { i -> Money.parse(amounts[i])?.takeIf { it > 0 }?.let { budgets[i] to it } }
        if (purchases.isNotEmpty()) {
            val after = MonthPlanLogic.whatIf(v, purchases)
            val total = purchases.sumOf { it.second }
            val tone = if (after.safeCents < 0) c.danger else if (after.safeCents < PlanAlerts.LOW_CENTS) c.warn else OK
            val bg = if (after.safeCents < 0) c.dangerSoft else if (after.safeCents < PlanAlerts.LOW_CENTS) c.warnSoft else OK.copy(alpha = 0.12f)
            Column(Modifier.padding(top = 10.dp).fillMaxWidth().background(bg, RoundedCornerShape(12.dp)).padding(10.dp)) {
                Text(
                    "${Money.whole(total)} would take safe to spend from ${Money.whole(v.safeCents)} to " +
                        if (after.safeCents < 0) "over by ${Money.whole(-after.safeCents)}." else "${Money.whole(after.safeCents)}.",
                    color = tone, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
                val before = v.groups.associate { it.id to it.roomCents }
                after.groups.filter { it.roomCents != null && it.roomCents != before[it.id] }.forEach { g ->
                    Text("${v.groups.first { it.id == g.id }.name}: room ${Money.whole(before[g.id] ?: 0)} → ${Money.whole(g.roomCents ?: 0)}", color = tone, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PlanGroupRow(g: PlanGroupDto, share: Float, safeCents: Long, onOpenCategory: (String) -> Unit, onMakeBill: ((String, String) -> Unit)?) {
    val c = Budget.colors
    var open by rememberSaveable(g.id) { mutableStateOf(false) }
    val limit = g.limitCents
    val used = if (limit != null && limit > 0) g.spentCents.toFloat() / limit else if (g.spentCents > 0) 1f else 0f
    val tone = if (g.overCents > 0) c.danger else if (limit != null && used > minOf(1f, share + 0.15f)) c.warn else OK
    val (right, note) = when (g.tier) {
        "FLEXIBLE" -> "${Money.whole(g.roomCents ?: 0)} room" to when {
            g.overCents > 0 -> "${Money.whole(g.spentCents)} spent of a ${Money.whole(limit ?: 0)} guardrail: ${Money.whole(g.overCents)} over"
            (g.roomCents ?: 0) < (g.leftCents ?: 0) -> "${Money.whole(g.spentCents)} of ${Money.whole(limit ?: 0)} spent. Only ${Money.whole(maxOf(0, safeCents))} is safe to spend this month, so that's all the room it has."
            else -> "${Money.whole(g.spentCents)} of ${Money.whole(limit ?: 0)} spent"
        }
        "ESSENTIAL" -> (if (g.overCents > 0) "${Money.whole(g.overCents)} over" else "${Money.whole(g.leftCents ?: 0)} still to come") to
            "${Money.whole(g.spentCents)} of ${Money.whole(limit ?: 0)} spent"
        "SORT" -> "${Money.whole(g.spentCents)} spent" to "Counts against the month until it's sorted"
        else -> "${Money.whole(g.spentCents)} spent" to if (g.usualCents > 0) "Not set aside. About ${Money.whole(g.usualCents * 12)} over the last year, in lumps." else "Not set aside"
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text(g.name + if (open) "  ▴" else "  ▾", color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(right, color = if (g.overCents > 0) c.danger else c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        if (limit != null) {
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(8.dp).background(c.raised, RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth(used.coerceIn(0f, 1f)).height(8.dp).background(tone, RoundedCornerShape(4.dp)))
            }
        }
        Text(note, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        if (open) {
            Column(Modifier.padding(top = 6.dp).fillMaxWidth().background(c.raised, RoundedCornerShape(12.dp)).padding(10.dp)) {
                if (g.categories.isEmpty()) Text("Nothing spent this month.", color = c.muted, fontSize = 12.sp)
                g.categories.forEach { cat ->
                    var catOpen by rememberSaveable("${g.id}:${cat.id}") { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().clickable { catOpen = !catOpen }.padding(vertical = 4.dp)) {
                        Text(cat.name + if (catOpen) "  ▴" else "  ▾", color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(Money.format(cat.spentCents), color = c.ink, fontSize = 13.sp)
                    }
                    if (catOpen) {
                        Column(Modifier.padding(start = 8.dp)) {
                            TxnRows(cat.transactions, onMakeBill)
                            cat.id?.let { id ->
                                Text("See this category's months", color = c.accentText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onOpenCategory(id) }.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private val TIERS = mapOf(
    "ESSENTIAL" to ("Essentials" to "Needed every month. What they haven't spent yet is held back from safe to spend."),
    "FLEXIBLE" to ("Extras" to "Room is never more than what's safe to spend, so an overspend anywhere shrinks all of them."),
    "UNPLANNED" to ("Unplanned" to "Irregular costs. No guardrail; they come out of the month they happen."),
    "SORT" to ("Needs sorting" to "Spending without a category or a budget, sorted on the web. It still counts."),
)

@Composable
fun PlanTierCard(v: MonthPlanResponse, tier: String, onOpenCategory: (String) -> Unit, onMakeBill: ((String, String) -> Unit)? = null) {
    val c = Budget.colors
    val rows = v.groups.filter { it.tier == tier && (tier != "SORT" || it.spentCents != 0L) }
    if (rows.isEmpty()) return
    val (title, note) = TIERS[tier] ?: (tier to "")
    val share = if (v.daysInMonth > 0) (v.daysInMonth - v.daysLeft).toFloat() / v.daysInMonth else 1f
    Panel {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${Money.whole(rows.sumOf { it.spentCents })} spent", color = c.muted, fontSize = 13.sp)
        }
        Text(note, color = c.muted, fontSize = 12.sp)
        rows.forEach { PlanGroupRow(it, share, v.safeCents, onOpenCategory, onMakeBill) }
    }
}

/** Will checking cover it? Checking through the rest of this month and next, and each paycheck's payments. */
@Composable
fun CashTimingCard(cash: CashTimingResponse) {
    val c = Budget.colors
    Panel {
        Text("Will checking cover it?", color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (cash.missing) {
            Text("This needs checking's balance. Choose the bills account in Settings on the web and link it to the bank.", color = c.muted, fontSize = 13.sp)
            return@Panel
        }
        Text("Checking ${Money.whole(cash.startCents)} now · through ${day(cash.until)}", color = c.muted, fontSize = 12.sp)
        val short = cash.shortOn != null
        val shortStep = cash.steps.firstOrNull { it.date == cash.shortOn && it.balanceCents < 0 }
        Text(
            if (short) "Checking would run short on ${day(cash.shortOn!!)}${shortStep?.let { ", when ${it.label} takes ${Money.whole(-it.cents)}" } ?: ""}. It gets as low as ${Money.whole(cash.lowCents)} on ${day(cash.lowOn)}."
            else "Checking covers every bill and full card payment through ${day(cash.until)}. The lowest it gets is ${Money.whole(cash.lowCents)}, on ${day(cash.lowOn)}.",
            color = if (short) c.danger else OK, fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().background(if (short) c.dangerSoft else OK.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(10.dp),
        )
        cash.spans.forEachIndexed { i, sp ->
            var open by rememberSaveable(sp.from) { mutableStateOf(i < 2) }
            Column(Modifier.padding(top = 10.dp).fillMaxWidth().border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(10.dp)) {
                Row(Modifier.fillMaxWidth().clickable { open = !open }) {
                    Text(
                        (if (i == 0 && sp.payCents == 0L) "Now until ${day(sp.to)}" else "${day(sp.from)} paycheck") + if (open) "  ▴" else "  ▾",
                        color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                    )
                    Text("${if (sp.payCents > 0) "${Money.whole(sp.payCents)} in · " else ""}${Money.whole(sp.outCents)} out", color = c.muted, fontSize = 12.sp)
                }
                if (open) {
                    if (sp.items.isEmpty()) Text("Nothing due.", color = c.muted, fontSize = 12.sp)
                    sp.items.forEach { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${day(s.date)}  ${s.label}${if (s.kind == "card") " · card, in full${if (s.estimate) " (so far)" else ""}" else ""}${if (s.kind == "debt") " · 0% payment" else ""}",
                                color = c.ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            if (s.overdue) Pill("Overdue", c.danger, c.dangerSoft)
                            Text(Money.format(-s.cents), color = c.ink, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
                val next = cash.spans.getOrNull(i + 1)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(if (next != null) "Before the ${day(next.from)} paycheck" else "On ${day(sp.to)}", color = if (sp.endCents < 0) c.danger else c.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(Money.whole(sp.endCents), color = if (sp.endCents < 0) c.danger else c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("Cards are paid in full on their due dates. A card's next payment is what's been charged since its statement, so it grows as you spend.", color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
