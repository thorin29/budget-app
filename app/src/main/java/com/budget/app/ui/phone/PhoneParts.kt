package com.budget.app.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.data.Dates
import com.budget.app.data.Money
import com.budget.app.ui.theme.Budget

/** Green for money left and things done, readable on both themes. */
@Composable
fun okColor(): Color = if (Budget.colors.dark) Color(0xFF5BD08C) else Color(0xFF067647)

/** The section colors, as on the web: bills, debt, essentials, extras, unplanned. */
@Composable
fun tierColor(tier: String): Color {
    val dark = Budget.colors.dark
    return when (tier) {
        "BILLS", "bills" -> if (dark) Color(0xFF3987E5) else Color(0xFF2A78D6)
        "debt" -> if (dark) Color(0xFFD95926) else Color(0xFFEB6834)
        "ESSENTIAL", "ess" -> if (dark) Color(0xFF199E70) else Color(0xFF1BAF7A)
        "FLEXIBLE", "ext" -> if (dark) Color(0xFFC98500) else Color(0xFFEDA100)
        "UNPLANNED", "unp" -> if (dark) Color(0xFFD55181) else Color(0xFFE87BA4)
        else -> if (dark) Color(0xFF4A505A) else Color(0xFFC9CCD1)
    }
}

/** "$1,240", or "$120 over" for a negative amount left. */
fun leftText(cents: Long): String = if (cents < 0) "${Money.whole(-cents)} over" else Money.whole(cents)

/** A card: surface, hairline border, rounded. Tappable when [onClick] is set. */
@Composable
fun Tile(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Budget.colors
    Column(
        modifier
            .background(c.surface, RoundedCornerShape(18.dp))
            .border(1.dp, c.line, RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
}

@Composable
fun Label(text: String) {
    Text(text, color = Budget.colors.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun Figure(text: String, color: Color = Budget.colors.ink, size: Int = 22) {
    Text(text, color = color, fontSize = size.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
}

@Composable
fun Note(text: String, color: Color = Budget.colors.muted) {
    Text(text, color = color, fontSize = 13.sp)
}

/**
 * Spent (solid), projected (pale) and the budget (a line), on one scale: the bigger of the
 * budget and the projection.
 */
@Composable
fun SpendBar(spent: Long, projected: Long, budget: Long?, color: Color, modifier: Modifier = Modifier) {
    val c = Budget.colors
    val scale = maxOf(1L, budget ?: 0L, projected, spent).toFloat()
    BoxWithConstraints(modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.CenterStart) {
        val w = maxWidth
        Box(Modifier.fillMaxWidth().height(8.dp).background(c.raised, RoundedCornerShape(50)))
        Box(Modifier.width(w * (projected.coerceAtLeast(0) / scale)).height(8.dp).background(color.copy(alpha = 0.3f), RoundedCornerShape(50)))
        Box(Modifier.width(w * (spent.coerceAtLeast(0) / scale)).height(8.dp).background(color, RoundedCornerShape(50)))
        if (budget != null && budget > 0) {
            Box(Modifier.offset(x = (w * (budget / scale)) - 1.dp).width(2.dp).fillMaxHeight().background(c.ink))
        }
    }
}

/** "Nov 16 – Dec 15 · day 6 of 30" over a thin bar. */
@Composable
fun PeriodLine(from: String, to: String, day: Int, days: Int) {
    val c = Budget.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Note("${Dates.short(from) ?: from} – ${Dates.short(to) ?: to} · day $day of $days")
            Note("${maxOf(0, days - day)} days left")
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(6.dp)) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(c.line, RoundedCornerShape(50)))
            Box(Modifier.width(maxWidth * (day.toFloat() / maxOf(1, days)).coerceIn(0f, 1f)).height(6.dp).background(c.ink, RoundedCornerShape(50)))
        }
    }
}

/** A row of choices; the chosen one is filled. */
@Composable
fun Chips(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Budget.colors
    androidx.compose.foundation.lazy.LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options.size) { i ->
            val (key, label) = options[i]
            val on = key == selected
            Text(
                label,
                color = if (on) c.surface else c.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(if (on) c.ink else c.surface, RoundedCornerShape(50))
                    .border(1.dp, if (on) c.ink else c.line, RoundedCornerShape(50))
                    .clickable(role = Role.Tab) { onPick(key) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** Two-way switch drawn as a segmented control. */
@Composable
fun Segments(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    val c = Budget.colors
    Row(Modifier.fillMaxWidth().background(c.raised, RoundedCornerShape(14.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (key, label) ->
            val on = key == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(if (on) c.surface else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable(role = Role.Tab) { onPick(key) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) c.ink else c.muted, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold)
            }
        }
    }
}

/** A screen's title row. */
@Composable
fun ScreenTitle(text: String, right: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Budget.colors.ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, modifier = Modifier.weight(1f))
        right?.invoke()
    }
}
