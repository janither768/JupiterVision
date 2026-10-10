package com.jupiter.vision.dotpro.classifier

import com.jupiter.vision.dotpro.features.FrameFeatures
import com.jupiter.vision.dotpro.model.AmbientState

/**
 * Trigger reason for YAMNet invocation.
 */
enum class GateTriggerReason {
    NONE,
    ENERGY_ACTIVITY,
    TRANSIENT_SPIKE,
    ACTIVE_REFRESH,
    AMBIENT_CADENCE
}

/**
 * 2. GATING & THREE-STATE AMBIENT MODEL — YamNetGate
 * Enforces the battery contract and state transitions with hysteresis:
 *
 * ACTIVE:
 * - A presence track is firing / something is changing.
 * - YAMNet fires immediately on activity/transient spikes + ~1.5s refresh.
 *
 * AMBIENT:
 * - No active track, but stable room tone above hardware floor.
 * - YAMNet fires on slow cadence: 7s (normal), 15s (low battery < 20% or power save), idle (< 10%).
 *
 * SILENT:
 * - Genuinely at or below hardware noise floor.
 * - YAMNet does NOT fire (idle).
 */
class YamNetGate {

    companion object {
        // Minimum debounce interval in ACTIVE state
        private const val MIN_ACTIVE_INTERVAL_MS = 500L
        private const val ACTIVE_REFRESH_INTERVAL_MS = 1500L

        // AMBIENT Cadences
        private const val AMBIENT_CADENCE_NORMAL_MS = 7000L
        private const val AMBIENT_CADENCE_LOW_BATTERY_MS = 15000L

        // Hysteresis frame hold counters (at 20fps = 50ms per frame)
        private const val SILENCE_HOLD_FRAMES = 25  // 1.25s below floor before AMBIENT -> SILENT
        private const val AMBIENT_HOLD_FRAMES = 8   // 400ms above floor before SILENT -> AMBIENT

        // Spikes above baseline for activity trigger
        private const val ACTIVITY_RATIO_FACTOR = 1.45f
        private const val ACTIVITY_ENERGY_MARGIN = 0.012f
        private const val TRANSIENT_DELTA_THRESHOLD = 0.018f
    }

    var currentState: AmbientState = AmbientState.AMBIENT
        private set

    var lastTriggerReason: GateTriggerReason = GateTriggerReason.NONE
        private set

    var cadenceDescription: String = "7s (ambient)"
        private set

    private var lastInferenceTimestampMs: Long = 0L

    // Hysteresis transition hold counters
    private var belowFloorCounter = 0
    private var aboveFloorCounter = 0

    // Thermal & Battery status
    var batteryPercentage: Int = 100
    var isPowerSaveMode: Boolean = false

    /**
     * Backward-compatible helper method for evaluation.
     */
    fun shouldInvoke(
        features: FrameFeatures,
        hasActivePresenceTrack: Boolean,
        currentTimestampMs: Long
    ): Boolean {
        return evaluate(
            features = features,
            refinedRms = features.frameRms,
            hardwareFloorRms = 0.003f,
            hasActivePresenceTrack = hasActivePresenceTrack,
            currentTimestampMs = currentTimestampMs
        )
    }

    /**
     * Updates state transitions with hysteresis and evaluates whether YAMNet should run this frame.
     */
    fun evaluate(
        features: FrameFeatures,
        refinedRms: Float,
        hardwareFloorRms: Float,
        hasActivePresenceTrack: Boolean,
        currentTimestampMs: Long
    ): Boolean {
        // 1. Update State Transitions with Hysteresis
        updateStateTransitions(refinedRms, hardwareFloorRms, hasActivePresenceTrack)

        // 2. Determine Cadence Interval based on State and Battery
        val timeSinceLast = currentTimestampMs - lastInferenceTimestampMs

        when (currentState) {
            AmbientState.ACTIVE -> {
                cadenceDescription = "immediate (active)"

                // Check immediate transient or activity spike
                val isActivitySpike = features.isTransient ||
                        features.changeMagnitude > TRANSIENT_DELTA_THRESHOLD ||
                        features.frameRms > (features.baselineRms * ACTIVITY_RATIO_FACTOR + ACTIVITY_ENERGY_MARGIN)

                if (isActivitySpike && timeSinceLast >= MIN_ACTIVE_INTERVAL_MS) {
                    lastTriggerReason = if (features.isTransient) GateTriggerReason.TRANSIENT_SPIKE else GateTriggerReason.ENERGY_ACTIVITY
                    lastInferenceTimestampMs = currentTimestampMs
                    return true
                }

                // Check active refresh cadence (~1.5s)
                if (timeSinceLast >= ACTIVE_REFRESH_INTERVAL_MS) {
                    lastTriggerReason = GateTriggerReason.ACTIVE_REFRESH
                    lastInferenceTimestampMs = currentTimestampMs
                    return true
                }

                lastTriggerReason = GateTriggerReason.NONE
                return false
            }

            AmbientState.AMBIENT -> {
                // Thermal/battery backoff:
                // Normal (> 20%): 7s
                // Low battery (< 20% or power save): 15s
                // Critical (< 10%): idle (suspended ambient)
                val cadenceMs = when {
                    batteryPercentage < 10 -> {
                        cadenceDescription = "idle (critical battery)"
                        Long.MAX_VALUE
                    }
                    batteryPercentage < 20 || isPowerSaveMode -> {
                        cadenceDescription = "15s (low-battery ambient)"
                        AMBIENT_CADENCE_LOW_BATTERY_MS
                    }
                    else -> {
                        cadenceDescription = "7s (ambient)"
                        AMBIENT_CADENCE_NORMAL_MS
                    }
                }

                // Check for activity spike that would push to active immediately
                val isActivitySpike = features.isTransient ||
                        features.changeMagnitude > TRANSIENT_DELTA_THRESHOLD ||
                        features.frameRms > (features.baselineRms * ACTIVITY_RATIO_FACTOR + ACTIVITY_ENERGY_MARGIN)

                if (isActivitySpike && timeSinceLast >= MIN_ACTIVE_INTERVAL_MS) {
                    currentState = AmbientState.ACTIVE
                    lastTriggerReason = if (features.isTransient) GateTriggerReason.TRANSIENT_SPIKE else GateTriggerReason.ENERGY_ACTIVITY
                    lastInferenceTimestampMs = currentTimestampMs
                    return true
                }

                // Periodic ambient refresh
                if (timeSinceLast >= cadenceMs) {
                    lastTriggerReason = GateTriggerReason.AMBIENT_CADENCE
                    lastInferenceTimestampMs = currentTimestampMs
                    return true
                }

                lastTriggerReason = GateTriggerReason.NONE
                return false
            }

            AmbientState.SILENT -> {
                cadenceDescription = "idle (silent)"

                // If activity spike occurs from silence, immediately wake to active
                val isActivitySpike = features.isTransient || features.changeMagnitude > TRANSIENT_DELTA_THRESHOLD
                if (isActivitySpike && timeSinceLast >= MIN_ACTIVE_INTERVAL_MS) {
                    currentState = AmbientState.ACTIVE
                    lastTriggerReason = GateTriggerReason.TRANSIENT_SPIKE
                    lastInferenceTimestampMs = currentTimestampMs
                    return true
                }

                // SILENT -> YAMNet is completely idle (0 CPU)
                lastTriggerReason = GateTriggerReason.NONE
                return false
            }
        }
    }

    /**
     * Strict hysteresis state transition machine:
     * - ACTIVE -> AMBIENT: last presence track exits and room tone > floor
     * - ACTIVE -> SILENT: last presence track exits and room tone <= floor
     * - AMBIENT -> ACTIVE: presence track begins firing
     * - AMBIENT -> SILENT: room tone drops below floor for sustained window (25 frames)
     * - SILENT -> AMBIENT: room tone crosses floor and holds (8 frames)
     * - SILENT -> ACTIVE: activity spike spawns presence track
     */
    private fun updateStateTransitions(
        refinedRms: Float,
        hardwareFloorRms: Float,
        hasActivePresenceTrack: Boolean
    ) {
        val ambientThreshold = hardwareFloorRms * 1.65f
        val silenceThreshold = hardwareFloorRms * 1.25f

        if (hasActivePresenceTrack) {
            currentState = AmbientState.ACTIVE
            belowFloorCounter = 0
            aboveFloorCounter = 0
            return
        }

        when (currentState) {
            AmbientState.ACTIVE -> {
                // Presence track just exited
                if (refinedRms > ambientThreshold) {
                    currentState = AmbientState.AMBIENT
                    belowFloorCounter = 0
                } else {
                    currentState = AmbientState.SILENT
                    aboveFloorCounter = 0
                }
            }

            AmbientState.AMBIENT -> {
                // Check if dropping into silence
                if (refinedRms <= silenceThreshold) {
                    belowFloorCounter++
                    if (belowFloorCounter >= SILENCE_HOLD_FRAMES) {
                        currentState = AmbientState.SILENT
                        belowFloorCounter = 0
                    }
                } else {
                    belowFloorCounter = 0
                }
            }

            AmbientState.SILENT -> {
                // Check if rising into ambient room tone
                if (refinedRms > ambientThreshold) {
                    aboveFloorCounter++
                    if (aboveFloorCounter >= AMBIENT_HOLD_FRAMES) {
                        currentState = AmbientState.AMBIENT
                        aboveFloorCounter = 0
                    }
                } else {
                    aboveFloorCounter = 0
                }
            }
        }
    }

    fun reset() {
        currentState = AmbientState.AMBIENT
        lastInferenceTimestampMs = 0L
        lastTriggerReason = GateTriggerReason.NONE
        belowFloorCounter = 0
        aboveFloorCounter = 0
        cadenceDescription = "7s (ambient)"
    }
}
