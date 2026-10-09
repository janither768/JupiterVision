// VisionEngine.kt - Core deterministic state machine and passive lifestyle context inference engine.
package com.jupiter.vision.engine

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.jupiter.vision.engine.data.dao.VisionDao
import com.jupiter.vision.engine.data.entities.PhaseLogEntity
import com.jupiter.vision.engine.data.repository.LocationRepository
import com.jupiter.vision.engine.data.repository.UsageRepository
import com.jupiter.vision.engine.model.LifestylePhase
import com.jupiter.vision.model.AppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisionEngine @Inject constructor(
    private val dao: VisionDao,
    private val locationRepository: LocationRepository,
    private val usageRepository: UsageRepository,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _currentPhase = MutableStateFlow(LifestylePhase.UNKNOWN)
    val currentPhase: StateFlow<LifestylePhase> = _currentPhase.asStateFlow()

    private var loopJob: Job? = null

    /**
     * Starts the 60-second passive inference loop.
     */
    fun startMonitoring() {
        if (loopJob?.isActive == true) return
        locationRepository.startTracking()

        loopJob = scope.launch {
            Log.d(TAG, "VisionEngine monitoring started")
            while (isActive) {
                try {
                    computeAndEmitPhase()
                } catch (t: Throwable) {
                    Log.e(TAG, "Error in VisionEngine tick: ${t.message}", t)
                }
                delay(60_000L) // 60 seconds polling
            }
        }
    }

    /**
     * Stops the monitoring loop to conserve battery.
     */
    fun stopMonitoring() {
        loopJob?.cancel()
        loopJob = null
        locationRepository.stopTracking()
        Log.d(TAG, "VisionEngine monitoring stopped")
    }

    /**
     * Evaluates current sensor inputs and computes the current LifestylePhase.
     */
    suspend fun computeAndEmitPhase(): LifestylePhase {
        val venue = locationRepository.currentVenue.value
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val isWeekday = dow in Calendar.MONDAY..Calendar.FRIDAY
        val minutesOfDay = hour * 60 + minute

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        val isSilentOrVibrate = (ringerMode == AudioManager.RINGER_MODE_SILENT ||
                ringerMode == AudioManager.RINGER_MODE_VIBRATE)

        val timeSinceMovement = locationRepository.getTimeSinceLastMovement()
        val isStationary30Min = timeSinceMovement >= 30 * 60 * 1000L

        var inferredPhase: LifestylePhase = LifestylePhase.UNKNOWN
        var confidence = 0.5f

        // Rule 1: Venue-based logic
        if (venue != null) {
            when (venue.inferredType.uppercase()) {
                "HOME" -> {
                    when {
                        hour >= 22 || hour < 5 -> {
                            inferredPhase = LifestylePhase.SLEEPING
                            confidence = 0.9f
                        }
                        hour in 6..7 -> {
                            inferredPhase = LifestylePhase.MORNING_WAKEUP
                            confidence = 0.85f
                        }
                        hour in 18..21 -> {
                            inferredPhase = LifestylePhase.EVENING_RELAX
                            confidence = 0.85f
                        }
                        else -> {
                            inferredPhase = LifestylePhase.UNKNOWN
                            confidence = 0.5f
                        }
                    }
                }
                "WORK" -> {
                    inferredPhase = LifestylePhase.AT_WORK
                    confidence = 0.95f
                }
                "GYM" -> {
                    inferredPhase = LifestylePhase.GYM_TIME
                    confidence = 0.95f
                }
                else -> {
                    // UNKNOWN venue: apply time heuristics
                    inferredPhase = evaluateTimeHeuristics(isWeekday, minutesOfDay)
                    confidence = if (inferredPhase != LifestylePhase.UNKNOWN) 0.7f else 0.4f
                }
            }
        } else {
            // Rule 2: In transit / no venue
            inferredPhase = evaluateTimeHeuristics(isWeekday, minutesOfDay)
            confidence = if (inferredPhase != LifestylePhase.UNKNOWN) 0.75f else 0.4f
        }

        // Rule 3: Override rule:
        // If ringer == SILENT AND no location change for 30+ minutes AND it's a weekday between 09:00–17:00 -> AT_WORK (confidence 0.6)
        val isWeekdayWorkHours = isWeekday && (minutesOfDay in (9 * 60)..(17 * 60))
        if (isSilentOrVibrate && isStationary30Min && isWeekdayWorkHours) {
            // Only apply override if not already confirmed at HOME or GYM
            if (venue?.inferredType?.uppercase() != "HOME" && venue?.inferredType?.uppercase() != "GYM") {
                inferredPhase = LifestylePhase.AT_WORK
                confidence = 0.6f
            }
        }

        // Persist inference event in database
        dao.insertPhaseLog(
            PhaseLogEntity(
                phase = inferredPhase.name,
                timestamp = System.currentTimeMillis(),
                confidence = confidence
            )
        )

        _currentPhase.value = inferredPhase
        Log.d(TAG, "Inferred Phase: $inferredPhase (confidence=$confidence, venue=${venue?.label})")
        return inferredPhase
    }

    private fun evaluateTimeHeuristics(isWeekday: Boolean, minutesOfDay: Int): LifestylePhase {
        return when {
            isWeekday && minutesOfDay in (8 * 60)..(9 * 60) -> LifestylePhase.COMMUTE_TO_WORK
            isWeekday && minutesOfDay in (16 * 60)..(17 * 60) -> LifestylePhase.COMMUTE_HOME
            else -> LifestylePhase.UNKNOWN
        }
    }

    /**
     * VENUE TYPE INFERENCE (Passive hourly background evaluation):
     * Updates UNKNOWN venues by analyzing historical arrival patterns.
     */
    suspend fun runVenueInference() {
        val allVenues = dao.getAllVenues()
        for (venue in allVenues) {
            if (venue.inferredType.uppercase() == "UNKNOWN") {
                val visits = dao.getVisitsForVenue(venue.id)
                if (visits.size < 2) continue

                var nightArrivals = 0
                var workArrivals = 0
                var gymArrivals = 0
                val totalVisits = visits.size.toFloat()

                for (visit in visits) {
                    val cal = Calendar.getInstance().apply { timeInMillis = visit.arrivalTime }
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    val minute = cal.get(Calendar.MINUTE)
                    val dow = cal.get(Calendar.DAY_OF_WEEK)
                    val isWk = dow in Calendar.MONDAY..Calendar.FRIDAY
                    val mins = hour * 60 + minute

                    // Arrivals 22:00–06:00
                    if (hour >= 22 || hour < 6) {
                        nightArrivals++
                    }
                    // Weekday arrivals 08:00–10:00
                    if (isWk && mins in (8 * 60)..(10 * 60)) {
                        workArrivals++
                    }
                    // Arrivals 17:00–20:00
                    if (mins in (17 * 60)..(20 * 60)) {
                        gymArrivals++
                    }
                }

                val newType: String?
                val newLabel: String?

                when {
                    (nightArrivals / totalVisits) > 0.60f -> {
                        newType = "HOME"
                        newLabel = "HOME"
                    }
                    (workArrivals / totalVisits) > 0.60f -> {
                        newType = "WORK"
                        newLabel = "WORK"
                    }
                    (gymArrivals / totalVisits) > 0.60f -> {
                        newType = "GYM"
                        newLabel = "GYM"
                    }
                    else -> {
                        newType = null
                        newLabel = null
                    }
                }

                if (newType != null && newLabel != null) {
                    dao.updateVenue(
                        venue.copy(
                            inferredType = newType,
                            label = newLabel
                        )
                    )
                    Log.d(TAG, "Venue #${venue.id} reclassified as $newType ($newLabel)")
                }
            }
        }
    }

    // =========================================
    // BACKWARD-COMPATIBILITY STRUCTS FOR LEGACY UI
    // =========================================

    enum class VisionState(val displayName: String, val statusLine: String) {
        NIGHT("NIGHT MODE", "NIGHT MODE"),
        GYM("GYM", "GYM SESSION"),
        WORK("WORK", "WORK PERIOD"),
        HOME("HOME", "HOME"),
        SETTLE("SETTLE", "SETTLING..."),
        TRANSIT("TRANSIT", "TRANSIT — ETA 12 MIN"),
        FOCUS("FOCUS MODE", "FOCUS MODE"),
        LEARNING("LEARNING", "ONLINE"),
        IDLE("IDLE", "IDLE")
    }

    data class EngineSnapshot(
        val state: VisionState = VisionState.IDLE,
        val statusText: String = "ONLINE",
        val topBarStatus: String = "ACTIVE",
        val contextualApps: List<AppInfo> = emptyList(),
        val isAppRowVisible: Boolean = true,
        val settleSecondsRemaining: Int = 300,
        val transitEtaMinutes: Int = 12,
        val learningDay: Int = 1
    )

    companion object {
        private const val TAG = "VisionEngine"

        fun getLearningDay(context: Context): Int {
            return 1
        }
    }
}
