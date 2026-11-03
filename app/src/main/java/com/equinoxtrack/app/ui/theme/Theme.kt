package com.equinoxtrack.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.equinoxtrack.app.R
import com.equinoxtrack.app.domain.AccentPalette
import com.equinoxtrack.app.domain.ThemeMode

/* ------------------------------------------------------------------ */
/* Palette                                                             */
/* ------------------------------------------------------------------ */

@Immutable
data class EquinoxColors(
    val isDark: Boolean,
    val background: Color,
    val backgroundGlow: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val stroke: Color,
    val strokeStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentAlt: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val warning: Color,
    val heroStart: Color,
    val heroMid: Color,
    val heroEnd: Color,
    val chart: List<Color>,
) {
    val accentBrush: Brush get() = Brush.linearGradient(listOf(accent, accentAlt))
    val heroBrush: Brush get() = Brush.linearGradient(listOf(heroStart, heroMid, heroEnd))
}

private data class AccentPair(val dark: Color, val darkAlt: Color, val light: Color, val lightAlt: Color)

private fun accentOf(p: AccentPalette): AccentPair = when (p) {
    AccentPalette.VIOLET -> AccentPair(Color(0xFF8B6CFF), Color(0xFF4F8BFF), Color(0xFF6A4DF5), Color(0xFF3C74F0))
    AccentPalette.ELECTRIC -> AccentPair(Color(0xFF3D8BFF), Color(0xFF22D3EE), Color(0xFF2468E8), Color(0xFF0EA5C6))
    AccentPalette.AURORA -> AccentPair(Color(0xFF19D3B9), Color(0xFF4F8BFF), Color(0xFF0E9F8B), Color(0xFF3164E0))
    AccentPalette.EMBER -> AccentPair(Color(0xFFFF7A45), Color(0xFFFF3D7F), Color(0xFFE85D24), Color(0xFFE0245E))
    AccentPalette.ROSE -> AccentPair(Color(0xFFFF4F9A), Color(0xFF9B5CFF), Color(0xFFE0337F), Color(0xFF7C3AED))
    AccentPalette.LIME -> AccentPair(Color(0xFFB4F04A), Color(0xFF19D3B9), Color(0xFF5E9E0A), Color(0xFF0E9F8B))
}

fun accentPreview(p: AccentPalette): Pair<Color, Color> = accentOf(p).let { it.dark to it.darkAlt }

private val Midnight = Color(0xFF060914)

fun equinoxColors(dark: Boolean, palette: AccentPalette): EquinoxColors {
    val a = accentOf(palette)
    return if (dark) {
        val accent = a.dark
        EquinoxColors(
            isDark = true,
            background = Color(0xFF070B18),
            backgroundGlow = accent.copy(alpha = 0.10f),
            surface = Color(0xFF0F1426),
            surfaceRaised = Color(0xFF161C33),
            surfaceSunken = Color(0xFF0A0E1C),
            stroke = Color(0xFF1F2742),
            strokeStrong = Color(0xFF2C365A),
            textPrimary = Color(0xFFEEF1FF),
            textSecondary = Color(0xFF9AA3C7),
            textTertiary = Color(0xFF636C92),
            accent = accent,
            accentAlt = a.darkAlt,
            accentSoft = accent.copy(alpha = 0.16f),
            onAccent = if (palette == AccentPalette.LIME) Color(0xFF0B1206) else Color.White,
            income = Color(0xFF34E3A0),
            expense = Color(0xFFFF6B86),
            transfer = Color(0xFF7AB8FF),
            warning = Color(0xFFFFC145),
            heroStart = lerp(accent, Midnight, 0.30f),
            heroMid = lerp(a.darkAlt, Midnight, 0.55f),
            heroEnd = Color(0xFF0A0F22),
            chart = chartColors(accent, a.darkAlt),
        )
    } else {
        val accent = a.light
        EquinoxColors(
            isDark = false,
            background = Color(0xFFF3F4FA),
            backgroundGlow = accent.copy(alpha = 0.08f),
            surface = Color(0xFFFFFFFF),
            surfaceRaised = Color(0xFFF8F8FD),
            surfaceSunken = Color(0xFFE9EBF5),
            stroke = Color(0xFFE3E6F2),
            strokeStrong = Color(0xFFCDD2E6),
            textPrimary = Color(0xFF0C1030),
            textSecondary = Color(0xFF555C7E),
            textTertiary = Color(0xFF8B91AE),
            accent = accent,
            accentAlt = a.lightAlt,
            accentSoft = accent.copy(alpha = 0.12f),
            onAccent = Color.White,
            income = Color(0xFF0E9F6E),
            expense = Color(0xFFE5395B),
            transfer = Color(0xFF2F7BEA),
            warning = Color(0xFFD48A00),
            // The hero card stays deep in light mode: it is the brand moment of the app.
            heroStart = lerp(accent, Midnight, 0.20f),
            heroMid = lerp(a.lightAlt, Midnight, 0.45f),
            heroEnd = Color(0xFF0E1330),
            chart = chartColors(accent, a.lightAlt),
        )
    }
}

private fun chartColors(accent: Color, alt: Color) = listOf(
    accent, alt, Color(0xFF19D3B9), Color(0xFFFFB547), Color(0xFFFF6B86),
    Color(0xFFE15BFF), Color(0xFF7AB8FF), Color(0xFFA3E635), Color(0xFF94A3B8),
)

val LocalEquinoxColors = staticCompositionLocalOf { equinoxColors(true, AccentPalette.VIOLET) }

object Eq {
    val colors: EquinoxColors
        @Composable @ReadOnlyComposable get() = LocalEquinoxColors.current
    val type: Typography
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography
}

/* ------------------------------------------------------------------ */
/* Typography                                                          */
/* ------------------------------------------------------------------ */

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val DisplayFamily = FontFamily(
    Font(R.font.inter_display_medium, FontWeight.Medium),
    Font(R.font.inter_display_semibold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold),
)

/** Tabular numerals keep amounts aligned and stop digits jumping during animations. */
const val TabularNums = "tnum, ss01"

private val EquinoxTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-1.2).sp, fontFeatureSettings = TabularNums),
    displayMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.9).sp, fontFeatureSettings = TabularNums),
    displaySmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.6).sp, fontFeatureSettings = TabularNums),
    headlineLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.1).sp),
    titleMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp),
)

/** Amount style used in lists. */
val AmountStyle = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, fontFeatureSettings = TabularNums, letterSpacing = (-0.1).sp)

private val EquinoxShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun EquinoxTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentPalette = AccentPalette.VIOLET,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val eq = equinoxColors(dark, accent)
    val scheme = if (dark) {
        darkColorScheme(
            primary = eq.accent, onPrimary = eq.onAccent, primaryContainer = eq.accentSoft, onPrimaryContainer = eq.textPrimary,
            secondary = eq.accentAlt, onSecondary = Color.White, tertiary = eq.income,
            background = eq.background, onBackground = eq.textPrimary,
            surface = eq.surface, onSurface = eq.textPrimary, surfaceVariant = eq.surfaceRaised, onSurfaceVariant = eq.textSecondary,
            surfaceContainerLowest = eq.surfaceSunken, surfaceContainerLow = eq.surface, surfaceContainer = eq.surface,
            surfaceContainerHigh = eq.surfaceRaised, surfaceContainerHighest = eq.surfaceRaised,
            outline = eq.strokeStrong, outlineVariant = eq.stroke, error = eq.expense, onError = Color.White,
            inverseSurface = eq.textPrimary, inverseOnSurface = eq.background, scrim = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = eq.accent, onPrimary = eq.onAccent, primaryContainer = eq.accentSoft, onPrimaryContainer = eq.textPrimary,
            secondary = eq.accentAlt, onSecondary = Color.White, tertiary = eq.income,
            background = eq.background, onBackground = eq.textPrimary,
            surface = eq.surface, onSurface = eq.textPrimary, surfaceVariant = eq.surfaceRaised, onSurfaceVariant = eq.textSecondary,
            surfaceContainerLowest = Color.White, surfaceContainerLow = eq.surface, surfaceContainer = eq.surface,
            surfaceContainerHigh = eq.surfaceRaised, surfaceContainerHighest = eq.surfaceSunken,
            outline = eq.strokeStrong, outlineVariant = eq.stroke, error = eq.expense, onError = Color.White,
            inverseSurface = eq.textPrimary, inverseOnSurface = eq.background, scrim = Color.Black,
        )
    }
    CompositionLocalProvider(LocalEquinoxColors provides eq) {
        MaterialTheme(colorScheme = scheme, typography = EquinoxTypography, shapes = EquinoxShapes, content = content)
    }
}
