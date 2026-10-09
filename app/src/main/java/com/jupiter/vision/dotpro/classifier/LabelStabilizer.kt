package com.jupiter.vision.dotpro.classifier

import com.jupiter.vision.dotpro.model.Signal

/**
 * Result of label stabilization.
 */
data class StabilizedLabel(
    val yamnetClass: String,
    val taxonomySignal: Signal,
    val confidence: Float,
    val isConfident: Boolean
)

/**
 * 3. TEMPORAL SMOOTHING — LabelStabilizer
 * - Rolling window of the last 3–5 seconds of YAMNet outputs (score vectors).
 * - Sum or average scores across the window. Top class of the smoothed vector becomes the candidate label.
 * - Anti-flicker rule: Label must hold stable across N (e.g. 2) consecutive smoothed outputs
 *   before it replaces the previous label.
 * - If smoothed top score is below confidence threshold, emit UNKNOWN or TRANSIENT rather than guess.
 */
class LabelStabilizer(
    private val labels: List<String>,
    private val windowDurationMs: Long = 4000L,
    private val consecutiveOutputsRequired: Int = 2,
    private val confidenceThreshold: Float = 0.20f
) {

    private data class TimestampedVector(
        val timestampMs: Long,
        val scores: FloatArray
    )

    private val history = ArrayDeque<TimestampedVector>()

    // Current stable label state
    private var currentStableClass: String = "Unknown"
    private var currentStableSignal: Signal = Signal.UNKNOWN
    private var currentConfidence: Float = 0.30f

    // Candidate label tracking for anti-flicker
    private var candidateClass: String = currentStableClass
    private var candidateSignal: Signal = currentStableSignal
    private var candidateConfidence: Float = currentConfidence
    private var candidateConsecutiveCount: Int = 0

    /**
     * Ingest a new YAMNet score vector.
     * Returns the stabilized label.
     */
    fun process(
        newScores: FloatArray,
        isTransient: Boolean,
        timestampMs: Long
    ): StabilizedLabel {
        // 1. Add to rolling window
        history.addLast(TimestampedVector(timestampMs, newScores.clone()))

        // Evict entries older than windowDurationMs
        while (history.isNotEmpty() && (timestampMs - history.first().timestampMs > windowDurationMs)) {
            history.removeFirst()
        }

        // 2. Average scores across the window
        val numClasses = if (newScores.isNotEmpty()) newScores.size else YamNetClassifier.NUM_CLASSES
        val smoothed = FloatArray(numClasses)
        val count = history.size

        if (count > 0) {
            for (entry in history) {
                for (i in 0 until minOf(numClasses, entry.scores.size)) {
                    smoothed[i] += entry.scores[i] / count
                }
            }
        }

        // 3. Find top class of smoothed vector
        var topIdx = 0
        var topScore = smoothed.getOrElse(0) { 0f }
        for (i in 1 until numClasses) {
            if (smoothed[i] > topScore) {
                topScore = smoothed[i]
                topIdx = i
            }
        }

        val topClassName = labels.getOrElse(topIdx) { "Unknown" }
        val mappedSignal = YamNetToTaxonomy.mapToSignal(topClassName)

        // 4. Check confidence threshold
        if (topScore < confidenceThreshold) {
            val fallbackSignal = if (isTransient) Signal.TRANSIENT else Signal.UNKNOWN
            val fallbackClass = if (isTransient) "Transient" else "Unknown"
            candidateClass = fallbackClass
            candidateSignal = fallbackSignal
            candidateConfidence = topScore
            candidateConsecutiveCount++

            if (candidateConsecutiveCount >= consecutiveOutputsRequired || currentStableClass == "Unknown") {
                currentStableClass = fallbackClass
                currentStableSignal = fallbackSignal
                currentConfidence = topScore
            }

            return StabilizedLabel(
                yamnetClass = currentStableClass,
                taxonomySignal = currentStableSignal,
                confidence = currentConfidence,
                isConfident = false
            )
        }

        // 5. Anti-flicker rule: must hold stable across consecutive smoothed outputs
        if (topClassName == candidateClass) {
            candidateConsecutiveCount++
            candidateConfidence = topScore
            if (candidateConsecutiveCount >= consecutiveOutputsRequired) {
                currentStableClass = topClassName
                currentStableSignal = mappedSignal
                currentConfidence = topScore
            }
        } else {
            candidateClass = topClassName
            candidateSignal = mappedSignal
            candidateConfidence = topScore
            candidateConsecutiveCount = 1

            // If we previously had no label, accept immediately
            if (currentStableClass == "Unknown" || currentStableClass == "Transient") {
                currentStableClass = topClassName
                currentStableSignal = mappedSignal
                currentConfidence = topScore
            }
        }

        return StabilizedLabel(
            yamnetClass = currentStableClass,
            taxonomySignal = currentStableSignal,
            confidence = currentConfidence,
            isConfident = true
        )
    }

    /**
     * Gets the currently retained stable label between invocations.
     */
    fun getCurrentStableLabel(): StabilizedLabel {
        return StabilizedLabel(
            yamnetClass = currentStableClass,
            taxonomySignal = currentStableSignal,
            confidence = currentConfidence,
            isConfident = currentStableClass != "Unknown" && currentStableClass != "Transient"
        )
    }

    fun reset() {
        history.clear()
        currentStableClass = "Unknown"
        currentStableSignal = Signal.UNKNOWN
        currentConfidence = 0.30f
        candidateClass = currentStableClass
        candidateSignal = currentStableSignal
        candidateConfidence = currentConfidence
        candidateConsecutiveCount = 0
    }
}
