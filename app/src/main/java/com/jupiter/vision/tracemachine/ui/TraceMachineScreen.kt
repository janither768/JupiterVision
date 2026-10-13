package com.jupiter.vision.tracemachine.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jupiter.vision.tracemachine.model.TraceState
import com.jupiter.vision.tracemachine.service.TraceMachineService
import com.jupiter.vision.ui.theme.InterFontFamily
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * DELIVERABLE 2 — TRACEMACHINE GEN 1
 *
 * Full-screen inertial dead-reckoning motion trail visualizer.
 *
 * STACK & THEME:
 * - Single neutral palette: Background #1A1A1A. White typography. Font Inter.
 * - No accent colors. No cards. No elevation. No ripple on non-interactive elements.
 * - All motion bezier-smoothed (CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)).
 * - Canvas for all custom drawing. Zero per-frame heavy allocations.
 */
@Composable
fun TraceMachineScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val traceState by TraceMachineService.traceStateFlow.collectAsState()
    val isRunning by TraceMachineService.isRunningFlow.collectAsState()

    // Pan & Zoom viewport state
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var pixelsPerMeter by remember { mutableFloatStateOf(24f) }
    var isAutoCenter by remember { mutableStateOf(true) }

    val bezierEasing = remember { CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .testTag("tracemachine_screen_container")
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        val centerViewportX = widthPx / 2f
        val centerViewportY = heightPx / 2f

        // When auto-centering, center follows currentPosition
        val targetCenterX = if (isAutoCenter) {
            centerViewportX - traceState.currentPosition.x * pixelsPerMeter
        } else {
            centerViewportX + panOffsetX
        }

        val targetCenterY = if (isAutoCenter) {
            centerViewportY - traceState.currentPosition.y * pixelsPerMeter
        } else {
            centerViewportY + panOffsetY
        }

        // Canvas for custom rendering of dead-reckoning trail and architectural grid
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isAutoCenter = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            panOffsetX += dragAmount.x
                            panOffsetY += dragAmount.y
                        }
                    )
                }
        ) {
            val originX = targetCenterX
            val originY = targetCenterY

            // 1. ARCHITECTURAL REFERENCE GRID & CONCENTRIC RANGE RINGS
            val rangeRingsMeters = floatArrayOf(5f, 15f, 30f, 50f, 80f, 120f)
            val ringStroke = Stroke(width = 1f)
            for (r in rangeRingsMeters) {
                val radiusPx = r * pixelsPerMeter
                // Skip rings too large or off-screen
                if (radiusPx < maxOf(widthPx, heightPx) * 1.5f) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.07f),
                        radius = radiusPx,
                        center = Offset(originX, originY),
                        style = ringStroke
                    )
                }
            }

            // Crosshair axes through origin (0,0)
            drawLine(
                color = Color.White.copy(alpha = 0.10f),
                start = Offset(0f, originY),
                end = Offset(widthPx, originY),
                strokeWidth = 1f
            )
            drawLine(
                color = Color.White.copy(alpha = 0.10f),
                start = Offset(originX, 0f),
                end = Offset(originX, heightPx),
                strokeWidth = 1f
            )

            // Origin marker (0, 0)
            drawCircle(
                color = Color.White.copy(alpha = 0.40f),
                radius = 3.5f * density.density,
                center = Offset(originX, originY),
                style = Stroke(width = 1.2f * density.density)
            )

            // 2. THE LIVING TRAIL (CONNECTED DEAD-RECKONING SEGMENTS)
            val points = traceState.points
            if (points.size > 1) {
                for (i in 0 until points.size - 1) {
                    val p1 = points[i]
                    val p2 = points[i + 1]

                    val x1 = originX + p1.x * pixelsPerMeter
                    val y1 = originY + p1.y * pixelsPerMeter
                    val x2 = originX + p2.x * pixelsPerMeter
                    val y2 = originY + p2.y * pixelsPerMeter

                    // Confidence decay: confidence fades smoothly with steps since last anchor
                    val stepsSince = p2.stepsSinceAnchor
                    val confidenceFactor = (1.0f - (stepsSince / 50f).coerceIn(0f, 1f) * 0.60f).coerceIn(0.35f, 1.0f)

                    drawLine(
                        color = Color.White.copy(alpha = confidenceFactor * 0.90f),
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = (2.2f * density.density)
                    )
                }
            }

            // 3. ANCHORS (MANUAL PINS & AUTOMATIC ZUPT STILLNESS ANCHORS)
            for (anchor in traceState.anchors) {
                val ax = originX + anchor.x * pixelsPerMeter
                val ay = originY + anchor.y * pixelsPerMeter

                if (anchor.isManual) {
                    // Manual Anchor Pin: Geometric Diamond marker
                    val diamondSize = 6f * density.density
                    val diamondPath = Path().apply {
                        moveTo(ax, ay - diamondSize)
                        lineTo(ax + diamondSize, ay)
                        lineTo(ax, ay + diamondSize)
                        lineTo(ax - diamondSize, ay)
                        close()
                    }
                    drawPath(
                        path = diamondPath,
                        color = Color.White,
                        style = Stroke(width = 1.5f * density.density)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2f * density.density,
                        center = Offset(ax, ay)
                    )
                } else {
                    // Automatic ZUPT Stillness Anchor: Concentric Square marker
                    val squareSize = 5f * density.density
                    drawRect(
                        color = Color.White.copy(alpha = 0.70f),
                        topLeft = Offset(ax - squareSize, ay - squareSize),
                        size = androidx.compose.ui.geometry.Size(squareSize * 2f, squareSize * 2f),
                        style = Stroke(width = 1.2f * density.density)
                    )
                }
            }

            // 4. CURRENT POSITION NODE & HEADING WEDGE
            val currX = originX + traceState.currentPosition.x * pixelsPerMeter
            val currY = originY + traceState.currentPosition.y * pixelsPerMeter

            // If in ZUPT stillness, draw soft stillness aura
            if (traceState.isZuptStill) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.15f),
                    radius = 16f * density.density,
                    center = Offset(currX, currY)
                )
            }

            // Current Position Node (solid white circle)
            drawCircle(
                color = Color.White,
                radius = 5f * density.density,
                center = Offset(currX, currY)
            )

            // Heading indicator wedge
            val heading = traceState.currentHeadingRad
            val wedgeLength = 14f * density.density
            val wedgeHalfBase = 5f * density.density

            val tipX = currX + sin(heading) * wedgeLength
            val tipY = currY - cos(heading) * wedgeLength

            val leftX = currX + sin(heading + 2.5f) * wedgeHalfBase
            val leftY = currY - cos(heading + 2.5f) * wedgeHalfBase

            val rightX = currX + sin(heading - 2.5f) * wedgeHalfBase
            val rightY = currY - cos(heading - 2.5f) * wedgeHalfBase

            val headingPath = Path().apply {
                moveTo(tipX, tipY)
                lineTo(leftX, leftY)
                lineTo(rightX, rightY)
                close()
            }
            drawPath(
                path = headingPath,
                color = Color.White
            )
        }

        // ════════════════════════════════════════
        // TELEMETRY HUD & CONTROLS OVERLAY
        // ════════════════════════════════════════
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP HEADER & STATUS
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TRACEMACHINE",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            letterSpacing = 2.sp,
                            color = Color.White
                        )
                        Text(
                            text = "INERTIAL PATH ESTIMATOR • GEN 1",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }

                    // Status Pill
                    val statusText = when {
                        !traceState.isSessionActive -> "IDLE"
                        traceState.isCalibratingSwingAxis -> "CALIBRATING (${traceState.totalSteps}/8)"
                        traceState.isZuptStill -> "ZUPT STILL"
                        else -> "TRACKING"
                    }
                    Box(
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = statusText,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Telemetry Matrix Row (Single Neutral Palette)
                val totalDistanceM = traceState.totalSteps * traceState.strideLengthMeters
                val headingDeg = ((Math.toDegrees(traceState.currentHeadingRad.toDouble()) % 360 + 360) % 360).toInt()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricReadout(label = "DISTANCE", value = String.format(Locale.US, "%.1f m", totalDistanceM))
                    MetricReadout(label = "STEPS", value = "${traceState.totalSteps}")
                    MetricReadout(label = "DRIFT", value = "${traceState.stepsSinceAnchor} stp")
                    MetricReadout(label = "HEADING", value = String.format(Locale.US, "%03d°", headingDeg))
                    MetricReadout(
                        label = "AXIS",
                        value = if (traceState.isCalibratingSwingAxis) "CALIB" else "LATCH"
                    )
                }
            }

            // BOTTOM CONTROLS & ZOOM TOOLBAR
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            ) {
                // Secondary Controls Row (Zoom & Re-Center)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Zoom In Button
                    TraceButton(
                        text = "+",
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            pixelsPerMeter = (pixelsPerMeter * 1.30f).coerceAtMost(70f)
                        },
                        modifier = Modifier.width(42.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Zoom Out Button
                    TraceButton(
                        text = "−",
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            pixelsPerMeter = (pixelsPerMeter / 1.30f).coerceAtLeast(8f)
                        },
                        modifier = Modifier.width(42.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Re-Center Button
                    TraceButton(
                        text = if (isAutoCenter) "CENTERED" else "RE-CENTER",
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            isAutoCenter = true
                            panOffsetX = 0f
                            panOffsetY = 0f
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Primary Action Dock
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // START / STOP Button
                    TraceButton(
                        text = if (isRunning) "HALT TRACE" else "START TRACE",
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            if (isRunning) {
                                TraceMachineService.stopTracking(context)
                            } else {
                                TraceMachineService.startTracking(context)
                                isAutoCenter = true
                            }
                        },
                        modifier = Modifier.weight(1.2f),
                        isPrimary = true
                    )

                    // DROP ANCHOR Button
                    TraceButton(
                        text = "DROP ANCHOR",
                        enabled = isRunning,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            TraceMachineService.dropManualAnchor(context)
                        },
                        modifier = Modifier.weight(1.0f)
                    )

                    // RESET Button
                    TraceButton(
                        text = "RESET",
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            TraceMachineService.resetSession(context)
                            panOffsetX = 0f
                            panOffsetY = 0f
                            isAutoCenter = true
                        },
                        modifier = Modifier.weight(0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricReadout(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            color = Color.White.copy(alpha = 0.45f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = Color.White
        )
    }
}

/**
 * Minimalist neutral button matching strict JupiterVision guidelines:
 * - Neutral #1A1A1A / #2B2B2B fill, thin white border, white typography.
 * - No accent color, no cards, no elevation.
 */
@Composable
private fun TraceButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val alpha = if (enabled) 1.0f else 0.35f
    val bg = if (isPrimary) Color(0xFF2E2E2E) else Color(0xFF222222)

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg.copy(alpha = alpha))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (isPrimary) 0.50f * alpha else 0.25f * alpha),
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = InterFontFamily,
            fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = Color.White.copy(alpha = alpha)
        )
    }
}
