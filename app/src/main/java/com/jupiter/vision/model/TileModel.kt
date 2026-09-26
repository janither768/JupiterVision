package com.jupiter.vision.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/**
 * colSpan / rowSpan measured in units (1 unit = 1/8 of screen width):
 *   1x1 unit  → micro (0.5x0.5 real)
 *   2x2 units → standard square (1x1 real)
 *   4x2 units → wide banner (2x1 real)
 *   2x4 units → tall vertical (1x2 real)
 *   4x4 units → macro / hero tile (2x2 real)
 */
data class TileModel(
    val id: String,
    val label: String,
    val packageName: String? = null,
    val isMicro: Boolean = false,
    val isMacro: Boolean = false,
    val colSpan: Int = 2,
    val rowSpan: Int = 2,
    val fixedCol: Int? = null,
    val fixedRow: Int? = null,
    val gridCol: Int = 0,
    val gridRow: Int = 0,
    val hasActivity: Boolean = false,
    val contentLines: List<String> = emptyList(),
    val accentColor: Color = Color(0xFF0A6CFF),
    val isInvisible: Boolean = false,
    val isSystem: Boolean = false,
    val role: String? = null,
    val iconBitmap: ImageBitmap? = null,
)

enum class TileMode { ICON, CONTENT }
