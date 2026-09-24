package com.jupiter.vision.model

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

/**
 * Extracts exactly 3 colors from the wallpaper.
 * Rejects bright colors. Falls back to 3 cobalt-blue shades.
 */
object ColorEngine {

    val Default: List<Color> = listOf(
        Color(0xFF13294B),  // deep cobalt
        Color(0xFF1B3A6B),  // mid cobalt
        Color(0xFF23508F),  // cobalt
    )

    fun fromWallpaper(bmp: Bitmap?): List<Color> {
        if (bmp == null) return Default

        val palette = Palette.from(bmp)
            .maximumColorCount(24)
            .generate()

        val raw = listOfNotNull(
            palette.darkVibrantSwatch,
            palette.darkMutedSwatch,
            palette.mutedSwatch,
            palette.vibrantSwatch,
        )
            .filter { sw -> luminance(sw.rgb) < 0.55f }   // reject brights
            .map { Color(it.rgb) }
            .distinct()

        return when {
            raw.size >= 3 -> raw.take(3)
            raw.isNotEmpty() -> (raw + Default).distinct().take(3)
            else -> Default
        }
    }

    private fun luminance(c: Int): Float {
        val r = (c shr 16 and 0xFF) / 255f
        val g = (c shr 8 and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
}
