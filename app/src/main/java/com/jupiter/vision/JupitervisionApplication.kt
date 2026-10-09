// JupitervisionApplication.kt - Main Application entry point configuring Hilt, WorkManager, and background monitoring.
package com.jupiter.vision

import android.app.Application
import android.util.Log
import com.jupiter.vision.engine.VisionEngine
import com.jupiter.vision.engine.service.VisionEngineService
import com.jupiter.vision.engine.workers.WorkManagerScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class JupitervisionApplication : Application() {

    @Inject
    lateinit var visionEngine: VisionEngine

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Initializing Jupitervision OS passive engine...")

        try {
            if (!::visionEngine.isInitialized) {
                visionEngine = com.jupiter.vision.di.AppModule.provideVisionEngine(this)
            }

            // 1. Enqueue the three WorkManager periodic tasks (Usage collection, Venue inference, Cleanup)
            WorkManagerScheduler.enqueueAll(this)

            // 2. Start VisionEngine's passive monitoring loop
            visionEngine.startMonitoring()
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing passive engine in Application: ${t.message}", t)
        }

        // 3. Launch foreground service safely
        try {
            VisionEngineService.start(this)
        } catch (t: Throwable) {
            Log.e(TAG, "Could not start VisionEngineService from Application: ${t.message}", t)
        }
    }

    companion object {
        private const val TAG = "JupitervisionApp"
    }
}
