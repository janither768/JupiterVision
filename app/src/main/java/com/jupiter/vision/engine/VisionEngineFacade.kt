// VisionEngineFacade.kt - Unified clean public interface for the launcher UI to observe context and dynamic app lists.
package com.jupiter.vision.engine

import com.jupiter.vision.engine.data.dao.VisionDao
import com.jupiter.vision.engine.data.entities.VenueEntity
import com.jupiter.vision.engine.data.repository.LocationRepository
import com.jupiter.vision.engine.data.repository.UsageRepository
import com.jupiter.vision.engine.model.LifestylePhase
import com.jupiter.vision.engine.provider.AppTemplateProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisionEngineFacade @Inject constructor(
    private val visionEngine: VisionEngine,
    private val locationRepository: LocationRepository,
    private val usageRepository: UsageRepository,
    private val dao: VisionDao,
    private val templateProvider: AppTemplateProvider
) {
    // Current Phase flow emitted by the deterministic state machine
    val currentPhase: StateFlow<LifestylePhase> = visionEngine.currentPhase

    // Current Venue flow emitted by the passive location repository
    val currentVenue: StateFlow<VenueEntity?> = locationRepository.currentVenue

    // Live raw GPS/network location flow from location repository
    val lastLocation: StateFlow<android.location.Location?> = locationRepository.lastLocation

    fun startLocationTracking() {
        locationRepository.startTracking()
    }

    fun startLiveLocationTracking() {
        locationRepository.startLiveTracking()
    }

    fun stopLiveLocationTracking() {
        locationRepository.stopLiveTracking()
    }

    suspend fun getCurrentLocation(): android.location.Location? {
        return locationRepository.getCurrentLocation()
    }

    private fun isExcludedApp(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower == "android" ||
                lower.startsWith("com.android.systemui") ||
                lower.contains("packageinstaller") ||
                lower.contains("permissioncontroller") ||
                lower.startsWith("com.google.android.packageinstaller") ||
                lower.startsWith("com.google.android.permissioncontroller") ||
                lower.startsWith("com.android.providers.") ||
                lower.startsWith("com.android.settings.intelligence") ||
                lower.startsWith("com.google.android.overlay") ||
                lower.startsWith("com.android.internal")
    }

    /**
     * Resolves the current contextual application list:
     * 1. Get current phase.
     * 2. Query dao.getAppEventsForPhase(phase.name, System.currentTimeMillis() - 7 days).
     * 3. Filter out system UI, package installer, permission controller.
     * 4. If list size >= 5 -> return top 5.
     * 5. If list size is 1–4 -> blend with templates from AppTemplateProvider (fill to 5, no duplicates).
     * 6. If list size is 0 -> return templates for the phase.
     * 7. If templates are empty -> return default list: ["com.android.dialer", "com.google.android.apps.messaging", "com.android.settings"].
     */
    suspend fun getCurrentAppList(): List<String> = withContext(Dispatchers.IO) {
        val phase = visionEngine.currentPhase.value
        val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000L)

        // Query historical app launches recorded in this phase
        val events = dao.getAppEventsForPhase(phase.name, sevenDaysAgo)
        val rankedApps = events
            .map { it.packageName }
            .filter { !isExcludedApp(it) }
            .groupBy { it }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .map { it.key }

        val templates = templateProvider.getTemplatesFor(phase).filter { !isExcludedApp(it) }
        val defaultFallback = listOf(
            "com.android.dialer",
            "com.google.android.apps.messaging",
            "com.android.settings"
        )

        when {
            // Case 1: At least 5 learned apps -> top 5
            rankedApps.size >= 5 -> {
                rankedApps.take(5)
            }
            // Case 2: 1 to 4 learned apps -> blend with templates up to 5
            rankedApps.isNotEmpty() -> {
                val blended = rankedApps.toMutableList()
                for (template in templates) {
                    if (blended.size >= 5) break
                    if (!blended.contains(template)) {
                        blended.add(template)
                    }
                }
                // If still under 5, fill with default fallback
                for (fallback in defaultFallback) {
                    if (blended.size >= 5) break
                    if (!blended.contains(fallback)) {
                        blended.add(fallback)
                    }
                }
                blended
            }
            // Case 3: 0 learned apps -> templates for this phase
            templates.isNotEmpty() -> {
                templates
            }
            // Case 4: Templates are empty (e.g. SLEEPING) -> default fallback
            else -> {
                defaultFallback
            }
        }
    }

    /**
     * Resolves the human-readable label for the current venue:
     * - If venue == null -> "UNKNOWN"
     * - If venue.label is not empty -> venue.label
     * - Else -> "LOCATION ${venue.id}"
     */
    suspend fun getCurrentVenueLabel(): String {
        val venue = locationRepository.currentVenue.value
        return when {
            venue == null -> "UNKNOWN"
            venue.label.isNotBlank() -> venue.label
            else -> "LOCATION ${venue.id}"
        }
    }
}
