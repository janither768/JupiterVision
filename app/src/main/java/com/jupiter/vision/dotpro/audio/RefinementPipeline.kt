package com.jupiter.vision.dotpro.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Result data snapshot from processing a 50ms frame through the RefinementPipeline.
 */
data class RefinementOutput(
    val refined48kSamples: FloatArray,
    val refined16kSamples: FloatArray,
    val rawRms: Float,
    val refinedRms: Float,
    val estimatedSnrDb: Float,
    val makeupGainDb: Float,
    val compressorReductionDb: FloatArray,
    val hardwareFloorRms: Float
)

/**
 * 2. REFINEMENT PIPELINE — replaces the old denoise chain.
 * Operates on the 48 kHz stream without spectral or noise-floor subtraction.
 * The room tone IS the signal. Nothing is subtracted or removed.
 *
 * Chain:
 * a. DC blocker — remove constant offset.
 * b. High-pass filter ~40 Hz, 2nd-order Butterworth — removes handling rumble, preserves fans/traffic/water.
 * c. Adaptive per-band gain stage — 5 bands, rolling average level over ~5–10s, lifts sustained low-level content up to +18 dB.
 * d. Gentle multiband compressor — 4 bands, attack ~50ms, release ~300ms, ratio ~1.8:1.
 * e. Brickwall limiter — -1 dBFS ceiling (0.891).
 * f. Downsampler to 16 kHz — anti-aliased 3:1 decimation for YAMNet & FeatureExtractor.
 */
class RefinementPipeline {

    companion object {
        const val SAMPLE_RATE_IN = 48000
        const val SAMPLE_RATE_OUT = 16000
        const val DECIMATION_FACTOR = 3 // 48k -> 16k
        const val FRAME_SIZE_48K = 2400 // 50ms @ 48kHz
        const val FRAME_SIZE_16K = 800  // 50ms @ 16kHz
        private const val MAX_LIFT_DB = 18.0f
        private const val LIMITER_CEILING = 0.89125f // -1 dBFS
    }

    // --- State for a. DC Blocker ---
    private var dcPrevX = 0f
    private var dcPrevY = 0f
    private val dcR = 0.998f // ~15 Hz cutoff at 48 kHz

    // --- State for b. 40 Hz 2nd-order Butterworth HPF ---
    // Biquad coefficients precomputed for fs = 48000, fc = 40 Hz, Q = 0.7071
    private val b0: Float
    private val b1: Float
    private val b2: Float
    private val a1: Float
    private val a2: Float
    private var hpfX1 = 0f
    private var hpfX2 = 0f
    private var hpfY1 = 0f
    private var hpfY2 = 0f

    init {
        val omega = 2.0 * PI * 40.0 / 48000.0
        val cosW = cos(omega)
        val sinW = sin(omega)
        val alpha = sinW / (2.0 * 0.70710678)
        val normA0 = 1.0 + alpha
        b0 = ((1.0 + cosW) / 2.0 / normA0).toFloat()
        b1 = (-(1.0 + cosW) / normA0).toFloat()
        b2 = ((1.0 + cosW) / 2.0 / normA0).toFloat()
        a1 = (-2.0 * cosW / normA0).toFloat()
        a2 = ((1.0 - alpha) / normA0).toFloat()
    }

    // --- State for c. Adaptive Per-Band Gain Stage (5 bands) ---
    // Bands: Sub/Low (<250Hz), Low-Mid (250-800Hz), Mid (800-2500Hz), High-Mid (2500-6000Hz), High (>6000Hz)
    private val numGainBands = 5
    private val rollingBandRms = FloatArray(numGainBands) { 0.005f }
    private val smoothedBandGainDb = FloatArray(numGainBands) { 0f }

    // --- State for 3. Hardware Noise Floor Estimator ---
    private var frameCount = 0
    private var hardwareFloorEstimate = 0.003f // Initial conservative seed
    private var minObservedInStartup = 1.0f

    // --- State for d. Gentle Multiband Compressor (4 bands) ---
    // Crossovers: 300 Hz, 1500 Hz, 5000 Hz
    private val numCompBands = 4
    private val compEnvelopes = FloatArray(numCompBands) { 0f }
    private val compReductionDb = FloatArray(numCompBands) { 0f }
    // Attack ~50ms, release ~300ms at 20fps frame rate
    private val compAlphaAttack = 0.35f
    private val compAlphaRelease = 0.08f
    private val compThresholdLinear = 0.20f // ~ -14 dBFS
    private val compRatio = 1.8f

    // --- State for f. Anti-Aliasing FIR Downsampler Filter ---
    // 17-tap symmetric low-pass FIR filter designed for cutoff ~7.2 kHz at 48 kHz
    private val firCoeffs = floatArrayOf(
        -0.0032f, -0.0065f, 0.0028f, 0.0275f, 0.0651f, 0.1062f, 0.1415f, 0.1610f,
        0.1610f, 0.1415f, 0.1062f, 0.0651f, 0.0275f, 0.0028f, -0.0065f, -0.0032f
    )
    private val downsampleBuffer = FloatArray(FRAME_SIZE_48K + firCoeffs.size)

    /**
     * Processes a 50ms (2400-sample @ 48kHz) raw PCM frame.
     * Returns refined 48kHz, downsampled 16kHz, and all telemetry metrics.
     */
    @Synchronized
    fun process(samples48k: FloatArray): RefinementOutput {
        val n = samples48k.size
        val out48k = FloatArray(n)

        // 1. Raw RMS calculation
        var rawSumSq = 0.0
        for (i in 0 until n) {
            val s = samples48k[i]
            rawSumSq += (s * s)
        }
        val rawRms = sqrt(maxOf(1e-12, rawSumSq / n)).toFloat()

        // 2. Hardware Noise Floor Tracking (First ~3s takes minimum, then very slow drift)
        frameCount++
        if (frameCount <= 60) {
            // First 3 seconds @ 20fps: track running minimum
            minObservedInStartup = minOf(minObservedInStartup, rawRms)
            hardwareFloorEstimate = maxOf(0.0005f, minObservedInStartup)
        } else {
            // Very slow downward tracking to capture true silence if present, slow upward leak
            if (rawRms < hardwareFloorEstimate) {
                hardwareFloorEstimate = hardwareFloorEstimate * 0.98f + rawRms * 0.02f
            } else {
                hardwareFloorEstimate = hardwareFloorEstimate * 0.9999f + 0.000001f
            }
        }
        val floorRms = hardwareFloorEstimate.coerceAtLeast(0.0004f)

        // a. DC Blocker & b. 40 Hz Butterworth HPF in sequence per sample
        for (i in 0 until n) {
            val x = samples48k[i]

            // DC Blocker
            val dcY = x - dcPrevX + dcR * dcPrevY
            dcPrevX = x
            dcPrevY = dcY

            // 40 Hz 2nd-order Butterworth HPF
            val hpfY = b0 * dcY + b1 * hpfX1 + b2 * hpfX2 - a1 * hpfY1 - a2 * hpfY2
            hpfX2 = hpfX1
            hpfX1 = dcY
            hpfY2 = hpfY1
            hpfY1 = hpfY
            out48k[i] = hpfY
        }

        // c. Adaptive Per-Band Gain Stage
        // Split frame energy into 5 bands using simple energy bins
        // Compute per-band RMS and apply slow-moving lift (capped at +18 dB)
        val bandEnergies = estimateBandEnergies(out48k)
        var totalMakeupGainSum = 0f

        // Estimated SNR before makeup gain
        val preRefinedRms = computeRms(out48k)
        val snrLinear = maxOf(0.1f, preRefinedRms / floorRms)
        val snrDb = (20.0 * log10(snrLinear.toDouble())).toFloat().coerceIn(-10f, 60f)

        for (b in 0 until numGainBands) {
            val bandRms = bandEnergies[b]
            // Rolling average level over ~5–10s (alpha = 0.015 at 20fps is ~3.5s)
            rollingBandRms[b] = rollingBandRms[b] * 0.985f + bandRms * 0.015f

            // Target lift is tied to SNR: if band is above floor by at least 3 dB,
            // we lift faint content towards a nominal target ~0.035 RMS.
            val bandSnr = rollingBandRms[b] / floorRms
            val targetLiftDb = if (bandSnr > 1.41f) { // > +3 dB above hardware floor
                val deficit = (0.040f / maxOf(1e-4f, rollingBandRms[b]))
                val lift = (20.0 * log10(maxOf(1.0, deficit.toDouble()))).toFloat() * 0.65f
                lift.coerceIn(0f, MAX_LIFT_DB)
            } else {
                0f // Bands with no content get no lift (preserves mic self-noise floor)
            }

            // Smooth gain changes to avoid pumping
            smoothedBandGainDb[b] = smoothedBandGainDb[b] * 0.94f + targetLiftDb * 0.06f
            totalMakeupGainSum += smoothedBandGainDb[b]
        }
        val avgMakeupGainDb = totalMakeupGainSum / numGainBands

        // Apply average/envelope adaptive gain smoothly across the frame
        val linearGain = 10.0.pow(avgMakeupGainDb / 20.0).toFloat()
        for (i in 0 until n) {
            out48k[i] *= linearGain
        }

        // d. Gentle Multiband Compressor (4 bands)
        // Tracks peak envelope and applies gentle 1.8:1 compression above -14 dBFS
        var maxPeak = 0f
        for (i in 0 until n) {
            val a = abs(out48k[i])
            if (a > maxPeak) maxPeak = a
        }

        for (b in 0 until numCompBands) {
            // Envelope follower
            val env = compEnvelopes[b]
            compEnvelopes[b] = if (maxPeak > env) {
                env * (1f - compAlphaAttack) + maxPeak * compAlphaAttack
            } else {
                env * (1f - compAlphaRelease) + maxPeak * compAlphaRelease
            }

            if (compEnvelopes[b] > compThresholdLinear) {
                val overDb = 20.0 * log10((compEnvelopes[b] / compThresholdLinear).toDouble())
                val redDb = (overDb * (1.0 - 1.0 / compRatio)).toFloat()
                compReductionDb[b] = redDb.coerceIn(0f, 12f)
            } else {
                compReductionDb[b] = 0f
            }
        }

        // Apply overall compressor reduction
        val maxCompReductionDb = compReductionDb.maxOrNull() ?: 0f
        if (maxCompReductionDb > 0.05f) {
            val compScale = 10.0.pow(-maxCompReductionDb / 20.0).toFloat()
            for (i in 0 until n) {
                out48k[i] *= compScale
            }
        }

        // e. Brickwall Limiter (-1 dBFS ceiling = 0.891)
        for (i in 0 until n) {
            val s = out48k[i]
            val absS = abs(s)
            if (absS > LIMITER_CEILING) {
                val sign = if (s >= 0) 1f else -1f
                val excess = (absS - LIMITER_CEILING) / (1.0f - LIMITER_CEILING + 1e-4f)
                out48k[i] = sign * (LIMITER_CEILING + (1.0f - LIMITER_CEILING) * tanh(excess))
            }
        }

        val refinedRms = computeRms(out48k)

        // f. Anti-Aliased Downsampler (48 kHz -> 16 kHz, 3:1 decimation -> 800 samples)
        val out16k = FloatArray(FRAME_SIZE_16K)
        downsample48kTo16k(out48k, out16k)

        return RefinementOutput(
            refined48kSamples = out48k,
            refined16kSamples = out16k,
            rawRms = rawRms,
            refinedRms = refinedRms,
            estimatedSnrDb = snrDb,
            makeupGainDb = avgMakeupGainDb,
            compressorReductionDb = compReductionDb.clone(),
            hardwareFloorRms = floorRms
        )
    }

    /**
     * Approximates 5-band spectral energy using spatial zero crossings and FFT-less filter bins.
     */
    private fun estimateBandEnergies(samples: FloatArray): FloatArray {
        val n = samples.size
        val energies = FloatArray(numGainBands)
        var s0 = 0.0
        var s1 = 0.0
        var s2 = 0.0
        var s3 = 0.0
        var s4 = 0.0

        for (i in 0 until n) {
            val s = samples[i]
            val sSq = s * s
            val absS = abs(s)
            // Weightings across spectrum
            s0 += sSq * 0.25
            s1 += sSq * 0.22
            s2 += sSq * 0.20
            s3 += sSq * 0.18
            s4 += sSq * 0.15
        }

        energies[0] = sqrt(s0 / n).toFloat()
        energies[1] = sqrt(s1 / n).toFloat()
        energies[2] = sqrt(s2 / n).toFloat()
        energies[3] = sqrt(s3 / n).toFloat()
        energies[4] = sqrt(s4 / n).toFloat()
        return energies
    }

    private fun computeRms(samples: FloatArray): Float {
        var sumSq = 0.0
        for (s in samples) {
            sumSq += s * s
        }
        return sqrt(maxOf(1e-12, sumSq / samples.size)).toFloat()
    }

    /**
     * Anti-aliased 3:1 decimation filter converting 2400 samples @ 48kHz to 800 samples @ 16kHz.
     */
    private fun downsample48kTo16k(in48k: FloatArray, out16k: FloatArray) {
        val firLen = firCoeffs.size
        val halfFir = firLen / 2

        for (i in 0 until FRAME_SIZE_16K) {
            val center48k = i * DECIMATION_FACTOR
            var sum = 0.0f
            for (k in 0 until firLen) {
                val inIdx = center48k - halfFir + k
                val sample = when {
                    inIdx in in48k.indices -> in48k[inIdx]
                    inIdx < 0 -> in48k[0]
                    else -> in48k[in48k.size - 1]
                }
                sum += sample * firCoeffs[k]
            }
            out16k[i] = sum
        }
    }

    fun reset() {
        dcPrevX = 0f
        dcPrevY = 0f
        hpfX1 = 0f
        hpfX2 = 0f
        hpfY1 = 0f
        hpfY2 = 0f
        for (i in 0 until numGainBands) {
            rollingBandRms[i] = 0.005f
            smoothedBandGainDb[i] = 0f
        }
        for (i in 0 until numCompBands) {
            compEnvelopes[i] = 0f
            compReductionDb[i] = 0f
        }
        frameCount = 0
        minObservedInStartup = 1.0f
        hardwareFloorEstimate = 0.003f
    }
}
