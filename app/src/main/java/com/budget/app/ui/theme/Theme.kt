package com.budget.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The web app's "cool" look: greys, a green accent, a dark green hero card. */
@Immutable
data class BudgetColors(
    val background: Color,
    val surface: Color,
    val sunken: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val accentText: Color,
    val accentSoft: Color,
    val hero: Color,
    val heroText: Color,
    val heroMuted: Color,
    val ok: Color,
    val okSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val neutral: Color,
    val neutralSoft: Color,
    val info: Color,
    val infoSoft: Color,
)

private val Light = BudgetColors(
    background = Color(0xFFF4F5F7), surface = Color(0xFFFFFFFF), sunken = Color(0xFFF9FAFB),
    line = Color(0xFFE4E7EC), ink = Color(0xFF101828), muted = Color(0xFF5D6B7E),
    accent = Color(0xFF0E8F5B), accentText = Color(0xFF0B7A4D), accentSoft = Color(0xFFE7F6EE),
    hero = Color(0xFF0F2E22), heroText = Color(0xFFFFFFFF), heroMuted = Color(0xFFA6E3C4),
    ok = Color(0xFF067647), okSoft = Color(0xFFDCFAE6), danger = Color(0xFFB42318), dangerSoft = Color(0xFFFEE4E2),
    warn = Color(0xFF93370D), warnSoft = Color(0xFFFEF0C7), neutral = Color(0xFF475467), neutralSoft = Color(0xFFF2F4F7),
    info = Color(0xFF2457D6), infoSoft = Color(0xFFE0EAFF),
)

private val Dark = BudgetColors(
    background = Color(0xFF0C111D), surface = Color(0xFF161B26), sunken = Color(0xFF121722),
    line = Color(0xFF2A303C), ink = Color(0xFFF5F5F6), muted = Color(0xFFA3ACB9),
    accent = Color(0xFF2FB57B), accentText = Color(0xFF47CD8F), accentSoft = Color(0xFF10342A),
    hero = Color(0xFF0F2E22), heroText = Color(0xFFFFFFFF), heroMuted = Color(0xFFA6E3C4),
    ok = Color(0xFF75E0A7), okSoft = Color(0xFF0F3324), danger = Color(0xFFF97066), dangerSoft = Color(0xFF3D1714),
    warn = Color(0xFFFDB022), warnSoft = Color(0xFF3A2A0C), neutral = Color(0xFFCECFD2), neutralSoft = Color(0xFF262B36),
    info = Color(0xFF6F97FF), infoSoft = Color(0xFF1B2A52),
)

val LocalBudgetColors = staticCompositionLocalOf { Light }

object Budget {
    val colors: BudgetColors
        @Composable get() = LocalBudgetColors.current
}

@Composable
fun BudgetTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = Color.White, background = c.background, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink, surfaceVariant = c.sunken, onSurfaceVariant = c.muted,
            outline = c.line, error = c.danger,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = Color.White, background = c.background, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink, surfaceVariant = c.sunken, onSurfaceVariant = c.muted,
            outline = c.line, error = c.danger,
        )
    }
    val base = Typography()
    val type = base.copy(
        headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalBudgetColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}
