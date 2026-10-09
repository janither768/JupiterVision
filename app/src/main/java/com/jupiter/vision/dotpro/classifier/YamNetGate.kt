package com.jupiter.vision.dotpro.classifier

import com.jupiter.vision.dotpro.features.FrameFeatures

/**
 * Trigger reason for YAMNet invocation.
 */
enum class GateTriggerReason {
    NONE,
    ENERGY_ACTIVITY,
    TRANSIENT_SPIKE,
    HEARTBEAT
}

/**
 * 2. GATING — YamNetGate
 * Enforces the battery contract: YAMNet is invoked only when the deterministic layer
 * signals something is worth classifying.
 *
 * Invocation triggers:
 * (a) Energy crosses an activity threshold above baseline.
 * (b) Change magnitude spikes (transient).
 * (c) Heartbeat: every 3 seconds while a presence track is active, to refresh the label.
 *
 * Between invocations, the last stable label is retained.
 * When the room is silent or steady, YAMNet is idle.
 */
class YamNetGate {

    companion object {
        // Minimum interval between inferences to prevent CPU burn on sustained sound
        private const val MIN_INFERENCE_INTERVAL_MS = 500L
        // Heartbeat interval while track is active
        private const val HEARTBEAT_INTERVAL_MS = 3000L
        // Multiplier over baseline for activity threshold
        private const val ACTIVITY_ENERGY_MARGIN = 0.012f
        private const val ACTIVITY_RATIO_FACTOR = 1.45f
        private const val TRANSIENT_DELTA_THRESHOLD = 0.018f
    }

    private var lastInferenceTimestampMs: Long = 0L
    var lastTriggerReason: GateTriggerReason = GateTriggerReason.NONE
        private set

    /**
     * Evaluates whether YAMNet should run for the current frame.
     */
    fun shouldInvoke(
        features: FrameFeatures,
        hasActivePresenceTrack: Boolean,
        currentTimestampMs: Long
    ): Boolean {
        val timeSinceLast = currentTimestampMs - lastInferenceTimestampMs

        // Check heartbeat trigger: every 3s while presence track is active
        if (hasActivePresenceTrack && timeSinceLast >= HEARTBEAT_INTERVAL_MS) {
            lastTriggerReason = GateTriggerReason.HEARTBEAT
            lastInferenceTimestampMs = currentTimestampMs
            return true
        }

        // Throttle check: must not invoke faster than MIN_INFERENCE_INTERVAL_MS
        if (timeSinceLast < MIN_INFERENCE_INTERVAL_MS) {
            lastTriggerReason = GateTriggerReason.NONE
            return false
        }

        // Trigger (b): Change magnitude spikes (transient)
        if (features.isTransient || features.changeMagnitude > TRANSIENT_DELTA_THRESHOLD) {
            lastTriggerReason = GateTriggerReason.TRANSIENT_SPIKE
            lastInferenceTimestampMs = currentTimestampMs
            return true
        }

        // Trigger (a): Energy crosses an activity threshold above baseline
        val activityThreshold = (features.baselineRms * ACTIVITY_RATIO_FACTOR) + ACTIVITY_ENERGY_MARGIN
        if (features.frameRms > activityThreshold) {
            lastTriggerReason = GateTriggerReason.ENERGY_ACTIVITY
            lastInferenceTimestampMs = currentTimestampMs
            return true
        }

        // Room is silent or steady -> YAMNet is idle
        lastTriggerReason = GateTriggerReason.NONE
        return false
    }

    fun reset() {
        lastInferenceTimestampMs = 0L
        lastTriggerReason = GateTriggerReason.NONE
    }
}
