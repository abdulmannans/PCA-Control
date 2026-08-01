package com.pca.control.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF1B3A4B)
private val Accent = Color(0xFF2A9D8F)
private val Surface = Color(0xFFF5F7F8)
private val Danger = Color(0xFF9B2226)

private val ColorScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = Accent,
    onSecondary = Color.White,
    background = Surface,
    onBackground = Color(0xFF12232E),
    surface = Color.White,
    onSurface = Color(0xFF12232E),
    error = Danger,
    onError = Color.White
)

@Composable
fun PcaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content
    )
}
