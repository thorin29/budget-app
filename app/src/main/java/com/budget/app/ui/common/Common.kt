package com.budget.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.data.BillIcons
import com.budget.app.data.remote.MonthEntry
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon
import com.budget.app.ui.theme.iconFor

/** A white (or dark) card with a hairline border. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val c = Budget.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(20.dp))
            .border(1.dp, c.line, RoundedCornerShape(20.dp))
            .padding(padding),
        content = content,
    )
}

@Composable
fun Pill(text: String, fg: Color, bg: Color) {
    Box(
        Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A colored strip: offline, waiting to send, update available, an error. */
@Composable
fun Banner(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier, icon: Ph? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) PhIcon(icon, null, fg, size = 20.dp)
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp)
    }
}

/** Small uppercase heading above a group of rows. */
@Composable
fun SectionLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp),
    )
}

/** The rounded square with a line item's icon. */
@Composable
fun BillTile(entry: MonthEntry, size: Dp = 40.dp, tint: Color = Budget.colors.muted, background: Color = Budget.colors.raised) {
    val key = BillIcons.forItem(entry.name, entry.categoryName, entry.isIncome)
    Box(
        Modifier
            .size(size)
            .background(background, RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        PhIcon(iconFor(key), null, tint, size = size * 0.55f)
    }
}

/** The app's mark: three ledger lines, in the accent. */
@Composable
fun LedgerMark(size: Dp = 32.dp) {
    val c = Budget.colors
    Box(
        Modifier
            .size(size)
            .background(c.accent, RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.62f)) {
            val u = this.size.width / 24f
            val h = 6f * u
            val r = CornerRadius(3f * u, 3f * u)
            drawRoundRect(c.onAccent, Offset(0f, 1f * u), Size(20f * u, h), r)
            drawRoundRect(c.onAccent.copy(alpha = 0.75f), Offset(0f, 9f * u), Size(24f * u, h), r)
            drawRoundRect(c.onAccent.copy(alpha = 0.5f), Offset(0f, 17f * u), Size(16f * u, h), r)
        }
    }
}
