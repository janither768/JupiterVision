package com.jupiter.vision.dotpro.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jupiter.vision.dotpro.model.AmbientState
import com.jupiter.vision.dotpro.model.DoTProEvent
import com.jupiter.vision.dotpro.service.DotProService
import com.jupiter.vision.dotpro.service.DotProServiceState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 6. EXPANDED DIAGNOSTIC UI SURFACE — DoTPro Module
 * - Prominent state banner (ACTIVE | AMBIENT | SILENT) at top.
 * - Live reference level (dBFS) & normalization offset (dB).
 * - Per-band lift (dB) for 5 bands (<120Hz, 120-500, 500-2k, 2k-6k, >6k) as small bars.
 * - SNR (dB), silent gate state (OPEN / CLOSED).
 * - Battery tier (0..4), thermal status, audio source (UNPROCESSED/VOICE_RECOGNITION/MIC).
 * - YAMNet delegate (XNNPACK / NNAPI) and current cadence.
 * - Side-by-side comparison of raw RMS and refined RMS demonstrating scale normalization.
 * - Auto-scrolling live event list with displayLabel formatting.
 */
@Composable
fun DotProDebugScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val serviceState by DotProService.serviceStateFlow.collectAsState()
    val ambientState by DotProService.ambientStateFlow.collectAsState()
    val refinementTelemetry by DotProService.refinementTelemetryFlow.collectAsState()
    val activeAudioSource by DotProService.activeAudioSourceFlow.collectAsState()
    val features by DotProService.currentFeaturesFlow.collectAsState()
    val currentSignal by DotProService.currentSignalFlow.collectAsState()
    val signalConfidence by DotProService.currentSignalConfidenceFlow.collectAsState()
    val currentDisplayLabel by DotProService.currentDisplayLabelFlow.collectAsState()
    val yamNetStatus by DotProService.yamNetStatusFlow.collectAsState()
    val rawYamNetClass by DotProService.rawYamNetClassFlow.collectAsState()
    val events by DotProService.recentEventsFlow.collectAsState()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            DotProService.startService(context)
        }
    }

    // Auto-scroll state management
    val listState = rememberLazyListState()
    var autoScrollEnabled by remember { mutableStateOf(true) }

    val isScrollInProgress = listState.isScrollInProgress
    val isAtBottom by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            if (total == 0) return@derivedStateOf true
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 1
        }
    }

    LaunchedEffect(isScrollInProgress, isAtBottom) {
        if (isScrollInProgress) {
            autoScrollEnabled = isAtBottom
        } else if (isAtBottom) {
            autoScrollEnabled = true
        }
    }

    LaunchedEffect(events.size) {
        if (events.isNotEmpty() && autoScrollEnabled) {
            listState.animateScrollToItem(events.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            .padding(14.dp)
            .testTag("dotpro_debug_screen")
    ) {
        // --- 1. PROMINENT TOP STATE BANNER ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (ambientState) {
                    AmbientState.ACTIVE -> Color(0xFF1B5E20)
                    AmbientState.AMBIENT -> Color(0xFF006064)
                    AmbientState.SILENT -> Color(0xFF263238)
                }
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "STATE: ${ambientState.name}",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Text(
                        text = when (ambientState) {
                            AmbientState.ACTIVE -> "Active Acoustic Presence Track Firing"
                            AmbientState.AMBIENT -> "Stable Ambient Room Tone (Reflex Normalizer Engaged)"
                            AmbientState.SILENT -> "Silence Declared (SNR <= 3dB, Per-band EQ Disabled)"
                        },
                        fontSize = 10.5.sp,
                        color = Color(0xFFE0E0E0)
                    )
                }

                val tel = refinementTelemetry
                val gateClosed = tel?.isSilentGateClosed ?: (ambientState == AmbientState.SILENT)
                Text(
                    text = if (gateClosed) "GATE: CLOSED" else "GATE: OPEN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (gateClosed) Color(0xFFFF8A80) else Color(0xFF69F0AE),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (gateClosed) Color(0x33FF5252) else Color(0x3300E676))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- 2. SERVICE CONTROL ROW ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (hasAudioPermission) {
                        DotProService.startService(context)
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                enabled = serviceState != DotProServiceState.ACTIVE,
                modifier = Modifier.weight(1f).testTag("dotpro_start_btn")
            ) {
                Text("Start Service", fontSize = 12.sp)
            }

            Button(
                onClick = {
                    DotProService.stopService(context)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                enabled = serviceState == DotProServiceState.ACTIVE || serviceState == DotProServiceState.DEGRADED,
                modifier = Modifier.weight(1f).testTag("dotpro_stop_btn")
            ) {
                Text("Stop Service", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // --- 3. DIAGNOSTIC TELEMETRY CARD ---
        val tel = refinementTelemetry
        val rawRmsVal = tel?.rawRms ?: 0f
        val refinedRmsVal = tel?.refinedRms ?: (features?.frameRms ?: 0f)
        val snrDb = tel?.snrDb ?: 0f
        val refLoudnessDb = tel?.referenceLoudnessDb ?: -45f
        val normOffsetDb = tel?.normalizationOffsetDb ?: 0f
        val batteryTier = tel?.batteryTier ?: 0
        val thermalStatus = tel?.thermalStatus ?: "NONE"
        val yamNetDelegate = tel?.yamNetDelegate ?: "XNNPACK"
        val cadenceDesc = tel?.cadenceDescription ?: "7s (ambient)"
        val sourceWarning = tel?.audioSourceWarning

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B22))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Tier & Route Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "TIER $batteryTier",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (batteryTier) {
                                0 -> Color(0xFF81C784)
                                1 -> Color(0xFFFFD54F)
                                2 -> Color(0xFFFFB74D)
                                else -> Color(0xFFFF7043)
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF2A2A35))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                        Text(
                            text = "THERMAL: $thermalStatus",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB0BEC5),
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF2A2A35))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = activeAudioSource,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD54F),
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0x33FFD54F))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                        Text(
                            text = yamNetDelegate,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFCE93D8),
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0x33CE93D8))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                if (sourceWarning != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = sourceWarning,
                        fontSize = 9.5.sp,
                        color = Color(0xFFFFAB91),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Normalization & Reference Level
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "REF: ${String.format(Locale.US, "%.1f", refLoudnessDb)} dBFS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        color = Color(0xFF80D8FF)
                    )
                    Text(
                        text = "OFFSET: ${if (normOffsetDb >= 0) "+" else ""}${String.format(Locale.US, "%.1f", normOffsetDb)} dB",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (normOffsetDb > 0) Color(0xFF69F0AE) else Color(0xFFFFD54F)
                    )
                    Text(
                        text = "SNR: ${String.format(Locale.US, "%.1f", snrDb)} dB",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        color = if (snrDb > 3f) Color(0xFF80CBC4) else Color(0xFFFF8A80)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Live Side-by-Side RMS Sparklines (Raw vs Refined Normalization Visual)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "RAW RMS: ${String.format(Locale.US, "%.4f", rawRmsVal)}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        LinearProgressIndicator(
                            progress = { (rawRmsVal * 8f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = Color(0xFF78909C),
                            trackColor = Color(0xFF26262E)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "NORMALIZED: ${String.format(Locale.US, "%.4f", refinedRmsVal)}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            color = Color(0xFF00E5FF)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        LinearProgressIndicator(
                            progress = { (refinedRmsVal * 8f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = Color(0xFF00E5FF),
                            trackColor = Color(0xFF26262E)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Per-Band Lift (5 Bands EQ Tilt)
                val bandLifts = tel?.bandLiftsDb ?: FloatArray(5)
                val bandNames = listOf("<120", "120-500", "500-2k", "2k-6k", ">6k")
                Text(
                    text = "5-BAND EQ TILT (CAPPED +12dB):",
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFB0BEC5)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (b in 0 until 5) {
                        val lift = bandLifts.getOrElse(b) { 0f }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${bandNames[b]}: +${String.format(Locale.US, "%.0f", lift)}",
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.LightGray
                            )
                            LinearProgressIndicator(
                                progress = { (lift / 12f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(3.dp),
                                color = Color(0xFFCE93D8),
                                trackColor = Color(0xFF26262E)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Display Label & Taxonomy Readout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DISPLAY: $currentDisplayLabel",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF80D8FF)
                        )
                        Text(
                            text = "TAXONOMY: $currentSignal (${(signalConfidence * 100).toInt()}%)",
                            fontSize = 11.5.sp,
                            color = Color(0xFFB0BEC5)
                        )
                    }
                    Text(
                        text = "CADENCE: $cadenceDesc",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFFFD54F)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "YAMNET: $yamNetStatus",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (yamNetStatus.startsWith("ACTIVE")) Color(0xFF69F0AE) else Color(0xFFFFD54F)
                    )
                    Text(
                        text = "TOP-1: $rawYamNetClass",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFCE93D8)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- 4. LIVE EMITTED EVENTS HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Live Emitted Events (${events.size})",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = Color.White
                )
                if (!autoScrollEnabled && events.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PAUSED (Tap to resume)",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB74D),
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF3E2723))
                            .clickable {
                                autoScrollEnabled = true
                                coroutineScope.launch {
                                    listState.animateScrollToItem(events.size - 1)
                                }
                            }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = if (autoScrollEnabled) "Auto-scroll: ON" else "Auto-scroll: PAUSED",
                fontSize = 10.5.sp,
                color = if (autoScrollEnabled) Color(0xFF81C784) else Color(0xFFFFB74D)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // --- 5. LIVE EVENT LIST ---
        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF16161C)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (serviceState == DotProServiceState.ACTIVE)
                        "Listening... Waiting for acoustic presence event"
                    else
                        "Service stopped. Press 'Start Service' to run pipeline.",
                    color = Color(0xFF666666),
                    fontSize = 11.5.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(events, key = { it.timestampMs.toString() + it.signal.name + it.presence.name + it.displayLabel }) { event ->
                    EventItemCard(event)
                }
            }
        }
    }
}

@Composable
private fun EventItemCard(event: DoTProEvent) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val formattedTime = remember(event.timestampMs) { timeFormat.format(Date(event.timestampMs)) }

    val displayTitle = remember(event.displayLabel, event.signal) {
        if (event.displayLabel.isNotBlank()) {
            event.displayLabel
        } else {
            "${event.signal.name.lowercase().replaceFirstChar { it.titlecase(Locale.US) }}, stable"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF191922))
    ) {
        Column(modifier = Modifier.padding(7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF80D8FF)
                    )
                    Text(
                        text = "[${event.state.name}]",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (event.state) {
                            AmbientState.ACTIVE -> Color(0xFF00E676)
                            AmbientState.AMBIENT -> Color(0xFF00E5FF)
                            AmbientState.SILENT -> Color(0xFFB0BEC5)
                        }
                    )
                    Text(
                        text = if (event.normalized) "[NORM]" else "[RAW]",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (event.normalized) Color(0xFF69F0AE) else Color(0xFFFFAB91)
                    )
                }
                Text(
                    text = formattedTime,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF888888)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "SRC: ${event.source.name}",
                    fontSize = 10.5.sp,
                    color = Color(0xFFE0E0E0)
                )
                Text(
                    text = "PRESENCE: ${event.presence.name}",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (event.presence.name) {
                        "ENTER" -> Color(0xFF81C784)
                        "SUSTAINED" -> Color(0xFFFFF176)
                        else -> Color(0xFFE57373)
                    }
                )
                Text(
                    text = "PROX: ${event.proximity.name}",
                    fontSize = 10.5.sp,
                    color = Color(0xFFFFB74D)
                )
                Text(
                    text = "TIER: ${event.batteryTier}",
                    fontSize = 10.sp,
                    color = Color(0xFF90A4AE)
                )
                Text(
                    text = "CONF: ${(event.confidence * 100).toInt()}%",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4DD0E1)
                )
            }
        }
    }
}
