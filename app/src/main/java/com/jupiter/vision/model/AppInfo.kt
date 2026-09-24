package com.jupiter.vision.model

import android.graphics.Bitmap

data class AppInfo(
    val label: String,
    val packageName: String,
    val iconBitmap: Bitmap? = null,
    val isSystemApp: Boolean = false
)
