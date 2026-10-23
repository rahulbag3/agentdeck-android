package com.agentdeck.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF17191C)
val InkSoft = Color(0xFF2D333A)
val Muted = Color(0xFF616870)
val Muted2 = Color(0xFF6A7179)
val Line = Color(0xFFE6E8EB)
val LineStrong = Color(0xFFD7DBE0)
val Surface = Color(0xFFFFFFFF)
val Surface2 = Color(0xFFF7F8F9)
val Surface3 = Color(0xFFF0F2F4)
val Canvas = Color(0xFFECEFF2)
val Blue = Color(0xFF2F6BEA)
val BlueInk = Color(0xFF1D4FBF)
val BlueSoft = Color(0xFFEDF3FF)
val Green = Color(0xFF1F9D63)
val GreenInk = Color(0xFF1B7A50)
val GreenSoft = Color(0xFFE7F7EF)
val Red = Color(0xFFD92D20)
val RedInk = Color(0xFFB42318)
val RedSoft = Color(0xFFFEECEA)
val Amber = Color(0xFFB7791F)
val AmberInk = Color(0xFF8A5A00)
val AmberSoft = Color(0xFFFFF3D8)
val Violet = Color(0xFF6C4CE0)
val VioletInk = Color(0xFF5741C8)
val VioletSoft = Color(0xFFF0EDFF)
val GrayInk = Color(0xFF5F6670)
val GraySoft = Color(0xFFF0F2F4)

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
    primary = Ink,
    onPrimary = Surface,
    primaryContainer = BlueSoft,
    onPrimaryContainer = BlueInk,
    secondary = Blue,
    onSecondary = Surface,
    secondaryContainer = BlueSoft,
    onSecondaryContainer = BlueInk,
    background = Surface2,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    outline = Line,
    outlineVariant = Line,
    error = Red,
    onError = Surface,
    errorContainer = RedSoft,
    onErrorContainer = RedInk
)

@Composable
fun AgentDeckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AdTypography,
        content = content
    )
}
