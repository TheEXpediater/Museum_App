package com.example.museumapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared museum brand accents. Kept close to the Visitor palette so both experiences read as one product. */
object AdminMuseumTokens {
    val MuseumNavy = Color(0xFF132238)
    val AntiqueGold = Color(0xFFC89B3C)
    val SlateBlue = Color(0xFF3F5D74)
}

private val LightColors = lightColorScheme(
    primary = AdminMuseumTokens.MuseumNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3EF),
    onPrimaryContainer = AdminMuseumTokens.MuseumNavy,
    secondary = AdminMuseumTokens.AntiqueGold,
    onSecondary = Color(0xFF2A2000),
    secondaryContainer = Color(0xFFF1E3C2),
    onSecondaryContainer = Color(0xFF332500),
    tertiary = AdminMuseumTokens.SlateBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE7EE),
    onTertiaryContainer = Color(0xFF16323F),
    background = Color(0xFFF3F5F8),
    onBackground = Color(0xFF1B1F24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1F24),
    surfaceVariant = Color(0xFFE7EAEE),
    onSurfaceVariant = Color(0xFF565E68),
    outline = Color(0xFFCBD2DA),
    outlineVariant = Color(0xFFE3E7EC),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C4DD),
    onPrimary = Color(0xFF16233A),
    primaryContainer = Color(0xFF283A54),
    onPrimaryContainer = Color(0xFFDCE3EF),
    secondary = Color(0xFFE0BE7A),
    onSecondary = Color(0xFF3A2E05),
    secondaryContainer = Color(0xFF4F3E12),
    onSecondaryContainer = Color(0xFFF1E3C2),
    tertiary = Color(0xFFA7C3D7),
    onTertiary = Color(0xFF102B3A),
    tertiaryContainer = Color(0xFF244254),
    onTertiaryContainer = Color(0xFFDCE7EE),
    background = Color(0xFF121417),
    onBackground = Color(0xFFE3E6E9),
    surface = Color(0xFF191C20),
    onSurface = Color(0xFFE3E6E9),
    surfaceVariant = Color(0xFF3F454C),
    onSurfaceVariant = Color(0xFFC1C7CE),
    outline = Color(0xFF8B9199),
    outlineVariant = Color(0xFF3F454C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val MuseumTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.15).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
)

private val MuseumShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MuseumAdminTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = MuseumTypography,
        shapes = MuseumShapes,
        content = content
    )
}
