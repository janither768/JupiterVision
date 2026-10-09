// VisionHomeTheme.kt - Custom Compose Theme applying strict industrial Inter typography and monochrome palette.
package com.jupiter.vision.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val VisionHomeColorScheme = darkColorScheme(
    background = Color(0xFF1A1A1A),
    surface = Color(0xFF1A1A1A),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xCCFFFFFF)
)

val VisionHomeTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 72.sp,
        fontFeatureSettings = "tnum"
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.05.sp
    )
)

@Composable
fun VisionHomeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VisionHomeColorScheme,
        typography = VisionHomeTypography,
        content = content
    )
}
