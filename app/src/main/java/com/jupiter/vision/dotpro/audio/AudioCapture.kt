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
 * Continuous raw PCM via AudioRecord. 16 kHz, mono.
 * Ring buffer only. Never written to disk. Never transmitted.
 * Reads into a rolling window of short frames (50 ms = 800 samples at 16 kHz).
 * Routed through earbud mic when earbuds are connected.
 */
class AudioCapture(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AudioCapture"
        const val SAMPLE_RATE = 16000
        const val FRAME_DURATION_MS = 50
        const val SAMPLES_PER_FRAME = (SAMPLE_RATE * FRAME_DURATION_MS) / 1000 // 800 samples

        // Ring buffer capacity (e.g. ~4 seconds of audio = 80 frames = 64,000 samples)
        private const val RING_BUFFER_CAPACITY = SAMPLES_PER_FRAME * 80
    }

    // In-memory raw PCM ring buffer (NEVER written to disk, NEVER transmitted)
    private val ringBuffer = FloatArray(RING_BUFFER_CAPACITY)
    private var writeHead = 0

    /**
     * Retrieves the most recent [sampleCount] audio samples from volatile ring buffer.
     * Never saved, never transmitted.
     */
    @Synchronized
    fun getRecentSamples(sampleCount: Int): FloatArray {
        val count = minOf(sampleCount, RING_BUFFER_CAPACITY)
        val result = FloatArray(count)
        val start = (writeHead - count + RING_BUFFER_CAPACITY) % RING_BUFFER_CAPACITY
        for (i in 0 until count) {
            result[i] = ringBuffer[(start + i) % RING_BUFFER_CAPACITY]
        }
        return result
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val isRunning = AtomicBoolean(false)

    // Listener for frame outputs
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
     * Start continuous audio recording.
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

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSizeInBytes
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return false
            }

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
                        // and write into volatile ring buffer
                        for (i in 0 until SAMPLES_PER_FRAME) {
                            val sample = shortBuffer[i] / 32768.0f
                            frameFloats[i] = sample
                            ringBuffer[(writeHead + i) % RING_BUFFER_CAPACITY] = sample
                        }
                        writeHead = (writeHead + SAMPLES_PER_FRAME) % RING_BUFFER_CAPACITY

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
