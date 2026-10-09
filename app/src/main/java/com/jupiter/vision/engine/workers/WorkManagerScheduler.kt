// WorkManagerScheduler.kt - Enqueues unique periodic background tasks for telemetry and maintenance.
package com.jupiter.vision.engine.workers

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkManagerScheduler {

    private const val TAG = "WorkManagerScheduler"

    /**
     * Registers all three background workers with ExistingPeriodicWorkPolicy.KEEP.
     */
    fun enqueueAll(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context)

            // 1. Usage Collection Worker (Every 15 minutes, no constraints)
            val usageRequest = PeriodicWorkRequestBuilder<UsageCollectionWorker>(
                15, TimeUnit.MINUTES
            ).build()

            workManager.enqueueUniquePeriodicWork(
                UsageCollectionWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                usageRequest
            )

            // 2. Venue Inference Worker (Every 1 hour, no constraints)
            val venueRequest = PeriodicWorkRequestBuilder<VenueInferenceWorker>(
                1, TimeUnit.HOURS
            ).build()

            workManager.enqueueUniquePeriodicWork(
                VenueInferenceWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                venueRequest
            )

            // 3. Cleanup Worker (Every 24 hours, requires device idle)
            val cleanupConstraints = Constraints.Builder()
                .setRequiresDeviceIdle(true)
                .build()

            val cleanupRequest = PeriodicWorkRequestBuilder<CleanupWorker>(
                24, TimeUnit.HOURS
            ).setConstraints(cleanupConstraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                CleanupWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                cleanupRequest
            )

            Log.d(TAG, "All background workers enqueued successfully with KEEP policy")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to enqueue background workers: ${t.message}", t)
        }
    }
}
