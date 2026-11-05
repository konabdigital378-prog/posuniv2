package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = KonabBlue,
    onPrimary = Color.White,
    primaryContainer = KonabBlueLight,
    onPrimaryContainer = KonabBlueDark,
    secondary = KonabOrange,
    onSecondary = Color.White,
    secondaryContainer = KonabOrangeContainer,
    onSecondaryContainer = KonabOrangeDark,
    tertiary = KonabOrangeLight,
    onTertiary = KonabTextPrimary,
    tertiaryContainer = KonabOrangeContainer,
    onTertiaryContainer = KonabOrangeDark,
    background = KonabBackground,
    onBackground = KonabTextPrimary,
    surface = KonabSurface,
    onSurface = KonabTextPrimary,
    surfaceVariant = KonabSurfaceVariant,
    onSurfaceVariant = KonabTextSecondary,
    outline = KonabBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4D85F0),
    onPrimary = Color(0xFF031842),
    primaryContainer = KonabBlueDark,
    onPrimaryContainer = Color(0xFFD6E4FA),
    secondary = KonabOrangeLight,
    onSecondary = Color(0xFF4A2000),
    secondaryContainer = Color(0xFF6B3000),
    onSecondaryContainer = Color(0xFFFFDBC2),
    tertiary = KonabOrange,
    onTertiary = Color.White,
    background = Color(0xFF0A111F),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF101B2E),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF17253D),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF2A3C59)
)

@Composable
fun KonabPOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
