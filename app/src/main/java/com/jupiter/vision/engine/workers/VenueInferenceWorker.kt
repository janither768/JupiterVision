// VenueInferenceWorker.kt - Periodic worker evaluating arrival times to classify venues hourly.
package com.jupiter.vision.engine.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jupiter.vision.di.AppModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VenueInferenceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val visionEngine = AppModule.provideVisionEngine(applicationContext)
            visionEngine.runVenueInference()
            Log.d(TAG, "VenueInferenceWorker finished successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "VenueInferenceWorker execution failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "VenueInferenceWorker"
        private const val TAG = "VenueInferenceWorker"
    }
}
