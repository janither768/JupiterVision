// VisionEngineService.kt - Foreground service ensuring continuous passive inference and location tracking.
package com.jupiter.vision.engine.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jupiter.vision.R
import com.jupiter.vision.di.AppModule
import com.jupiter.vision.engine.VisionEngine

class VisionEngineService : Service() {

    private lateinit var visionEngine: VisionEngine
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        try {
            visionEngine = AppModule.provideVisionEngine(this)
            createNotificationChannel()
            val notification = buildForegroundNotification()

            val hasLocationPerm = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (hasLocationPerm) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // On Android 14+, without location permission, start with DATA_SYNC
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start foreground notification: ${t.message}", t)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val fallback = buildForegroundNotification()
                    startForeground(NOTIFICATION_ID, fallback)
                }
            } catch (_: Throwable) {}
        }

        try {
            acquireBriefWakeLock()
        } catch (_: Throwable) {}

        try {
            if (::visionEngine.isInitialized) {
                visionEngine.startMonitoring()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start monitoring in service: ${t.message}", t)
        }
        Log.d(TAG, "VisionEngineService active in foreground")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (!::visionEngine.isInitialized) {
                visionEngine = AppModule.provideVisionEngine(this)
            }
            visionEngine.startMonitoring()
        } catch (t: Throwable) {
            Log.e(TAG, "Error in onStartCommand: ${t.message}", t)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (::visionEngine.isInitialized) {
            visionEngine.stopMonitoring()
        }
        releaseWakeLock()
        super.onDestroy()
        Log.d(TAG, "VisionEngineService stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireBriefWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jupitervision:engine_init")?.apply {
                acquire(3000L) // 3 seconds maximum for initialization burst
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire temporary wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jupitervision Passive Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Observes context locally on device"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jupitervision Engine is active")
            .setContentText("Observing context locally")
            .setSmallIcon(R.drawable.ic_location_pin)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "VisionEngineService"
        private const val CHANNEL_ID = "jupitervision_engine_channel"
        private const val NOTIFICATION_ID = 2112

        fun start(context: Context) {
            val intent = Intent(context, VisionEngineService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, VisionEngineService::class.java)
                context.stopService(intent)
            } catch (_: Throwable) {}
        }
    }
}
