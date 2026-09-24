package com.jupiter.vision.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Sharp = 0.dp // Strict industrial design; no rounding anywhere

val Inter = FontFamily.SansSerif

private val JVTypography = Typography(
    displayLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.2.sp),
)

private val JVScheme = darkColorScheme(
    background = Color(0xFF000000),
    surface = Color(0xFF101012),
    onBackground = Color(0xFFE8E8E8),
    onSurface = Color(0xFFE8E8E8),
)

@Composable
fun JupiterVisionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JVScheme,
        typography = JVTypography,
        content = content
    )
}
