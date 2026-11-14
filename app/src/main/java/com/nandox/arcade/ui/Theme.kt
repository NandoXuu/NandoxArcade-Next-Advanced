package com.nandox.arcade.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val ArcadeColors = darkColorScheme(
    primary = Color(0xFF9CC2FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF1F4777),
    onPrimaryContainer = Color(0xFFD6E6FF),
    secondary = Color(0xFFB9C7DB),
    onSecondary = Color(0xFF233140),
    secondaryContainer = Color(0xFF3A4858),
    onSecondaryContainer = Color(0xFFD5E3F7),
    tertiary = Color(0xFFD0D7E2),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE6E9EE),
    surface = Color(0xFF181C21),
    onSurface = Color(0xFFE6E9EE),
    surfaceVariant = Color(0xFF262C34),
    onSurfaceVariant = Color(0xFFC3CAD4),
    outline = Color(0xFF6B7482),
    error = Color(0xFFFFB4AB),
)

/**
 * A Surface é essencial: sem ela o Compose usa preto como cor padrão do texto, e texto preto sobre
 * o fundo escuro ficava invisível.
 */
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
