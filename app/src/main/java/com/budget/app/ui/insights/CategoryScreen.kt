package com.budget.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.data.Dates
import com.budget.app.data.Money
import com.budget.app.data.remote.CategoryDetailResponse
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One category up close: twelve months, this month's merchants, and the charges. */
@Composable
fun CategoryScreen(detail: CategoryDetailResponse?, onBack: () -> Unit) {
    val c = Budget.colors
    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.statusBarsPadding().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) { PhIcon(Ph.CARET_LEFT, "Back", c.ink) }
                Text(detail?.name ?: "Loading…", color = c.ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (detail == null) return@LazyColumn
        val month = if (detail.year > 0) YearMonth.of(detail.year, detail.month).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)) else ""
        val shown = detail.months.dropWhile { it.cents == 0L }
        val thisMonth = detail.months.lastOrNull()?.cents ?: 0L
        val prior = detail.months.dropLast(1).takeLast(3)
        val average = if (prior.isEmpty()) 0L else prior.sumOf { it.cents } / prior.size
        item {
            Text(
                "${Money.format(thisMonth)} in $month · usually ${Money.whole(average)} a month · ${Money.whole(detail.months.sumOf { it.cents })} over 12 months",
                color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (shown.isNotEmpty()) {
            item {
                Panel("Each month") {
                    BarChart(values = shown.map { it.cents }, labels = shown.map { YearMonth.of(it.year, it.month).format(DateTimeFormatter.ofPattern("MMM", Locale.US)) })
                }
            }
        }
        item {
            Panel("Merchants this month") {
                if (detail.merchants.isEmpty()) Text("Nothing this month.", color = c.muted, fontSize = 13.sp)
                detail.merchants.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.merchant + if (m.count > 1) "  ×${m.count}" else "", color = c.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Money.format(m.cents), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("usually ${Money.whole(m.averageCents)}", color = c.muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        item {
            Panel("Charges this month") {
                if (detail.charges.isEmpty()) Text("Nothing this month.", color = c.muted, fontSize = 13.sp)
                detail.charges.forEach { ch ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ch.merchant, color = c.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${Dates.short(ch.postedOn) ?: ch.postedOn} · ${ch.account}", color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            if (ch.cents < 0) "refund ${Money.format(-ch.cents)}" else Money.format(ch.cents),
                            color = if (ch.cents < 0) c.accent else c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
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
        content()
    }
}
