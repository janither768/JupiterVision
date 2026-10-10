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
    val referenceLoudnessDb: Float,
    val normalizationOffsetDb: Float,
    val bandLiftsDb: FloatArray,
    val compressorReductionDb: FloatArray,
    val isSilentGateClosed: Boolean,
    val hardwareFloorRms: Float
)

/**
 * REFINEMENT PIPELINE — Scale-normalization architecture for low-end Android.
 * Runs continuously on the 48 kHz stream without spectral or noise-floor subtraction.
 * The room tone IS the signal.
 *
 * Architecture:
 * 1. SCALE:
 *    a. DC blocker — single-pole high-pass at ~5 Hz. Removes offset.
 *    b. High-pass filter — 40 Hz, 2nd-order Butterworth (RBJ biquad). Removes rumble.
 *    c. Short-window loudness estimator — Rolling RMS over 2.5s, in dBFS.
 *    d. Dynamic reference tracker — Median of short-window loudness over 25s (O(log n) rolling sorted buffer).
 *    e. Scale normalization — offset = TARGET_WORKING_LEVEL_dBFS - reference_dB (clamped -6 dB to +40 dB).
 *
 * 2. SHAPE:
 *    f. Adaptive per-band gain on NORMALIZED signal — 5 bands via Linkwitz-Riley crossovers
 *       (120 Hz, 500 Hz, 2 kHz, 6 kHz). Gentle lift capped at +12 dB per band. Disabled in SILENT.
 *    g. Multiband compressor — 5 bands, attack ~50ms, release ~300ms, ratio 1.8:1, cap -6 dB reduction.
 *    h. Brickwall limiter — -1 dBFS ceiling, 2ms lookahead delay buffer.
 *    i. Downsampler to 16 kHz — anti-aliased 3:1 decimation FIR.
 */
class RefinementPipeline {

    companion object {
        const val SAMPLE_RATE_IN = 48000
        const val SAMPLE_RATE_OUT = 16000
        const val DECIMATION_FACTOR = 3 // 48k -> 16k
        const val FRAME_SIZE_48K = 2400 // 50ms @ 48kHz
        const val FRAME_SIZE_16K = 800  // 50ms @ 16kHz

        const val TARGET_WORKING_LEVEL_DBFS = -20.0f
        const val MIN_NORMALIZATION_OFFSET_DB = -6.0f
        const val MAX_NORMALIZATION_OFFSET_DB = 40.0f
        const val MAX_BAND_LIFT_DB = 12.0f
        const val SILENT_SNR_THRESHOLD_DB = 3.0f
        const val SILENT_HOLD_FRAMES = 40 // ~2 seconds @ 50ms per frame

        private const val LIMITER_CEILING = 0.89125f // -1 dBFS
        private const val LOOKAHEAD_SAMPLES = 96     // 2ms lookahead @ 48kHz
        private const val NUM_BANDS = 5
    }

    // --- a. DC Blocker State (~5 Hz cutoff @ 48 kHz) ---
    private var dcPrevX = 0f
    private var dcPrevY = 0f
    private val dcR = 0.99935f

    // --- b. 40 Hz 2nd-order Butterworth HPF (RBJ Biquad) ---
    private val b0Hpf: Float
    private val b1Hpf: Float
    private val b2Hpf: Float
    private val a1Hpf: Float
    private val a2Hpf: Float
    private var hpfX1 = 0f
    private var hpfX2 = 0f
    private var hpfY1 = 0f
    private var hpfY2 = 0f

    init {
        val omega = 2.0 * PI * 40.0 / SAMPLE_RATE_IN.toDouble()
        val cosW = cos(omega)
        val sinW = sin(omega)
        val alpha = sinW / (2.0 * 0.70710678)
        val a0 = 1.0 + alpha
        b0Hpf = ((1.0 + cosW) / 2.0 / a0).toFloat()
        b1Hpf = (-(1.0 + cosW) / a0).toFloat()
        b2Hpf = ((1.0 + cosW) / 2.0 / a0).toFloat()
        a1Hpf = (-2.0 * cosW / a0).toFloat()
        a2Hpf = ((1.0 - alpha) / a0).toFloat()
    }

    // --- c. Short-Window Loudness Estimator (2.5s = 50 frames @ 50ms) ---
    private val shortWindowFrames = 50
    private val shortWindowSumSqHistory = DoubleArray(shortWindowFrames)
    private var shortWindowHead = 0
    private var shortWindowCount = 0
    private var shortWindowRunningSumSq = 0.0

    // --- d. Dynamic Reference Tracker (25s = 500 frames median) ---
    private val referenceWindowSize = 500
    private val referenceHistory = FloatArray(referenceWindowSize)
    private val referenceSorted = FloatArray(referenceWindowSize)
    private var referenceHead = 0
    private var referenceCount = 0
    private var currentReferenceLoudnessDb = -45.0f

    // --- e. Scale Normalization Smoothed Gain ---
    private var smoothedScaleGain = 1.0f
    private var currentNormalizationOffsetDb = 0.0f

    // --- Hardware Noise Floor & SNR Gate ---
    private var frameCount = 0
    private var startupMinObservedRms = 1.0f
    private var hardwareFloorEstimate = 0.003f
    private var silentWindowFramesCounter = 0
    private var isSilentGateClosed = false

    // --- f. Linkwitz-Riley 4th Order 5-Band Crossovers ---
    // Crossover frequencies: 120 Hz, 500 Hz, 2 kHz, 6 kHz
    // Crossover 500 Hz: splits into <500 and >500
    private val lr4Lp500 = LR4BiquadPair(isLowPass = true, fc = 500f, fs = 48000f)
    private val lr4Hp500 = LR4BiquadPair(isLowPass = false, fc = 500f, fs = 48000f)

    // Crossover 120 Hz: splits <500 into Band 0 (<120) and Band 1 (120..500)
    private val lr4Lp120 = LR4BiquadPair(isLowPass = true, fc = 120f, fs = 48000f)
    private val lr4Hp120 = LR4BiquadPair(isLowPass = false, fc = 120f, fs = 48000f)

    // Crossover 2000 Hz: splits >500 into Band 2 (500..2000) and >2000
    private val lr4Lp2000 = LR4BiquadPair(isLowPass = true, fc = 2000f, fs = 48000f)
    private val lr4Hp2000 = LR4BiquadPair(isLowPass = false, fc = 2000f, fs = 48000f)

    // Crossover 6000 Hz: splits >2000 into Band 3 (2000..6000) and Band 4 (>6000)
    private val lr4Lp6000 = LR4BiquadPair(isLowPass = true, fc = 6000f, fs = 48000f)
    private val lr4Hp6000 = LR4BiquadPair(isLowPass = false, fc = 6000f, fs = 48000f)

    // Per-band rolling RMS over 5-10s (~7.5s, alpha = 0.007 @ 20fps)
    private val rollingBandRms = FloatArray(NUM_BANDS) { 0.02f }
    private val smoothedBandLiftDb = FloatArray(NUM_BANDS) { 0f }

    // --- g. Multiband Compressor State (5 bands) ---
    private val compEnvelopes = FloatArray(NUM_BANDS) { 0f }
    private val compReductionDb = FloatArray(NUM_BANDS) { 0f }
    private val compThreshold = 0.20f // ~ -14 dBFS
    private val compRatio = 1.8f
    private val compAlphaAttack = 0.35f  // ~50ms
    private val compAlphaRelease = 0.08f // ~300ms

    // --- h. Brickwall Limiter State (2ms lookahead) ---
    private val delayLine = FloatArray(LOOKAHEAD_SAMPLES)
    private var delayWriteHead = 0
    private var smoothedLimiterGain = 1.0f

    // --- i. Anti-Aliasing FIR Downsampler (48k -> 16k) ---
    private val firCoeffs = floatArrayOf(
        -0.0032f, -0.0065f, 0.0028f, 0.0275f, 0.0651f, 0.1062f, 0.1415f, 0.1610f,
        0.1610f, 0.1415f, 0.1062f, 0.0651f, 0.0275f, 0.0028f, -0.0065f, -0.0032f
    )

    // Preallocated buffers (zero allocation in loop)
    private val preAllocHpfBuffer = FloatArray(FRAME_SIZE_48K)
    private val preAllocBand0 = FloatArray(FRAME_SIZE_48K)
    private val preAllocBand1 = FloatArray(FRAME_SIZE_48K)
    private val preAllocBand2 = FloatArray(FRAME_SIZE_48K)
    private val preAllocBand3 = FloatArray(FRAME_SIZE_48K)
    private val preAllocBand4 = FloatArray(FRAME_SIZE_48K)
    private val preAllocSum48k = FloatArray(FRAME_SIZE_48K)
    private val preAllocOut48k = FloatArray(FRAME_SIZE_48K)
    private val preAllocOut16k = FloatArray(FRAME_SIZE_16K)

    // Battery tier throttle (0..4)
    var batteryTier: Int = 0

    /**
     * Processes a 50ms (2400-sample @ 48kHz) raw PCM frame.
     */
    @Synchronized
    fun process(samples48k: FloatArray): RefinementOutput {
        val n = minOf(samples48k.size, FRAME_SIZE_48K)

        // 1. Compute Raw RMS
        var rawSumSq = 0.0
        for (i in 0 until n) {
            val s = samples48k[i]
            rawSumSq += (s * s)
        }
        val rawRms = sqrt(maxOf(1e-12, rawSumSq / n)).toFloat()

        // 2. Hardware Noise Floor Tracking (first 3s tracks minimum, then persists)
        frameCount++
        if (frameCount <= 60) {
            startupMinObservedRms = minOf(startupMinObservedRms, rawRms)
            hardwareFloorEstimate = maxOf(0.0003f, startupMinObservedRms)
        }
        val floorRms = hardwareFloorEstimate.coerceAtLeast(0.0003f)

        // a. DC Blocker (~5 Hz) & b. 40 Hz Butterworth HPF
        var hpfSumSq = 0.0
        for (i in 0 until n) {
            val x = samples48k[i]

            // DC Blocker
            val dcY = x - dcPrevX + dcR * dcPrevY
            dcPrevX = x
            dcPrevY = dcY

            // 40 Hz 2nd-order Butterworth HPF
            val hpfY = b0Hpf * dcY + b1Hpf * hpfX1 + b2Hpf * hpfX2 - a1Hpf * hpfY1 - a2Hpf * hpfY2
            hpfX2 = hpfX1
            hpfX1 = dcY
            hpfY2 = hpfY1
            hpfY1 = hpfY

            preAllocHpfBuffer[i] = hpfY
            hpfSumSq += (hpfY * hpfY)
        }

        // c. Short-Window Loudness Estimator (rolling RMS over 2.5s)
        if (shortWindowCount < shortWindowFrames) {
            shortWindowSumSqHistory[shortWindowHead] = hpfSumSq
            shortWindowRunningSumSq += hpfSumSq
            shortWindowCount++
        } else {
            shortWindowRunningSumSq -= shortWindowSumSqHistory[shortWindowHead]
            shortWindowSumSqHistory[shortWindowHead] = hpfSumSq
            shortWindowRunningSumSq += hpfSumSq
        }
        shortWindowHead = (shortWindowHead + 1) % shortWindowFrames

        val totalWindowSamples = maxOf(1, shortWindowCount * n)
        val shortWindowRms = sqrt(maxOf(1e-12, shortWindowRunningSumSq / totalWindowSamples)).toFloat()
        val shortWindowLoudnessDb = (20.0 * log10(maxOf(1e-6, shortWindowRms.toDouble()))).toFloat()

        // d. Dynamic Reference Tracker (median of short-window loudness over 25s)
        updateReferenceMedian(shortWindowLoudnessDb)

        // e. Scale Normalization
        val targetOffsetDb = (TARGET_WORKING_LEVEL_DBFS - currentReferenceLoudnessDb)
            .coerceIn(MIN_NORMALIZATION_OFFSET_DB, MAX_NORMALIZATION_OFFSET_DB)
        currentNormalizationOffsetDb = targetOffsetDb

        val targetScaleGainLinear = 10.0.pow(targetOffsetDb / 20.0).toFloat()
        smoothedScaleGain = smoothedScaleGain * 0.95f + targetScaleGainLinear * 0.05f

        // Apply scale normalization to HPF output
        for (i in 0 until n) {
            preAllocHpfBuffer[i] *= smoothedScaleGain
        }

        // SNR Gate Evaluation
        val snrLinear = maxOf(0.01f, rawRms / floorRms)
        val snrDb = (20.0 * log10(snrLinear.toDouble())).toFloat().coerceIn(-20f, 80f)

        if (snrDb <= SILENT_SNR_THRESHOLD_DB) {
            silentWindowFramesCounter++
            if (silentWindowFramesCounter >= SILENT_HOLD_FRAMES) {
                isSilentGateClosed = true
            }
        } else {
            silentWindowFramesCounter = 0
            isSilentGateClosed = false
        }

        // f. Linkwitz-Riley 4th Order 5-Band Crossover Split (SHAPE stage)
        // Tree: 500 Hz -> (120 Hz -> B0, B1) and (2000 Hz -> B2, (6000 Hz -> B3, B4))
        for (i in 0 until n) {
            val s = preAllocHpfBuffer[i]

            val low500 = lr4Lp500.process(s)
            val high500 = lr4Hp500.process(s)

            preAllocBand0[i] = lr4Lp120.process(low500)
            preAllocBand1[i] = lr4Hp120.process(low500)

            preAllocBand2[i] = lr4Lp2000.process(high500)
            val high2000 = lr4Hp2000.process(high500)

            preAllocBand3[i] = lr4Lp6000.process(high2000)
            preAllocBand4[i] = lr4Hp6000.process(high2000)
        }

        // Compute per-band rolling RMS and gentle lift (EQ tilt, not level)
        // In SILENT: per-band gain is disabled (0 dB lift)
        val updateGainRate = if (batteryTier == 1) (frameCount % 2 == 0) else true

        if (updateGainRate) {
            computeBandLift(0, preAllocBand0, n, isSilentGateClosed)
            computeBandLift(1, preAllocBand1, n, isSilentGateClosed)
            computeBandLift(2, preAllocBand2, n, isSilentGateClosed)
            computeBandLift(3, preAllocBand3, n, isSilentGateClosed)
            computeBandLift(4, preAllocBand4, n, isSilentGateClosed)
        }

        // g. Multiband Compressor (same 5 bands, 1.8:1 ratio, max -6 dB reduction)
        applyBandCompression(0, preAllocBand0, n)
        applyBandCompression(1, preAllocBand1, n)
        applyBandCompression(2, preAllocBand2, n)
        applyBandCompression(3, preAllocBand3, n)
        applyBandCompression(4, preAllocBand4, n)

        // Sum 5 bands back together
        for (i in 0 until n) {
            preAllocSum48k[i] = preAllocBand0[i] + preAllocBand1[i] + preAllocBand2[i] +
                    preAllocBand3[i] + preAllocBand4[i]
        }

        // h. Brickwall Limiter (-1 dBFS ceiling with 2ms lookahead delay line)
        for (i in 0 until n) {
            val inSample = preAllocSum48k[i]

            // Lookahead: compute peak in lookahead delay buffer
            val delayedSample = delayLine[delayWriteHead]
            delayLine[delayWriteHead] = inSample
            delayWriteHead = (delayWriteHead + 1) % LOOKAHEAD_SAMPLES

            val lookaheadPeak = maxOf(abs(inSample), abs(delayedSample))
            val targetLimiterGain = if (lookaheadPeak > LIMITER_CEILING) {
                LIMITER_CEILING / lookaheadPeak
            } else {
                1.0f
            }
            smoothedLimiterGain = smoothedLimiterGain * 0.90f + targetLimiterGain * 0.10f

            var s = delayedSample * smoothedLimiterGain
            val absS = abs(s)
            if (absS > LIMITER_CEILING) {
                val sign = if (s >= 0) 1f else -1f
                val excess = (absS - LIMITER_CEILING) / (1.0f - LIMITER_CEILING + 1e-4f)
                s = sign * (LIMITER_CEILING + (1.0f - LIMITER_CEILING) * tanh(excess))
            }

            preAllocOut48k[i] = s
        }

        val refinedRms = computeRms(preAllocOut48k, n)

        // i. Downsample to 16 kHz (anti-aliasing FIR filter and 3:1 decimation)
        downsample48kTo16k(preAllocOut48k, preAllocOut16k)

        return RefinementOutput(
            refined48kSamples = preAllocOut48k,
            refined16kSamples = preAllocOut16k,
            rawRms = rawRms,
            refinedRms = refinedRms,
            estimatedSnrDb = snrDb,
            makeupGainDb = currentNormalizationOffsetDb,
            referenceLoudnessDb = currentReferenceLoudnessDb,
            normalizationOffsetDb = currentNormalizationOffsetDb,
            bandLiftsDb = smoothedBandLiftDb.clone(),
            compressorReductionDb = compReductionDb.clone(),
            isSilentGateClosed = isSilentGateClosed,
            hardwareFloorRms = floorRms
        )
    }

    private fun computeBandLift(bandIdx: Int, bandSamples: FloatArray, n: Int, isSilent: Boolean) {
        var sumSq = 0.0
        for (i in 0 until n) {
            val s = bandSamples[i]
            sumSq += (s * s)
        }
        val bandRms = sqrt(maxOf(1e-12, sumSq / n)).toFloat()
        rollingBandRms[bandIdx] = rollingBandRms[bandIdx] * 0.993f + bandRms * 0.007f

        val targetLiftDb = if (isSilent) {
            0f
        } else {
            val targetLevel = 0.030f
            if (rollingBandRms[bandIdx] < targetLevel && rollingBandRms[bandIdx] > 1e-4f) {
                val deficit = targetLevel / rollingBandRms[bandIdx]
                val lift = (20.0 * log10(deficit.toDouble())).toFloat() * 0.5f
                lift.coerceIn(0f, MAX_BAND_LIFT_DB)
            } else {
                0f
            }
        }

        if (isSilent) {
            smoothedBandLiftDb[bandIdx] = 0f
        } else {
            smoothedBandLiftDb[bandIdx] = smoothedBandLiftDb[bandIdx] * 0.95f + targetLiftDb * 0.05f
        }
        val gainLinear = 10.0.pow(smoothedBandLiftDb[bandIdx] / 20.0).toFloat()
        for (i in 0 until n) {
            bandSamples[i] *= gainLinear
        }
    }

    private fun applyBandCompression(bandIdx: Int, bandSamples: FloatArray, n: Int) {
        var maxPeak = 0f
        for (i in 0 until n) {
            val a = abs(bandSamples[i])
            if (a > maxPeak) maxPeak = a
        }

        val env = compEnvelopes[bandIdx]
        compEnvelopes[bandIdx] = if (maxPeak > env) {
            env * (1f - compAlphaAttack) + maxPeak * compAlphaAttack
        } else {
            env * (1f - compAlphaRelease) + maxPeak * compAlphaRelease
        }

        if (compEnvelopes[bandIdx] > compThreshold) {
            val overDb = 20.0 * log10((compEnvelopes[bandIdx] / compThreshold).toDouble())
            val redDb = (overDb * (1.0 - 1.0 / compRatio)).toFloat()
            compReductionDb[bandIdx] = redDb.coerceIn(0f, 6.0f) // cap at -6 dB
        } else {
            compReductionDb[bandIdx] = 0f
        }

        if (compReductionDb[bandIdx] > 0.05f) {
            val scale = 10.0.pow(-compReductionDb[bandIdx] / 20.0).toFloat()
            for (i in 0 until n) {
                bandSamples[i] *= scale
            }
        }
    }

    /**
     * Updates rolling sorted buffer to compute exact median in O(N) shift without full sort.
     */
    private fun updateReferenceMedian(newLoudnessDb: Float) {
        if (referenceCount < referenceWindowSize) {
            referenceHistory[referenceHead] = newLoudnessDb
            // Insert into sorted array
            var insertPos = referenceCount
            for (i in 0 until referenceCount) {
                if (newLoudnessDb < referenceSorted[i]) {
                    insertPos = i
                    break
                }
            }
            if (insertPos < referenceCount) {
                System.arraycopy(referenceSorted, insertPos, referenceSorted, insertPos + 1, referenceCount - insertPos)
            }
            referenceSorted[insertPos] = newLoudnessDb
            referenceCount++
        } else {
            val oldVal = referenceHistory[referenceHead]
            referenceHistory[referenceHead] = newLoudnessDb

            // Remove oldVal from sorted
            var removeIdx = -1
            for (i in 0 until referenceCount) {
                if (referenceSorted[i] == oldVal) {
                    removeIdx = i
                    break
                }
            }
            if (removeIdx != -1) {
                System.arraycopy(referenceSorted, removeIdx + 1, referenceSorted, removeIdx, referenceCount - 1 - removeIdx)
            }

            // Insert newLoudnessDb
            var insertPos = referenceCount - 1
            for (i in 0 until referenceCount - 1) {
                if (newLoudnessDb < referenceSorted[i]) {
                    insertPos = i
                    break
                }
            }
            System.arraycopy(referenceSorted, insertPos, referenceSorted, insertPos + 1, referenceCount - 1 - insertPos)
            referenceSorted[insertPos] = newLoudnessDb
        }
        referenceHead = (referenceHead + 1) % referenceWindowSize

        val medianIdx = referenceCount / 2
        val rawMedian = referenceSorted[medianIdx]

        // Slow-moving reference tracker follows room
        currentReferenceLoudnessDb = currentReferenceLoudnessDb * 0.98f + rawMedian * 0.02f
    }

    private fun computeRms(samples: FloatArray, n: Int): Float {
        var sumSq = 0.0
        for (i in 0 until n) {
            val s = samples[i]
            sumSq += (s * s)
        }
        return sqrt(maxOf(1e-12, sumSq / n)).toFloat()
    }

    private fun downsample48kTo16k(in48k: FloatArray, out16k: FloatArray) {
        val firLen = firCoeffs.size
        val halfFir = firLen / 2

        for (i in 0 until FRAME_SIZE_16K) {
            val center48k = i * DECIMATION_FACTOR
            var sum = 0.0f
            for (k in 0 until firLen) {
                val inIdx = center48k - halfFir + k
                val sample = when {
                    inIdx in 0 until FRAME_SIZE_48K -> in48k[inIdx]
                    inIdx < 0 -> in48k[0]
                    else -> in48k[FRAME_SIZE_48K - 1]
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
        shortWindowHead = 0
        shortWindowCount = 0
        shortWindowRunningSumSq = 0.0
        referenceHead = 0
        referenceCount = 0
        currentReferenceLoudnessDb = -45.0f
        smoothedScaleGain = 1.0f
        currentNormalizationOffsetDb = 0.0f
        frameCount = 0
        startupMinObservedRms = 1.0f
        hardwareFloorEstimate = 0.003f
        silentWindowFramesCounter = 0
        isSilentGateClosed = false
        lr4Lp500.reset()
        lr4Hp500.reset()
        lr4Lp120.reset()
        lr4Hp120.reset()
        lr4Lp2000.reset()
        lr4Hp2000.reset()
        lr4Lp6000.reset()
        lr4Hp6000.reset()
        for (i in 0 until NUM_BANDS) {
            rollingBandRms[i] = 0.02f
            smoothedBandLiftDb[i] = 0f
            compEnvelopes[i] = 0f
            compReductionDb[i] = 0f
        }
        delayLine.fill(0f)
        delayWriteHead = 0
        smoothedLimiterGain = 1.0f
    }
}

/**
 * Linkwitz-Riley 4th order (LR4) filter: cascaded pair of 2nd-order Butterworth biquads.
 */
private class LR4BiquadPair(
    private val isLowPass: Boolean,
    private val fc: Float,
    private val fs: Float
) {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f

    // Biquad 1 state
    private var x1_1 = 0f
    private var x2_1 = 0f
    private var y1_1 = 0f
    private var y2_1 = 0f

    // Biquad 2 state
    private var x1_2 = 0f
    private var x2_2 = 0f
    private var y1_2 = 0f
    private var y2_2 = 0f

    init {
        val omega = 2.0 * PI * fc / fs
        val cosW = cos(omega)
        val sinW = sin(omega)
        val alpha = sinW / (2.0 * 0.70710678)
        val a0 = 1.0 + alpha

        if (isLowPass) {
            b0 = ((1.0 - cosW) / 2.0 / a0).toFloat()
            b1 = ((1.0 - cosW) / a0).toFloat()
            b2 = ((1.0 - cosW) / 2.0 / a0).toFloat()
        } else {
            b0 = ((1.0 + cosW) / 2.0 / a0).toFloat()
            b1 = (-(1.0 + cosW) / a0).toFloat()
            b2 = ((1.0 + cosW) / 2.0 / a0).toFloat()
        }
        a1 = (-2.0 * cosW / a0).toFloat()
        a2 = ((1.0 - alpha) / a0).toFloat()
    }

    inline fun process(x: Float): Float {
        // Stage 1
        val y1 = b0 * x + b1 * x1_1 + b2 * x2_1 - a1 * y1_1 - a2 * y2_1
        x2_1 = x1_1
        x1_1 = x
        y2_1 = y1_1
        y1_1 = y1

        // Stage 2
        val y2 = b0 * y1 + b1 * x1_2 + b2 * x2_2 - a1 * y1_2 - a2 * y2_2
        x2_2 = x1_2
        x1_2 = y1
        y2_2 = y1_2
        y1_2 = y2

        return y2
    }

    fun reset() {
        x1_1 = 0f
        x2_1 = 0f
        y1_1 = 0f
        y2_1 = 0f
        x1_2 = 0f
        x2_2 = 0f
        y1_2 = 0f
        y2_2 = 0f
    }
}
