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
 * Continuous raw PCM via AudioRecord.
 * Low-end tiering:
 * 1. Source priority: UNPROCESSED (disables AGC/AEC/NS) -> VOICE_RECOGNITION -> MIC (with warning).
 * 2. Sample rate priority: 48 kHz first, with 44.1 kHz fallback and in-pipeline linear resampling.
 * 3. Tier 4 support: direct 16 kHz capture mode under CRITICAL thermal condition.
 */
class AudioCapture(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AudioCapture"
        const val SAMPLE_RATE_TARGET = 48000
        const val SAMPLE_RATE_FALLBACK = 44100
        const val FRAME_DURATION_MS = 50
        const val SAMPLES_PER_FRAME_48K = (SAMPLE_RATE_TARGET * FRAME_DURATION_MS) / 1000 // 2400 samples
        const val SAMPLES_PER_FRAME_44K = (SAMPLE_RATE_FALLBACK * FRAME_DURATION_MS) / 1000 // 2205 samples

        private const val RING_BUFFER_CAPACITY_48K = SAMPLES_PER_FRAME_48K * 80
        private const val RING_BUFFER_CAPACITY_16K = 800 * 80
    }

    // In-memory volatile ring buffers
    private val ringBuffer48k = FloatArray(RING_BUFFER_CAPACITY_48K)
    private var writeHead48k = 0

    private val ringBuffer16k = FloatArray(RING_BUFFER_CAPACITY_16K)
    private var writeHead16k = 0

    private val _activeAudioSourceName = MutableStateFlow("UNKNOWN")
    val activeAudioSourceName: StateFlow<String> = _activeAudioSourceName.asStateFlow()

    private val _audioSourceWarning = MutableStateFlow<String?>(null)
    val audioSourceWarning: StateFlow<String?> = _audioSourceWarning.asStateFlow()

    private val _actualSampleRate = MutableStateFlow(SAMPLE_RATE_TARGET)
    val actualSampleRate: StateFlow<Int> = _actualSampleRate.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val isRunning = AtomicBoolean(false)

    var onFrameRead: ((FloatArray, Boolean) -> Unit)? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val _isEarbudConnected = MutableStateFlow(false)
    val isEarbudConnected: StateFlow<Boolean> = _isEarbudConnected.asStateFlow()

    init {
        checkEarbudRouting()
    }

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

    @Synchronized
    fun append16kSamples(samples: FloatArray) {
        val n = samples.size
        for (i in 0 until n) {
            ringBuffer16k[(writeHead16k + i) % RING_BUFFER_CAPACITY_16K] = samples[i]
        }
        writeHead16k = (writeHead16k + n) % RING_BUFFER_CAPACITY_16K
    }

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

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(): Boolean {
        if (isRunning.get()) return true

        // Candidate sources in strict priority order:
        // 1. UNPROCESSED (disables AGC/AEC/NS)
        // 2. VOICE_RECOGNITION
        // 3. MIC (fallback)
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
        candidateSources.add(MediaRecorder.AudioSource.MIC to "MIC")

        // Try 48 kHz first, then fallback to 44.1 kHz
        val sampleRatesToTry = listOf(SAMPLE_RATE_TARGET, SAMPLE_RATE_FALLBACK)

        var chosenRecord: AudioRecord? = null
        var chosenName = "MIC"
        var chosenSampleRate = SAMPLE_RATE_TARGET

        rateLoop@ for (rate in sampleRatesToTry) {
            val samplesPerFrame = if (rate == SAMPLE_RATE_TARGET) SAMPLES_PER_FRAME_48K else SAMPLES_PER_FRAME_44K
            val minBufSize = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSizeInBytes = maxOf(minBufSize, samplesPerFrame * 2 * 4)

            for ((source, name) in candidateSources) {
                try {
                    val record = AudioRecord(
                        source,
                        rate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSizeInBytes
                    )
                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        chosenRecord = record
                        chosenName = name
                        chosenSampleRate = rate
                        Log.i(TAG, "AudioRecord initialized with source=$name at $rate Hz")
                        break@rateLoop
                    } else {
                        record.release()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to initialize source=$name at $rate Hz", e)
                }
            }
        }

        if (chosenRecord == null) {
            Log.e(TAG, "AudioRecord failed to initialize across all sources and sample rates")
            return false
        }

        audioRecord = chosenRecord
        _activeAudioSourceName.value = chosenName
        _actualSampleRate.value = chosenSampleRate

        if (chosenName == "MIC") {
            _audioSourceWarning.value = "Hardware Warning: UNPROCESSED & VOICE_RECOGNITION unsupported. Running on MIC fallback."
        } else {
            _audioSourceWarning.value = null
        }

        try {
            // Preferred device route to headset if available
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

            val samplesToRead = if (chosenSampleRate == SAMPLE_RATE_TARGET) SAMPLES_PER_FRAME_48K else SAMPLES_PER_FRAME_44K

            recordingJob = scope.launch(Dispatchers.Default) {
                val shortBuffer = ShortArray(samplesToRead)
                val rawFloats = FloatArray(samplesToRead)
                val resampled48k = FloatArray(SAMPLES_PER_FRAME_48K)

                while (isActive && isRunning.get()) {
                    var samplesRead = 0
                    while (samplesRead < samplesToRead && isActive && isRunning.get()) {
                        val read = audioRecord?.read(
                            shortBuffer,
                            samplesRead,
                            samplesToRead - samplesRead
                        ) ?: -1

                        if (read > 0) {
                            samplesRead += read
                        } else if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                            Log.e(TAG, "AudioRecord read error: $read")
                            break
                        }
                    }

                    if (samplesRead == samplesToRead) {
                        // Normalize 16-bit PCM shorts to floats [-1.0f, 1.0f]
                        for (i in 0 until samplesToRead) {
                            rawFloats[i] = shortBuffer[i] / 32768.0f
                        }

                        // Resample from 44.1 kHz to 48 kHz if needed
                        val frame48k = if (chosenSampleRate == SAMPLE_RATE_TARGET) {
                            rawFloats
                        } else {
                            resample44kTo48k(rawFloats, resampled48k)
                            resampled48k
                        }

                        // Write to volatile 48 kHz ring buffer
                        for (i in 0 until SAMPLES_PER_FRAME_48K) {
                            val sample = frame48k[i]
                            ringBuffer48k[(writeHead48k + i) % RING_BUFFER_CAPACITY_48K] = sample
                        }
                        writeHead48k = (writeHead48k + SAMPLES_PER_FRAME_48K) % RING_BUFFER_CAPACITY_48K

                        val isEarbud = _isEarbudConnected.value
                        onFrameRead?.invoke(frame48k.clone(), isEarbud)
                    }
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioCapture recording loop", e)
            stop()
            return false
        }
    }

    private fun resample44kTo48k(in44k: FloatArray, out48k: FloatArray) {
        val inLen = in44k.size
        val outLen = out48k.size
        val ratio = (inLen - 1).toDouble() / (outLen - 1).toDouble()

        for (i in 0 until outLen) {
            val pos = i * ratio
            val index0 = pos.toInt().coerceIn(0, inLen - 1)
            val index1 = (index0 + 1).coerceIn(0, inLen - 1)
            val frac = (pos - index0).toFloat()
            out48k[i] = in44k[index0] * (1f - frac) + in44k[index1] * frac
        }
    }

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
