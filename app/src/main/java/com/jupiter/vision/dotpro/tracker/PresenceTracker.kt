package com.jupiter.vision.dotpro.tracker

import com.jupiter.vision.dotpro.features.FrameFeatures
import com.jupiter.vision.dotpro.model.Bearing
import com.jupiter.vision.dotpro.model.Motion
import com.jupiter.vision.dotpro.model.Presence
import com.jupiter.vision.dotpro.model.Proximity
import com.jupiter.vision.dotpro.model.Signal

/**
 * An active track inside the presence tracking state machine.
 */
data class PresenceTrack(
    val id: Long,
    val signal: Signal,
    val signalConfidence: Float,
    val proximity: Proximity,
    val motion: Motion,
    val bearing: Bearing,
    val presence: Presence,
    val ageFrames: Int,
    val lastChangeTimestampMs: Long,
    val overallConfidence: Float,
    val hasChanged: Boolean,
    val displayLabel: String = ""
)

/**
 * PresenceTracker:
 * - State machine holding 1..N active presence tracks.
 * - Hysteresis on every transition:
 *     - Enter threshold strictly higher than exit threshold.
 *     - Proximity tier changes: enter threshold > exit threshold.
 *     - Signal label changes: a new label must hold stable across N (e.g. 4) frames before replacing current label.
 * - Tracks age out when they stop sustaining and stop changing.
 * - No identity. No memory across sessions. Session-local only.
 */
class PresenceTracker {

    companion object {
        // Hysteresis Energy Thresholds:
        // Enter threshold strictly higher than exit threshold
        private const val ENTER_RMS_THRESHOLD = 0.022f
        private const val EXIT_RMS_THRESHOLD = 0.010f

        // Proximity tier thresholds with hysteresis:
        // NEAR: RMS > 0.08f (exit < 0.06f)
        // MID:  RMS > 0.035f (exit < 0.025f)
        // FAR:  otherwise
        private const val NEAR_ENTER_RMS = 0.080f
        private const val NEAR_EXIT_RMS = 0.060f
        private const val MID_ENTER_RMS = 0.035f
        private const val MID_EXIT_RMS = 0.025f

        // Label stability requirement: 4 consecutive frames (~200ms) before label replaces active track label
        private const val LABEL_STABILITY_FRAMES = 4

        // Maximum frames of silence/inactivity before a track ages out and transitions to EXIT
        private const val TRACK_TIMEOUT_FRAMES = 12 // ~600ms of below-exit energy
    }

    private var nextTrackId = 1L

    // Internal active track representation
    private class MutableTrack(
        val id: Long,
        var signal: Signal,
        var signalConfidence: Float,
        var proximity: Proximity,
        var motion: Motion,
        var bearing: Bearing,
        var presence: Presence,
        var ageFrames: Int = 0,
        var lastChangeTimestampMs: Long,
        var overallConfidence: Float,
        var candidateSignal: Signal = signal,
        var candidateSignalCount: Int = 0,
        var lowEnergyFrames: Int = 0,
        var isMarkedForRemoval: Boolean = false,
        var justChanged: Boolean = true,
        var displayLabel: String = ""
    )

    private val activeTracks = mutableListOf<MutableTrack>()

    fun hasActiveTracks(): Boolean = activeTracks.isNotEmpty()

    /**
     * Update presence state machine with new frame features and classified signal.
     * Returns the list of immutable snapshot tracks.
     */
    fun update(
        features: FrameFeatures,
        signal: Signal,
        signalConfidence: Float,
        bearing: Bearing,
        displayLabel: String = ""
    ): List<PresenceTrack> {
        val rms = features.frameRms
        val timestamp = features.timestampMs
        val motion = features.motion

        // 1. Check if we should spawn a new track
        if (activeTracks.isEmpty() && rms >= ENTER_RMS_THRESHOLD) {
            val initialProximity = determineInitialProximity(rms)
            val newTrack = MutableTrack(
                id = nextTrackId++,
                signal = signal,
                signalConfidence = signalConfidence,
                proximity = initialProximity,
                motion = motion,
                bearing = bearing,
                presence = Presence.ENTER,
                lastChangeTimestampMs = timestamp,
                overallConfidence = (signalConfidence * 0.5f + (rms * 5f).coerceAtMost(0.5f)).coerceIn(0.2f, 1.0f),
                justChanged = true,
                displayLabel = displayLabel
            )
            activeTracks.add(newTrack)
        }

        // 2. Update existing tracks with hysteresis
        val iterator = activeTracks.iterator()
        while (iterator.hasNext()) {
            val track = iterator.next()
            track.ageFrames++
            track.justChanged = false
            track.displayLabel = displayLabel

            // Presence State Transition with Hysteresis
            if (rms < EXIT_RMS_THRESHOLD) {
                track.lowEnergyFrames++
                if (track.presence != Presence.EXIT && track.lowEnergyFrames >= TRACK_TIMEOUT_FRAMES) {
                    track.presence = Presence.EXIT
                    track.justChanged = true
                    track.lastChangeTimestampMs = timestamp
                } else if (track.presence == Presence.EXIT && track.lowEnergyFrames >= TRACK_TIMEOUT_FRAMES + 4) {
                    track.isMarkedForRemoval = true
                }
            } else {
                track.lowEnergyFrames = 0
                if (track.presence == Presence.ENTER && track.ageFrames >= 2) {
                    track.presence = Presence.SUSTAINED
                    track.justChanged = true
                    track.lastChangeTimestampMs = timestamp
                } else if (track.presence == Presence.EXIT && rms >= ENTER_RMS_THRESHOLD) {
                    // Re-enter with enter threshold
                    track.presence = Presence.ENTER
                    track.justChanged = true
                    track.lastChangeTimestampMs = timestamp
                }
            }

            // Proximity Tier Transition with Hysteresis
            val newProximity = updateProximityWithHysteresis(track.proximity, rms)
            if (newProximity != track.proximity) {
                track.proximity = newProximity
                track.justChanged = true
                track.lastChangeTimestampMs = timestamp
            }

            // Motion Transition
            if (motion != Motion.UNKNOWN && motion != track.motion) {
                track.motion = motion
                track.justChanged = true
                track.lastChangeTimestampMs = timestamp
            }

            // Bearing Transition
            if (bearing != track.bearing) {
                track.bearing = bearing
                track.justChanged = true
                track.lastChangeTimestampMs = timestamp
            }

            // Signal Label Stability Transition (must hold for LABEL_STABILITY_FRAMES)
            if (signal != track.signal) {
                if (signal == track.candidateSignal) {
                    track.candidateSignalCount++
                    if (track.candidateSignalCount >= LABEL_STABILITY_FRAMES) {
                        track.signal = signal
                        track.signalConfidence = signalConfidence
                        track.justChanged = true
                        track.lastChangeTimestampMs = timestamp
                        track.candidateSignalCount = 0
                    }
                } else {
                    track.candidateSignal = signal
                    track.candidateSignalCount = 1
                }
            } else {
                track.candidateSignalCount = 0
                track.signalConfidence = (track.signalConfidence * 0.7f + signalConfidence * 0.3f)
            }

            // Overall Confidence calculation
            track.overallConfidence = (track.signalConfidence * 0.6f + (rms * 4f).coerceAtMost(0.4f)).coerceIn(0.15f, 1.0f)
        }

        // 3. Remove aged-out tracks
        activeTracks.removeAll { it.isMarkedForRemoval }

        // 4. Return immutable snapshots
        return activeTracks.map {
            PresenceTrack(
                id = it.id,
                signal = it.signal,
                signalConfidence = it.signalConfidence,
                proximity = it.proximity,
                motion = it.motion,
                bearing = it.bearing,
                presence = it.presence,
                ageFrames = it.ageFrames,
                lastChangeTimestampMs = it.lastChangeTimestampMs,
                overallConfidence = it.overallConfidence,
                hasChanged = it.justChanged,
                displayLabel = it.displayLabel
            )
        }
    }

    private fun determineInitialProximity(rms: Float): Proximity {
        return when {
            rms >= NEAR_ENTER_RMS -> Proximity.NEAR
            rms >= MID_ENTER_RMS -> Proximity.MID
            else -> Proximity.FAR
        }
    }

    private fun updateProximityWithHysteresis(current: Proximity, rms: Float): Proximity {
        return when (current) {
            Proximity.NEAR -> {
                if (rms < NEAR_EXIT_RMS) {
                    if (rms < MID_EXIT_RMS) Proximity.FAR else Proximity.MID
                } else {
                    Proximity.NEAR
                }
            }
            Proximity.MID -> {
                if (rms >= NEAR_ENTER_RMS) {
                    Proximity.NEAR
                } else if (rms < MID_EXIT_RMS) {
                    Proximity.FAR
                } else {
                    Proximity.MID
                }
            }
            Proximity.FAR -> {
                if (rms >= NEAR_ENTER_RMS) {
                    Proximity.NEAR
                } else if (rms >= MID_ENTER_RMS) {
                    Proximity.MID
                } else {
                    Proximity.FAR
                }
            }
        }
    }

    fun reset() {
        activeTracks.clear()
    }
}
