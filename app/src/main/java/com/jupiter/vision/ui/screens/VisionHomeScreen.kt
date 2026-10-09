// VisionHomeScreen.kt - Full overhaul for Jupitervision OS (Build 2112.18)
package com.jupiter.vision.ui.screens

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupiter.vision.ui.theme.DarkGreyBackground
import com.jupiter.vision.ui.theme.InterFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive

/**
 * Data structure representing an active animated ambient text line in Band 2 or Band 3.
 */
data class AmbientLine(
    val id: Long,
    val text: String
)

/**
 * Placeholder strings for Band 2 (Ambient Sound) — fast tempo (~6s).
 */
private val BAND_2_SOUND_STRINGS = listOf(
    "Environmental noise, steady",
    "Random people talking nearby",
    "Chatter drifting in",
    "Wind low, distant",
    "Engine sound, approaching",
    "Music track playing, subtle",
    "Voices thinning out"
)

/**
 * Placeholder strings for Band 3 (Other Sensors) — slow drifting tempo (~12s).
 */
private val BAND_3_SENSOR_STRINGS = listOf(
    "Something moved nearby",
    "Room got slightly darker",
    "Door shifted",
    "Stillness settling in",
    "Light change, soft",
    "Presence at the edge"
)

/**
 * VisionHomeScreen - Build 2112.18
 * Full overhaul: 3-band vertical stack, Inter typography, neutral palette,
 * solid #1A1A1A base with top-left #2A2A2E gradient blend, breathing sound blob,
 * and independent timers for Band 2 & 3 text rise-and-fade.
 */
@Composable
fun VisionHomeScreen(
    viewModel: VisionHomeViewModel = viewModel(
        factory = VisionHomeViewModelFactory(LocalContext.current.applicationContext as Application)
    ),
    @Suppress("UNUSED_PARAMETER") wallpaperBitmap: Bitmap? = null,
    modifier: Modifier = Modifier
) {
    // Background: solid dark grey #1A1A1A, with top-left #2A2A2E smooth radial blend.
    // No wallpaper layer, no cards, no panels, no grey boxes behind content.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkGreyBackground)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2A2A2E),
                        Color(0xFF232326),
                        DarkGreyBackground
                    ),
                    center = Offset(0f, 0f),
                    radius = 1200f
                )
            )
            .testTag("vision_home_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Generous top padding below status bar
            Spacer(modifier = Modifier.height(28.dp))

            // ==========================================
            // [ BAND 1 — TOP ]
            // Quiet, settled, left-aligned, ample whitespace.
            // TIME (large display scale) + DATE / WEATHER / LOCATION inline.
            // Isolated time state holder prevents per-second screen recomposition.
            // ==========================================
            Band1Header(
                timeState = viewModel.timeState,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("band_1_top")
            )

            // Clear vertical gap between Band 1 and Band 2
            Spacer(modifier = Modifier.height(44.dp))

            // ==========================================
            // [ BAND 2 — AMBIENT SOUND ]
            // Upper half: 4 lines of ambient sound state strings.
            // Enters at bottom (largest), rises, shrinks, fades.
            // Churns every ~6s. Right side: breathing white light blob.
            // ==========================================
            Band2AmbientSound(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("band_2_ambient_sound")
            )

            // Clear vertical gap from Band 2 to Band 3
            Spacer(modifier = Modifier.height(52.dp))

            // ==========================================
            // [ BAND 3 — OTHER SENSORS ]
            // Lower half: 3 lines of other-sensor state strings.
            // Slower, softer drift every ~12s. Cap at 3 lines.
            // ==========================================
            Band3OtherSensors(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("band_3_other_sensors")
            )

            // ==========================================
            // [ FREE SPACE ]
            // Empty vertical space reserved for future contextual row + toggles.
            // No placeholders, no dividers, no hints.
            // ==========================================
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * BAND 1: Top header with large display time and secondary date, weather, and location.
 * Isolates per-second ticking clock in [TimeDisplayIsolated] to prevent recomposing other bands.
 */
@Composable
fun Band1Header(
    timeState: StateFlow<String>,
    dateText: String = "THU, 8 OCT",
    weatherText: String = "21° CLEAR",
    locationText: String = "SAN FRANCISCO, CA",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        // Isolated Time Composable
        TimeDisplayIsolated(
            timeState = timeState
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Secondary info line: DATE · WEATHER · LOCATION (left-aligned)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.testTag("date_weather_location_row")
        ) {
            Text(
                text = dateText.uppercase(),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                letterSpacing = 0.8.sp,
                color = Color.White.copy(alpha = 0.50f)
            )
            Text(
                text = "  ·  ",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.28f)
            )
            Text(
                text = weatherText.uppercase(),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                letterSpacing = 0.8.sp,
                color = Color.White.copy(alpha = 0.50f)
            )
            Text(
                text = "  ·  ",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.28f)
            )
            Text(
                text = locationText.uppercase(),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                color = Color.White.copy(alpha = 0.50f)
            )
        }
    }
}

/**
 * Isolated per-second clock display: only this composable recomposes on second ticks.
 * Large display scale, Inter Light/Normal, left-aligned, tabular figures.
 */
@Composable
fun TimeDisplayIsolated(
    timeState: StateFlow<String>,
    modifier: Modifier = Modifier
) {
    val time by timeState.collectAsStateWithLifecycle()
    val parts = remember(time) {
        val tokens = time.split(":").map { it.trim() }
        if (tokens.size >= 3) {
            "${tokens[0]}:${tokens[1]}" to tokens[2]
        } else {
            time to ""
        }
    }
    val hoursMinutes = parts.first
    val seconds = parts.second

    Row(
        modifier = modifier.testTag("time_display_isolated"),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = hoursMinutes,
            style = TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Light,
                fontSize = 58.sp,
                fontFeatureSettings = "tnum",
                letterSpacing = (-1.5).sp,
                color = Color.White
            ),
            maxLines = 1,
            softWrap = false
        )
        if (seconds.isNotEmpty()) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = seconds,
                style = TextStyle(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 20.sp,
                    fontFeatureSettings = "tnum",
                    letterSpacing = (-0.5).sp,
                    color = Color.White.copy(alpha = 0.70f)
                ),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }
}

/**
 * BAND 2: Ambient Sound strings column with right-side soft breathing light blob visualizer.
 * Displays 4 lines capped, newest at bottom (largest), older lines rise upward, shrink and fade.
 * Loops new string every ~6 seconds.
 */
@Composable
fun Band2AmbientSound(
    modifier: Modifier = Modifier
) {
    var lines by remember {
        mutableStateOf(
            listOf(
                AmbientLine(1L, BAND_2_SOUND_STRINGS[0]),
                AmbientLine(2L, BAND_2_SOUND_STRINGS[1]),
                AmbientLine(3L, BAND_2_SOUND_STRINGS[2]),
                AmbientLine(4L, BAND_2_SOUND_STRINGS[3])
            )
        )
    }
    var lineCounter by remember { mutableLongStateOf(5L) }
    var stringIndex by remember { mutableIntStateOf(4) }

    // Independent churn timer: new string every ~6 seconds (hard rule: Band 2 visibly churns)
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(6000L)
            val nextStr = BAND_2_SOUND_STRINGS[stringIndex % BAND_2_SOUND_STRINGS.size]
            stringIndex = (stringIndex + 1) % BAND_2_SOUND_STRINGS.size
            val newLine = AmbientLine(lineCounter++, nextStr)
            lines = (lines + newLine).takeLast(4)
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left text column (leaving room on the right for visualizer blob)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            lines.forEachIndexed { index, line ->
                val rankFromBottom = (lines.size - 1) - index
                AnimatedAmbientLine(
                    line = line,
                    rankFromBottom = rankFromBottom,
                    animDurationMillis = 550
                )
            }
        }

        // Right side: soft transparent white light blob acting as ambient sound visualizer
        AmbientSoundBlob(
            modifier = Modifier.size(110.dp)
        )
    }
}

/**
 * BAND 3: Other Sensors (motion, light, door, stillness).
 * 3 visible lines, slower and softer rise-and-fade drifting every ~12 seconds.
 */
@Composable
fun Band3OtherSensors(
    modifier: Modifier = Modifier
) {
    var lines by remember {
        mutableStateOf(
            listOf(
                AmbientLine(101L, BAND_3_SENSOR_STRINGS[0]),
                AmbientLine(102L, BAND_3_SENSOR_STRINGS[1]),
                AmbientLine(103L, BAND_3_SENSOR_STRINGS[2])
            )
        )
    }
    var lineCounter by remember { mutableLongStateOf(104L) }
    var stringIndex by remember { mutableIntStateOf(3) }

    // Independent drift timer: new string every ~12 seconds (hard rule: Band 3 visibly drifts, not churns)
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(12000L)
            val nextStr = BAND_3_SENSOR_STRINGS[stringIndex % BAND_3_SENSOR_STRINGS.size]
            stringIndex = (stringIndex + 1) % BAND_3_SENSOR_STRINGS.size
            val newLine = AmbientLine(lineCounter++, nextStr)
            lines = (lines + newLine).takeLast(3)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(9.dp),
        horizontalAlignment = Alignment.Start
    ) {
        lines.forEachIndexed { index, line ->
            val rankFromBottom = (lines.size - 1) - index
            AnimatedAmbientLine(
                line = line,
                rankFromBottom = rankFromBottom,
                animDurationMillis = 850
            )
        }
    }
}

/**
 * Individual animated ambient line for Band 2 & Band 3:
 * - Newest at bottom: largest size and highest opacity.
 * - Older lines rise upward, shrink slightly, and fade progressively.
 * - Left-aligned, Inter font, non-interactive (no ripples, elevation, or touch feedback).
 */
@Composable
private fun AnimatedAmbientLine(
    line: AmbientLine,
    rankFromBottom: Int,
    animDurationMillis: Int,
    modifier: Modifier = Modifier
) {
    // Opacity: bottom line ~0.95f, diminishing as it rises
    val targetAlpha = when (rankFromBottom) {
        0 -> 0.95f
        1 -> 0.70f
        2 -> 0.45f
        else -> 0.25f
    }

    // Font size: bottom line ~15.5sp, shrinking as it rises
    val targetFontSize = when (rankFromBottom) {
        0 -> 15.5f
        1 -> 14.0f
        2 -> 12.8f
        else -> 11.5f
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = animDurationMillis, easing = FastOutSlowInEasing),
        label = "line_alpha_${line.id}"
    )

    val animatedFontSize by animateFloatAsState(
        targetValue = targetFontSize,
        animationSpec = tween(durationMillis = animDurationMillis, easing = FastOutSlowInEasing),
        label = "line_font_size_${line.id}"
    )

    // Slight gentle upward entry slide for the newest line
    var hasAppeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasAppeared = true
    }
    val animatedOffsetY by animateFloatAsState(
        targetValue = if (hasAppeared) 0f else 6f,
        animationSpec = tween(durationMillis = animDurationMillis, easing = FastOutSlowInEasing),
        label = "line_offset_${line.id}"
    )

    Text(
        text = line.text,
        fontFamily = InterFontFamily,
        fontWeight = if (rankFromBottom == 0) FontWeight.Normal else FontWeight.Light,
        fontSize = animatedFontSize.sp,
        letterSpacing = 0.3.sp,
        lineHeight = (animatedFontSize * 1.35f).sp,
        color = Color.White.copy(alpha = animatedAlpha),
        textAlign = TextAlign.Start,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = animatedOffsetY
            }
            .testTag("ambient_line_${line.id}")
    )
}

/**
 * Ambient sound visualizer light blob:
 * Soft transparent white light radial gradient, blurred edges, roughly 30-40% peak opacity.
 * Slow sine-based breathing animation with period ~4.5 seconds.
 * Non-interactive quiet presence.
 */
@Composable
fun AmbientSoundBlob(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_sound_blob")
    val breathProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_anim"
    )

    // Breathing period: 2 * 2250ms = 4.5 seconds (~4-5 seconds)
    // Scale breathes between 0.88f and 1.14f
    val scale = 0.88f + 0.26f * breathProgress
    // Opacity: peak roughly 35-40% (0.38f), minimum roughly 24% (0.24f)
    val peakAlpha = 0.24f + 0.14f * breathProgress

    Canvas(
        modifier = modifier.testTag("ambient_sound_blob")
    ) {
        val maxRadius = (size.minDimension / 2f) * scale
        val centerOffset = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = peakAlpha),
                    Color.White.copy(alpha = peakAlpha * 0.72f),
                    Color.White.copy(alpha = peakAlpha * 0.38f),
                    Color.White.copy(alpha = peakAlpha * 0.10f),
                    Color.Transparent
                ),
                center = centerOffset,
                radius = maxRadius
            ),
            radius = maxRadius,
            center = centerOffset
        )
    }
}
