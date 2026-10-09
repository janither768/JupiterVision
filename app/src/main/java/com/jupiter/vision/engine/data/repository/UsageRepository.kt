// UsageRepository.kt - Passive application usage collector and phase-correlated launcher metrics.
package com.jupiter.vision.engine.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import com.jupiter.vision.engine.data.dao.VisionDao
import com.jupiter.vision.engine.data.entities.AppEventEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: VisionDao,
    private val locationRepository: LocationRepository
) {

    /**
     * Checks if PACKAGE_USAGE_STATS permission is granted without prompting the user.
     */
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Queries UsageStatsManager for the given lookback window and inserts AppEventEntity records.
     */
    suspend fun collectUsageEvents(lookbackMinutes: Int = 15, currentPhaseName: String? = null): Int = withContext(Dispatchers.IO) {
        if (!hasUsageStatsPermission()) {
            Log.d(TAG, "PACKAGE_USAGE_STATS permission not granted. Skipping usage collection.")
            return@withContext 0
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext 0

        val endTime = System.currentTimeMillis()
        val startTime = endTime - (lookbackMinutes * 60 * 1000L)

        val usageEvents = try {
            usageStatsManager.queryEvents(startTime, endTime)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying usage events: ${e.message}")
            return@withContext 0
        } ?: return@withContext 0

        val currentVenueId = locationRepository.currentVenue.value?.id
        val newEvents = mutableListOf<AppEventEntity>()
        val event = UsageEvents.Event()

        val pm = context.packageManager
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            // Filter strictly for MOVE_TO_FOREGROUND (event type 1 / ACTIVITY_RESUMED)
            val isForeground = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED

            if (isForeground) {
                val pkg = event.packageName
                if (!pkg.isNullOrBlank() && pkg != context.packageName && isUserApp(pm, pkg)) {
                    newEvents.add(
                        AppEventEntity(
                            packageName = pkg,
                            timestamp = event.timeStamp,
                            venueId = currentVenueId,
                            phase = currentPhaseName
                        )
                    )
                }
            }
        }

        if (newEvents.isNotEmpty()) {
            // Batch insert in a single transaction
            dao.insertAppEvents(newEvents)
            Log.d(TAG, "Recorded ${newEvents.size} app foreground events for phase: $currentPhaseName")
        }

        return@withContext newEvents.size
    }

    /**
     * Returns recent app events logged in the last N minutes.
     */
    suspend fun getRecentEvents(minutes: Int): List<AppEventEntity> = withContext(Dispatchers.IO) {
        val end = System.currentTimeMillis()
        val start = end - (minutes * 60 * 1000L)
        dao.getAppEventsBetween(start, end)
    }

    /**
     * Queries app_events for the specified phase since sinceTimestamp,
     * groups by packageName, counts occurrences, sorts descending, and returns top N.
     */
    suspend fun getTopAppsForPhase(
        phase: String,
        sinceTimestamp: Long,
        limit: Int
    ): List<String> = withContext(Dispatchers.IO) {
        val events = dao.getAppEventsForPhase(phase, sinceTimestamp)
        events
            .groupBy { it.packageName }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    companion object {
        private const val TAG = "UsageRepository"

        private fun isUserApp(pm: android.content.pm.PackageManager, pkg: String): Boolean {
            val lower = pkg.lowercase()
            if (lower == "android" ||
                lower.startsWith("com.android.systemui") ||
                lower.contains("packageinstaller") ||
                lower.contains("permissioncontroller") ||
                lower.startsWith("com.google.android.packageinstaller") ||
                lower.startsWith("com.google.android.permissioncontroller") ||
                lower.startsWith("com.android.providers.") ||
                lower.startsWith("com.android.settings.intelligence") ||
                lower.startsWith("com.google.android.overlay") ||
                lower.startsWith("com.android.internal")
            ) {
                return false
            }
            return pm.getLaunchIntentForPackage(pkg) != null
        }
    }
}
