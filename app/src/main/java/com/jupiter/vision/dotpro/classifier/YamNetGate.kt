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
 * Enforces the battery contract, SNR gate agreement, and state transitions with hysteresis.
 *
 * Three states:
 * - ACTIVE:
 *   - Presence track is firing / something is changing.
 *   - YAMNet fires immediately on activity/transient spikes (500ms debounce) + ~1.5s refresh.
 *
 * - AMBIENT:
 *   - No active presence track AND SNR > SILENT_THRESHOLD AND stable content above reference.
 *   - YAMNet fires on cadence: 7s (Tier 0), 15s (Tier 1), suspended (Tier 2..4).
 *
 * - SILENT:
 *   - No active presence track AND SNR <= SILENT_THRESHOLD for sustained window (~2s).
 *   - YAMNet does NOT fire (idle, 0 CPU). Silence is a state.
 */
class YamNetGate {

    companion object {
        // Minimum debounce interval in ACTIVE state
        private const val MIN_ACTIVE_INTERVAL_MS = 500L
        private const val ACTIVE_REFRESH_INTERVAL_MS = 1500L

        // AMBIENT Cadences
        private const val AMBIENT_CADENCE_TIER0_MS = 7000L
        private const val AMBIENT_CADENCE_TIER1_MS = 15000L

        // Hysteresis frame hold counters (at 20fps = 50ms per frame)
        private const val SILENCE_HOLD_FRAMES = 35  // ~1.75s below floor before AMBIENT -> SILENT
        private const val AMBIENT_HOLD_FRAMES = 8   // ~400ms above floor before SILENT -> AMBIENT

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
    var currentBatteryTier: Int = 0

    /**
     * Evaluates state transitions and determines if YAMNet should execute.
     */
    fun evaluate(
        features: FrameFeatures,
        refinedRms: Float,
        hardwareFloorRms: Float,
        hasActivePresenceTrack: Boolean,
        currentTimestampMs: Long,
        snrDb: Float = 10f,
        isSilentGateClosed: Boolean = false,
        batteryTier: Int = 0
    ): Boolean {
        currentBatteryTier = batteryTier

        // 1. Update State Transitions with Hysteresis
        updateStateTransitions(
            refinedRms = refinedRms,
            hardwareFloorRms = hardwareFloorRms,
            hasActivePresenceTrack = hasActivePresenceTrack,
            snrDb = snrDb,
            isSilentGateClosed = isSilentGateClosed
        )

        // 2. Battery Tier 3 & 4: YAMNet is fully idle across all states
        if (batteryTier >= 3) {
            cadenceDescription = "idle (tier $batteryTier power save)"
            lastTriggerReason = GateTriggerReason.NONE
            return false
        }

        // 3. Cadence and trigger evaluation per state
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
                // Tier 2: AMBIENT YAMNet suspended. Only ACTIVE spikes fire YAMNet.
                if (batteryTier >= 2) {
                    cadenceDescription = "idle (tier 2 suspended ambient)"
                    // Still detect sudden activity spike to transition to active
                    val isActivitySpike = features.isTransient ||
                            features.changeMagnitude > TRANSIENT_DELTA_THRESHOLD ||
                            features.frameRms > (features.baselineRms * ACTIVITY_RATIO_FACTOR + ACTIVITY_ENERGY_MARGIN)
                    if (isActivitySpike && timeSinceLast >= MIN_ACTIVE_INTERVAL_MS) {
                        currentState = AmbientState.ACTIVE
                        lastTriggerReason = if (features.isTransient) GateTriggerReason.TRANSIENT_SPIKE else GateTriggerReason.ENERGY_ACTIVITY
                        lastInferenceTimestampMs = currentTimestampMs
                        return true
                    }
                    lastTriggerReason = GateTriggerReason.NONE
                    return false
                }

                val cadenceMs = when (batteryTier) {
                    1 -> {
                        cadenceDescription = "15s (tier 1 ambient)"
                        AMBIENT_CADENCE_TIER1_MS
                    }
                    else -> {
                        cadenceDescription = "7s (ambient)"
                        AMBIENT_CADENCE_TIER0_MS
                    }
                }

                // Check for activity spike pushing into active immediately
                val isActivitySpike = features.isTransient ||
                        features.changeMagnitude > TRANSIENT_DELTA_THRESHOLD ||
                        features.frameRms > (features.baselineRms * ACTIVITY_RATIO_FACTOR + ACTIVITY_ENERGY_MARGIN)

                if (isActivitySpike && timeSinceLast >= MIN_ACTIVE_INTERVAL_MS) {
                    currentState = AmbientState.ACTIVE
                    lastTriggerReason = if (features.isTransient) GateTriggerReason.TRANSIENT_SPIKE else GateTriggerReason.ENERGY_ACTIVITY
                    lastInferenceTimestampMs = currentTimestampMs
                    return true
                }

                // Periodic ambient cadence
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

                // If activity spike occurs from silence, wake to active
                val isActivitySpike = features.isTransient || features.changeMagnitude > TRANSIENT_DELTA_THRESHOLD
                if (isActivitySpike && timeSinceLast >= MIN_ACTIVE_INTERVAL_MS) {
                    currentState = AmbientState.ACTIVE
                    lastTriggerReason = GateTriggerReason.TRANSIENT_SPIKE
                    lastInferenceTimestampMs = currentTimestampMs
                    return true
                }

                // SILENT: YAMNet does not fire
                lastTriggerReason = GateTriggerReason.NONE
                return false
            }
        }
    }

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

    private fun updateStateTransitions(
        refinedRms: Float,
        hardwareFloorRms: Float,
        hasActivePresenceTrack: Boolean,
        snrDb: Float,
        isSilentGateClosed: Boolean
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
                if (isSilentGateClosed || (snrDb <= 3.0f && refinedRms <= silenceThreshold)) {
                    currentState = AmbientState.SILENT
                    aboveFloorCounter = 0
                } else if (refinedRms > ambientThreshold) {
                    currentState = AmbientState.AMBIENT
                    belowFloorCounter = 0
                } else {
                    currentState = AmbientState.SILENT
                    aboveFloorCounter = 0
                }
            }

            AmbientState.AMBIENT -> {
                if (isSilentGateClosed || snrDb <= 3.0f || refinedRms <= silenceThreshold) {
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
                if (!isSilentGateClosed && snrDb > 3.0f && refinedRms > ambientThreshold) {
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
