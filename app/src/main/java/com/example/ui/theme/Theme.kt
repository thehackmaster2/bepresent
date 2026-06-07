package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ElectricTeal,
    secondary = SoftWarningAmber,
    tertiary = AccentGold,
    background = DarkMidnightBg,
    surface = CardBackground,
    onBackground = Color.White,
    onSurface = Color.White,
    error = BlockCoral
)

@Composable
fun BePresentTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
