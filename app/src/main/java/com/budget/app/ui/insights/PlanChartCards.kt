package com.budget.app.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.data.Dates
import com.budget.app.data.Money
import com.budget.app.data.remote.DatedCentsDto
import com.budget.app.data.remote.DebtChartDto
import com.budget.app.data.remote.MonthHistoryDto
import com.budget.app.data.remote.MonthPlanResponse
import com.budget.app.ui.common.Panel
import com.budget.app.ui.theme.Budget
import java.time.LocalDate

/**
 * The plan's charts (server 0.45+), as on the web: where this month's money
 * goes, whether spending is on pace, the 0% balance against the end of the
 * offer, and money in against money out by month.
 */

/** A validated categorical order (fixed, never cycled); light and dark steps. */
private fun series(dark: Boolean) = if (dark) {
    listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500), Color(0xFFD55181))
} else {
    listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100), Color(0xFFE87BA4))
}

@Composable
private fun isDark(): Boolean = Budget.colors.background.let { (it.red + it.green + it.blue) / 3f < 0.5f }

private fun epochDay(iso: String): Long = runCatching { LocalDate.parse(iso.take(10)).toEpochDay() }.getOrDefault(0L)

@Composable
private fun Swatch(color: Color, label: String, value: String? = null, valueColor: Color? = null) {
    val c = Budget.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, color = c.ink, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp).weight(1f))
        value?.let { Text(it, color = valueColor ?: c.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun PoolBarCard(v: MonthPlanResponse) {
    val c = Budget.colors
    val s = series(isDark())
    val neutral = if (isDark()) Color(0xFF4A505A) else Color(0xFFC9CCD1)
    fun by(t: String) = v.groups.filter { it.tier == t }.sumOf { it.spentCents }
    val parts = listOf(
        Triple("Bills", v.bills.totalCents, s[0]),
        Triple("Debt payment", v.debt.cents, s[1]),
        Triple("Essentials", by("ESSENTIAL"), s[2]),
        Triple("Extras", by("FLEXIBLE"), s[3]),
        Triple("Unplanned", by("UNPLANNED") + by("SORT"), s[4]),
    ).filter { it.second > 0 }
    val income = maxOf(0L, v.income.totalCents)
    val out = parts.sumOf { it.second }
    val left = income - out
    val total = maxOf(income, out, 1L).toFloat()
    val danger = c.danger
    Panel {
        Text("Where this month's money goes", color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("${Money.whole(v.income.totalCents)} in${if (v.closed) "" else ", spending so far"}", color = c.muted, fontSize = 12.sp)
        Canvas(Modifier.padding(top = 10.dp).fillMaxWidth().height(28.dp)) {
            var x = 0f
            val gap = 2.dp.toPx()
            parts.forEach { (_, cents, color) ->
                val w = size.width * cents / total
                drawRoundRect(color, Offset(x + gap / 2, 0f), Size(maxOf(0f, w - gap), size.height), CornerRadius(4.dp.toPx()))
                x += w
            }
            if (left > 0) drawRoundRect(neutral, Offset(x + gap / 2, 0f), Size(maxOf(0f, size.width * left / total - gap), size.height), CornerRadius(4.dp.toPx()))
            if (out > income) {
                val m = size.width * income / total
                drawLine(danger, Offset(m, 0f), Offset(m, size.height), strokeWidth = 2.dp.toPx())
            }
        }
        Column(Modifier.padding(top = 8.dp)) {
            parts.forEach { (label, cents, color) -> Swatch(color, label, Money.whole(cents)) }
            Swatch(if (left >= 0) neutral else danger, if (left >= 0) "Not spent yet" else "Over", Money.whole(kotlin.math.abs(left)), if (left < 0) danger else null)
        }
    }
}

@Composable
fun PaceChartCard(v: MonthPlanResponse) {
    val c = Budget.colors
    val blue = series(isDark())[0]
    var spent = 0L
    val points = v.daily.filter { it.date <= v.today }.map { spent += it.spentCents; v.poolCents - spent }
    val n = v.daily.size
    if (points.isEmpty() || n < 2) return
    val idx = points.lastIndex
    val pace = { i: Int -> v.poolCents * (1f - (i + 1f) / n) }
    val ahead = points[idx] - pace(idx).toLong()
    val lo = minOf(0L, points.minOrNull() ?: 0L)
    val hi = maxOf(v.poolCents, 1L)
    val muted = c.muted
    val grid = c.line
    Panel {
        Text("Are we on pace?", color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("What's left in the pool each day, against spending it evenly through the month.", color = c.muted, fontSize = 12.sp)
        Text(
            if (ahead < 0) "${Money.whole(-ahead)} less left than an even pace would leave" else "${Money.whole(ahead)} more left than an even pace would leave: on track",
            color = if (ahead < 0) c.danger else Color(0xFF1F8A4C), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp),
        )
        Canvas(Modifier.padding(top = 10.dp).fillMaxWidth().height(150.dp)) {
            val x = { i: Int -> size.width * i / (n - 1).toFloat() }
            val y = { cents: Float -> size.height * (hi - cents) / (hi - lo).toFloat() }
            drawLine(grid, Offset(0f, y(0f)), Offset(size.width, y(0f)), strokeWidth = 1.dp.toPx())
            drawLine(muted, Offset(x(0), y(v.poolCents.toFloat())), Offset(x(n - 1), y(0f)), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            for (i in 1..idx) drawLine(blue, Offset(x(i - 1), y(points[i - 1].toFloat())), Offset(x(i), y(points[i].toFloat())), strokeWidth = 2.dp.toPx())
            drawCircle(blue, 5.dp.toPx(), Offset(x(idx), y(points[idx].toFloat())))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(Dates.short(v.daily.first().date) ?: "", color = c.muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("Pool ${Money.whole(v.poolCents)} → today ${Money.whole(points[idx])}", color = c.muted, fontSize = 11.sp)
            Text(Dates.short(v.daily.last().date) ?: "", color = c.muted, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

@Composable
fun DebtChartCard(d: DebtChartDto) {
    val c = Budget.colors
    val s = series(isDark())
    val danger = c.danger
    val grid = c.line
    val start = d.points.firstOrNull()?.date ?: d.projected.firstOrNull()?.date ?: return
    val end = LocalDate.parse(d.endsOn).withDayOfMonth(1).plusMonths(2).toString()
    val t0 = epochDay(start)
    val t1 = epochDay(end)
    val hi = maxOf(1L, d.points.maxOfOrNull { it.cents } ?: 0L, d.balanceCents)
    val clears = d.projected.firstOrNull { it.cents == 0L }?.date
    val late = clears == null || clears > d.endsOn
    val owedAtEnd = d.projected.lastOrNull { it.date <= d.endsOn }?.cents ?: d.balanceCents
    Panel {
        Text("Is the 0% balance on track?", color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("${d.name?.let { "$it: " } ?: ""}${Money.whole(d.balanceCents)} owed; the offer ends ${Dates.monthYear(d.endsOn) ?: d.endsOn}.", color = c.muted, fontSize = 12.sp)
        Text(
            if (late) "At ${Money.whole(d.monthlyCents)} a month about ${Money.whole(owedAtEnd)} is still owed when it ends${d.targetCents?.let { "; ${Money.whole(it)} a month clears it" } ?: ""}."
            else "At ${Money.whole(d.monthlyCents)} a month it clears in ${Dates.monthYear(clears) ?: clears}, before the offer ends.",
            color = if (late) c.warn else Color(0xFF1F8A4C), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp),
        )
        Canvas(Modifier.padding(top = 10.dp).fillMaxWidth().height(150.dp)) {
            val x = { iso: String -> size.width * (minOf(epochDay(iso), t1) - t0) / maxOf(1L, t1 - t0).toFloat() }
            val y = { cents: Long -> size.height * (hi - cents) / hi.toFloat() }
            fun line(pts: List<DatedCentsDto>, color: Color, dash: FloatArray?, width: Float) {
                val shown = pts.filter { epochDay(it.date) <= t1 }
                for (i in 1 until shown.size) {
                    drawLine(color, Offset(x(shown[i - 1].date), y(shown[i - 1].cents)), Offset(x(shown[i].date), y(shown[i].cents)), strokeWidth = width, pathEffect = dash?.let { PathEffect.dashPathEffect(it) })
                }
            }
            drawLine(grid, Offset(0f, y(0)), Offset(size.width, y(0)), strokeWidth = 1.dp.toPx())
            drawLine(danger, Offset(x(d.endsOn), 0f), Offset(x(d.endsOn), size.height), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            line(d.target, s[2], floatArrayOf(4f, 8f), 2.dp.toPx())
            line(d.projected, s[0], floatArrayOf(14f, 8f), 2.dp.toPx())
            line(d.points, s[0], null, 2.5.dp.toPx())
        }
        Column(Modifier.padding(top = 6.dp)) {
            Swatch(s[0], "Balance so far, then at ${Money.whole(d.monthlyCents)} a month (dashed)")
            d.targetCents?.let { Swatch(s[2], "At ${Money.whole(it)} a month (dotted)") }
            Swatch(danger, "The offer ends")
        }
    }
}

@Composable
fun HistoryChartCard(rows: List<MonthHistoryDto>) {
    val c = Budget.colors
    val s = series(isDark())
    val outOf = { r: MonthHistoryDto -> r.billsCents + r.debtCents + r.spentCents }
    val hi = maxOf(1L, rows.maxOf { maxOf(it.inCents, outOf(it)) })
    val complete = rows.filter { !it.current }
    val gap = if (complete.isEmpty()) 0L else complete.sumOf { it.netCents } / complete.size
    Panel {
        Text("Did each month live within its money?", color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("Money in against everything out: bills, the debt payment and spending.", color = c.muted, fontSize = 12.sp)
        if (complete.isNotEmpty()) Text(
            if (gap < 0) "Over the last ${complete.size} full months, money out ran ${Money.whole(-gap)} a month more than money in."
            else "Over the last ${complete.size} full months, money in ran ${Money.whole(gap)} a month more than money out.",
            color = if (gap < 0) c.danger else Color(0xFF1F8A4C), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp),
        )
        Canvas(Modifier.padding(top = 10.dp).fillMaxWidth().height(140.dp)) {
            val band = size.width / rows.size
            val bw = minOf(18.dp.toPx(), band / 3f)
            rows.forEachIndexed { i, r ->
                val cx = band * i + band / 2
                val alpha = if (r.current) 0.55f else 1f
                val hIn = size.height * r.inCents / hi
                val hOut = size.height * outOf(r) / hi
                drawRoundRect(s[0].copy(alpha = alpha), Offset(cx - bw - 1f, size.height - hIn), Size(bw, hIn), CornerRadius(3.dp.toPx()))
                drawRoundRect(s[1].copy(alpha = alpha), Offset(cx + 1f, size.height - hOut), Size(bw, hOut), CornerRadius(3.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceAround) {
            rows.forEach { r ->
                Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text((Dates.monthYear("${r.month}-01") ?: r.month).take(3), color = c.muted, fontSize = 11.sp)
                    Text((if (r.netCents < 0) "−" else "+") + Money.whole(kotlin.math.abs(r.netCents)), color = if (r.netCents < 0) c.danger else Color(0xFF1F8A4C), fontSize = 10.sp)
                }
            }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) { Swatch(s[0], "Money in") }
            Box(Modifier.weight(1f)) { Swatch(s[1], "Money out") }
        }
        Text("The number under each month is in minus out. This month is lighter: it isn't over.", color = c.muted, fontSize = 11.sp)
    }
}
