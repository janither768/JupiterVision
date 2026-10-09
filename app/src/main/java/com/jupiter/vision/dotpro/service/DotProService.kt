package com.jupiter.vision.dotpro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jupiter.vision.MainActivity
import com.jupiter.vision.R
import com.jupiter.vision.dotpro.audio.AudioCapture
import com.jupiter.vision.dotpro.classifier.DisplayLabelFormatter
import com.jupiter.vision.dotpro.classifier.LabelStabilizer
import com.jupiter.vision.dotpro.classifier.YamNetClassifier
import com.jupiter.vision.dotpro.classifier.YamNetGate
import com.jupiter.vision.dotpro.emitter.DotProEventEmitter
import com.jupiter.vision.dotpro.features.FeatureExtractor
import com.jupiter.vision.dotpro.features.FrameFeatures
import com.jupiter.vision.dotpro.model.Bearing
import com.jupiter.vision.dotpro.model.DoTProEvent
import com.jupiter.vision.dotpro.model.Signal
import com.jupiter.vision.dotpro.model.Source
import com.jupiter.vision.dotpro.tracker.PresenceTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Service state enum.
 */
enum class DotProServiceState {
    STOPPED,
    STARTING,
    ACTIVE,
    DEGRADED,
    SUSPENDED
}

/**
 * DotProService:
 * 1. Persistent, low-importance notification identifying DoTPro as active.
 * 2. Owns the module lifecycle: start, stop, degrade, suspend.
 * 3. All other components (AudioCapture, FeatureExtractor, YamNetClassifier,
 *    YamNetGate, LabelStabilizer, DisplayLabelFormatter, PresenceTracker,
 *    DotProEventEmitter) live inside or are owned by this service.
 */
class DotProService : Service() {

    companion object {
        private const val TAG = "DotProService"
        private const val CHANNEL_ID = "dotpro_module_channel"
        private const val NOTIFICATION_ID = 21122

        const val ACTION_START = "com.jupiter.vision.dotpro.START"
        const val ACTION_STOP = "com.jupiter.vision.dotpro.STOP"
        const val ACTION_SUSPEND = "com.jupiter.vision.dotpro.SUSPEND"
        const val ACTION_RESUME = "com.jupiter.vision.dotpro.RESUME"

        // Global state accessor for debug screen or clients
        private val _serviceStateFlow = MutableStateFlow(DotProServiceState.STOPPED)
        val serviceStateFlow: StateFlow<DotProServiceState> = _serviceStateFlow.asStateFlow()

        private val _currentFeaturesFlow = MutableStateFlow<FrameFeatures?>(null)
        val currentFeaturesFlow: StateFlow<FrameFeatures?> = _currentFeaturesFlow.asStateFlow()

        private val _currentSignalFlow = MutableStateFlow<Signal>(Signal.UNKNOWN)
        val currentSignalFlow: StateFlow<Signal> = _currentSignalFlow.asStateFlow()

        private val _currentSignalConfidenceFlow = MutableStateFlow(0f)
        val currentSignalConfidenceFlow: StateFlow<Float> = _currentSignalConfidenceFlow.asStateFlow()

        private val _currentDisplayLabelFlow = MutableStateFlow("Unknown, stable")
        val currentDisplayLabelFlow: StateFlow<String> = _currentDisplayLabelFlow.asStateFlow()

        private val _yamNetStatusFlow = MutableStateFlow("IDLE (Gated)")
        val yamNetStatusFlow: StateFlow<String> = _yamNetStatusFlow.asStateFlow()

        private val _rawYamNetClassFlow = MutableStateFlow("Unknown")
        val rawYamNetClassFlow: StateFlow<String> = _rawYamNetClassFlow.asStateFlow()

        private val _recentEventsFlow = MutableStateFlow<List<DoTProEvent>>(emptyList())
        val recentEventsFlow: StateFlow<List<DoTProEvent>> = _recentEventsFlow.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, DotProService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, DotProService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Internal pipeline components
    private lateinit var audioCapture: AudioCapture
    private val featureExtractor = FeatureExtractor()
    private lateinit var yamNetClassifier: YamNetClassifier
    private val yamNetGate = YamNetGate()
    private lateinit var labelStabilizer: LabelStabilizer
    private val displayLabelFormatter = DisplayLabelFormatter()
    private val presenceTracker = PresenceTracker()
    private val eventEmitter = DotProEventEmitter()

    val events: SharedFlow<DoTProEvent> get() = eventEmitter.events

    inner class LocalBinder : Binder() {
        fun getService(): DotProService = this@DotProService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioCapture = AudioCapture(this, serviceScope)
        yamNetClassifier = YamNetClassifier(this)
        labelStabilizer = LabelStabilizer(labels = yamNetClassifier.labels)

        // Connect audio capture output frames to the pipeline
        audioCapture.onFrameRead = { frameSamples, isEarbud ->
            processFrame(frameSamples, isEarbud)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopModule()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SUSPEND -> {
                suspendModule()
            }
            ACTION_RESUME -> {
                resumeModule()
            }
            ACTION_START, null -> {
                startModule()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DoTPro Proximity Module",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "DoTPro near-field audio reflex module"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DoTPro Active")
            .setContentText("Near-field proximity acoustic reflex running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    @Synchronized
    private fun startModule() {
        _serviceStateFlow.value = DotProServiceState.STARTING
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground", e)
        }

        val started = audioCapture.start()
        if (started) {
            _serviceStateFlow.value = DotProServiceState.ACTIVE
            Log.i(TAG, "DoTPro module pipeline ACTIVE")
        } else {
            _serviceStateFlow.value = DotProServiceState.DEGRADED
            Log.w(TAG, "DoTPro audio capture failed to start; entering DEGRADED state")
        }
    }

    @Synchronized
    private fun stopModule() {
        _serviceStateFlow.value = DotProServiceState.STOPPED
        audioCapture.stop()
        yamNetGate.reset()
        labelStabilizer.reset()
        displayLabelFormatter.reset()
        presenceTracker.reset()
        eventEmitter.reset()
        _currentFeaturesFlow.value = null
        _currentSignalFlow.value = Signal.UNKNOWN
        _currentSignalConfidenceFlow.value = 0f
        _currentDisplayLabelFlow.value = "Unknown, stable"
        _yamNetStatusFlow.value = "IDLE (Gated)"
        _rawYamNetClassFlow.value = "Unknown"
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "DoTPro module STOPPED")
    }

    @Synchronized
    private fun suspendModule() {
        _serviceStateFlow.value = DotProServiceState.SUSPENDED
        audioCapture.stop()
        Log.i(TAG, "DoTPro module SUSPENDED")
    }

    @Synchronized
    private fun resumeModule() {
        if (_serviceStateFlow.value == DotProServiceState.SUSPENDED) {
            val started = audioCapture.start()
            _serviceStateFlow.value = if (started) DotProServiceState.ACTIVE else DotProServiceState.DEGRADED
            Log.i(TAG, "DoTPro module RESUMED -> ${_serviceStateFlow.value}")
        }
    }

    /**
     * Executes the pipeline sequence on each 50ms audio frame:
     * 1. FeatureExtractor -> FrameFeatures (deterministic envelope & spectral features)
     * 2. YamNetGate -> Gated YAMNet invocation (activity / transient / heartbeat)
     * 3. LabelStabilizer -> Temporal smoothing over 3-5s rolling score vectors & anti-flicker
     * 4. DisplayLabelFormatter -> "<Class>, <state>" rich diagnostic label
     * 5. PresenceTracker -> State machine with hysteresis & label stability
     * 6. DotProEventEmitter -> Emit DoTProEvent objects at rate <= 4/sec
     */
    private fun processFrame(samples: FloatArray, isEarbud: Boolean) {
        if (_serviceStateFlow.value != DotProServiceState.ACTIVE) return

        val timestamp = System.currentTimeMillis()

        // 1. Feature Extraction (deterministic layer)
        val features = featureExtractor.processFrame(samples, timestamp)
        displayLabelFormatter.recordFrame(features)
        _currentFeaturesFlow.value = features

        // 2. GATING — YamNetGate
        // YAMNet does NOT run continuously. It is invoked only when the deterministic layer signals
        // something is worth classifying (activity above baseline, transient spike, or 3s heartbeat).
        // Between invocations, the last stable label is retained.
        // When room is silent or steady, YAMNet is idle.
        val hasActiveTrack = presenceTracker.hasActiveTracks()
        val shouldInvokeYamNet = yamNetGate.shouldInvoke(features, hasActiveTrack, timestamp)

        val stabilizedLabel = if (shouldInvokeYamNet) {
            // Retrieve recent 1-second audio window (~15,600 samples) from ring buffer
            val recentSamples = audioCapture.getRecentSamples(15600)
            val scoreVector = yamNetClassifier.classify(recentSamples)
            val stabilized = labelStabilizer.process(scoreVector, features.isTransient, timestamp)
            _yamNetStatusFlow.value = "ACTIVE (${yamNetGate.lastTriggerReason})"
            stabilized
        } else {
            // Retain last stable label while YAMNet is idle
            _yamNetStatusFlow.value = "IDLE (Gated)"
            labelStabilizer.getCurrentStableLabel()
        }

        // Format rich display label: "<Class>, <state>"
        val displayLabel = displayLabelFormatter.format(
            yamnetClass = stabilizedLabel.yamnetClass,
            taxonomySignal = stabilizedLabel.taxonomySignal,
            isConfident = stabilizedLabel.isConfident,
            currentFeatures = features
        )

        _currentSignalFlow.value = stabilizedLabel.taxonomySignal
        _currentSignalConfidenceFlow.value = stabilizedLabel.confidence
        _currentDisplayLabelFlow.value = displayLabel
        _rawYamNetClassFlow.value = stabilizedLabel.yamnetClass

        // 3. Source & Bearing Rules
        val source = if (isEarbud) Source.AUDIO_EARBUD else Source.AUDIO
        val bearing = if (isEarbud) {
            Bearing.FRONT
        } else {
            Bearing.UNKNOWN
        }

        // 4. Presence Tracking (with hysteresis & label stability)
        val tracks = presenceTracker.update(
            features = features,
            signal = stabilizedLabel.taxonomySignal,
            signalConfidence = stabilizedLabel.confidence,
            bearing = bearing,
            displayLabel = displayLabel
        )

        // 5. Event Emission (bounded rate <= 4/sec)
        val newEvents = eventEmitter.processTracks(tracks, source, timestamp)
        if (newEvents.isNotEmpty()) {
            serviceScope.launch(Dispatchers.Main) {
                val currentList = _recentEventsFlow.value
                // Append newest events at the end for chronological scrolling
                val updated = (currentList + newEvents).takeLast(100)
                _recentEventsFlow.value = updated
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopModule()
        yamNetClassifier.close()
        serviceScope.cancel()
    }
}
