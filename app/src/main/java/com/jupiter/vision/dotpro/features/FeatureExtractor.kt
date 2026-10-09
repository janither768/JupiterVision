package com.jupiter.vision.dotpro.features

import com.jupiter.vision.dotpro.model.Motion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Feature vector extracted per 50ms audio frame.
 */
data class FrameFeatures(
    val frameRms: Float,
    val baselineRms: Float,
    val changeMagnitude: Float,
    val isTransient: Boolean,
    val envelopeVelocity: Float,
    val motion: Motion,
    val spectralCentroid: Float,
    val spectralBandwidth: Float,
    val spectralRolloff: Float,
    val spectralFlatness: Float,
    val zeroCrossingRate: Float,
    val periodicityEstimate: Float,
    val timestampMs: Long
)

/**
 * Continuous digital signal processing and spectral feature extraction:
 * - Per-frame: energy (RMS)
 * - Rolling baseline energy over the last ~10 seconds (200 frames @ 50ms)
 * - Change magnitude = absolute delta between current frame energy and baseline
 * - Transient detection: frame energy exceeds baseline by a threshold
 * - Envelope velocity: derivative of smoothed energy across last few frames -> APPROACHING / RECEDING / STATIC
 * - Spectral features per frame: centroid, bandwidth, rolloff, flatness, zero-crossing rate, periodicity estimate
 */
class FeatureExtractor {

    companion object {
        private const val BASELINE_WINDOW_FRAMES = 200 // 10 seconds @ 50ms (20fps)
        private const val VELOCITY_WINDOW_FRAMES = 5   // ~250ms smoothed envelope
        private const val TRANSIENT_FACTOR = 2.4f
        private const val TRANSIENT_MIN_DELTA = 0.015f
    }

    // Rolling baseline buffer
    private val baselineBuffer = FloatArray(BASELINE_WINDOW_FRAMES)
    private var baselineHead = 0
    private var baselineCount = 0
    private var baselineSum = 0f

    // Smoothed envelope history for velocity derivative
    private val envelopeHistory = FloatArray(VELOCITY_WINDOW_FRAMES)
    private var envelopeIndex = 0

    // FFT scratch buffers for 512-point FFT on 800-sample frame
    private val fftSize = 512
    private val fftReal = FloatArray(fftSize)
    private val fftImag = FloatArray(fftSize)
    private val windowCoeffs = FloatArray(fftSize) { i ->
        // Hann window
        (0.5f * (1.0f - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
    }
    private val magnitudeSpectrum = FloatArray(fftSize / 2)

    /**
     * Process an 800-sample PCM frame [-1.0f..1.0f] at 16kHz and extract features.
     */
    fun processFrame(samples: FloatArray, timestampMs: Long): FrameFeatures {
        val n = samples.size

        // 1. Frame Energy (RMS) & Zero-Crossing Rate
        var sumSquares = 0.0
        var zeroCrossings = 0
        var prevSign = samples.getOrElse(0) { 0f } >= 0

        for (i in 0 until n) {
            val s = samples[i]
            sumSquares += (s * s)
            val currentSign = s >= 0
            if (currentSign != prevSign) {
                zeroCrossings++
                prevSign = currentSign
            }
        }

        val frameRms = sqrt(sumSquares / n).toFloat()
        val zcr = zeroCrossings.toFloat() / n

        // 2. Rolling Baseline Energy (over last 10 seconds)
        if (baselineCount < BASELINE_WINDOW_FRAMES) {
            baselineBuffer[baselineHead] = frameRms
            baselineSum += frameRms
            baselineCount++
        } else {
            baselineSum -= baselineBuffer[baselineHead]
            baselineBuffer[baselineHead] = frameRms
            baselineSum += frameRms
        }
        baselineHead = (baselineHead + 1) % BASELINE_WINDOW_FRAMES
        val baselineRms = if (baselineCount > 0) baselineSum / baselineCount else frameRms

        // 3. Change Magnitude
        val changeMagnitude = abs(frameRms - baselineRms)

        // 4. Transient Detection
        val isTransient = frameRms > (baselineRms * TRANSIENT_FACTOR) && changeMagnitude > TRANSIENT_MIN_DELTA

        // 5. Envelope Velocity (derivative of smoothed energy across recent frames)
        envelopeHistory[envelopeIndex % VELOCITY_WINDOW_FRAMES] = frameRms
        envelopeIndex++

        val velocity = if (envelopeIndex >= 2) {
            val currentIdx = (envelopeIndex - 1) % VELOCITY_WINDOW_FRAMES
            val prevIdx = (envelopeIndex - 2) % VELOCITY_WINDOW_FRAMES
            envelopeHistory[currentIdx] - envelopeHistory[prevIdx]
        } else {
            0f
        }

        val motion = when {
            velocity > 0.012f -> Motion.APPROACHING
            velocity < -0.012f -> Motion.RECEDING
            abs(velocity) <= 0.012f && frameRms > 0.01f -> Motion.STATIC
            else -> Motion.UNKNOWN
        }

        // 6. Spectral Features (via 512-point FFT)
        for (i in 0 until fftSize) {
            fftReal[i] = if (i < n) samples[i] * windowCoeffs[i] else 0f
            fftImag[i] = 0f
        }

        computeFft512(fftReal, fftImag)

        val halfSize = fftSize / 2
        var totalPower = 0.0f
        var weightedFreqSum = 0.0f
        var logPowerSum = 0.0
        val sampleRate = 16000f
        val freqBinWidth = sampleRate / fftSize // 31.25 Hz per bin

        for (k in 0 until halfSize) {
            val r = fftReal[k]
            val im = fftImag[k]
            val mag = sqrt(r * r + im * im)
            magnitudeSpectrum[k] = mag
            val power = mag * mag
            totalPower += power
            val freq = k * freqBinWidth
            weightedFreqSum += freq * power
            logPowerSum += ln(maxOf(power.toDouble(), 1e-12))
        }

        val spectralCentroid = if (totalPower > 1e-6f) weightedFreqSum / totalPower else 0f

        var bandwidthSum = 0.0f
        for (k in 0 until halfSize) {
            val freq = k * freqBinWidth
            val diff = freq - spectralCentroid
            val power = magnitudeSpectrum[k] * magnitudeSpectrum[k]
            bandwidthSum += (diff * diff) * power
        }
        val spectralBandwidth = if (totalPower > 1e-6f) sqrt(bandwidthSum / totalPower) else 0f

        // Spectral Rolloff: frequency below which 85% of total power lies
        val rolloffThreshold = totalPower * 0.85f
        var cumulativePower = 0f
        var spectralRolloff = 0f
        for (k in 0 until halfSize) {
            cumulativePower += magnitudeSpectrum[k] * magnitudeSpectrum[k]
            if (cumulativePower >= rolloffThreshold) {
                spectralRolloff = k * freqBinWidth
                break
            }
        }

        // Spectral Flatness (Wiener entropy: geometric mean / arithmetic mean)
        val arithmeticMean = (totalPower / halfSize).toDouble()
        val geometricMean = exp(logPowerSum / halfSize)
        val spectralFlatness = if (arithmeticMean > 1e-8) {
            (geometricMean / arithmeticMean).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

        // 7. Periodicity Estimate (Normalized Autocorrelation in cadence range ~1 Hz to 4 Hz)
        val periodicityEstimate = computePeriodicity(samples)

        return FrameFeatures(
            frameRms = frameRms,
            baselineRms = baselineRms,
            changeMagnitude = changeMagnitude,
            isTransient = isTransient,
            envelopeVelocity = velocity,
            motion = motion,
            spectralCentroid = spectralCentroid,
            spectralBandwidth = spectralBandwidth,
            spectralRolloff = spectralRolloff,
            spectralFlatness = spectralFlatness,
            zeroCrossingRate = zcr,
            periodicityEstimate = periodicityEstimate,
            timestampMs = timestampMs
        )
    }

    /**
     * Estimates periodicity in human cadence range (approx 1 Hz to 4 Hz -> lag 4000 to 16000 samples).
     * For 50ms frames, autocorrelation of energy envelope over recent history gives rhythmic cadence.
     */
    private val energyCadenceHistory = FloatArray(40) // 2 seconds @ 20fps
    private var cadenceHead = 0

    private fun computePeriodicity(samples: FloatArray): Float {
        // Fast local pitch autocorrelation on raw samples (between lag 32 and 160 -> 100 Hz to 500 Hz)
        var maxCorr = 0.0f
        val maxLag = minOf(160, samples.size / 2)
        val minLag = 32

        var energy0 = 0.0f
        for (i in 0 until 400) {
            val s = samples[i]
            energy0 += s * s
        }

        if (energy0 < 1e-5f) return 0f

        for (lag in minLag..maxLag step 4) {
            var dot = 0.0f
            for (i in 0 until 300) {
                dot += samples[i] * samples[i + lag]
            }
            val normCorr = dot / energy0
            if (normCorr > maxCorr) {
                maxCorr = normCorr
            }
        }
        return maxCorr.coerceIn(0f, 1f)
    }

    /**
     * Radix-2 In-place Cooley-Tukey FFT for N = 512.
     */
    private fun computeFft512(real: FloatArray, imag: FloatArray) {
        val n = 512
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR
                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = -2.0 * PI / len
            val wStepR = cos(angle).toFloat()
            val wStepI = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var wR = 1.0f
                var wI = 0.0f
                for (k in 0 until halfLen) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + halfLen] * wR - imag[i + k + halfLen] * wI
                    val vI = real[i + k + halfLen] * wI + imag[i + k + halfLen] * wR

                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + halfLen] = uR - vR
                    imag[i + k + halfLen] = uI - vI

                    val nextWR = wR * wStepR - wI * wStepI
                    val nextWI = wR * wStepI + wI * wStepR
                    wR = nextWR
                    wI = nextWI
                }
                i += len
            }
            len = len shl 1
        }
    }
}
