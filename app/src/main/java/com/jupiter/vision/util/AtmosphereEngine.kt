package com.jupiter.vision.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Random

/**
 * Pre-rendered global atmospheric asset generator.
 * Builds a looping sequence of alpha-enabled frames ONCE up-front.
 * Every tile windows a different region of the shared atmosphere.
 */
object AtmosphereEngine {

    const val NUM_FRAMES = 16
    private const val ASSET_WIDTH = 720
    private const val ASSET_HEIGHT = 1280

    private var cachedFrames: List<ImageBitmap>? = null

    suspend fun getOrGenerateFrames(): List<ImageBitmap> = withContext(Dispatchers.Default) {
        cachedFrames?.let { return@withContext it }

        val frames = mutableListOf<ImageBitmap>()
        val baseRandom = Random(101L)

        // Seed 40 particles with fixed random paths & depths
        data class Particle(
            val startX: Float,
            val startY: Float,
            val speedX: Float,
            val speedY: Float,
            val size: Float,
            val alpha: Int,
            val shape: Int // 0 = circle, 1 = diamond, 2 = line
        )

        val particles = (0 until 48).map {
            Particle(
                startX = baseRandom.nextFloat() * ASSET_WIDTH,
                startY = baseRandom.nextFloat() * ASSET_HEIGHT,
                speedX = (baseRandom.nextFloat() * 14f - 7f),
                speedY = (baseRandom.nextFloat() * 20f + 8f), // drifting downward/diagonal
                size = baseRandom.nextFloat() * 4.5f + 1.5f,
                alpha = baseRandom.nextInt(90) + 40,
                shape = baseRandom.nextInt(3)
            )
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        for (f in 0 until NUM_FRAMES) {
            val progress = f.toFloat() / NUM_FRAMES.toFloat()
            val bmp = Bitmap.createBitmap(ASSET_WIDTH, ASSET_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            for (p in particles) {
                val currentX = (p.startX + p.speedX * progress * ASSET_WIDTH * 0.15f + ASSET_WIDTH) % ASSET_WIDTH
                val currentY = (p.startY + p.speedY * progress * ASSET_HEIGHT * 0.15f + ASSET_HEIGHT) % ASSET_HEIGHT

                paint.color = AndroidColor.argb(p.alpha, 255, 255, 255)

                when (p.shape) {
                    0 -> {
                        canvas.drawCircle(currentX, currentY, p.size, paint)
                    }
                    1 -> {
                        val path = Path().apply {
                            moveTo(currentX, currentY - p.size * 1.5f)
                            lineTo(currentX + p.size * 1.5f, currentY)
                            lineTo(currentX, currentY + p.size * 1.5f)
                            lineTo(currentX - p.size * 1.5f, currentY)
                            close()
                        }
                        canvas.drawPath(path, paint)
                    }
                    2 -> {
                        canvas.drawLine(
                            currentX - p.size * 2f, currentY - p.size,
                            currentX + p.size * 2f, currentY + p.size,
                            paint
                        )
                    }
                }
            }

            frames.add(bmp.asImageBitmap())
        }

        cachedFrames = frames
        frames
    }

    @Composable
    fun rememberGlobalFrameIndex(): State<Int> {
        val frameIndex = remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(90) // ~11 fps animation loop — ultra light on CPU & RAM
                frameIndex.intValue = (frameIndex.intValue + 1) % NUM_FRAMES
            }
        }
        return frameIndex
    }
}
