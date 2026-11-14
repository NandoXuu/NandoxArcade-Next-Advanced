package com.nandox.arcade.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val ArcadeColors = darkColorScheme(
    primary = Color(0xFF9B7BFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF3B2A6E),
    onPrimaryContainer = Color(0xFFEDE6FF),
    secondary = Color(0xFFB7AFCB),
    onSecondary = Color(0xFF1F1B2E),
    secondaryContainer = Color(0xFF2E2A3D),
    onSecondaryContainer = Color(0xFFE2DCF2),
    tertiary = Color(0xFFCDB8FF),
    background = Color(0xFF0D0C13),
    onBackground = Color(0xFFF2EFFA),
    surface = Color(0xFF15131D),
    onSurface = Color(0xFFF2EFFA),
    surfaceVariant = Color(0xFF1F1C2B),
    onSurfaceVariant = Color(0xFF9C97AE),
    outline = Color(0xFF4A4658),
    error = Color(0xFFFFB4AB),
)

@Composable
fun ArcadeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ArcadeColors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}
