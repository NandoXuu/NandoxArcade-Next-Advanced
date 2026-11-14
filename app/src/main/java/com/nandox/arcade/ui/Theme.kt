package com.nandox.arcade.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ArcadeColors = darkColorScheme(
    primary = Color(0xFFB7C7E8),
    onPrimary = Color(0xFF172033),
    secondary = Color(0xFF9EABC2),
    onSecondary = Color(0xFF1A202B),
    tertiary = Color(0xFFD0D7E2),
    background = Color(0xFF101216),
    onBackground = Color(0xFFF1F3F6),
    surface = Color(0xFF191D23),
    onSurface = Color(0xFFF1F3F6),
    surfaceVariant = Color(0xFF292F38),
    onSurfaceVariant = Color(0xFFC0C7D2),
    outline = Color(0xFF596272),
    error = Color(0xFFFFB4AB),
)

@Composable
fun ArcadeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ArcadeColors, content = content)
}
