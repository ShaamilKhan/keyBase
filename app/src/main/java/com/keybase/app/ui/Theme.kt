package com.keybase.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object KB {
    val Blue = Color(0xFF0A5FD6)      // solid button blue
    val BlueHover = Color(0xFF0C6BF0)
    val BlueSoft = Color(0xFF5B9DFF)
    val Ok = Color(0xFF30D158)
    val OkLight = Color(0xFF1E9E3E)
    val Warn = Color(0xFFFFB800)
    val Orange = Color(0xFFFB923C)
    val Bad = Color(0xFFFF453A)
    val BadLight = Color(0xFFD92D20)
}

// Dark is true-black (AMOLED): unlit pixels, with hairline borders for depth instead of shadows.
private val Dark = darkColorScheme(
    primary = KB.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0B2A57),
    onPrimaryContainer = KB.BlueSoft,
    secondary = KB.BlueSoft,
    secondaryContainer = Color(0xFF0B2A57),
    onSecondaryContainer = KB.BlueSoft,
    surfaceTint = Color(0xFF0C0C0E),
    background = Color.Black,
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF0C0C0E),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF161618),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF242427),
    outlineVariant = Color(0xFF161618),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0C0C0E),
    surfaceContainer = Color(0xFF111113),
    surfaceContainerHigh = Color(0xFF161618),
    surfaceContainerHighest = Color(0xFF1C1C1F),
    error = KB.Bad,
)

private val Light = lightColorScheme(
    primary = KB.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9FF),
    onPrimaryContainer = Color(0xFF0A4DAE),
    secondary = Color(0xFF0A5FD6),
    secondaryContainer = Color(0xFFDCE9FF),
    onSecondaryContainer = Color(0xFF0A4DAE),
    surfaceTint = Color.White,
    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF1D1D1F),
    surface = Color.White,
    onSurface = Color(0xFF1D1D1F),
    surfaceVariant = Color(0xFFEDEDF0),
    onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFE0E0E5),
    outlineVariant = Color(0xFFEDEDF0),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFB),
    surfaceContainer = Color(0xFFF2F2F5),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFEDEDF0),
    error = KB.BadLight,
)

private val Type = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
)

val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, letterSpacing = 0.4.sp)

private val Round = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
)

/** 0 = follow the system, 1 = light, 2 = dark. */
@Composable
fun isDarkTheme(mode: Int): Boolean = when (mode) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

val LocalDark = staticCompositionLocalOf { true }

@Composable
fun KeyBaseTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = isDarkTheme(mode)
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, shapes = Round) {
        CompositionLocalProvider(LocalDark provides dark, content = content)
    }
}

@Composable
fun strengthColor(level: Int): Color {
    val dark = LocalDark.current
    return when (level) {
        0 -> if (dark) KB.Bad else KB.BadLight
        1 -> KB.Orange
        2 -> KB.Warn
        else -> if (dark) KB.Ok else KB.OkLight
    }
}
