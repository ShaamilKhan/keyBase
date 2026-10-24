package com.keybase.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object KB {
    val Blue = Color(0xFF0A5FD6)      // solid Cloudflare-style button blue
    val BlueSoft = Color(0xFF5B9DFF)  // links / focus rings on black
    val Ok = Color(0xFF30D158)
    val Warn = Color(0xFFFFD60A)
    val Bad = Color(0xFFFF453A)
}

// Pure-black AMOLED palette: unlit pixels on OLED, with hairline borders for depth instead of shadows.
private val Amoled = darkColorScheme(
    primary = KB.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0B2A57),
    onPrimaryContainer = KB.BlueSoft,
    secondary = KB.BlueSoft,
    background = Color.Black,
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF0C0C0E),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF161618),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF242427),
    error = KB.Bad,
)

private val Type = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
)

val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, letterSpacing = 0.4.sp)

private val Round = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
)

/** The app is AMOLED dark only, regardless of the system theme. */
@Composable
fun KeyBaseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Amoled, typography = Type, shapes = Round, content = content)
}
