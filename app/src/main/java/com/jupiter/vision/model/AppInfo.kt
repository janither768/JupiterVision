package com.jupiter.vision.model

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap

data class AppInfo(
    val label: String,
    val packageName: String,
    val iconBitmap: Bitmap? = null,
    val isSystemApp: Boolean = false,
    val iconImageBitmap: ImageBitmap? = null
)
