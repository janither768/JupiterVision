package com.jupiter.vision.tracemachine.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jupiter.vision.MainActivity
import com.jupiter.vision.R
import com.jupiter.vision.tracemachine.model.TraceState
import com.jupiter.vision.tracemachine.sensor.TraceEstimatorEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * TraceMachine Gen 1 Inertial Path Dead-Reckoning Foreground Service.
 *
 * Keeps sensors actively listening and accumulating step dead-reckoning
 * when the application is active or running in the background.
 */
class TraceMachineService : Service() {

    companion object {
        const val ACTION_START = "com.jupiter.vision.tracemachine.ACTION_START"
        const val ACTION_STOP = "com.jupiter.vision.tracemachine.ACTION_STOP"
        const val ACTION_DROP_ANCHOR = "com.jupiter.vision.tracemachine.ACTION_DROP_ANCHOR"
        const val ACTION_RESET = "com.jupiter.vision.tracemachine.ACTION_RESET"

        private const val NOTIFICATION_ID = 4201
        private const val CHANNEL_ID = "tracemachine_channel"

        private val _traceStateFlow = MutableStateFlow(TraceState())
        val traceStateFlow: StateFlow<TraceState> = _traceStateFlow.asStateFlow()

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        @Volatile
        private var engineInstance: TraceEstimatorEngine? = null

        @Synchronized
        fun getEngine(context: Context): TraceEstimatorEngine {
            return engineInstance ?: TraceEstimatorEngine(context.applicationContext).also {
                engineInstance = it
            }
        }

        fun startTracking(context: Context) {
            val intent = Intent(context, TraceMachineService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopTracking(context: Context) {
            val intent = Intent(context, TraceMachineService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun dropManualAnchor(context: Context) {
            getEngine(context).dropManualAnchor()
        }

        fun resetSession(context: Context) {
            val intent = Intent(context, TraceMachineService::class.java).apply {
                action = ACTION_RESET
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var estimatorEngine: TraceEstimatorEngine

    override fun onCreate() {
        super.onCreate()
        estimatorEngine = getEngine(this)
        createNotificationChannel()

        serviceScope.launch {
            estimatorEngine.traceStateFlow.collect { state ->
                _traceStateFlow.value = state
                _isRunningFlow.value = state.isSessionActive
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundTracking()
            }
            ACTION_STOP -> {
                stopForegroundTracking()
            }
            ACTION_DROP_ANCHOR -> {
                estimatorEngine.dropManualAnchor()
            }
            ACTION_RESET -> {
                estimatorEngine.stopSession()
                estimatorEngine.startSession()
            }
            else -> {
                startForegroundTracking()
            }
        }
        return START_STICKY
    }

    private fun startForegroundTracking() {
        val notification = buildNotification("TraceMachine Gen 1 Active", "Inertial dead-reckoning tracking running")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        estimatorEngine.startSession()
        _isRunningFlow.value = true
    }

    private fun stopForegroundTracking() {
        estimatorEngine.stopSession()
        _isRunningFlow.value = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun buildNotification(title: String, content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TraceMachine Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Dead-reckoning inertial path tracker for TraceMachine Gen 1"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        estimatorEngine.stopSession()
        _isRunningFlow.value = false
        serviceScope.cancel()
    }
}
