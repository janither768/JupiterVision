package com.jupiter.vision.model

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

/**
 * Vivid, highly saturated blue-forward color engine for JupiterVision 2112.8.
 * Value ceiling >= 0.72, saturation floor >= 0.72.
 */
object ColorEngine {

    val Default: List<Color> = listOf(
        Color(0xFF007AFF), // vivid electric azure
        Color(0xFF0052FF), // intense cobalt blue
        Color(0xFF2979FF)  // vibrant bright cyan-blue
    )

    fun fromWallpaper(bmp: Bitmap?): List<Color> {
        if (bmp == null) return Default

        val palette = try {
            Palette.from(bmp)
                .maximumColorCount(32)
                .generate()
        } catch (_: Throwable) {
            null
        } ?: return Default

        val swatches = listOfNotNull(
            palette.vibrantSwatch,
            palette.lightVibrantSwatch,
            palette.darkVibrantSwatch,
            palette.dominantSwatch,
            palette.mutedSwatch
        )

        val usableColors = mutableListOf<Color>()
        val hsv = FloatArray(3)

        for (swatch in swatches) {
            val rgb = swatch.rgb
            android.graphics.Color.colorToHSV(rgb, hsv)

            // Raise saturation floor to at least 0.72
            hsv[1] = hsv[1].coerceIn(0.72f, 1.0f)

            // Raise value ceiling / floor to at least 0.72
            hsv[2] = hsv[2].coerceIn(0.72f, 0.95f)

            val adjustedRgb = android.graphics.Color.HSVToColor(hsv)
            usableColors.add(Color(adjustedRgb))
        }

        val distinctColors = usableColors.distinct()
        return when {
            distinctColors.size >= 3 -> distinctColors.take(3)
            distinctColors.size == 2 -> listOf(distinctColors[0], distinctColors[1], Default[2])
            distinctColors.size == 1 -> listOf(distinctColors[0], Default[1], Default[2])
            else -> Default
        }
    }

    fun fromAppIcon(bmp: Bitmap?, fallback: Color): Color {
        if (bmp == null) return fallback

        val palette = try {
            Palette.from(bmp)
                .maximumColorCount(24)
                .generate()
        } catch (_: Throwable) {
            null
        } ?: return fallback

        val swatch = palette.vibrantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.dominantSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.mutedSwatch
            ?: return fallback

        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(swatch.rgb, hsv)

        if (hsv[1] < 0.15f) {
            return fallback
        }

        // Saturation floor 0.72, value >= 0.72
        hsv[1] = hsv[1].coerceIn(0.72f, 1.0f)
        hsv[2] = hsv[2].coerceIn(0.72f, 0.95f)

        val adjustedRgb = android.graphics.Color.HSVToColor(hsv)
        return Color(adjustedRgb)
    }

    fun fromAppIconForMacro(bmp: Bitmap?, fallback: Color = Color(0xFF007AFF)): Color {
        if (bmp == null) return fallback

        val palette = try {
            Palette.from(bmp)
                .maximumColorCount(32)
                .generate()
        } catch (_: Throwable) {
            null
        } ?: return fallback

        val swatch = palette.dominantSwatch
            ?: palette.vibrantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.darkVibrantSwatch
            ?: return fallback

        val rgb = swatch.rgb
        val r = android.graphics.Color.red(rgb) / 255.0
        val g = android.graphics.Color.green(rgb) / 255.0
        val b = android.graphics.Color.blue(rgb) / 255.0

        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b

        return when {
            luminance < 0.12 -> Color(0xFFFFFFFF)
            luminance > 0.88 -> Color(0xFF121216)
            else -> {
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(rgb, hsv)
                hsv[1] = hsv[1].coerceIn(0.72f, 1.0f)
                hsv[2] = hsv[2].coerceIn(0.72f, 0.95f)
                Color(android.graphics.Color.HSVToColor(hsv))
            }
        }
    }
}
