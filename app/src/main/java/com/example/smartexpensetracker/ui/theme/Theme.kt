package com.example.smartexpensetracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary          = PrimaryGreen,
    onPrimary        = Color.White,
    secondary        = LightMint,
    onSecondary      = PrimaryGreen,
    background       = LightBackground,
    onBackground     = TextPrimary,
    surface          = CardBackground,
    onSurface        = TextPrimary,
    surfaceVariant   = Color(0xFFE5F2EF),
    onSurfaceVariant = TextSecondary,
    error            = ExpenseRed,
    onError          = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary          = PrimaryGreen,
    onPrimary        = Color.Black,
    secondary        = Color(0xFF134E4A),
    onSecondary      = PrimaryGreen,
    background       = Color(0xFF0F1714),
    onBackground     = Color(0xFFE5E7EB),
    surface          = Color(0xFF1C2B27),
    onSurface        = Color(0xFFE5E7EB),
    surfaceVariant   = Color(0xFF243330),
    onSurfaceVariant = Color(0xFF9CA3AF),
    error            = ExpenseRed,
    onError          = Color.White
)

@Composable
fun SmartExpenseTrackerTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography  = Typography,
        content     = content
    )
}