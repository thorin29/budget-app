package com.budget.app.ui.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.budget.app.data.Dates
import com.budget.app.data.Money
import com.budget.app.data.remote.TodayResponse
import com.budget.app.ui.theme.Budget

/** Top of the Cards tab: the main card's statement so far, and its budget left. */
@Composable
fun StatementHeader(t: TodayResponse, onOpenBudget: () -> Unit) {
    val s = t.statement ?: return
    val c = Budget.colors
    val ok = okColor()
    val left = if (t.available.basis == "statement") t.available.cents else null
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("${s.name} · this statement")
        PeriodLine(s.from, s.to, s.day, s.days)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Modifier.weight(1f)) {
                Label("Charged so far")
                Figure(Money.whole(s.chargedCents), size = 20)
            }
            Tile(Modifier.weight(1f)) {
                Label("Bills still to land")
                Figure(Money.whole(s.billsToComeCents), size = 20)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Modifier.weight(1f), onClick = onOpenBudget) {
                Label("Budget left here")
                Figure(left?.let { leftText(it) } ?: "—", if ((left ?: 0L) < 0L) c.danger else ok, 20)
                t.available.perDayCents?.let { if (left != null) Note("about ${Money.whole(it)} a day") }
            }
            Tile(Modifier.weight(1f)) {
                Label("Projected at close")
                Figure(Money.whole(s.projectedCents), size = 20)
                s.dueOn?.let { Note("due ${Dates.short(it)}") }
            }
        }
    }
}
