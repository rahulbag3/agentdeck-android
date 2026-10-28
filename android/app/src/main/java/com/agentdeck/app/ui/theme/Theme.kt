package com.agentdeck.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private data class Palette(
    val ink: Color,
    val inkSoft: Color,
    val muted: Color,
    val muted2: Color,
    val line: Color,
    val lineStrong: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val canvas: Color,
    val blue: Color,
    val blueInk: Color,
    val blueSoft: Color,
    val green: Color,
    val greenInk: Color,
    val greenSoft: Color,
    val red: Color,
    val redInk: Color,
    val redSoft: Color,
    val amber: Color,
    val amberInk: Color,
    val amberSoft: Color,
    val violet: Color,
    val violetInk: Color,
    val violetSoft: Color,
    val grayInk: Color,
    val graySoft: Color
)

private val LightPalette = Palette(
    ink = Color(0xFF17191C),
    inkSoft = Color(0xFF2D333A),
    muted = Color(0xFF616870),
    muted2 = Color(0xFF6A7179),
    line = Color(0xFFE6E8EB),
    lineStrong = Color(0xFFD7DBE0),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF7F8F9),
    surface3 = Color(0xFFF0F2F4),
    canvas = Color(0xFFECEFF2),
    blue = Color(0xFF2F6BEA),
    blueInk = Color(0xFF1D4FBF),
    blueSoft = Color(0xFFEDF3FF),
    green = Color(0xFF1F9D63),
    greenInk = Color(0xFF1B7A50),
    greenSoft = Color(0xFFE7F7EF),
    red = Color(0xFFD92D20),
    redInk = Color(0xFFB42318),
    redSoft = Color(0xFFFEECEA),
    amber = Color(0xFFB7791F),
    amberInk = Color(0xFF8A5A00),
    amberSoft = Color(0xFFFFF3D8),
    violet = Color(0xFF6C4CE0),
    violetInk = Color(0xFF5741C8),
    violetSoft = Color(0xFFF0EDFF),
    grayInk = Color(0xFF5F6670),
    graySoft = Color(0xFFF0F2F4)
)

private val DarkPalette = Palette(
    ink = Color(0xFFE8EAED),
    inkSoft = Color(0xFFC9CED5),
    muted = Color(0xFF9AA1A9),
    muted2 = Color(0xFF7F868E),
    line = Color(0xFF262A2F),
    lineStrong = Color(0xFF343A41),
    surface = Color(0xFF101215),
    surface2 = Color(0xFF15181C),
    surface3 = Color(0xFF1D2126),
    canvas = Color(0xFF0A0C0E),
    blue = Color(0xFF6FA0FF),
    blueInk = Color(0xFF9CBDFF),
    blueSoft = Color(0xFF16223A),
    green = Color(0xFF4CC38A),
    greenInk = Color(0xFF7AD9A8),
    greenSoft = Color(0xFF12271D),
    red = Color(0xFFF97066),
    redInk = Color(0xFFFF9B94),
    redSoft = Color(0xFF2B1615),
    amber = Color(0xFFD9A441),
    amberInk = Color(0xFFF0C674),
    amberSoft = Color(0xFF2A2112),
    violet = Color(0xFF9B85FF),
    violetInk = Color(0xFFC0B0FF),
    violetSoft = Color(0xFF221C38),
    grayInk = Color(0xFFA7ADB4),
    graySoft = Color(0xFF1B1E22)
)

private var palette by mutableStateOf(LightPalette)

/** Sets the palette before the first frame so dark launches do not flash light. */
fun primePalette(dark: Boolean) {
    palette = if (dark) DarkPalette else LightPalette
}

val Ink: Color get() = palette.ink
val InkSoft: Color get() = palette.inkSoft
val Muted: Color get() = palette.muted
val Muted2: Color get() = palette.muted2
val Line: Color get() = palette.line
val LineStrong: Color get() = palette.lineStrong
val Surface: Color get() = palette.surface
val Surface2: Color get() = palette.surface2
val Surface3: Color get() = palette.surface3
val Canvas: Color get() = palette.canvas
val Blue: Color get() = palette.blue
val BlueInk: Color get() = palette.blueInk
val BlueSoft: Color get() = palette.blueSoft
val Green: Color get() = palette.green
val GreenInk: Color get() = palette.greenInk
val GreenSoft: Color get() = palette.greenSoft
val Red: Color get() = palette.red
val RedInk: Color get() = palette.redInk
val RedSoft: Color get() = palette.redSoft
val Amber: Color get() = palette.amber
val AmberInk: Color get() = palette.amberInk
val AmberSoft: Color get() = palette.amberSoft
val Violet: Color get() = palette.violet
val VioletInk: Color get() = palette.violetInk
val VioletSoft: Color get() = palette.violetSoft
val GrayInk: Color get() = palette.grayInk
val GraySoft: Color get() = palette.graySoft

val Mono = FontFamily.Monospace

private val AdTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.5).sp, lineHeight = 29.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = (-0.55).sp, lineHeight = 25.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.5.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp, lineHeight = 13.sp)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF17191C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDF3FF),
    onPrimaryContainer = Color(0xFF1D4FBF),
    secondary = Color(0xFF2F6BEA),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDF3FF),
    onSecondaryContainer = Color(0xFF1D4FBF),
    background = Color(0xFFF7F8F9),
    onBackground = Color(0xFF17191C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17191C),
    surfaceVariant = Color(0xFFF7F8F9),
    onSurfaceVariant = Color(0xFF616870),
    outline = Color(0xFFE6E8EB),
    outlineVariant = Color(0xFFE6E8EB),
    error = Color(0xFFD92D20),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEECEA),
    onErrorContainer = Color(0xFFB42318)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8EAED),
    onPrimary = Color(0xFF101215),
    primaryContainer = Color(0xFF16223A),
    onPrimaryContainer = Color(0xFF9CBDFF),
    secondary = Color(0xFF6FA0FF),
    onSecondary = Color(0xFF0A0C0E),
    secondaryContainer = Color(0xFF16223A),
    onSecondaryContainer = Color(0xFF9CBDFF),
    background = Color(0xFF15181C),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF101215),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF15181C),
    onSurfaceVariant = Color(0xFF9AA1A9),
    outline = Color(0xFF262A2F),
    outlineVariant = Color(0xFF262A2F),
    error = Color(0xFFF97066),
    onError = Color(0xFF0A0C0E),
    errorContainer = Color(0xFF2B1615),
    onErrorContainer = Color(0xFFFF9B94)
)

@Composable
fun AgentDeckTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    SideEffect { primePalette(dark) }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AdTypography,
        content = content
    )
}
