package com.jupiter.vision.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.jupiter.vision.engine.VisionEngine
import com.jupiter.vision.model.AppInfo
import kotlin.math.cos
import kotlin.math.sin

/**
 * VISION HOME RENDERER
 * Draws the full-page Vision Home inside the single hardware-accelerated Canvas.
 * Divided into:
 * 1. Top Bar (Time, Date, Weather, Battery, Engine Status Indicator)
 * 2. Center Visual (Cinematic Viewport responding to 9 Vision Engine states)
 * 3. Contextual App Row (5-6 large glassy 64x64dp icons, 0.dp corners)
 * 4. Bottom-Left Status Text (Single line in small muted Inter, no punctuation)
 */
class VisionHomePaints(val densityScale: Float, val typeface: Typeface) {
    val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 28f * densityScale
        this.typeface = Typeface.create(this@VisionHomePaints.typeface, Typeface.BOLD)
        letterSpacing = 0.05f
    }

    val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#9CA3AF")
        textSize = 11f * densityScale
        this.typeface = this@VisionHomePaints.typeface
        letterSpacing = 0.08f
    }

    val weatherTempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 15f * densityScale
        this.typeface = Typeface.create(this@VisionHomePaints.typeface, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    val batteryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 13f * densityScale
        this.typeface = Typeface.create(this@VisionHomePaints.typeface, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }

    val engineStatusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#9CA3AF")
        textSize = 9.5f * densityScale
        this.typeface = Typeface.create(this@VisionHomePaints.typeface, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
        letterSpacing = 0.12f
    }

    val bottomStatusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#80FFFFFF")
        textSize = 11f * densityScale
        this.typeface = Typeface.create(this@VisionHomePaints.typeface, Typeface.BOLD)
        letterSpacing = 0.14f
    }

    val appLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 10f * densityScale
        this.typeface = this@VisionHomePaints.typeface
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.04f
    }

    val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    val timerLargePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 46f * densityScale
        this.typeface = Typeface.create(this@VisionHomePaints.typeface, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.08f
    }

    val etaLargePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 22f * densityScale
        this.typeface = Typeface.create(this@VisionHomePaints.typeface, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.12f
    }
}

data class VisionHomeAppHitBox(
    val app: AppInfo,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

object VisionHomeRenderer {

    fun DrawScope.drawVisionHome(
        offsetY: Float,
        canvasW: Float,
        canvasH: Float,
        timeString: String,
        dateString: String,
        batteryPercent: Int,
        weatherTemp: String,
        snapshot: VisionEngine.EngineSnapshot,
        fluidTimeSec: Float,
        paints: VisionHomePaints,
        outHitBoxes: MutableList<VisionHomeAppHitBox>
    ) {
        outHitBoxes.clear()

        val density = paints.densityScale
        val topBarH = 74f * density
        val appRowH = if (snapshot.isAppRowVisible) 100f * density else 0f
        val bottomPadding = 36f * density

        // ==========================================
        // 1. TOP BAR (~64dp - 74dp)
        // ==========================================
        val topBarY = offsetY + 18f * density
        val horizontalMargin = 20f * density

        // 1a. Left: Time & Date
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.drawText(timeString.ifEmpty { "10:42 PM" }, horizontalMargin, topBarY + 24f * density, paints.timePaint)
            native.drawText(dateString.ifEmpty { "WEDNESDAY, SEP 30" }, horizontalMargin, topBarY + 42f * density, paints.datePaint)
        }

        // 1b. Center: Weather (Condition Icon + Temperature)
        val centerX = canvasW * 0.5f
        val weatherY = topBarY + 22f * density
        drawWeatherCondition(centerX - 26f * density, weatherY - 6f * density, 16f * density)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(weatherTemp.ifEmpty { "22°C" }, centerX + 12f * density, weatherY, paints.weatherTempPaint)
        }

        // 1c. Right: Battery percentage + icon & Engine Status Indicator beneath it
        val rightX = canvasW - horizontalMargin
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.drawText("$batteryPercent%", rightX - 28f * density, topBarY + 22f * density, paints.batteryTextPaint)
            native.drawText(snapshot.topBarStatus, rightX, topBarY + 42f * density, paints.engineStatusPaint)
        }
        drawBatteryIcon(rightX - 22f * density, topBarY + 11f * density, 22f * density, 12f * density, batteryPercent)

        // ==========================================
        // 2. CENTER VISUAL (Cinematic Viewport)
        // ==========================================
        val centerTop = topBarY + topBarH + 6f * density
        val centerBottom = offsetY + canvasH - appRowH - bottomPadding - 16f * density
        val centerH = (centerBottom - centerTop).coerceAtLeast(80f * density)

        drawCenterVisual(
            left = horizontalMargin,
            top = centerTop,
            width = canvasW - horizontalMargin * 2f,
            height = centerH,
            state = snapshot.state,
            snapshot = snapshot,
            fluidTimeSec = fluidTimeSec,
            paints = paints
        )

        // ==========================================
        // 3. CONTEXTUAL APP ROW (5-6 glassy icons, 64x64dp, 0.dp corners)
        // ==========================================
        if (snapshot.isAppRowVisible && snapshot.contextualApps.isNotEmpty()) {
            val iconSize = 58f * density
            val iconSpacing = 10f * density
            val appCount = snapshot.contextualApps.size
            val totalRowW = appCount * iconSize + (appCount - 1) * iconSpacing
            val startX = (canvasW - totalRowW) * 0.5f
            val rowY = offsetY + canvasH - bottomPadding - 80f * density

            snapshot.contextualApps.forEachIndexed { index, app ->
                val ax = startX + index * (iconSize + iconSpacing)
                val ay = rowY

                // Store hit box for direct tap launch
                outHitBoxes.add(
                    VisionHomeAppHitBox(
                        app = app,
                        left = ax,
                        top = ay,
                        right = ax + iconSize,
                        bottom = ay + iconSize + 16f * density
                    )
                )

                // Translucent glassy tile background (0.dp corners)
                drawRect(
                    color = Color(0x33FFFFFF), // Translucent glass fill
                    topLeft = Offset(ax, ay),
                    size = Size(iconSize, iconSize)
                )
                // Crisp 1px specular top/left highlight line
                drawLine(
                    color = Color(0x4DFFFFFF),
                    start = Offset(ax, ay),
                    end = Offset(ax + iconSize, ay),
                    strokeWidth = 1f
                )

                // Draw app icon inside 64x64 glass tile
                val bmp = app.iconBitmap
                val pad = 12f * density
                val innerSize = iconSize - pad * 2f
                if (bmp != null && !bmp.isRecycled) {
                    val dst = Rect(
                        (ax + pad).toInt(),
                        (ay + pad).toInt(),
                        (ax + pad + innerSize).toInt(),
                        (ay + pad + innerSize).toInt()
                    )
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawBitmap(bmp, null, dst, paints.bitmapPaint)
                    }
                }

                // App label beneath icon (small Inter)
                drawIntoCanvas { canvas ->
                    val label = if (app.label.length > 8) app.label.take(7) + "…" else app.label
                    canvas.nativeCanvas.drawText(
                        label,
                        ax + iconSize * 0.5f,
                        ay + iconSize + 14f * density,
                        paints.appLabelPaint
                    )
                }
            }
        }

        // ==========================================
        // 4. BOTTOM-LEFT STATUS TEXT
        // ==========================================
        val statusX = horizontalMargin
        val statusY = offsetY + canvasH - 16f * density
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(
                snapshot.statusText,
                statusX,
                statusY,
                paints.bottomStatusPaint
            )
        }
    }

    private fun DrawScope.drawCenterVisual(
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        state: VisionEngine.VisionState,
        snapshot: VisionEngine.EngineSnapshot,
        fluidTimeSec: Float,
        paints: VisionHomePaints
    ) {
        val cx = left + width * 0.5f
        val cy = top + height * 0.5f

        when (state) {
            VisionEngine.VisionState.IDLE -> {
                // Dark grey field (~#1A1A1A) with Ghost Echo radial pulse on 4-second cycle
                drawRect(
                    color = Color(0xFF1A1A1A),
                    topLeft = Offset(left, top),
                    size = Size(width, height)
                )
                // 4-second cycle Ghost Echo pulse
                val cycleTime = (fluidTimeSec % 4.0f) / 4.0f
                val pulseRadius = (width * 0.45f) * cycleTime
                val pulseAlpha = (0.05f + 0.10f * sin(cycleTime * Math.PI.toFloat())).coerceIn(0.04f, 0.15f)
                drawCircle(
                    color = Color.White.copy(alpha = pulseAlpha),
                    radius = pulseRadius,
                    center = Offset(cx, cy)
                )
                // Subtle center orb core
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f),
                    radius = 16f * paints.densityScale,
                    center = Offset(cx, cy)
                )
            }

            VisionEngine.VisionState.TRANSIT -> {
                // Tactical map view: Near-black, tactical route vector line, pulsating destination, overlaid ETA
                drawRect(
                    color = Color(0xFF0F1014),
                    topLeft = Offset(left, top),
                    size = Size(width, height)
                )
                // Tactical route vector
                val p1 = Offset(left + width * 0.22f, cy + height * 0.28f)
                val p2 = Offset(left + width * 0.45f, cy - height * 0.10f)
                val p3 = Offset(left + width * 0.78f, cy - height * 0.22f)

                drawLine(color = Color(0x330495FF), start = p1, end = p2, strokeWidth = 3f)
                drawLine(color = Color(0x330495FF), start = p2, end = p3, strokeWidth = 3f)
                drawLine(color = Color(0xFF0495FF), start = p1, end = p2, strokeWidth = 1.5f)
                drawLine(color = Color(0xFF0495FF), start = p2, end = p3, strokeWidth = 1.5f)

                // Pulse destination node
                val nodePulse = (sin(fluidTimeSec * 3f) * 0.5f + 0.5f)
                drawCircle(color = Color(0xFF0495FF).copy(alpha = 0.3f * nodePulse), radius = 18f * paints.densityScale, center = p3)
                drawCircle(color = Color(0xFF0495FF), radius = 5f * paints.densityScale, center = p3)
                drawCircle(color = Color.White, radius = 4f * paints.densityScale, center = p1)

                // Overlaid ETA
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText("ETA ${snapshot.transitEtaMinutes} MIN", cx, cy + 30f * paints.densityScale, paints.etaLargePaint)
                }
            }

            VisionEngine.VisionState.SETTLE -> {
                // Settle: Map zoomed on arrival venue, 5-minute countdown timer
                drawRect(
                    color = Color(0xFF121318),
                    topLeft = Offset(left, top),
                    size = Size(width, height)
                )
                // Concentric arrival radar target rings
                val rPulse = ((fluidTimeSec * 0.8f) % 1.0f)
                drawCircle(color = Color(0x335B3BFF), radius = 60f * paints.densityScale * (1f + rPulse * 0.3f), center = Offset(cx, cy))
                drawCircle(color = Color(0x555B3BFF), radius = 40f * paints.densityScale, center = Offset(cx, cy))

                // 5-minute Countdown Timer (mm:ss)
                val mins = snapshot.settleSecondsRemaining / 60
                val secs = snapshot.settleSecondsRemaining % 60
                val timeStr = String.format("%02d:%02d", mins, secs)
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(timeStr, cx, cy + 14f * paints.densityScale, paints.timerLargePaint)
                }
            }

            VisionEngine.VisionState.WORK -> {
                // Dark grid texture: Subtle, muted, industrial cybernetic lines
                drawRect(
                    color = Color(0xFF16171B),
                    topLeft = Offset(left, top),
                    size = Size(width, height)
                )
                val gridStep = 24f * paints.densityScale
                var gx = left
                while (gx <= left + width) {
                    drawLine(color = Color(0x1AFFFFFF), start = Offset(gx, top), end = Offset(gx, top + height), strokeWidth = 1f)
                    gx += gridStep
                }
                var gy = top
                while (gy <= top + height) {
                    drawLine(color = Color(0x1AFFFFFF), start = Offset(left, gy), end = Offset(left + width, gy), strokeWidth = 1f)
                    gy += gridStep
                }
            }

            VisionEngine.VisionState.HOME -> {
                // Warm grey field with soft gradient, no text
                val warmBrush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF23211F), Color(0xFF171615)),
                    startY = top,
                    endY = top + height
                )
                drawRect(
                    brush = warmBrush,
                    topLeft = Offset(left, top),
                    size = Size(width, height)
                )
            }

            VisionEngine.VisionState.GYM -> {
                // Athletic focused grid with tighter spacing & dynamic energy lines
                drawRect(
                    color = Color(0xFF151917),
                    topLeft = Offset(left, top),
                    size = Size(width, height)
                )
                val gridStep = 16f * paints.densityScale
                var gx = left
                while (gx <= left + width) {
                    drawLine(color = Color(0x126DCD00), start = Offset(gx, top), end = Offset(gx, top + height), strokeWidth = 1f)
                    gx += gridStep
                }
                // Energy bar line
                val pulseX = left + ((fluidTimeSec * 0.6f) % 1.0f) * width
                drawLine(color = Color(0xFF6DCD00).copy(alpha = 0.55f), start = Offset(pulseX, top), end = Offset(pulseX, top + height), strokeWidth = 2f)
            }

            VisionEngine.VisionState.FOCUS -> {
                // Neutral dark grid, minimal, no texture, zero distractions
                drawRect(
                    color = Color(0xFF121214),
                    topLeft = Offset(left, top),
                    size = Size(width, height)
                )
                val gridStep = 32f * paints.densityScale
                var gx = left
                while (gx <= left + width) {
                    drawLine(color = Color(0x0EFFFFFF), start = Offset(gx, top), end = Offset(gx, top + height), strokeWidth = 1f)
                    gx += gridStep
                }
            }

            VisionEngine.VisionState.NIGHT -> {
                // Pure black, empty, dim
                drawRect(
                    color = Color(0xFF000000),
                    topLeft = Offset(left, top),
                    size = Size(width, height)
                )
            }

            VisionEngine.VisionState.LEARNING -> {
                // Dark grey with faster radar pulse on a 2-second cycle
                drawRect(
                    color = Color(0xFF18181A),
                    topLeft = Offset(left, top),
                    size = Size(width, height)
                )
                val cycle2s = (fluidTimeSec % 2.0f) / 2.0f
                val sweepRadius = (width * 0.42f) * cycle2s
                drawCircle(
                    color = Color(0xFF0495FF).copy(alpha = 0.22f * (1f - cycle2s)),
                    radius = sweepRadius,
                    center = Offset(cx, cy)
                )
                // Rotating radar beam
                val angle = fluidTimeSec * 3.14159f
                val beamEnd = Offset(cx + cos(angle) * (width * 0.38f), cy + sin(angle) * (width * 0.38f))
                drawLine(
                    color = Color(0xFF0495FF).copy(alpha = 0.45f),
                    start = Offset(cx, cy),
                    end = beamEnd,
                    strokeWidth = 1.5f
                )
                drawCircle(
                    color = Color(0xFF0495FF),
                    radius = 4f * paints.densityScale,
                    center = Offset(cx, cy)
                )
            }
        }
    }

    private fun DrawScope.drawWeatherCondition(x: Float, y: Float, sizePx: Float) {
        // Crisp sun icon representation
        drawCircle(
            color = Color(0xFFFFBA2A),
            radius = sizePx * 0.38f,
            center = Offset(x, y)
        )
        // Ray lines
        for (i in 0 until 8) {
            val angle = (i * Math.PI / 4).toFloat()
            val r1 = sizePx * 0.48f
            val r2 = sizePx * 0.68f
            drawLine(
                color = Color(0xFFFFBA2A).copy(alpha = 0.85f),
                start = Offset(x + cos(angle) * r1, y + sin(angle) * r1),
                end = Offset(x + cos(angle) * r2, y + sin(angle) * r2),
                strokeWidth = 1.5f
            )
        }
    }

    private fun DrawScope.drawBatteryIcon(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        percent: Int
    ) {
        // Battery outline
        drawLine(Color.White, Offset(x, y), Offset(x + w, y), 1.5f)
        drawLine(Color.White, Offset(x, y + h), Offset(x + w, y + h), 1.5f)
        drawLine(Color.White, Offset(x, y), Offset(x, y + h), 1.5f)
        drawLine(Color.White, Offset(x + w, y), Offset(x + w, y + h), 1.5f)

        // Terminal nipple
        drawLine(Color.White, Offset(x + w + 2f, y + h * 0.25f), Offset(x + w + 2f, y + h * 0.75f), 1.5f)

        // Fill level
        val fillW = (w - 4f) * (percent / 100f).coerceIn(0f, 1f)
        if (fillW > 0f) {
            val fillColor = if (percent <= 20) Color(0xFFFF3B30) else Color(0xFF6DCD00)
            drawRect(
                color = fillColor,
                topLeft = Offset(x + 2f, y + 2f),
                size = Size(fillW, h - 4f)
            )
        }
    }
}
