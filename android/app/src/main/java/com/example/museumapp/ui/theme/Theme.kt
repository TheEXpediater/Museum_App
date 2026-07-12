package com.example.museumapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF355C43),
    onPrimary = Color.White,
    secondary = Color(0xFF6B5B8A),
    tertiary = Color(0xFF8B5E3C),
    surface = Color(0xFFFCFCF7),
    surfaceVariant = Color(0xFFE5E6DA),
    background = Color(0xFFFCFCF7)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ED0AE),
    secondary = Color(0xFFC7B7E8),
    tertiary = Color(0xFFE3B386)
)

@Composable
fun MuseumAdminTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
