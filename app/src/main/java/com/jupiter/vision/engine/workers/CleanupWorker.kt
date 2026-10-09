// CleanupWorker.kt - Periodic maintenance worker purging old event logs every 24 hours while device is idle.
package com.jupiter.vision.engine.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jupiter.vision.di.AppModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val dao = AppModule.provideVisionDao(applicationContext)
            val now = System.currentTimeMillis()
            val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000L)
            val sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000L)

            val deletedEvents = dao.deleteAppEventsOlderThan(thirtyDaysAgo)
            val deletedPhases = dao.deletePhaseLogsOlderThan(sevenDaysAgo)

            Log.d(TAG, "Cleanup completed: pruned $deletedEvents app events, $deletedPhases phase records")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "CleanupWorker execution failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "CleanupWorker"
        private const val TAG = "CleanupWorker"
    }
}
