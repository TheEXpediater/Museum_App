package com.example.museumapp.ui.visitor.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object VisitorMuseumTokens {
    val MuseumNavy = Color(0xFF132238)
    val AntiqueGold = Color(0xFFC89B3C)
    val WarmIvory = Color(0xFFF8F5EF)
    val MutedSage = Color(0xFF728C78)
    val Ink = Color(0xFF20242A)
    val SoftIvory = Color(0xFFEDE7DC)
}

private val VisitorColors = lightColorScheme(
    primary = VisitorMuseumTokens.MuseumNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E7F0),
    onPrimaryContainer = VisitorMuseumTokens.MuseumNavy,
    secondary = VisitorMuseumTokens.AntiqueGold,
    onSecondary = VisitorMuseumTokens.MuseumNavy,
    secondaryContainer = Color(0xFFF1E3C2),
    onSecondaryContainer = Color(0xFF332500),
    tertiary = VisitorMuseumTokens.MutedSage,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3ECE5),
    onTertiaryContainer = Color(0xFF1F3225),
    background = VisitorMuseumTokens.WarmIvory,
    onBackground = VisitorMuseumTokens.Ink,
    surface = Color.White,
    onSurface = VisitorMuseumTokens.Ink,
    surfaceVariant = VisitorMuseumTokens.SoftIvory,
    onSurfaceVariant = Color(0xFF62666D),
    outline = Color(0xFFD6CCBC),
    outlineVariant = Color(0xFFE8DFD2),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val VisitorTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 44.sp, lineHeight = 52.sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 38.sp, lineHeight = 46.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
)

private val VisitorShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
)

@Composable
fun VisitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VisitorColors,
        typography = VisitorTypography,
        shapes = VisitorShapes,
        content = content
    )
}
