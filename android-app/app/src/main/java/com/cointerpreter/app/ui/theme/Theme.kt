package com.cointerpreter.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A deliberately restrained palette: deep navy + warm gold accent, quiet
// neutrals for transcript text. No neon gradients, no neural-network
// iconography (spec §7): this should look at home in a ministerial meeting.
private val Navy = Color(0xFF0E2233)
private val NavyLight = Color(0xFF1B3A52)
private val Gold = Color(0xFFC9A24B)
private val Ink = Color(0xFF1A1D21)
private val Paper = Color(0xFFF7F5F1)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    secondary = Gold,
    onSecondary = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE9E6DF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9CBDA),
    onPrimary = Navy,
    secondary = Gold,
    onSecondary = Ink,
    background = Color(0xFF10151B),
    onBackground = Color(0xFFEDEFF2),
    surface = Color(0xFF171D24),
    onSurface = Color(0xFFEDEFF2),
    surfaceVariant = NavyLight,
)

enum class ThemePreference { SYSTEM, LIGHT, DARK }

@Composable
fun CoInterpreterTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDark = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = CoInterpreterTypography,
        content = content,
    )
}
