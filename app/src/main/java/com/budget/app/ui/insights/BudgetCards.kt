package com.budget.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.data.CardLogic
import com.budget.app.data.Dates
import com.budget.app.data.Money
import com.budget.app.data.remote.BudgetPlanDto
import com.budget.app.data.remote.CardBudgetRowDto
import com.budget.app.data.remote.CardColorDto
import com.budget.app.ui.theme.Budget

/** The parts of the income bar, with colors that stay apart in light and dark. */
private val BANK_BILLS = Color(0xFF64748B)
private val CARD_BILLS = Color(0xFF93A8E8)
private val CARD_SPENDING = Color(0xFF2851D6)
private val DEBIT = Color(0xFFD97706)
private val PAYOFF = Color(0xFF7C3AED)
private val LEFT_OVER = Color(0xFF16A34A)

@Composable
private fun Panel(title: String, note: String? = null, content: @Composable () -> Unit) {
    val c = Budget.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(20.dp))
            .border(1.dp, c.line, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (note != null) Text(note, color = c.muted, fontSize = 12.sp)
        content()
    }
}

/** 1. Where the income goes: the whole bar is income; a shortfall shows past its end. */
@Composable
fun IncomeBarCard(p: BudgetPlanDto) {
    val c = Budget.colors
    val b = p.bar
    val parts = listOf(
        Triple("Bills from the bank", b.bankBillsCents, BANK_BILLS),
        Triple("Bills on cards", b.cardBillsCents, CARD_BILLS),
        Triple("Card spending", b.cardSpendingCents, CARD_SPENDING),
        Triple("Debit and other spending", b.debitSpendingCents, DEBIT),
        Triple("0% payoff", b.payoffCents, PAYOFF),
    )
    val spent = parts.sumOf { it.second }
    val short = b.leftOverCents < 0
    val scale = maxOf(p.incomeCents, spent, 1L).toFloat()
    Panel("Where the income goes", "A typical month · bills from the plan, spending from the last three months") {
        Text(
            "${Money.whole(p.incomeCents)} income a month" + (if (p.incomeSource == "bank") " (recent deposits)" else "") + ". " +
                if (short) "Spending and bills run about ${Money.whole(-b.leftOverCents)} a month past it." else "About ${Money.whole(b.leftOverCents)} a month is left over.",
            color = if (short) c.danger else c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        )
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(36.dp).semantics { contentDescription = parts.joinToString(", ") { "${it.first} ${Money.whole(it.second)}" } },
        ) {
            val w = maxWidth
            Row(Modifier.fillMaxWidth().height(28.dp).align(Alignment.BottomStart).clip(RoundedCornerShape(8.dp))) {
                parts.filter { it.second > 0 }.forEach { (_, cents, color) ->
                    Box(Modifier.width(w * (cents / scale)).fillMaxHeight().background(color))
                    Box(Modifier.width(2.dp).fillMaxHeight().background(c.surface))
                }
                if (!short && b.leftOverCents > 0) Box(Modifier.width(w * (b.leftOverCents / scale)).fillMaxHeight().background(LEFT_OVER.copy(alpha = 0.7f)))
            }
            if (short) {
                // The end of the income; everything past it is the shortfall.
                Box(Modifier.offset(x = w * (p.incomeCents / scale) - 1.dp).width(3.dp).fillMaxHeight().background(c.ink))
                Box(
                    Modifier.offset(x = w * (p.incomeCents / scale) + 2.dp).width((w * ((spent - p.incomeCents) / scale) - 2.dp).coerceAtLeast(0.dp)).height(28.dp)
                        .align(Alignment.BottomStart).background(c.danger.copy(alpha = 0.35f)),
                )
            }
        }
        parts.forEach { (label, cents, color) -> LegendRow(color, label, cents, p.incomeCents) }
        LegendRow(if (short) c.danger else LEFT_OVER, if (short) "Short each month" else "Left over", kotlin.math.abs(b.leftOverCents), null, if (short) c.danger else LEFT_OVER)
    }
}

@Composable
private fun LegendRow(color: Color, label: String, cents: Long, of: Long?, tint: Color = Budget.colors.ink) {
    val c = Budget.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(8.dp))
        Text(label, color = c.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(Money.whole(cents), color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (of != null && of > 0) Text("  ${cents * 100 / of}%", color = c.muted, fontSize = 12.sp)
    }
}

/** 2. The card budget: what can go on cards, and per card how much of the balance is bills and how much is spending. */
@Composable
fun CardBudgetCard(p: BudgetPlanDto, colors: Map<String, CardColorDto>) {
    val c = Budget.colors
    val cb = p.cardBudget
    if (cb.cards.isEmpty()) return
    Panel("Card budget", "0% cards are left out: they're only being paid down") {
        if (cb.totalCents > 0) {
            Text("The cards can take about", color = c.muted, fontSize = 13.sp)
            Text("${Money.whole(cb.totalCents)} a month", color = c.ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("${Money.whole(cb.billsCents)} for the bills on them and ${Money.whole(cb.spendingCents)} for spending.", color = c.ink, fontSize = 14.sp)
        } else {
            Text("Bills, debit spending and the 0% payoff already take the whole income, so there's no room on the cards.", color = c.danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        if (cb.averageCents > 0) {
            Text(
                "They've averaged ${Money.whole(cb.averageCents)} a month" + if (cb.averageCents > cb.totalCents) ", about ${Money.whole(cb.averageCents - cb.totalCents)} over." else ".",
                color = if (cb.averageCents > cb.totalCents) c.danger else c.muted, fontSize = 13.sp,
            )
        }
        cb.cards.forEach { CardBudgetRow(it, colors[it.id]) }
        Text("Light blue is bills (faded: bills still to come), blue is spending, the line is the card's budget.", color = c.muted, fontSize = 12.sp)
    }
}

/** One card: bills and spending since its statement closed, against its budget. Also used on the Cards tab. */
@Composable
fun CardBudgetRow(r: CardBudgetRowDto, color: CardColorDto?, showName: Boolean = true) {
    val c = Budget.colors
    val over = r.leftCents < 0
    val scale = maxOf(r.budgetCents, r.sinceBillsCents + r.sinceSpendingCents + r.billsToComeCents, 1L).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showName) Row(verticalAlignment = Alignment.CenterVertically) {
            color?.let {
                Box(
                    Modifier.size(width = 24.dp, height = 16.dp).background(
                        Brush.linearGradient(listOf(Color(CardLogic.argb(it.from)), Color(CardLogic.argb(it.to)))),
                        RoundedCornerShape(3.dp),
                    ),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(CardLogic.faceName(r.name), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Budget ${Money.whole(r.budgetCents)}", color = c.muted, fontSize = 12.sp)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(20.dp)) {
            val w = maxWidth
            Row(Modifier.fillMaxWidth().height(14.dp).align(Alignment.Center).clip(RoundedCornerShape(6.dp)).background(c.raised)) {
                Box(Modifier.width(w * (r.sinceBillsCents / scale)).fillMaxHeight().background(CARD_BILLS))
                Box(Modifier.width(w * (r.billsToComeCents / scale)).fillMaxHeight().background(CARD_BILLS.copy(alpha = 0.4f)))
                Box(Modifier.width(w * (r.sinceSpendingCents / scale)).fillMaxHeight().background(if (over) c.danger else CARD_SPENDING))
            }
            Box(Modifier.offset(x = w * (r.budgetCents / scale).coerceIn(0f, 1f) - 1.dp).width(2.dp).fillMaxHeight().background(c.ink))
        }
        Text(
            "Since ${if (r.periodIsStatement) "the statement closed" else "the month began"} (${Dates.short(r.periodFrom) ?: r.periodFrom}): " +
                "${Money.whole(r.sinceBillsCents)} bills, ${Money.whole(r.sinceSpendingCents)} spending" +
                (if (r.billsToComeCents > 0) ", about ${Money.whole(r.billsToComeCents)} in bills still to come." else "."),
            color = c.muted, fontSize = 12.sp,
        )
        Text(
            if (over) "${Money.whole(-r.leftCents)} over its ${Money.whole(r.spendingBudgetCents)} spending budget"
            else "${Money.whole(r.leftCents)} of spending room left (of ${Money.whole(r.spendingBudgetCents)})",
            color = if (over) c.danger else c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 3. Category targets: essentials keep their usual amount; flexible categories share what's left. */
@Composable
fun CategoryTargetsCard(p: BudgetPlanDto, onOpenCategory: (String) -> Unit) {
    val c = Budget.colors
    val t = p.categoryTargets
    if (t.rows.isEmpty()) return
    val cut = Math.round((1 - t.flexibleScale) * 100).toInt()
    Panel("Category targets", "Everyday spending, cards and debit together") {
        Text(
            "Everyday budget ${Money.whole(t.everydayBudgetCents)} a month. This month: ${Money.whole(t.monthSpentCents)} spent, " +
                if (t.leftCents >= 0) "${Money.whole(t.leftCents)} left (about ${Money.whole(t.perWeekLeftCents)} a week)." else "${Money.whole(-t.leftCents)} over.",
            color = if (t.leftCents < 0) c.danger else c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (cut > 0) "Essentials keep their usual amount; flexible categories come down about $cut% from usual to fit." else "Everything fits at its usual amount.",
            color = c.muted, fontSize = 12.sp,
        )
        t.rows.forEach { r ->
            val over = r.monthCents > r.targetCents
            val scale = maxOf(r.targetCents, r.monthCents, r.averageCents, 1L).toFloat()
            Column(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenCategory(r.id ?: "none") }.padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (r.essential) {
                        Text("ESSENTIAL", color = c.muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(c.raised, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(Money.whole(r.monthCents), color = if (over) c.danger else c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(" of ${Money.whole(r.targetCents)}", color = c.muted, fontSize = 13.sp)
                }
                BoxWithConstraints(Modifier.fillMaxWidth().height(12.dp)) {
                    val w = maxWidth
                    Box(Modifier.fillMaxWidth().height(6.dp).align(Alignment.Center).background(c.raised, RoundedCornerShape(3.dp)))
                    Box(Modifier.width(w * (r.monthCents / scale).coerceIn(0f, 1f)).height(6.dp).align(Alignment.CenterStart).background(if (over) c.danger else c.accent, RoundedCornerShape(3.dp)))
                    Box(Modifier.offset(x = w * (r.targetCents / scale).coerceIn(0f, 1f) - 1.dp).width(2.dp).fillMaxHeight().background(c.ink))
                }
                if (!r.essential && r.targetCents < r.averageCents) Text("Usually ${Money.whole(r.averageCents)}", color = c.muted, fontSize = 12.sp)
            }
        }
        Text("The line is each category's target for the month; tap one for its charges.", color = c.muted, fontSize = 12.sp)
    }
}
