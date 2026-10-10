package com.jupiter.vision.dotpro.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Continuous raw PCM via AudioRecord at 48 kHz mono.
 * Preferred source: MediaRecorder.AudioSource.UNPROCESSED (disables AGC/AEC/NS).
 * Fallbacks: VOICE_RECOGNITION -> MIC.
 * Ring buffer only. Never written to disk. Never transmitted.
 * Reads into short frames (50 ms = 2400 samples at 48 kHz).
 * Feeds RefinementPipeline which outputs refined 48 kHz and downsamples to 16 kHz.
 */
class AudioCapture(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AudioCapture"
        const val SAMPLE_RATE = 48000
        const val FRAME_DURATION_MS = 50
        const val SAMPLES_PER_FRAME = (SAMPLE_RATE * FRAME_DURATION_MS) / 1000 // 2400 samples @ 48kHz

        // 48 kHz Ring buffer capacity (~4 seconds = 80 frames = 192,000 samples)
        private const val RING_BUFFER_CAPACITY_48K = SAMPLES_PER_FRAME * 80

        // 16 kHz Ring buffer capacity (~4 seconds = 80 frames = 64,000 samples)
        private const val RING_BUFFER_CAPACITY_16K = 800 * 80
    }

    // In-memory raw PCM ring buffers (NEVER written to disk, NEVER transmitted)
    private val ringBuffer48k = FloatArray(RING_BUFFER_CAPACITY_48K)
    private var writeHead48k = 0

    private val ringBuffer16k = FloatArray(RING_BUFFER_CAPACITY_16K)
    private var writeHead16k = 0

    private val _activeAudioSourceName = MutableStateFlow("UNKNOWN")
    val activeAudioSourceName: StateFlow<String> = _activeAudioSourceName.asStateFlow()

    /**
     * Retrieves the most recent [sampleCount] 48kHz audio samples from volatile ring buffer.
     */
    @Synchronized
    fun getRecentSamples(sampleCount: Int): FloatArray {
        val count = minOf(sampleCount, RING_BUFFER_CAPACITY_48K)
        val result = FloatArray(count)
        val start = (writeHead48k - count + RING_BUFFER_CAPACITY_48K) % RING_BUFFER_CAPACITY_48K
        for (i in 0 until count) {
            result[i] = ringBuffer48k[(start + i) % RING_BUFFER_CAPACITY_48K]
        }
        return result
    }

    /**
     * Appends refined downsampled 16kHz samples (800 samples per 50ms frame) into the 16k ring buffer.
     */
    @Synchronized
    fun append16kSamples(samples: FloatArray) {
        val n = samples.size
        for (i in 0 until n) {
            ringBuffer16k[(writeHead16k + i) % RING_BUFFER_CAPACITY_16K] = samples[i]
        }
        writeHead16k = (writeHead16k + n) % RING_BUFFER_CAPACITY_16K
    }

    /**
     * Retrieves the most recent [sampleCount] 16kHz audio samples for YAMNet (e.g. 15600 samples).
     */
    @Synchronized
    fun getRecent16kSamples(sampleCount: Int): FloatArray {
        val count = minOf(sampleCount, RING_BUFFER_CAPACITY_16K)
        val result = FloatArray(count)
        val start = (writeHead16k - count + RING_BUFFER_CAPACITY_16K) % RING_BUFFER_CAPACITY_16K
        for (i in 0 until count) {
            result[i] = ringBuffer16k[(start + i) % RING_BUFFER_CAPACITY_16K]
        }
        return result
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val isRunning = AtomicBoolean(false)

    // Listener for frame outputs (48kHz float array, isEarbud)
    var onFrameRead: ((FloatArray, Boolean) -> Unit)? = null

    // Route tracking
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val _isEarbudConnected = MutableStateFlow(false)
    val isEarbudConnected: StateFlow<Boolean> = _isEarbudConnected.asStateFlow()

    init {
        checkEarbudRouting()
    }

    /**
     * Inspect audio routes to detect connected wired or Bluetooth headsets/earbuds.
     */
    fun checkEarbudRouting(): Boolean {
        var earbudFound = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager != null) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            for (device in devices) {
                when (device.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_USB_HEADSET -> {
                        earbudFound = true
                    }
                }
            }
        } else if (audioManager != null) {
            @Suppress("DEPRECATION")
            earbudFound = audioManager.isWiredHeadsetOn || audioManager.isBluetoothScoOn
        }
        _isEarbudConnected.value = earbudFound
        return earbudFound
    }

    /**
     * Start continuous 48 kHz audio recording with source prioritization:
     * 1. UNPROCESSED (disables AGC/AEC/NS)
     * 2. VOICE_RECOGNITION (minimal processing)
     * 3. MIC (fallback)
     */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(): Boolean {
        if (isRunning.get()) return true

        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSizeInBytes = maxOf(minBufSize, SAMPLES_PER_FRAME * 2 * 4)

        // Determine if UNPROCESSED is supported
        val isUnprocessedSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && audioManager != null) {
            audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)?.toBoolean() == true
        } else {
            false
        }

        val candidateSources = mutableListOf<Pair<Int, String>>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && (isUnprocessedSupported || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
            candidateSources.add(MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED")
        }
        candidateSources.add(MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION")
        candidateSources.add(MediaRecorder.AudioSource.MIC to "MIC (Fallback)")

        var chosenRecord: AudioRecord? = null
        var chosenName = "MIC"

        for ((source, name) in candidateSources) {
            try {
                val record = AudioRecord(
                    source,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSizeInBytes
                )
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    chosenRecord = record
                    chosenName = name
                    Log.i(TAG, "Successfully initialized AudioRecord with source: $name at 48kHz")
                    break
                } else {
                    record.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed candidate audio source: $name", e)
            }
        }

        if (chosenRecord == null) {
            Log.e(TAG, "AudioRecord failed to initialize with all candidate sources")
            return false
        }

        audioRecord = chosenRecord
        _activeAudioSourceName.value = chosenName

        try {
            // Route to earbud device if available on Android M+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager != null) {
                val inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                for (device in inputDevices) {
                    if (device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        device.type == AudioDeviceInfo.TYPE_USB_HEADSET
                    ) {
                        audioRecord?.preferredDevice = device
                        _isEarbudConnected.value = true
                        break
                    }
                }
            }

            audioRecord?.startRecording()
            isRunning.set(true)

            recordingJob = scope.launch(Dispatchers.Default) {
                val shortBuffer = ShortArray(SAMPLES_PER_FRAME)
                val frameFloats = FloatArray(SAMPLES_PER_FRAME)

                while (isActive && isRunning.get()) {
                    var samplesRead = 0
                    while (samplesRead < SAMPLES_PER_FRAME && isActive && isRunning.get()) {
                        val read = audioRecord?.read(
                            shortBuffer,
                            samplesRead,
                            SAMPLES_PER_FRAME - samplesRead
                        ) ?: -1

                        if (read > 0) {
                            samplesRead += read
                        } else if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                            Log.e(TAG, "AudioRecord read error: $read")
                            break
                        }
                    }

                    if (samplesRead == SAMPLES_PER_FRAME) {
                        // Normalize 16-bit PCM shorts to floats [-1.0f, 1.0f]
                        // and write into volatile 48 kHz ring buffer
                        for (i in 0 until SAMPLES_PER_FRAME) {
                            val sample = shortBuffer[i] / 32768.0f
                            frameFloats[i] = sample
                            ringBuffer48k[(writeHead48k + i) % RING_BUFFER_CAPACITY_48K] = sample
                        }
                        writeHead48k = (writeHead48k + SAMPLES_PER_FRAME) % RING_BUFFER_CAPACITY_48K

                        val isEarbud = _isEarbudConnected.value
                        onFrameRead?.invoke(frameFloats.clone(), isEarbud)
                    }
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioCapture", e)
            stop()
            return false
        }
    }

    /**
     * Stop continuous recording and release hardware.
     */
    @Synchronized
    fun stop() {
        isRunning.set(false)
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {}

        try {
            audioRecord?.release()
        } catch (_: Exception) {}

        audioRecord = null
    }
}
