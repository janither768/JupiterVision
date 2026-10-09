// DoTProModuleScreen.kt - Downtown Proximity (DoTPro) Module Surface - Phase 3
package com.jupiter.vision.ui.screens

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jupiter.vision.dotpro.ui.DotProDebugScreen
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jupiter.vision.ui.theme.InterFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Prebuilt Facts cycling array for DoTPro
private val DOTPRO_FACT_STRINGS = arrayOf(
    "Downtown grid density is elevated. Proximity nodes online.",
    "Proximity bubble active — 12m sphere mapped. Earbud sync ready.",
    "Bicycle corridor crossing detected. Spatial sound focused forward.",
    "Narrow alley reflection ahead. Acoustic echo threshold 0.84.",
    "Heavy pedestrian cluster entering 6m radius. High sensitivity.",
    "Subway ventilation rumble isolated. Mid-frequency filtered.",
    "Sidewalk café boundary mapped. Dynamic perimeter updated.",
    "Crosswalk countdown acoustic chirp captured. Syncing stride cadence.",
    "Storefront awning acoustic damping registered. Elevation steady.",
    "Transit bus hydraulic hiss detected at 18m. Proximity alert idle."
)

/**
 * Procedural low-latency synthesizer for DoTPro ignition & deactivation sound effects:
 * - Rising tone (riser) with bass foundation
 * - Falling tone (downlifter) with bass
 * - Settle tone (engine idle)
 * - Inverted riser tone for deactivation plate
 * - Deactivate settle sound
 */
class IgnitionAudioEngine {
    private var activeJob: Job? = null

    fun playRiser(scope: kotlinx.coroutines.CoroutineScope) {
        stop()
        activeJob = scope.launch(Dispatchers.Default) {
            synthesizeSweep(
                startFreq = 80.0,
                endFreq = 440.0,
                bassFreq = 55.0,
                durationMs = 2100,
                volume = 0.55f
            )
        }
    }

    fun playDownlifter(scope: kotlinx.coroutines.CoroutineScope) {
        stop()
        activeJob = scope.launch(Dispatchers.Default) {
            synthesizeSweep(
                startFreq = 340.0,
                endFreq = 70.0,
                bassFreq = 50.0,
                durationMs = 360,
                volume = 0.40f
            )
        }
    }

    fun playEngineSettle(scope: kotlinx.coroutines.CoroutineScope) {
        stop()
        activeJob = scope.launch(Dispatchers.Default) {
            synthesizeTonePulse(
                freq = 110.0,
                subFreq = 55.0,
                durationMs = 520,
                volume = 0.60f
            )
        }
    }

    fun playInvertedRiser(scope: kotlinx.coroutines.CoroutineScope) {
        stop()
        activeJob = scope.launch(Dispatchers.Default) {
            synthesizeSweep(
                startFreq = 440.0,
                endFreq = 90.0,
                bassFreq = 65.0,
                durationMs = 1900,
                volume = 0.50f
            )
        }
    }

    fun playDeactivateSettle(scope: kotlinx.coroutines.CoroutineScope) {
        stop()
        activeJob = scope.launch(Dispatchers.Default) {
            synthesizeTonePulse(
                freq = 75.0,
                subFreq = 40.0,
                durationMs = 450,
                volume = 0.55f
            )
        }
    }

    fun stop() {
        activeJob?.cancel()
        activeJob = null
    }

    private suspend fun synthesizeSweep(
        startFreq: Double,
        endFreq: Double,
        bassFreq: Double,
        durationMs: Int,
        volume: Float
    ) = withContext(Dispatchers.Default) {
        val sampleRate = 22050
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt().coerceAtLeast(sampleRate / 10)
        val buffer = ShortArray(numSamples)

        var phase = 0.0
        var bassPhase = 0.0
        val twoPi = 2.0 * Math.PI

        for (i in 0 until numSamples) {
            if (!isActive) break
            val progress = i.toDouble() / numSamples
            val currentFreq = startFreq + (endFreq - startFreq) * (progress * progress)
            val freqStep = twoPi * currentFreq / sampleRate
            val bassStep = twoPi * bassFreq / sampleRate

            phase += freqStep
            bassPhase += bassStep

            val env = when {
                progress < 0.08 -> progress / 0.08
                progress > 0.90 -> (1.0 - progress) / 0.10
                else -> 1.0
            }

            val sample = (sin(phase) * 0.65 + sin(bassPhase) * 0.35) * env * volume
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }

        writeToAudioTrack(buffer, sampleRate)
    }

    private suspend fun synthesizeTonePulse(
        freq: Double,
        subFreq: Double,
        durationMs: Int,
        volume: Float
    ) = withContext(Dispatchers.Default) {
        val sampleRate = 22050
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        val twoPi = 2.0 * Math.PI

        for (i in 0 until numSamples) {
            if (!isActive) break
            val progress = i.toDouble() / numSamples
            val env = (1.0 - progress) * (1.0 - progress)
            val sample = (sin(twoPi * freq * i / sampleRate) * 0.6 + sin(twoPi * subFreq * i / sampleRate) * 0.4) * env * volume
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }

        writeToAudioTrack(buffer, sampleRate)
    }

    private suspend fun writeToAudioTrack(buffer: ShortArray, sampleRate: Int) = withContext(Dispatchers.IO) {
        var audioTrack: AudioTrack? = null
        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufSize = maxOf(minBufSize, buffer.size * 2)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            val waitMs = (buffer.size * 1000L) / sampleRate
            delay(waitMs)
        } catch (_: Throwable) {
            // Fails gracefully if audio device is unavailable
        } finally {
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (_: Throwable) {}
        }
    }
}

/**
 * Global persistent holder for DoTPro module activation state across page swipes / navigation.
 * Changing pages does not reset or change the state of the module page.
 */
object DoTProStateHolder {
    var isIgnited by mutableStateOf(false)
}

/**
 * DoTPro Module Surface (Downtown Proximity) - Phase 3
 * Standalone surface placed to the LEFT of Vision Home.
 */
@Composable
fun DoTProModuleScreen(
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current
    val audioEngine = remember { IgnitionAudioEngine() }

    DisposableEffect(Unit) {
        onDispose {
            audioEngine.stop()
        }
    }

    // State for showing the DoTPro sub-engine diagnostic / debug surface
    var showDebugScreen by remember { mutableStateOf(false) }

    // Persisted ignited state (persists across page switches)
    val isIgnited = DoTProStateHolder.isIgnited

    // Ignition wave progress: 0f (center) to 1f (reached edges)
    val waveProgressAnim = remember { Animatable(0f) }
    var isWakePressed by remember { mutableStateOf(false) }

    // Deactivation wave progress and press state
    val deactivateWaveAnim = remember { Animatable(0f) }
    var isDeactivatePressed by remember { mutableStateOf(false) }

    // Deactivation trigger animation for sliding facts left & visualizer collapsing to out of view center
    var isDeactivatingOut by remember { mutableStateOf(false) }
    val deactivatingAnim = remember { Animatable(0f) }

    // Smooth bezier transition between State 1 (Idle = 0f) and State 2 (Active = 1f)
    val stateTransition by animateFloatAsState(
        targetValue = if (isIgnited) 1f else 0f,
        animationSpec = tween(durationMillis = 850, easing = CubicBezierEasing(0.20f, 0.0f, 0.15f, 1.0f)),
        label = "dotpro_state_transition"
    )

    // Liquid mesh drift animation (subtle harmonic drift for ignition button & visualizer bg)
    val meshTransition = rememberInfiniteTransition(label = "mesh_drift")
    val meshDriftProgress by meshTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mesh_drift_progress"
    )

    // Surroundings react slightly to the incoming radial waves:
    // During ignition hold: title block lifts slightly and scale adjusts subtly.
    // During deactivation hold: fact band and visualizer adjust subtly.
    val ignitionHoldProgress = waveProgressAnim.value
    val deactivationHoldProgress = deactivateWaveAnim.value

    // Base background: #1E1E22, bottom-right #2E2E34, purple vignette #1A1420, subtle drifting light blobs
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E22))
            .testTag("dotpro_module_screen")
    ) {
        // 1. Bottom-right corner smooth radial gradient blend (#2E2E34 -> #1E1E22)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2E2E34),
                        Color(0xFF26262C),
                        Color(0xFF1E1E22)
                    ),
                    center = Offset(size.width, size.height),
                    radius = size.width * 1.35f
                )
            )
        }

        // 2. Faint drifting ambient white light blobs (5-12% peak opacity, 10-20s cycles)
        DriftingLightBlobs(modifier = Modifier.fillMaxSize())

        // 3. Wide feathered purple-tinted vignette from all edges (#1A1420 tint, NOT black)
        PurpleFeatheredVignette(modifier = Modifier.fillMaxSize())

        // Content Container
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            val availableHeight = maxHeight
            val availableWidth = maxWidth

            // ------------------------------------------------------------------------
            // 3 DoTPro TEXTS: "DOWNTOWN PROXIMITY", "DoTPro", Module Quote
            // In State 1 (Idle): Taken significantly higher so the ignition plate does NOT overlap it!
            //   Top padding is set comfortably above the ignition plate (~44dp).
            // When ignited: Texts move to top header position (24dp) with cubic-bezier smoothing.
            // Surroundings effect: As ignition radial wave progresses, title block scales/lifts slightly (1.02x).
            // Releasing early smoothly returns it back like normal.
            // ------------------------------------------------------------------------
            val topHeaderY = 24.dp
            val idleAboveButtonY = 44.dp
            val currentTextsTop = idleAboveButtonY + (topHeaderY - idleAboveButtonY) * stateTransition

            val titleSurroundingScale = 1.0f + (0.025f * ignitionHoldProgress * (1f - stateTransition))
            val titleSurroundingOffset = (-4.dp) * ignitionHoldProgress * (1f - stateTransition)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = currentTextsTop + titleSurroundingOffset)
                    .graphicsLayer {
                        scaleX = titleSurroundingScale
                        scaleY = titleSurroundingScale
                    },
                horizontalAlignment = Alignment.Start
            ) {
                // "DOWNTOWN PROXIMITY" (fades out slightly in compacted State 2)
                if (stateTransition < 0.95f) {
                    Text(
                        text = "DOWNTOWN PROXIMITY",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        letterSpacing = 2.4.sp,
                        color = Color(0xFFC0C0C8).copy(alpha = (1f - stateTransition).coerceIn(0f, 1f)),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.testTag("dotpro_cinematic_title")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // "DoTPro" module callsign (large in idle, slightly compacted in active)
                // Clicking "DoTPro" or the adjacent diagnostic label toggles the debug verification surface
                val callsignSize = (38f - 14f * stateTransition).sp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "DoTPro",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = callsignSize,
                        letterSpacing = (-0.5).sp,
                        color = Color(0xFFEAEAEE),
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .clickable { showDebugScreen = true }
                            .testTag("dotpro_callsign")
                    )

                    Text(
                        text = "• DIAGNOSTIC",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                        color = Color(0xFF80D8FF).copy(alpha = 0.85f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF263238))
                            .clickable { showDebugScreen = true }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("dotpro_diag_badge")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Module quote: Full in idle, condensed in active state
                val quoteText = if (stateTransition > 0.5f) {
                    "Aware of your proximity surroundings, even when you aren't. Earbuds help both you and the module."
                } else {
                    "The phone will be aware of your proximity surroundings, even when you aren't. Earbuds help both you and the module."
                }

                Text(
                    text = quoteText,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = if (stateTransition > 0.5f) 11.5.sp else 12.5.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFF9494A2),
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth(if (stateTransition > 0.5f) 0.95f else 0.88f)
                        .testTag("dotpro_quote")
                )
            }

            // ------------------------------------------------------------------------
            // CENTRAL ELEMENT:
            // State 1: WAKE BUTTON (180dp square, sharp corners, liquid mesh drift gradient).
            // State 2: PROXIMITY VISUALIZER:
            //   In Phase 3: "the visualizer bg should be a bigger square centre aligned as before,
            //   NOT stretch to horizontal edges." -> 280dp centered square (both width and height 280dp).
            //   Filled with same cosmic purple & amber liquid mesh gradient.
            // Surroundings effect:
            //   During ignition wave, square expands slightly (1.03x).
            //   During deactivation wave, square shrinks slightly (0.97x).
            // ------------------------------------------------------------------------
            val buttonBaseSize = 180.dp
            val visualizerSquareSize = 280.dp

            val currentElementSize = buttonBaseSize + (visualizerSquareSize - buttonBaseSize) * stateTransition

            // Slight square size pulse during radial wave expansion
            val squareSurroundingScale = if (stateTransition < 0.5f) {
                1f + 0.035f * ignitionHoldProgress
            } else {
                1f - 0.035f * deactivationHoldProgress
            }

            // Center vertical placement (comfortably below text block, centered on screen)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 110.dp, bottom = 180.dp),
                contentAlignment = Alignment.Center
            ) {
                // Interactive Container
                Box(
                    modifier = Modifier
                        .size(currentElementSize)
                        .clip(RoundedCornerShape(0.dp)) // sharp corners strictly
                        .graphicsLayer {
                            scaleX = squareSurroundingScale
                            scaleY = squareSurroundingScale
                            // If deactivating, visualizer falls on to its out of view center
                            val deactVal = deactivatingAnim.value
                            if (deactVal > 0f) {
                                scaleX *= (1f - deactVal * 0.5f)
                                scaleY *= (1f - deactVal * 0.5f)
                                alpha = (1f - deactVal).coerceIn(0f, 1f)
                            }
                        }
                        .testTag("dotpro_ignition_container")
                        .then(
                            if (!isIgnited) {
                                Modifier.pointerInput(Unit) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        down.consume()
                                        isWakePressed = true

                                        // Initial press: haptic tick + start riser audio + wave progress
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        audioEngine.playRiser(coroutineScope)

                                        val ignitionJob = coroutineScope.launch {
                                            waveProgressAnim.animateTo(
                                                targetValue = 1f,
                                                animationSpec = tween(
                                                    durationMillis = 2000,
                                                    easing = CubicBezierEasing(0.42f, 0.0f, 0.15f, 1.0f)
                                                )
                                            )
                                        }

                                        // In Phase 3:
                                        // "when pressed on enough to ignite or deactivate, ignition plate or
                                        //  deactivation plate shouldn't wait for release."
                                        // As soon as waveProgressAnim hits >= 0.98f, fire immediately!
                                        var triggeredIgnition = false
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.changes.all { !it.pressed }) {
                                                // Pointer released before or after
                                                break
                                            }
                                            if (waveProgressAnim.value >= 0.98f && !triggeredIgnition) {
                                                triggeredIgnition = true
                                                ignitionJob.cancel()
                                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                audioEngine.playEngineSettle(coroutineScope)
                                                DoTProStateHolder.isIgnited = true
                                                break
                                            }
                                        }

                                        isWakePressed = false
                                        if (triggeredIgnition || waveProgressAnim.value >= 0.98f) {
                                            // Ignited successfully!
                                            ignitionJob.cancel()
                                            coroutineScope.launch { waveProgressAnim.snapTo(1f) }
                                            if (!triggeredIgnition) {
                                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                audioEngine.playEngineSettle(coroutineScope)
                                                DoTProStateHolder.isIgnited = true
                                            }
                                        } else {
                                            // Released before reaching edge: collapse back, downlifter tone, haptic tick
                                            ignitionJob.cancel()
                                            audioEngine.playDownlifter(coroutineScope)
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            coroutineScope.launch {
                                                waveProgressAnim.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    val density = LocalDensity.current

                    // Canvas rendering the Wake Button and Visualizer Background with Liquid Mesh Drift Effect
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val centerPt = Offset(w / 2f, h / 2f)
                        val maxRadius = sqrt((w * w + h * h).toDouble()).toFloat() / 2f

                        // Liquid Mesh Drift calculations:
                        // Multi-point radial blend with smooth trigonometric offsets
                        val angle = meshDriftProgress * 2.0 * PI
                        val driftX1 = (cos(angle) * w * 0.18f).toFloat()
                        val driftY1 = (sin(angle) * h * 0.18f).toFloat()

                        val driftX2 = (sin(angle * 1.3) * w * 0.15f).toFloat()
                        val driftY2 = (cos(angle * 1.3) * h * 0.15f).toFloat()

                        val startOffset = Offset(w * 0.1f + driftX1, h * 0.1f + driftY1)
                        val endOffset = Offset(w * 0.9f + driftX2, h * 0.9f + driftY2)

                        // Base gradient: Cosmic Purple (#6A3FB5) to Amber (#D9982A)
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF6A3FB5),
                                    Color(0xFF8A4BB0),
                                    Color(0xFFB57065),
                                    Color(0xFFD9982A)
                                ),
                                start = startOffset,
                                end = endOffset
                            )
                        )

                        // Subtle secondary harmonic layer to give authentic "liquid mesh" depth
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF9E5EB0).copy(alpha = 0.50f),
                                    Color.Transparent
                                ),
                                center = Offset(w * 0.5f + driftX2, h * 0.5f + driftY1),
                                radius = w * 0.75f
                            ),
                            center = Offset(w * 0.5f + driftX2, h * 0.5f + driftY1),
                            radius = w * 0.75f
                        )

                        // On initial wake press: button darkens slightly
                        if (isWakePressed && stateTransition < 1f) {
                            drawRect(color = Color.Black.copy(alpha = 0.28f * (1f - stateTransition)))
                        }

                        // Ignition radial wave expanding from center to edges
                        if (stateTransition < 1f) {
                            val waveR = maxRadius * waveProgressAnim.value
                            if (waveR > 0f) {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.85f),
                                            Color.White.copy(alpha = 0.45f),
                                            Color.White.copy(alpha = 0.12f),
                                            Color.Transparent
                                        ),
                                        center = centerPt,
                                        radius = waveR
                                    ),
                                    center = centerPt,
                                    radius = waveR
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.90f),
                                    center = centerPt,
                                    radius = waveR,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = with(density) { 2.5.dp.toPx() }
                                    )
                                )
                            }
                        }
                    }

                    // Active Spectrum Visualizer in Phase 3:
                    // - Curves filled with slime green & cyan gradient with liquid mesh effect.
                    // - Second curve present with different brightness/colors (cyan & neon lime variation).
                    // - Gridlines strictly REMOVED!
                    if (stateTransition > 0.05f) {
                        OzoneSpectrumVisualizerPhase3(
                            meshDriftProgress = meshDriftProgress,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = stateTransition
                                }
                        )
                    }
                }
            }

            // ------------------------------------------------------------------------
            // STATE FACTS BAND (STATE 2 ACTIVE ONLY)
            // Position: Placed above the deactivation plate, with generous spacing so
            // the deactivation plate NEVER overlaps the facts!
            // In Phase 3:
            // - Bigger font size (latest fact 19sp, bold & clear).
            // - Decreased vertical spacing (spacedBy 5.dp).
            // - Facts slide to left fast on deactivation.
            // - Surroundings effect: During deactivation wave, facts scale down subtly (0.97x)
            //   and shift down 3dp. Releasing early smoothly returns it back like normal.
            // ------------------------------------------------------------------------
            if (stateTransition > 0.1f) {
                val slideLeftOffset = if (isDeactivatingOut) {
                    -(availableWidth.value * 1.25f * deactivatingAnim.value)
                } else {
                    0f
                }

                val factBandSurroundingScale = 1.0f - (0.035f * deactivationHoldProgress)
                val factBandSurroundingShift = 4.dp * deactivationHoldProgress

                StateFactsBandPhase3(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 92.dp) // Comfortably ABOVE the deactivation plate (height 72dp)
                        .padding(horizontal = 20.dp)
                        .graphicsLayer {
                            translationX = slideLeftOffset
                            translationY = factBandSurroundingShift.toPx()
                            scaleX = factBandSurroundingScale
                            scaleY = factBandSurroundingScale
                            alpha = (stateTransition * (1f - deactivatingAnim.value)).coerceIn(0f, 1f)
                        }
                )
            }

            // ------------------------------------------------------------------------
            // DEACTIVATE PLATE (BOTTOM OF SCREEN)
            // Shape: Circle's top curve area, not fully visible as a circle, just top curve.
            // No text.
            // In Phase 3:
            // - Color changed to light grey (#D0D0D8 / #C4C4CE with translucency).
            // - Does NOT overlap facts (placed underneath the fact band at bottom = 0).
            // - Immediate trigger on reaching threshold: does NOT wait for release!
            // - Radial wave emanates from center to visible part.
            // ------------------------------------------------------------------------
            if (stateTransition > 0.05f) {
                DeactivatePlatePhase3(
                    stateTransition = stateTransition,
                    isDeactivatingOut = isDeactivatingOut,
                    waveAnim = deactivateWaveAnim,
                    onDeactivateInitiated = {
                        // Start deactivation sequence
                        coroutineScope.launch {
                            isDeactivatingOut = true
                            audioEngine.playDeactivateSettle(coroutineScope)
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

                            deactivatingAnim.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = 480,
                                    easing = CubicBezierEasing(0.20f, 0.0f, 0.15f, 1.0f)
                                )
                            )

                            // State is reset to Idle
                            DoTProStateHolder.isIgnited = false
                            waveProgressAnim.snapTo(0f)
                            deactivateWaveAnim.snapTo(0f)
                            deactivatingAnim.snapTo(0f)
                            isDeactivatingOut = false
                        }
                    },
                    audioEngine = audioEngine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .align(Alignment.BottomCenter)
                        .testTag("dotpro_deactivate_plate_zone")
                )
            }
        }

        // 7. DEBUG SURFACE OVERLAY DIALOG (to verify the DoTPro sub-engine pipeline end-to-end)
        if (showDebugScreen) {
            Dialog(
                onDismissRequest = { showDebugScreen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    DotProDebugScreen(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/**
 * Deactivate plate at the very bottom of the screen (Phase 3):
 * - Shape: Circle's top curve area (not fully visible as a circle, only the top arc).
 * - Color: Light grey (#D2D2DC with soft translucency).
 * - Immediate trigger when held enough: does not wait for release!
 * - When released early: collapses back smoothly and surroundings reset.
 */
@Composable
private fun DeactivatePlatePhase3(
    stateTransition: Float,
    isDeactivatingOut: Boolean,
    waveAnim: Animatable<Float, *>,
    onDeactivateInitiated: () -> Unit,
    audioEngine: IgnitionAudioEngine,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current
    var isPressed by remember { mutableStateOf(false) }

    // Subtle scale-up when pressed
    val scaleFactor by animateFloatAsState(
        targetValue = if (isPressed) 1.07f else 1.0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "deact_scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scaleFactor
                scaleY = scaleFactor
                alpha = stateTransition
            }
            .pointerInput(isDeactivatingOut) {
                if (isDeactivatingOut) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true

                    // Haptic tick + inverted riser effect
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    audioEngine.playInvertedRiser(coroutineScope)

                    val deactJob = coroutineScope.launch {
                        waveAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 1800,
                                easing = CubicBezierEasing(0.42f, 0.0f, 0.15f, 1.0f)
                            )
                        )
                    }

                    // In Phase 3:
                    // "ignition plate or deactivation plate shouldn't wait for release."
                    // Trigger as soon as the threshold (>= 0.97f) is reached!
                    var triggeredDeact = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) {
                            break
                        }
                        if (waveAnim.value >= 0.97f && !triggeredDeact) {
                            triggeredDeact = true
                            deactJob.cancel()
                            onDeactivateInitiated()
                            break
                        }
                    }

                    isPressed = false
                    if (triggeredDeact || waveAnim.value >= 0.97f) {
                        deactJob.cancel()
                        if (!triggeredDeact) {
                            onDeactivateInitiated()
                        }
                    } else {
                        // Released early before trigger: send everything back like normal
                        deactJob.cancel()
                        audioEngine.playDownlifter(coroutineScope)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        coroutineScope.launch {
                            waveAnim.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Circle's top curve area:
            // Circle center is placed below screen bottom so only the upper arc dome enters the viewport
            val circleRadius = w * 0.68f
            val circleCenter = Offset(w / 2f, h + circleRadius * 0.50f)

            val path = Path().apply {
                addOval(
                    Rect(
                        center = circleCenter,
                        radius = circleRadius
                    )
                )
            }

            // Draw plate background: Light grey (#D2D2DC / #BFBFCB)
            clipPath(path) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFE2E2EC).copy(alpha = if (isPressed) 0.88f else 0.72f),
                            Color(0xFFC0C0CC).copy(alpha = if (isPressed) 0.84f else 0.65f)
                        ),
                        startY = 0f,
                        endY = h
                    )
                )

                // Top rim border stroke highlight for clean definition
                drawCircle(
                    color = Color.White.copy(alpha = 0.55f),
                    center = circleCenter,
                    radius = circleRadius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx())
                )

                // Deactivation wave emanating from center to visible area
                val currentWaveRadius = circleRadius * waveAnim.value
                if (currentWaveRadius > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.90f),
                                Color.White.copy(alpha = 0.45f),
                                Color.White.copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            center = circleCenter,
                            radius = currentWaveRadius
                        ),
                        center = circleCenter,
                        radius = currentWaveRadius
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.92f),
                        center = circleCenter,
                        radius = currentWaveRadius,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
    }
}

/**
 * Faint white light blobs drifting across the background:
 * Varied sizes (110dp to 200dp), 5–12% peak opacity, 10–20s smooth drifting cycles.
 */
@Composable
private fun DriftingLightBlobs(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "drifting_blobs")

    val blob1Progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob1_drift"
    )

    val blob2Progress by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob2_drift"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Blob 1: Upper-left quadrant drift (opacity 5-11%)
        val b1X = w * 0.25f + (w * 0.12f) * sin(blob1Progress * 2.0 * PI).toFloat()
        val b1Y = h * 0.28f + (h * 0.08f) * cos(blob1Progress * 2.0 * PI).toFloat()
        val b1Alpha = 0.05f + 0.06f * sin(blob1Progress * PI).toFloat()
        val b1Radius = w * 0.38f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = b1Alpha),
                    Color.White.copy(alpha = b1Alpha * 0.5f),
                    Color.Transparent
                ),
                center = Offset(b1X, b1Y),
                radius = b1Radius
            ),
            center = Offset(b1X, b1Y),
            radius = b1Radius
        )

        // Blob 2: Mid-right quadrant drift (opacity 4-10%)
        val b2X = w * 0.78f - (w * 0.10f) * cos(blob2Progress * 2.0 * PI).toFloat()
        val b2Y = h * 0.62f + (h * 0.10f) * sin(blob2Progress * 2.0 * PI).toFloat()
        val b2Alpha = 0.04f + 0.06f * sin(blob2Progress * PI).toFloat()
        val b2Radius = w * 0.44f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = b2Alpha),
                    Color.White.copy(alpha = b2Alpha * 0.45f),
                    Color.Transparent
                ),
                center = Offset(b2X, b2Y),
                radius = b2Radius
            ),
            center = Offset(b2X, b2Y),
            radius = b2Radius
        )
    }
}

/**
 * Soft feathered vignette from all edges with low-contrast dark purple tint (#1A1420), NOT black.
 */
@Composable
private fun PurpleFeatheredVignette(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val purpleTint = Color(0xFF1A1420)

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    purpleTint.copy(alpha = 0.35f),
                    purpleTint.copy(alpha = 0.78f)
                ),
                center = Offset(w / 2f, h / 2f),
                radius = sqrt((w * w + h * h).toDouble()).toFloat() * 0.52f
            )
        )
    }
}

/**
 * Ozone Spectrum Visualizer (Phase 3):
 * - Gridlines strictly REMOVED.
 * - Curves filled with slime green (#8FE04A) and cyan (#3FD8D0) gradient with liquid mesh effect.
 * - Second curve also present, but with slightly different brightness and colors.
 * - Nodes and harmonic flow tuned for clean, modern look.
 */
@Composable
private fun OzoneSpectrumVisualizerPhase3(
    meshDriftProgress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ozone_spectrum_p3")

    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_1_p3"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_2_p3"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("ozone_proximity_visualizer")
    ) {
        val w = size.width
        val h = size.height

        // Phase 3 requirement: Gridlines REMOVED! (No grid lines drawn)

        // Palette definitions: Slime Green & Cyan with liquid mesh dynamic drift
        val slimeGreen = Color(0xFF8FE04A)
        val slimeLime = Color(0xFFA6F247)
        val cyanBlue = Color(0xFF3FD8D0)
        val cyanBright = Color(0xFF67ECE5)
        val deepCyan = Color(0xFF22B4AC)

        // Liquid mesh drift angle
        val meshAngle = meshDriftProgress * 2.0 * PI
        val driftOffsetX = (cos(meshAngle) * w * 0.15f).toFloat()
        val driftOffsetY = (sin(meshAngle) * h * 0.15f).toFloat()

        // 1. Primary Curve Gradients (Slime Green to Cyan)
        val primaryStrokeGradient = Brush.linearGradient(
            colors = listOf(slimeLime, cyanBright),
            start = Offset(0f + driftOffsetX, h * 0.3f),
            end = Offset(w - driftOffsetX, h * 0.7f)
        )

        // Liquid mesh gradient fill under primary curve
        val primaryFillGradient = Brush.verticalGradient(
            colors = listOf(
                slimeGreen.copy(alpha = 0.42f),
                cyanBlue.copy(alpha = 0.28f),
                Color.Transparent
            ),
            startY = h * 0.15f + driftOffsetY,
            endY = h
        )

        // 2. Second Curve Gradients (Slightly different brightness & colors: Cyan to Deep Turquoise / Slime)
        val secondaryStrokeGradient = Brush.linearGradient(
            colors = listOf(deepCyan.copy(alpha = 0.75f), slimeGreen.copy(alpha = 0.65f)),
            start = Offset(w * 0.1f, h * 0.6f + driftOffsetY),
            end = Offset(w * 0.9f, h * 0.4f - driftOffsetY)
        )

        val secondaryFillGradient = Brush.verticalGradient(
            colors = listOf(
                cyanBright.copy(alpha = 0.22f),
                deepCyan.copy(alpha = 0.14f),
                Color.Transparent
            ),
            startY = h * 0.30f,
            endY = h
        )

        // Draw Layer 1 (Second curve): softer harmonic resonance, different colors & brightness
        drawSpectrumLayer(
            w = w,
            h = h,
            phaseA = phase1 * 0.75f,
            phaseB = phase2 * 0.60f,
            baseY = h * 0.60f,
            amplitude = h * 0.20f,
            strokeBrush = secondaryStrokeGradient,
            fillBrush = secondaryFillGradient,
            strokeWidth = 1.8.dp.toPx()
        )

        // Draw Layer 2 (Primary curve): vibrant slime green & cyan liquid mesh curve
        drawSpectrumLayer(
            w = w,
            h = h,
            phaseA = phase1,
            phaseB = phase2,
            baseY = h * 0.50f,
            amplitude = h * 0.28f,
            strokeBrush = primaryStrokeGradient,
            fillBrush = primaryFillGradient,
            strokeWidth = 2.8.dp.toPx()
        )

        // 3. Active resonance frequency nodes on primary curve
        val numNodes = 5
        for (i in 1..numNodes) {
            val nx = (w / (numNodes + 1)) * i
            val normX = nx / w
            val ny = calculateSpectrumY(normX, phase1, phase2, h * 0.50f, h * 0.28f)

            // Outer soft glow node
            drawCircle(
                color = if (i % 2 == 0) cyanBright.copy(alpha = 0.60f) else slimeLime.copy(alpha = 0.60f),
                center = Offset(nx, ny),
                radius = 6.5.dp.toPx()
            )
            // Center pip
            drawCircle(
                color = Color.White.copy(alpha = 0.95f),
                center = Offset(nx, ny),
                radius = 2.5.dp.toPx()
            )
        }
    }
}

/**
 * Calculates Y coordinate for smooth multi-harmonic spline wave.
 */
private fun calculateSpectrumY(normX: Float, p1: Float, p2: Float, baseY: Float, amp: Float): Float {
    val w1 = sin((normX * 2.5 * PI + p1).toDouble()).toFloat()
    val w2 = sin((normX * 5.0 * PI - p2 * 1.3).toDouble()).toFloat() * 0.4f
    val w3 = cos((normX * 1.2 * PI + p1 * 0.6).toDouble()).toFloat() * 0.25f
    return baseY - (w1 + w2 + w3) * (amp * (0.6f + 0.4f * sin(normX * PI.toFloat())))
}

/**
 * Helper to render smooth parametric curve and area under it.
 */
private fun DrawScope.drawSpectrumLayer(
    w: Float,
    h: Float,
    phaseA: Float,
    phaseB: Float,
    baseY: Float,
    amplitude: Float,
    strokeColor: Color? = null,
    strokeBrush: Brush? = null,
    fillBrush: Brush? = null,
    strokeWidth: Float = 2f
) {
    val path = Path()
    val fillPath = Path()

    val steps = 60
    val stepX = w / steps

    for (i in 0..steps) {
        val x = i * stepX
        val normX = i.toFloat() / steps
        val y = calculateSpectrumY(normX, phaseA, phaseB, baseY, amplitude)

        if (i == 0) {
            path.moveTo(x, y)
            fillPath.moveTo(x, h)
            fillPath.lineTo(x, y)
        } else {
            path.lineTo(x, y)
            fillPath.lineTo(x, y)
        }
    }

    fillPath.lineTo(w, h)
    fillPath.close()

    // Draw translucent fill under curve
    if (fillBrush != null) {
        drawPath(path = fillPath, brush = fillBrush)
    }

    // Draw main stroke
    if (strokeBrush != null) {
        drawPath(path = path, brush = strokeBrush, style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth))
    } else if (strokeColor != null) {
        drawPath(path = path, color = strokeColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth))
    }
}

/**
 * State Facts Band (Phase 3):
 * - Facts are significantly bigger (latest fact 19sp, high contrast, crisp read).
 * - Decreased vertical spacing (spacedBy 5.dp).
 * - Aligned middle (center).
 * - Older ones are faded when they rise up (~0.62, ~0.36, ~0.18 alpha).
 * - Strictly last 4 facts are kept.
 */
@Composable
private fun StateFactsBandPhase3(
    modifier: Modifier = Modifier
) {
    var lines by remember {
        mutableStateOf(
            listOf(
                AmbientLine(1001L, DOTPRO_FACT_STRINGS[0]),
                AmbientLine(1002L, DOTPRO_FACT_STRINGS[1]),
                AmbientLine(1003L, DOTPRO_FACT_STRINGS[2]),
                AmbientLine(1004L, DOTPRO_FACT_STRINGS[3])
            )
        )
    }
    var lineCounter by remember { mutableLongStateOf(1005L) }
    var stringIndex by remember { mutableIntStateOf(4) }

    // Tempo: new string every ~5 seconds
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(5000L)
            val nextStr = DOTPRO_FACT_STRINGS[stringIndex % DOTPRO_FACT_STRINGS.size]
            stringIndex = (stringIndex + 1) % DOTPRO_FACT_STRINGS.size
            val newLine = AmbientLine(lineCounter++, nextStr)
            lines = (lines + newLine).takeLast(4)
        }
    }

    Column(
        modifier = modifier.testTag("dotpro_state_facts_band"),
        verticalArrangement = Arrangement.spacedBy(5.dp), // Decreased vertical spacing
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        lines.forEachIndexed { index, line ->
            val rankFromBottom = (lines.size - 1) - index

            val targetAlpha = when (rankFromBottom) {
                0 -> 1.0f // Latest fact: maximum clarity, very easy to read
                1 -> 0.62f
                2 -> 0.36f
                else -> 0.18f
            }

            // In Phase 3: "The facts are not big enough, specially latest fact."
            val targetFontSize = when (rankFromBottom) {
                0 -> 19.0f // Big, prominent, easy to read
                1 -> 16.0f
                2 -> 14.0f
                else -> 12.5f
            }

            val animatedAlpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                label = "dotpro_line_alpha_${line.id}"
            )

            val animatedFontSize by animateFloatAsState(
                targetValue = targetFontSize,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                label = "dotpro_line_font_${line.id}"
            )

            Text(
                text = line.text,
                fontFamily = InterFontFamily,
                fontWeight = if (rankFromBottom == 0) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = animatedFontSize.sp,
                lineHeight = (animatedFontSize + 4f).sp,
                letterSpacing = 0.3.sp,
                color = Color(0xFFF6F6FA).copy(alpha = animatedAlpha),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dotpro_fact_line_${line.id}")
            )
        }
    }
}
