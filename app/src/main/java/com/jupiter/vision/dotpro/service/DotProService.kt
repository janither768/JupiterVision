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
import com.jupiter.vision.dotpro.BatteryThermalManager
import com.jupiter.vision.dotpro.audio.AudioCapture
import com.jupiter.vision.dotpro.audio.RefinementPipeline
import com.jupiter.vision.dotpro.classifier.DisplayLabelFormatter
import com.jupiter.vision.dotpro.classifier.GateTriggerReason
import com.jupiter.vision.dotpro.classifier.LabelStabilizer
import com.jupiter.vision.dotpro.classifier.YamNetClassifier
import com.jupiter.vision.dotpro.classifier.YamNetGate
import com.jupiter.vision.dotpro.emitter.DotProEventEmitter
import com.jupiter.vision.dotpro.features.FeatureExtractor
import com.jupiter.vision.dotpro.features.FrameFeatures
import com.jupiter.vision.dotpro.model.AmbientState
import com.jupiter.vision.dotpro.model.Bearing
import com.jupiter.vision.dotpro.model.DoTProEvent
import com.jupiter.vision.dotpro.model.Motion
import com.jupiter.vision.dotpro.model.Presence
import com.jupiter.vision.dotpro.model.Proximity
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
 * Telemetry snapshot for the refinement pipeline, scale normalizer, and 3-state engine.
 */
data class RefinementTelemetry(
    val rawRms: Float,
    val refinedRms: Float,
    val snrDb: Float,
    val makeupGainDb: Float,
    val referenceLoudnessDb: Float,
    val normalizationOffsetDb: Float,
    val bandLiftsDb: FloatArray,
    val hardwareFloorRms: Float,
    val maxCompressorReductionDb: Float,
    val bandCompressorReductionsDb: FloatArray,
    val isSilentGateClosed: Boolean,
    val batteryTier: Int,
    val thermalStatus: String,
    val ambientState: AmbientState,
    val cadenceDescription: String,
    val audioSourceName: String,
    val yamNetDelegate: String,
    val audioSourceWarning: String?
)

/**
 * DotProService:
 * 1. Persistent, low-importance notification identifying DoTPro as active.
 * 2. Owns the module lifecycle: start, stop, degrade, suspend.
 * 3. Integrates the 48 kHz RefinementPipeline (SCALE + SHAPE), YAMNet classification,
 *    Three-State Ambient Model (ACTIVE, AMBIENT, SILENT), battery/thermal tiering,
 *    and DoTPro Event Emitter.
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

        private val _ambientStateFlow = MutableStateFlow(AmbientState.AMBIENT)
        val ambientStateFlow: StateFlow<AmbientState> = _ambientStateFlow.asStateFlow()

        private val _refinementTelemetryFlow = MutableStateFlow<RefinementTelemetry?>(null)
        val refinementTelemetryFlow: StateFlow<RefinementTelemetry?> = _refinementTelemetryFlow.asStateFlow()

        private val _activeAudioSourceFlow = MutableStateFlow("UNKNOWN")
        val activeAudioSourceFlow: StateFlow<String> = _activeAudioSourceFlow.asStateFlow()

        private val _audioSourceWarningFlow = MutableStateFlow<String?>(null)
        val audioSourceWarningFlow: StateFlow<String?> = _audioSourceWarningFlow.asStateFlow()

        private val _batteryTierFlow = MutableStateFlow(0)
        val batteryTierFlow: StateFlow<Int> = _batteryTierFlow.asStateFlow()

        private val _thermalStatusFlow = MutableStateFlow("NONE")
        val thermalStatusFlow: StateFlow<String> = _thermalStatusFlow.asStateFlow()

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
    private val refinementPipeline = RefinementPipeline()
    private val featureExtractor = FeatureExtractor()
    private lateinit var yamNetClassifier: YamNetClassifier
    private val yamNetGate = YamNetGate()
    private lateinit var labelStabilizer: LabelStabilizer
    private val displayLabelFormatter = DisplayLabelFormatter()
    private val presenceTracker = PresenceTracker()
    private val eventEmitter = DotProEventEmitter()
    private lateinit var batteryThermalManager: BatteryThermalManager

    val events: SharedFlow<DoTProEvent> get() = eventEmitter.events

    inner class LocalBinder : Binder() {
        fun getService(): DotProService = this@DotProService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        batteryThermalManager = BatteryThermalManager(this)
        audioCapture = AudioCapture(this, serviceScope)
        yamNetClassifier = YamNetClassifier(this)
        labelStabilizer = LabelStabilizer(labels = yamNetClassifier.labels)

        // Observe battery and thermal tiers
        serviceScope.launch {
            batteryThermalManager.batteryTier.collect { tier ->
                _batteryTierFlow.value = tier
                refinementPipeline.batteryTier = tier
            }
        }
        serviceScope.launch {
            batteryThermalManager.thermalStatusName.collect { status ->
                _thermalStatusFlow.value = status
            }
        }
        serviceScope.launch {
            audioCapture.audioSourceWarning.collect { warning ->
                _audioSourceWarningFlow.value = warning
            }
        }

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
            _activeAudioSourceFlow.value = audioCapture.activeAudioSourceName.value
            _audioSourceWarningFlow.value = audioCapture.audioSourceWarning.value
            Log.i(TAG, "DoTPro module pipeline ACTIVE [source: ${_activeAudioSourceFlow.value}]")
        } else {
            _serviceStateFlow.value = DotProServiceState.DEGRADED
            Log.w(TAG, "DoTPro audio capture failed to start; entering DEGRADED state")
        }
    }

    @Synchronized
    private fun stopModule() {
        _serviceStateFlow.value = DotProServiceState.STOPPED
        audioCapture.stop()
        refinementPipeline.reset()
        yamNetGate.reset()
        labelStabilizer.reset()
        displayLabelFormatter.reset()
        presenceTracker.reset()
        eventEmitter.reset()
        _currentFeaturesFlow.value = null
        _ambientStateFlow.value = AmbientState.AMBIENT
        _refinementTelemetryFlow.value = null
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
            _activeAudioSourceFlow.value = audioCapture.activeAudioSourceName.value
            _audioSourceWarningFlow.value = audioCapture.audioSourceWarning.value
            Log.i(TAG, "DoTPro module RESUMED -> ${_serviceStateFlow.value}")
        }
    }

    /**
     * Executes the pipeline sequence on each 50ms audio frame:
     * 1. RefinementPipeline (SCALE first, then SHAPE) -> Refined 48 kHz + Anti-Aliased 16 kHz downsampled stream
     * 2. FeatureExtractor -> FrameFeatures extracted from refined 16 kHz stream
     * 3. YamNetGate -> Three-state evaluation (ACTIVE, AMBIENT, SILENT) with SNR gate agreement & battery tiering
     * 4. YamNetClassifier -> Classify 16 kHz PCM window (~15,600 samples) via XNNPACK/NNAPI
     * 5. LabelStabilizer -> Temporal smoothing over 3-5s rolling score vectors & anti-flicker
     * 6. DisplayLabelFormatter -> "<Class>, <state>" rich diagnostic label
     * 7. PresenceTracker -> State machine with hysteresis & label stability
     * 8. DotProEventEmitter -> Emit DoTProEvent objects (with normalized flag & battery tier)
     */
    private fun processFrame(raw48kSamples: FloatArray, isEarbud: Boolean) {
        if (_serviceStateFlow.value != DotProServiceState.ACTIVE) return

        val timestamp = System.currentTimeMillis()
        val currentTier = batteryThermalManager.batteryTier.value
        refinementPipeline.batteryTier = currentTier

        // 1. REFINEMENT PIPELINE (runs continuously on 48 kHz stream)
        val refinementOutput = refinementPipeline.process(raw48kSamples)

        // Store refined 16 kHz samples into ring buffer for YAMNet
        audioCapture.append16kSamples(refinementOutput.refined16kSamples)

        // 2. Feature Extraction on refined 16 kHz stream
        val features = featureExtractor.processFrame(refinementOutput.refined16kSamples, timestamp)
        displayLabelFormatter.recordFrame(features)
        _currentFeaturesFlow.value = features

        // Keep active audio source updated
        _activeAudioSourceFlow.value = audioCapture.activeAudioSourceName.value
        _audioSourceWarningFlow.value = audioCapture.audioSourceWarning.value

        // 3. GATING & THREE-STATE AMBIENT MODEL (YamNetGate)
        val hasActiveTrack = presenceTracker.hasActiveTracks()
        val shouldInvokeYamNet = yamNetGate.evaluate(
            features = features,
            refinedRms = refinementOutput.refinedRms,
            hardwareFloorRms = refinementOutput.hardwareFloorRms,
            hasActivePresenceTrack = hasActiveTrack,
            currentTimestampMs = timestamp,
            snrDb = refinementOutput.estimatedSnrDb,
            isSilentGateClosed = refinementOutput.isSilentGateClosed,
            batteryTier = currentTier
        )

        val currentState = yamNetGate.currentState
        _ambientStateFlow.value = currentState

        val stabilizedLabel = if (shouldInvokeYamNet) {
            // Retrieve recent 1-second 16kHz audio window (~15,600 samples) from ring buffer
            val recent16k = audioCapture.getRecent16kSamples(15600)
            val scoreVector = yamNetClassifier.classify(recent16k)
            val stabilized = labelStabilizer.process(scoreVector, features.isTransient, timestamp)
            _yamNetStatusFlow.value = "${currentState.name} (${yamNetGate.lastTriggerReason})"
            stabilized
        } else {
            // Retain last stable label while YAMNet is idle
            _yamNetStatusFlow.value = "${currentState.name} (${yamNetGate.cadenceDescription})"
            labelStabilizer.getCurrentStableLabel()
        }

        // 4. Format rich display label: "<Class>, <state>"
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

        // 5. Update Telemetry for Diagnostic UI
        val maxCompReduction = refinementOutput.compressorReductionDb.maxOrNull() ?: 0f
        _refinementTelemetryFlow.value = RefinementTelemetry(
            rawRms = refinementOutput.rawRms,
            refinedRms = refinementOutput.refinedRms,
            snrDb = refinementOutput.estimatedSnrDb,
            makeupGainDb = refinementOutput.makeupGainDb,
            referenceLoudnessDb = refinementOutput.referenceLoudnessDb,
            normalizationOffsetDb = refinementOutput.normalizationOffsetDb,
            bandLiftsDb = refinementOutput.bandLiftsDb,
            hardwareFloorRms = refinementOutput.hardwareFloorRms,
            maxCompressorReductionDb = maxCompReduction,
            bandCompressorReductionsDb = refinementOutput.compressorReductionDb,
            isSilentGateClosed = refinementOutput.isSilentGateClosed,
            batteryTier = currentTier,
            thermalStatus = batteryThermalManager.thermalStatusName.value,
            ambientState = currentState,
            cadenceDescription = yamNetGate.cadenceDescription,
            audioSourceName = audioCapture.activeAudioSourceName.value,
            yamNetDelegate = yamNetClassifier.delegateInUse,
            audioSourceWarning = audioCapture.audioSourceWarning.value
        )

        // 6. Source & Bearing Rules
        val source = if (isEarbud) Source.AUDIO_EARBUD else Source.AUDIO
        val bearing = if (isEarbud) Bearing.FRONT else Bearing.UNKNOWN

        // 7. Presence Tracking (with hysteresis & label stability)
        val tracks = presenceTracker.update(
            features = features,
            signal = stabilizedLabel.taxonomySignal,
            signalConfidence = stabilizedLabel.confidence,
            bearing = bearing,
            displayLabel = displayLabel
        )

        // 8. Event Emission (bounded rate <= 4/sec) with normalized state & batteryTier
        val isNormalized = !refinementOutput.isSilentGateClosed
        val newEvents = eventEmitter.processTracks(
            tracks = tracks,
            source = source,
            currentTimestampMs = timestamp,
            ambientState = currentState,
            normalized = isNormalized,
            batteryTier = currentTier
        )
        val allEventsToEmit = mutableListOf<DoTProEvent>()
        allEventsToEmit.addAll(newEvents)

        // In AMBIENT state on periodic cadence, if no presence track fired, emit ambient facts
        if (currentState == AmbientState.AMBIENT &&
            yamNetGate.lastTriggerReason == GateTriggerReason.AMBIENT_CADENCE &&
            shouldInvokeYamNet
        ) {
            val ambientEvent = DoTProEvent(
                signal = stabilizedLabel.taxonomySignal,
                source = source,
                presence = Presence.SUSTAINED,
                proximity = Proximity.FAR,
                bearing = bearing,
                motion = Motion.STATIC,
                change = false,
                confidence = stabilizedLabel.confidence,
                timestampMs = timestamp,
                displayLabel = displayLabel,
                state = AmbientState.AMBIENT,
                normalized = isNormalized,
                batteryTier = currentTier
            )
            allEventsToEmit.add(ambientEvent)
        }

        if (allEventsToEmit.isNotEmpty()) {
            serviceScope.launch(Dispatchers.Main) {
                val currentList = _recentEventsFlow.value
                val updated = (currentList + allEventsToEmit).takeLast(100)
                _recentEventsFlow.value = updated
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopModule()
        batteryThermalManager.release()
        yamNetClassifier.close()
        serviceScope.cancel()
    }
}
