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
import com.jupiter.vision.dotpro.model.DoTProEvent
import com.jupiter.vision.dotpro.service.DotProService
import com.jupiter.vision.dotpro.service.DotProServiceState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 7. DIAGNOSTIC UI SURFACE
 * - Start / stop service buttons.
 * - Live list of emitted events displaying displayLabel string ("Music, stable").
 * - Auto-scroll: auto-scrolls to newest entry as it arrives. Pauses on manual scroll up,
 *   resumes on reaching bottom.
 * - Live raw energy + current signal label readout + YAMNet status.
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

    // Track scroll interactions: pause when scrolled up, resume when at bottom
    LaunchedEffect(isScrollInProgress, isAtBottom) {
        if (isScrollInProgress) {
            autoScrollEnabled = isAtBottom
        } else if (isAtBottom) {
            autoScrollEnabled = true
        }
    }

    // Auto-scroll to newest event when active
    LaunchedEffect(events.size) {
        if (events.isNotEmpty() && autoScrollEnabled) {
            listState.animateScrollToItem(events.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121214))
            .padding(16.dp)
            .testTag("dotpro_debug_screen")
    ) {
        // Header
        Text(
            text = "DoTPro Module Diagnostic",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "YAMNet Reflex Pipeline (Gated & Temporally Smoothed)",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFAAAAAA)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Control Row
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
                Text("Start Service")
            }

            Button(
                onClick = {
                    DotProService.stopService(context)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                enabled = serviceState == DotProServiceState.ACTIVE || serviceState == DotProServiceState.DEGRADED,
                modifier = Modifier.weight(1f).testTag("dotpro_stop_btn")
            ) {
                Text("Stop Service")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Diagnostic Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STATUS: $serviceState",
                        fontWeight = FontWeight.SemiBold,
                        color = when (serviceState) {
                            DotProServiceState.ACTIVE -> Color(0xFF66BB6A)
                            DotProServiceState.DEGRADED -> Color(0xFFFFA726)
                            DotProServiceState.SUSPENDED -> Color(0xFF42A5F5)
                            else -> Color(0xFFEF5350)
                        },
                        fontSize = 13.sp
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Ambient State Pill
                        Text(
                            text = ambientState.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (ambientState) {
                                com.jupiter.vision.dotpro.model.AmbientState.ACTIVE -> Color(0xFF00E676)
                                com.jupiter.vision.dotpro.model.AmbientState.AMBIENT -> Color(0xFF00E5FF)
                                com.jupiter.vision.dotpro.model.AmbientState.SILENT -> Color(0xFFB0BEC5)
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when (ambientState) {
                                        com.jupiter.vision.dotpro.model.AmbientState.ACTIVE -> Color(0x3300E676)
                                        com.jupiter.vision.dotpro.model.AmbientState.AMBIENT -> Color(0x3300E5FF)
                                        com.jupiter.vision.dotpro.model.AmbientState.SILENT -> Color(0x33B0BEC5)
                                    }
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        // Audio Source Pill
                        Text(
                            text = activeAudioSource,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD54F),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x33FFD54F))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Refinement Pipeline Telemetry Row
                val tel = refinementTelemetry
                val snrDb = tel?.snrDb ?: 0f
                val makeupDb = tel?.makeupGainDb ?: 0f
                val floorRms = tel?.hardwareFloorRms ?: 0.003f
                val compRedDb = tel?.maxCompressorReductionDb ?: 0f
                val refinedRmsVal = tel?.refinedRms ?: (features?.frameRms ?: 0f)
                val rawRmsVal = tel?.rawRms ?: (features?.frameRms ?: 0f)

                Text(
                    text = "REFINEMENT: 48kHz->16kHz | SNR: ${String.format(Locale.US, "%.1f", snrDb)} dB | LIFT: +${String.format(Locale.US, "%.1f", makeupDb)} dB | COMP: -${String.format(Locale.US, "%.1f", compRedDb)} dB | FLOOR: ${String.format(Locale.US, "%.4f", floorRms)}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF80CBC4)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Raw Energy Readout
                val rawEnergy = refinedRmsVal
                val baseline = features?.baselineRms ?: 0f
                val changeMag = features?.changeMagnitude ?: 0f
                val velocity = features?.envelopeVelocity ?: 0f
                val motion = features?.motion?.name ?: "UNKNOWN"
                val isTransient = features?.isTransient ?: false

                Text(
                    text = "REFINED RMS: ${String.format(Locale.US, "%.3f", rawEnergy)} (RAW: ${String.format(Locale.US, "%.3f", rawRmsVal)}) | BASELINE: ${String.format(Locale.US, "%.3f", baseline)} | DELTA: ${String.format(Locale.US, "%.3f", changeMag)}",
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.LightGray
                )

                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (rawEnergy * 8f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (isTransient) Color(0xFFFF5252) else if (ambientState == com.jupiter.vision.dotpro.model.AmbientState.AMBIENT) Color(0xFF00E5FF) else Color(0xFF00E676),
                    trackColor = Color(0xFF333333)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Display Label & Current Signal Readout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DISPLAY: $currentDisplayLabel",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF80D8FF)
                        )
                        Text(
                            text = "TAXONOMY: $currentSignal (${(signalConfidence * 100).toInt()}%)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFB0BEC5)
                        )
                    }

                    Text(
                        text = "MOTION: $motion",
                        fontSize = 11.sp,
                        color = Color(0xFFB0BEC5)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Gating and Model Engine Readout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "YAMNET: $yamNetStatus",
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (yamNetStatus.startsWith("ACTIVE")) Color(0xFF69F0AE) else Color(0xFFFFD54F)
                    )
                    Text(
                        text = "TOP-1: $rawYamNetClass",
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFCE93D8)
                    )
                }

                Text(
                    text = "TRANSIENT: $isTransient | VEL: ${String.format(Locale.US, "%.4f", velocity)} | CENTROID: ${(features?.spectralCentroid ?: 0f).toInt()}Hz",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF9E9E9E)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Live Emitted Events Header & Auto-scroll indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Live Emitted Events (${events.size})",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White
                )
                if (!autoScrollEnabled && events.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PAUSED (Tap to bottom)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB74D),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF3E2723))
                            .clickable {
                                autoScrollEnabled = true
                                coroutineScope.launch {
                                    listState.animateScrollToItem(events.size - 1)
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = if (autoScrollEnabled) "Auto-scroll: ON" else "Auto-scroll: PAUSED",
                fontSize = 11.sp,
                color = if (autoScrollEnabled) Color(0xFF81C784) else Color(0xFFFFB74D)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Live Event List
        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF18181C)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (serviceState == DotProServiceState.ACTIVE)
                        "Listening... Waiting for acoustic presence event"
                    else
                        "Service stopped. Press 'Start Service' to run pipeline.",
                    color = Color(0xFF666666),
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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

    // Use displayLabel formatted string ("<Class>, <state>") if present, fallback to titlecase
    val displayTitle = remember(event.displayLabel, event.signal) {
        if (event.displayLabel.isNotBlank()) {
            event.displayLabel
        } else {
            "${event.signal.name.lowercase().replaceFirstChar { it.titlecase(Locale.US) }}, stable"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A22))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = Color(0xFF80D8FF)
                    )
                    Text(
                        text = "[${event.state.name}]",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (event.state == com.jupiter.vision.dotpro.model.AmbientState.AMBIENT) Color(0xFF00E5FF) else Color(0xFF00E676)
                    )
                    Text(
                        text = "[${event.signal.name}]",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF90A4AE)
                    )
                }
                Text(
                    text = formattedTime,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF888888)
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "SRC: ${event.source.name}",
                    fontSize = 11.sp,
                    color = Color(0xFFE0E0E0)
                )
                Text(
                    text = "PRESENCE: ${event.presence.name}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (event.presence.name) {
                        "ENTER" -> Color(0xFF81C784)
                        "SUSTAINED" -> Color(0xFFFFF176)
                        else -> Color(0xFFE57373)
                    }
                )
                Text(
                    text = "PROX: ${event.proximity.name}",
                    fontSize = 11.sp,
                    color = Color(0xFFFFB74D)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "MOTION: ${event.motion.name}",
                    fontSize = 10.sp,
                    color = Color(0xFFB0BEC5)
                )
                Text(
                    text = "BEARING: ${event.bearing.name}",
                    fontSize = 10.sp,
                    color = Color(0xFFB0BEC5)
                )
                Text(
                    text = "CONF: ${(event.confidence * 100).toInt()}%",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4DD0E1)
                )
                if (event.change) {
                    Text(
                        text = "[CHANGE]",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF8A80)
                    )
                }
            }
        }
    }
}

