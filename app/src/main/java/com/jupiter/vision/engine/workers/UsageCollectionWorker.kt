// UsageCollectionWorker.kt - Periodic worker querying app usage events every 15 minutes.
package com.jupiter.vision.engine.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jupiter.vision.di.AppModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsageCollectionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val visionEngine = AppModule.provideVisionEngine(applicationContext)
            val usageRepo = AppModule.provideUsageRepository(applicationContext)
            val currentPhase = visionEngine.currentPhase.value.name

            usageRepo.collectUsageEvents(
                lookbackMinutes = 15,
                currentPhaseName = currentPhase
            )
            Log.d(TAG, "UsageCollectionWorker finished successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "UsageCollectionWorker execution failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "UsageCollectionWorker"
        private const val TAG = "UsageCollectionWorker"
    }
}
