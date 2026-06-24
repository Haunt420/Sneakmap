package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val ImmersiveDarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color.Black,
    secondary = CyanAccent,
    onSecondary = Color.Black,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkCanvas,
    onSurfaceVariant = TextSecondary,
    outlineVariant = DividerColor
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme for Immersive UI
    dynamicColor: Boolean = false, // Disable dynamic colors to keep cyan theme
    content: @Composable () -> Unit,
) {
    val colorScheme = ImmersiveDarkColorScheme

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
