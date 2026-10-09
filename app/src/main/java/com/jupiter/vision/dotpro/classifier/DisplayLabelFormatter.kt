package com.jupiter.vision.dotpro.classifier

import com.jupiter.vision.dotpro.features.FrameFeatures
import com.jupiter.vision.dotpro.model.Signal
import java.util.Locale

/**
 * 5. DISPLAY LABEL — richer than the taxonomy.
 * Diagnostic UI text format: "<Class>, <state>"
 *
 * Class: YAMNet's top class name (Title Case) when confidence is sufficient;
 * otherwise the taxonomy fallback (UNKNOWN or TRANSIENT).
 *
 * State: derived from the envelope over the smoothing window.
 * State descriptors (v1):
 * - subtle: present, low energy
 * - stable: present, sustained, steady
 * - rising: energy increasing
 * - fading: energy decreasing
 * - intermittent: coming and going
 * - dominant: high energy, foreground
 * - transient: brief, sharp, momentary
 *
 * Both class and state are lowercase in the display string. Comma-separated, single space.
 * Examples: "Music, stable" · "Music, subtle" · "Footsteps, subtle" · "Chatter, dominant" · "Wind, fading" · "Door, transient" · "Rustle, intermittent"
 */
class DisplayLabelFormatter {

    // Rolling frame history for envelope trajectory (last 3-4 seconds = ~60-80 frames at 50ms)
    private val envelopeHistory = ArrayDeque<Float>(80)

    fun recordFrame(features: FrameFeatures) {
        envelopeHistory.addLast(features.frameRms)
        if (envelopeHistory.size > 80) {
            envelopeHistory.removeFirst()
        }
    }

    /**
     * Formats the display label string: "<Class>, <state>"
     */
    fun format(
        yamnetClass: String,
        taxonomySignal: Signal,
        isConfident: Boolean,
        currentFeatures: FrameFeatures?
    ): String {
        val rawClassName = if (isConfident && yamnetClass.isNotBlank() && !yamnetClass.equals("Unknown", ignoreCase = true)) {
            yamnetClass
        } else {
            when (taxonomySignal) {
                Signal.TRANSIENT -> "Transient"
                Signal.UNKNOWN -> "Unknown"
                else -> taxonomySignal.name.lowercase().replaceFirstChar { it.titlecase(Locale.US) }
            }
        }

        val titleCaseClass = toTitleCase(rawClassName)
        val state = deriveState(currentFeatures)

        return "$titleCaseClass, $state"
    }

    /**
     * Derives envelope state descriptor from rolling history and current features:
     * subtle, stable, rising, fading, intermittent, dominant, transient
     */
    fun deriveState(features: FrameFeatures?): String {
        if (features == null) return "stable"

        // 1. Transient check: brief, sharp, momentary spike
        if (features.isTransient || features.changeMagnitude > 0.025f) {
            return "transient"
        }

        val currentRms = features.frameRms

        // 2. Dominant: high energy, foreground
        if (currentRms > 0.075f) {
            return "dominant"
        }

        // If not enough history, use simple energy check
        if (envelopeHistory.size < 6) {
            return if (currentRms < 0.022f) "subtle" else "stable"
        }

        // Compute mean, trend (slope), and variance over recent window
        val count = envelopeHistory.size
        var sum = 0f
        var sqSum = 0f
        for (v in envelopeHistory) {
            sum += v
            sqSum += v * v
        }
        val mean = sum / count
        val variance = maxOf(0f, (sqSum / count) - (mean * mean))

        // Compare first third vs last third of window to detect rising / fading
        val third = maxOf(2, count / 3)
        var firstThirdSum = 0f
        var lastThirdSum = 0f
        val list = envelopeHistory.toList()
        for (i in 0 until third) {
            firstThirdSum += list[i]
            lastThirdSum += list[list.size - 1 - i]
        }
        val firstMean = firstThirdSum / third
        val lastMean = lastThirdSum / third
        val delta = lastMean - firstMean

        // 3. Rising: energy increasing
        if (delta > 0.015f || features.envelopeVelocity > 0.012f) {
            return "rising"
        }

        // 4. Fading: energy decreasing
        if (delta < -0.015f || features.envelopeVelocity < -0.012f) {
            return "fading"
        }

        // 5. Intermittent: high variance (coming and going)
        if (variance > 0.00035f) {
            return "intermittent"
        }

        // 6. Subtle: present, low energy
        if (mean < 0.024f) {
            return "subtle"
        }

        // 7. Stable: sustained, steady
        return "stable"
    }

    private fun toTitleCase(input: String): String {
        // Clean formatting: split compound label if comma-separated
        val cleaned = input.split(",")[0].trim()
        return cleaned.split(" ").joinToString(" ") { word ->
            word.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
        }
    }

    fun reset() {
        envelopeHistory.clear()
    }
}
