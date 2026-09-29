package com.jupiter.vision.model

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

/**
 * JUPITERVISION 2112.13 — Color Engine & Palette System
 * High-vibrancy, punchy, popping colors.
 */
object ColorEngine {

    val ROYAL_BLUE_PURPLE = Color(0xFF5B3BFF)
    val BLUE              = Color(0xFF0495FF)
    val GOLD_ORANGE       = Color(0xFFFFBA2A)
    val GREEN             = Color(0xFF6DCD00)
    val CRIMSON           = Color(0xFFE5093A)
    val PINK_RED          = Color(0xFFFF1475)
    val DARK_GREY         = Color(0xFF22242A)
    val MID_DARK_GREY     = Color(0xFF2E3038)
    val PURPLE            = Color(0xFF8A2BE2)
    val LIGHT_GREY        = Color(0xFFD1D5DB)

    // Palette list
    val PaletteList: List<Color> = listOf(
        ROYAL_BLUE_PURPLE,
        BLUE,
        GOLD_ORANGE,
        GREEN,
        CRIMSON,
        PINK_RED
    )

    val Default: List<Color> = PaletteList

    /**
     * Default System App Color Map (when Color Engine is OFF):
     * - phone, settings, camera, messages -> mid dark grey
     * - gallery -> purple
     * - music -> crimson red
     * - clock -> light grey
     * - flash -> dark grey
     */
    fun getSystemAppDefaultColor(roleOrId: String): Color {
        val key = roleOrId.lowercase()
        return when {
            key.contains("phone") || key.contains("dial") -> MID_DARK_GREY
            key.contains("set") || key.contains("setting") -> MID_DARK_GREY
            key.contains("cam") || key.contains("camera") -> MID_DARK_GREY
            key.contains("msg") || key.contains("messag") -> MID_DARK_GREY
            key.contains("gal") || key.contains("gallery") || key.contains("photo") || key.contains("image") -> PURPLE
            key.contains("music") || key.contains("mus") || key.contains("audio") -> CRIMSON
            key.contains("clock") || key.contains("time") -> LIGHT_GREY
            key.contains("flash") || key.contains("torch") || key.contains("fl") -> DARK_GREY
            key.contains("calc") -> MID_DARK_GREY
            key.contains("notes") || key.contains("nts") -> MID_DARK_GREY
            else -> MID_DARK_GREY
        }
    }

    /**
     * Helper to ensure wallpaper extracted colors are vibrant, never dimmed
     */
    fun undimmedColor(rgb: Int): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(rgb, hsv)
        // No dimmed colors: maintain strong saturation and high brightness
        hsv[1] = hsv[1].coerceIn(0.75f, 1.0f)
        hsv[2] = hsv[2].coerceIn(0.70f, 0.98f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    /**
     * Color Engine Rules for System Apps from Wallpaper (Color Engine ON):
     * Strictly rule-based matching using Palette swatches, with undimmed vibrant saturation & brightness.
     */
    fun getSystemAppWallpaperColor(
        roleOrId: String,
        bmp: Bitmap?,
        paletteList: List<Color>,
        index: Int
    ): Color {
        val key = roleOrId.lowercase()
        if (key.contains("flash") || key.contains("torch")) return DARK_GREY

        if (bmp == null) {
            return paletteList.getOrElse(index % paletteList.size) { BLUE }
        }

        val palette = try {
            Palette.from(bmp).maximumColorCount(32).generate()
        } catch (_: Throwable) {
            null
        }

        if (palette == null) {
            return paletteList.getOrElse(index % paletteList.size) { BLUE }
        }

        val vibrant = palette.vibrantSwatch?.rgb?.let { undimmedColor(it) }
        val lightVibrant = palette.lightVibrantSwatch?.rgb?.let { undimmedColor(it) }
        val darkVibrant = palette.darkVibrantSwatch?.rgb?.let { undimmedColor(it) }
        val dominant = palette.dominantSwatch?.rgb?.let { undimmedColor(it) }
        val muted = palette.mutedSwatch?.rgb?.let { undimmedColor(it) }

        return when {
            // Phone: Vibrant / Dominant
            key.contains("phone") || key.contains("dial") -> {
                dominant ?: vibrant ?: BLUE
            }
            // Messages: Light Vibrant / Vibrant
            key.contains("msg") || key.contains("messag") -> {
                lightVibrant ?: vibrant ?: BLUE
            }
            // Gallery: Vibrant / Light Vibrant
            key.contains("gal") || key.contains("gallery") || key.contains("photo") -> {
                vibrant ?: lightVibrant ?: PURPLE
            }
            // Camera: Dark Vibrant / Dominant
            key.contains("cam") || key.contains("camera") -> {
                darkVibrant ?: dominant ?: vibrant ?: ROYAL_BLUE_PURPLE
            }
            // Settings: Dominant / Muted
            key.contains("set") || key.contains("setting") -> {
                dominant ?: muted ?: darkVibrant ?: ROYAL_BLUE_PURPLE
            }
            // Music: Vibrant / Crimson
            key.contains("music") || key.contains("mus") -> {
                vibrant ?: darkVibrant ?: CRIMSON
            }
            // Clock: Light Vibrant / Dominant
            key.contains("clock") || key.contains("time") -> {
                lightVibrant ?: dominant ?: GOLD_ORANGE
            }
            // Calculator
            key.contains("calc") -> {
                dominant ?: vibrant ?: ROYAL_BLUE_PURPLE
            }
            // Notes
            key.contains("notes") || key.contains("nts") -> {
                lightVibrant ?: vibrant ?: GOLD_ORANGE
            }
            else -> {
                paletteList.getOrElse(index % paletteList.size) { BLUE }
            }
        }
    }

    /**
     * Wallpaper-derived colors (Color Engine ON):
     * Force saturation high (0.82f..1.0f) and brightness high (0.60f..0.96f) to make colors POP.
     */
    fun fromWallpaper(bmp: Bitmap?): List<Color> {
        if (bmp == null) return PaletteList

        val palette = try {
            Palette.from(bmp)
                .maximumColorCount(32)
                .generate()
        } catch (_: Throwable) {
            null
        } ?: return PaletteList

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

            // Force high saturation & high brightness to POP
            hsv[1] = hsv[1].coerceIn(0.82f, 1.0f)
            hsv[2] = hsv[2].coerceIn(0.60f, 0.96f)

            val adjustedRgb = android.graphics.Color.HSVToColor(hsv)
            usableColors.add(Color(adjustedRgb))
        }

        val distinctColors = usableColors.distinct()
        return when {
            distinctColors.size >= 6 -> distinctColors.take(6)
            distinctColors.isNotEmpty() -> {
                val combined = distinctColors.toMutableList()
                for (c in PaletteList) {
                    if (combined.size >= 6) break
                    if (c !in combined) combined.add(c)
                }
                combined
            }
            else -> PaletteList
        }
    }

    /**
     * Third-party app icon-derived color logic (2112.13):
     * Make colors POP with vibrant saturation (0.82f..1.0f) and brightness (0.60f..0.96f).
     */
    fun fromAppIcon(bmp: Bitmap?, fallback: Color = BLUE): Color {
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

        if (hsv[1] < 0.08f && swatch.population < 10) {
            return fallback
        }

        // Boost saturation & brightness to make colors pop
        hsv[1] = hsv[1].coerceIn(0.82f, 1.0f)
        hsv[2] = hsv[2].coerceIn(0.60f, 0.96f)

        val adjustedRgb = android.graphics.Color.HSVToColor(hsv)
        return Color(adjustedRgb)
    }

    fun fromAppIconForMacro(bmp: Bitmap?, fallback: Color = BLUE): Color {
        return fromAppIcon(bmp, fallback)
    }
}
