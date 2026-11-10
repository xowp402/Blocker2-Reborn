package com.blocker2.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    secondary = Color(0xFF80CBC4),
    background = Color(0xFF10151F),
    surface = Color(0xFF192231),
    surfaceVariant = Color(0xFF263244)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2459A6),
    secondary = Color(0xFF287F78),
    background = Color(0xFFF5F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EDF5)
)

@Composable
fun Blocker2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
