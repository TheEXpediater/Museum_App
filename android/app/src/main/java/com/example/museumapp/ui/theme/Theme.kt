package com.example.museumapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF14532D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF3E2),
    onPrimaryContainer = Color(0xFF0B3D24),
    secondary = Color(0xFF2E7D32),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF3E2),
    onSecondaryContainer = Color(0xFF0B3D24),
    tertiary = Color(0xFF69A86B),
    onTertiary = Color(0xFF17211A),
    background = Color(0xFFF5F8F5),
    onBackground = Color(0xFF17211A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17211A),
    surfaceVariant = Color(0xFFE8EFE9),
    onSurfaceVariant = Color(0xFF526158),
    outline = Color(0xFFCCD8CE),
    outlineVariant = Color(0xFFE8EFE9),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA7DDB5),
    onPrimary = Color(0xFF00210E),
    primaryContainer = Color(0xFF0B3D24),
    onPrimaryContainer = Color(0xFFDDF3E2),
    secondary = Color(0xFFA7DDB5),
    onSecondary = Color(0xFF05210E),
    secondaryContainer = Color(0xFF245C2B),
    onSecondaryContainer = Color(0xFFDDF3E2),
    tertiary = Color(0xFFB8CCB9),
    onTertiary = Color(0xFF233426),
    background = Color(0xFF101511),
    onBackground = Color(0xFFE0E6DE),
    surface = Color(0xFF141A15),
    onSurface = Color(0xFFE0E6DE),
    surfaceVariant = Color(0xFF3F4941),
    onSurfaceVariant = Color(0xFFC0CABF),
    outline = Color(0xFF899389),
    outlineVariant = Color(0xFF3F4941),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val MuseumTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp),
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

@Composable
fun MuseumAdminTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = MuseumTypography,
        content = content
    )
}
