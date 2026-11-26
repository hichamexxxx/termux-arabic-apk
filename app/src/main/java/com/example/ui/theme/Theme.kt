package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalTerminalPalette = staticCompositionLocalOf { TermuxClassicPalette }

@Composable
fun TermuxArabicTheme(
    palette: TerminalPalette = TermuxClassicPalette,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = palette.accentGreen,
        onPrimary = Color.Black,
        primaryContainer = palette.surfaceElevated,
        onPrimaryContainer = palette.accentGreen,
        secondary = palette.accentCyan,
        onSecondary = Color.Black,
        secondaryContainer = palette.surfaceElevated,
        onSecondaryContainer = palette.accentCyan,
        tertiary = palette.accentYellow,
        background = palette.background,
        onBackground = palette.foreground,
        surface = palette.surface,
        onSurface = palette.foreground,
        surfaceVariant = palette.surfaceElevated,
        onSurfaceVariant = palette.mutedForeground,
        outline = palette.borderSubtle,
        error = palette.accentRed,
        onError = Color.White
    )

    CompositionLocalProvider(LocalTerminalPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
