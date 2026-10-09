// WallpaperProvider.kt - In-memory provider and cache for system wallpaper bitmaps used in Band 3 reveal.
package com.jupiter.vision.data.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _wallpaper = MutableStateFlow<ImageBitmap?>(null)
    val wallpaper: StateFlow<ImageBitmap?> = _wallpaper.asStateFlow()

    init {
        refresh()
    }

    /**
     * Reads the current wallpaper from WallpaperManager and caches it as ImageBitmap.
     */
    fun refresh() {
        scope.launch {
            try {
                val wm = WallpaperManager.getInstance(context)
                val drawable = wm.drawable ?: wm.peekDrawable()
                val imageBitmap = drawableToImageBitmap(drawable)
                _wallpaper.value = imageBitmap
                Log.d(TAG, "Wallpaper loaded successfully (hasBitmap=${imageBitmap != null})")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load system wallpaper: ${e.message}")
            }
        }
    }

    private fun drawableToImageBitmap(drawable: Drawable?): ImageBitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
            return drawable.bitmap.asImageBitmap()
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceAtMost(1440) else 1080
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceAtMost(2560) else 1920
        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM creating wallpaper bitmap")
            null
        }
    }

    companion object {
        private const val TAG = "WallpaperProvider"
    }
}
