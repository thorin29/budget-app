package com.budget.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.ui.theme.Budget

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Budget.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(16.dp))
            .border(1.dp, c.line, RoundedCornerShape(16.dp))
            .padding(16.dp),
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

/** A coloured strip across the top: offline, waiting to sync, update available. */
@Composable
fun Banner(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, color = fg, fontSize = 14.sp)
    }
}
