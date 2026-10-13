package com.jupiter.vision.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jupiter.vision.ui.theme.InterFontFamily
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * DELIVERABLE 1 — MODULE CHANGER PLATE
 *
 * A radial dial that lives as a sliver on the left edge of the Module Page.
 *
 * REST STATE:
 * - Small silver plate (rounded sliver) vertically centered on the left edge, partially off-screen.
 * - No label, no text, no indicator of any kind on it. Sits over module page content as an overlay.
 *
 * GEOMETRY:
 * - Arc center is off-screen to the LEFT of the viewport.
 * - Radius = 0.90 * screenWidth. Arc center Y = screenHeight / 2.
 * - Arc bulges to the right. Horizontal cross point (angle 0) at y = screenHeight / 2, x = arcCenterX + radius.
 * - Arc span: 140 degrees total (-70 to +70 deg). Padding at both ends.
 * - Module names kept upright (never rotated), close to horizontal axis cross point.
 * - Names fit up to 7 items across 140 degrees without redesign. Currently: "DoTPro", "TraceMachine".
 *
 * GESTURE — ONE CONTINUOUS MOTION:
 * - Press plate: CLOCK_TICK haptic.
 * - Drag right: translates right and unfurls. Dims module page behind (0 -> 0.55 scrim).
 * - Angular selection: angle = atan2(fingerY - arcCenterY, fingerX - arcCenterX), clamped.
 * - Nearest name is selected.
 * - Release on a name: CONTEXT_CLICK haptic, switches module, retracts immediately.
 * - Release off name: retracts immediately, no action, no haptic.
 *
 * SELECTED STATE:
 * - Unselected: white, ~55% alpha.
 * - Selected: opaque white, bezier scale 1.15x, soft white glow behind it.
 * - All motion bezier-smoothed (CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)).
 */
@Composable
fun ModuleChangerPlate(
    activeModule: String,
    onModuleSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    modules: List<String> = listOf("DoTPro", "TraceMachine")
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // Reveal progress: 0f (rest / retracted) to 1f (fully unfurled)
    val revealAnim = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Currently selected index during active gesture
    var selectedIndex by remember { mutableIntStateOf(modules.indexOf(activeModule).coerceAtLeast(0)) }

    val bezierEasing = remember { CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("module_changer_plate_container")
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        // Arc Geometry
        val radius = widthPx * 0.90f
        val arcCenterY = heightPx / 2f
        val maxUnfurlPx = widthPx * 0.42f
        val plateProtrusionPx = with(density) { 7.dp.toPx() }
        val plateWidthPx = with(density) { 16.dp.toPx() }
        val plateHeightPx = with(density) { 78.dp.toPx() }

        // In rest state, the arc boundary at angle 0 sits right at the silver plate edge
        val restArcCenterX = plateProtrusionPx - radius

        val currentReveal = revealAnim.value
        val currentArcCenterX = restArcCenterX + currentReveal * maxUnfurlPx

        // Arc span is 140 deg (-70 to +70)
        // With 14 deg padding on each side, active slot span is 112 deg.
        // For up to 7 items, slot step is 112 / 6 = 18.67 deg.
        val slotStepDeg = 18.67f
        val itemAnglesDeg = remember(modules.size) {
            val count = modules.size
            if (count == 1) {
                listOf(0f)
            } else {
                // Centered symmetrically around 0 deg (horizontal axis)
                val totalSpread = (count - 1) * slotStepDeg
                val startAngle = -totalSpread / 2f
                List(count) { i -> startAngle + i * slotStepDeg }
            }
        }

        // Full-screen dimming scrim behind arc overlay (alpha: 0 -> 0.55)
        if (currentReveal > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = currentReveal * 0.55f))
            )
        }

        // Canvas for Radial Dial Arc & Glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (currentReveal > 0.05f) {
                val arcStroke = Stroke(width = 1.25f * density.density)
                val glowAlpha = (currentReveal * 0.45f).coerceIn(0f, 0.45f)

                // 1. Draw subtle architectural dial arc (-70 deg to +70 deg)
                val startAngleRad = (-70.0 * PI / 180.0).toFloat()
                val endAngleRad = (70.0 * PI / 180.0).toFloat()
                val arcPath = Path()
                val steps = 60
                for (s in 0..steps) {
                    val angle = startAngleRad + s * (endAngleRad - startAngleRad) / steps
                    val px = currentArcCenterX + radius * cos(angle)
                    val py = arcCenterY + radius * sin(angle)
                    if (s == 0) arcPath.moveTo(px, py) else arcPath.lineTo(px, py)
                }
                drawPath(
                    path = arcPath,
                    color = Color.White.copy(alpha = currentReveal * 0.25f),
                    style = arcStroke
                )

                // 2. Draw soft white glow behind the selected module name
                val selectedAngleRad = (itemAnglesDeg.getOrElse(selectedIndex) { 0f } * PI / 180.0).toFloat()
                val selX = currentArcCenterX + radius * cos(selectedAngleRad)
                val selY = arcCenterY + radius * sin(selectedAngleRad)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = glowAlpha),
                            Color.White.copy(alpha = glowAlpha * 0.35f),
                            Color.Transparent
                        ),
                        center = Offset(selX + 40f * density.density, selY),
                        radius = 65f * density.density
                    ),
                    center = Offset(selX + 40f * density.density, selY),
                    radius = 65f * density.density
                )
            }
        }

        // Module Names rendered upright along the arc perimeter
        if (currentReveal > 0.20f) {
            val namesAlpha = ((currentReveal - 0.20f) / 0.35f).coerceIn(0f, 1f)

            modules.forEachIndexed { index, name ->
                val angleDeg = itemAnglesDeg.getOrElse(index) { 0f }
                val angleRad = (angleDeg * PI / 180.0).toFloat()
                val itemX = currentArcCenterX + radius * cos(angleRad) + with(density) { 14.dp.toPx() }
                val itemY = arcCenterY + radius * sin(angleRad)

                val isSelected = (index == selectedIndex)
                val targetScale = if (isSelected) 1.15f else 1.0f
                val scale by animateFloatAsState(
                    targetValue = targetScale,
                    animationSpec = tween(160, easing = bezierEasing),
                    label = "name_scale_$name"
                )

                val textColor = if (isSelected) {
                    Color.White.copy(alpha = namesAlpha)
                } else {
                    Color.White.copy(alpha = namesAlpha * 0.55f)
                }

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = itemX.roundToInt(),
                                y = (itemY - with(density) { 16.dp.toPx() }).roundToInt()
                            )
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }
                ) {
                    Text(
                        text = name,
                        fontFamily = InterFontFamily,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 15.sp,
                        color = textColor
                    )
                }
            }
        }

        // The Physical Sliver Plate (Vertically Centered on Left Edge)
        val plateX = (currentArcCenterX + radius - plateWidthPx).coerceAtLeast(plateProtrusionPx - plateWidthPx)
        val plateY = arcCenterY - plateHeightPx / 2f

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = plateX.roundToInt(),
                        y = plateY.roundToInt()
                    )
                }
                .size(width = 16.dp, height = 78.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF888B94),
                            Color(0xFFD4D8E0),
                            Color(0xFFACAFB7)
                        )
                    )
                )
                .pointerInput(modules) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            // Soft buttery haptic on touch
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            coroutineScope.launch {
                                revealAnim.animateTo(
                                    targetValue = 0.35f,
                                    animationSpec = tween(120, easing = bezierEasing)
                                )
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            val finalReveal = revealAnim.value
                            coroutineScope.launch {
                                if (finalReveal > 0.40f) {
                                    // Released on a name: second haptic & switch module
                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                    val chosenModule = modules.getOrElse(selectedIndex) { modules[0] }
                                    onModuleSelected(chosenModule)
                                }
                                // Immediate bezier retract
                                revealAnim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(200, easing = bezierEasing)
                                )
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            coroutineScope.launch {
                                revealAnim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(180, easing = bezierEasing)
                                )
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val nextReveal = (revealAnim.value + dragAmount.x / maxUnfurlPx).coerceIn(0f, 1f)
                            coroutineScope.launch {
                                revealAnim.snapTo(nextReveal)
                            }

                            // Angular selection: compute angle from current arc center
                            val fingerX = change.position.x + plateX
                            val fingerY = change.position.y + plateY
                            val dx = fingerX - currentArcCenterX
                            val dy = fingerY - arcCenterY
                            val fingerAngleDeg = (atan2(dy, dx) * 180.0 / PI).toFloat().coerceIn(-70f, 70f)

                            // Pick name whose angular slot is nearest
                            var nearestIdx = 0
                            var minDiff = Float.MAX_VALUE
                            for (i in itemAnglesDeg.indices) {
                                val diff = kotlin.math.abs(fingerAngleDeg - itemAnglesDeg[i])
                                if (diff < minDiff) {
                                    minDiff = diff
                                    nearestIdx = i
                                }
                            }

                            if (nearestIdx != selectedIndex) {
                                selectedIndex = nearestIdx
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                    )
                }
        )
    }
}
