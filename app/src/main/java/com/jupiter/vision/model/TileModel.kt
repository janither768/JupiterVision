package com.jupiter.vision.model

import androidx.compose.ui.graphics.Color

/**
 * colSpan / rowSpan measured in HALF-COLUMNS ("units"):
 *   1x1 tile  → 2x2 units
 *   0.5x0.5   → 1x1 unit
 *   2x2 tile  → 4x4 units
 */
data class TileModel(
    val id: String,
    val label: String,
    val packageName: String? = null,
    val isMicro: Boolean = false,
    val colSpan: Int = 2,
    val rowSpan: Int = 2,
    val hasActivity: Boolean = false,
    val contentLines: List<String> = emptyList(),
    val accentColor: Color = Color(0xFF13294B),
)

enum class TileMode { ICON, CONTENT }
