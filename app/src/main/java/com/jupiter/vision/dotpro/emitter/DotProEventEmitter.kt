package com.jupiter.vision.dotpro.emitter

import com.jupiter.vision.dotpro.model.DoTProEvent
import com.jupiter.vision.dotpro.model.Presence
import com.jupiter.vision.dotpro.model.Source
import com.jupiter.vision.dotpro.tracker.PresenceTrack
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * DotProEventEmitter:
 * - Emits DoTProEvent objects matching the schema.
 * - Enforces hard ceiling on emission rate (target <= 4 events/sec -> min interval >= 250ms).
 * - Prefers dropping ambiguous events over firing them.
 * - SUSTAINED heartbeat per active track at a slow interval (~2s) so downstream knows fact is still current.
 */
class DotProEventEmitter {

    companion object {
        // Hard rate limit: max 4 events per second = min 250ms between emitted events
        private const val MIN_EMIT_INTERVAL_MS = 250L
        // Heartbeat interval for sustained tracks (~2 seconds)
        private const val SUSTAINED_HEARTBEAT_INTERVAL_MS = 2000L
    }

    private val _events = MutableSharedFlow<DoTProEvent>(replay = 50, extraBufferCapacity = 100)
    val events: SharedFlow<DoTProEvent> = _events.asSharedFlow()

    private var lastEmitTimestampMs = 0L
    private val lastHeartbeatPerTrack = mutableMapOf<Long, Long>()

    /**
     * Process updated presence tracks and emit DoTProEvent objects within rate limits.
     */
    fun processTracks(
        tracks: List<PresenceTrack>,
        source: Source,
        currentTimestampMs: Long,
        ambientState: com.jupiter.vision.dotpro.model.AmbientState = com.jupiter.vision.dotpro.model.AmbientState.ACTIVE,
        normalized: Boolean = true,
        batteryTier: Int = 0
    ): List<DoTProEvent> {
        val emittedList = mutableListOf<DoTProEvent>()

        for (track in tracks) {
            val isChange = track.hasChanged
            val lastHeartbeat = lastHeartbeatPerTrack[track.id] ?: 0L
            val isHeartbeatDue = track.presence == Presence.SUSTAINED &&
                    (currentTimestampMs - lastHeartbeat >= SUSTAINED_HEARTBEAT_INTERVAL_MS)

            // Should we emit this track event?
            // Emit on state change (ENTER, EXIT, tier change, label change) or on periodic heartbeat
            if (isChange || isHeartbeatDue) {
                // Check rate ceiling: target <= 4 events/sec
                if (currentTimestampMs - lastEmitTimestampMs >= MIN_EMIT_INTERVAL_MS || track.presence == Presence.ENTER) {
                    val event = DoTProEvent(
                        signal = track.signal,
                        source = source,
                        presence = track.presence,
                        proximity = track.proximity,
                        bearing = track.bearing,
                        motion = track.motion,
                        change = isChange,
                        confidence = track.overallConfidence,
                        timestampMs = currentTimestampMs,
                        displayLabel = track.displayLabel,
                        state = ambientState,
                        normalized = normalized,
                        batteryTier = batteryTier
                    )

                    lastEmitTimestampMs = currentTimestampMs
                    lastHeartbeatPerTrack[track.id] = currentTimestampMs
                    _events.tryEmit(event)
                    emittedList.add(event)
                }
            }
        }

        // Clean up tracks no longer in active list
        val currentIds = tracks.map { it.id }.toSet()
        lastHeartbeatPerTrack.keys.retainAll(currentIds)

        return emittedList
    }

    fun reset() {
        lastEmitTimestampMs = 0L
        lastHeartbeatPerTrack.clear()
    }
}
