package com.tradingview.binancefutures.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TVBackground = Color(0xFF131722)
val TVSurface = Color(0xFF181B24)
val TVCard = Color(0xFF1E222D)
val TVBorder = Color(0xFF242731)
val TVPrimaryBlue = Color(0xFF2962FF)
val TVBullishGreen = Color(0xFF089981)
val TVBearishRed = Color(0xFFF23645)
val TVTextPrimary = Color(0xFFD1D4DC)
val TVTextSecondary = Color(0xFF787B86)

private val DarkColorScheme = darkColorScheme(
    primary = TVPrimaryBlue,
    background = TVBackground,
    surface = TVSurface,
    onPrimary = Color.White,
    onBackground = TVTextPrimary,
    onSurface = TVTextPrimary
)

@Composable
fun TradingViewTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
