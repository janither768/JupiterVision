package com.jupiter.vision.tracemachine.model

import androidx.compose.ui.geometry.Offset

/**
 * A single relative position node in the TraceMachine living trail.
 * Units: relative meters from start of trail session (x = east/right, y = north/up).
 */
data class TracePoint(
    val x: Float,
    val y: Float,
    val stepsSinceAnchor: Int,
    val timestampMs: Long
)

/**
 * An anchor event: manual user pin drop or automatic ZUPT stillness anchor.
 */
data class AnchorPoint(
    val x: Float,
    val y: Float,
    val index: Int,
    val isManual: Boolean,
    val timestampMs: Long
)

/**
 * Complete immutable snapshot of the active TraceMachine trail session.
 * Lives purely in memory. No disk writes, no Room, no database.
 */
data class TraceState(
    val points: List<TracePoint> = emptyList(),
    val anchors: List<AnchorPoint> = emptyList(),
    val currentPosition: Offset = Offset.Zero,
    val currentHeadingRad: Float = 0f,
    val stepsSinceAnchor: Int = 0,
    val totalSteps: Int = 0,
    val isSessionActive: Boolean = false,
    val isCalibratingSwingAxis: Boolean = true,
    val isZuptStill: Boolean = false,
    val strideLengthMeters: Float = 0.70f
)
