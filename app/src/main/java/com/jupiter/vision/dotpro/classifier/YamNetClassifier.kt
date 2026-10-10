package com.jupiter.vision.dotpro.classifier

import android.content.Context
import android.os.Build
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * 1. SIGNAL CLASSIFIER — YamNetClassifier
 * - Model: YAMNet via TensorFlow Lite. 521 AudioSet classes.
 * - Input: 16 kHz mono PCM, ~1-second windows (~15,600 samples).
 * - Delegates: Attempts NNAPI on capable devices (API 27+), silently falling back to mandatory XNNPACK.
 * - Threads: Capped at 2 to minimize scheduling overhead on low-end SoCs.
 * - Output: per-class score vector (521 floats).
 */
class YamNetClassifier(private val context: Context) {

    companion object {
        private const val TAG = "YamNetClassifier"
        const val MODEL_FILE = "yamnet.tflite"
        const val LABELS_FILE = "yamnet_label_list.txt"
        const val NUM_CLASSES = 521
        const val EXPECTED_SAMPLE_RATE = 16000
        const val DEFAULT_INPUT_SAMPLES = 15600 // 0.975s at 16kHz
    }

    private var interpreter: Interpreter? = null
    val labels: List<String> = loadLabels()
    var isInitialized: Boolean = false
        private set

    var delegateInUse: String = "XNNPACK"
        private set

    private var inputSize: Int = DEFAULT_INPUT_SAMPLES
    private var outputFrames: Int = 1

    init {
        initialize()
    }

    private fun initialize() {
        try {
            val byteBuffer = loadModelFile(context, MODEL_FILE)
            var interp: Interpreter? = null
            var chosenDelegate = "XNNPACK"

            // 1. Attempt NNAPI delegate first if Android 8.1+ (API 27+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                try {
                    val nnapiOptions = Interpreter.Options().apply {
                        setNumThreads(2)
                        setUseNNAPI(true)
                    }
                    val testInterp = Interpreter(byteBuffer, nnapiOptions)
                    interp = testInterp
                    chosenDelegate = "NNAPI"
                    Log.i(TAG, "YAMNet successfully initialized with NNAPI delegate")
                } catch (e: Throwable) {
                    Log.w(TAG, "NNAPI initialization failed, silently falling back to XNNPACK", e)
                    interp = null
                }
            }

            // 2. Fall back to mandatory XNNPACK delegate
            if (interp == null) {
                val xnnpackOptions = Interpreter.Options().apply {
                    setNumThreads(2)
                    setUseXNNPACK(true)
                }
                interp = Interpreter(byteBuffer, xnnpackOptions)
                chosenDelegate = "XNNPACK"
                Log.i(TAG, "YAMNet successfully initialized with XNNPACK delegate")
            }

            interpreter = interp
            delegateInUse = chosenDelegate

            // Inspect input shape
            val inShape = interp.getInputTensor(0).shape()
            if (inShape.isNotEmpty() && inShape[0] > 0) {
                inputSize = inShape[0]
            }

            // Inspect output shape (typically [N, 521])
            val outShape = interp.getOutputTensor(0).shape()
            if (outShape.isNotEmpty()) {
                outputFrames = outShape[0]
            }

            isInitialized = true
            Log.i(TAG, "YAMNet initialized: delegate=$delegateInUse, inputSize=$inputSize, outputFrames=$outputFrames, classes=${labels.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize YamNetClassifier", e)
            isInitialized = false
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadLabels(): List<String> {
        return try {
            context.assets.open(LABELS_FILE).bufferedReader().useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load labels from $LABELS_FILE", e)
            emptyList()
        }
    }

    /**
     * Runs YAMNet inference on input PCM audio.
     * Expects 16kHz mono audio float samples normalized to [-1.0f, 1.0f].
     * Returns a 521-element score vector (averaged across output frames).
     */
    @Synchronized
    fun classify(samples: FloatArray): FloatArray {
        val interp = interpreter
        if (interp == null || !isInitialized) {
            return FloatArray(NUM_CLASSES)
        }

        try {
            // Prepare input buffer of exact inputSize
            val inputBuffer = FloatArray(inputSize)
            if (samples.size >= inputSize) {
                System.arraycopy(samples, samples.size - inputSize, inputBuffer, 0, inputSize)
            } else {
                System.arraycopy(samples, 0, inputBuffer, inputSize - samples.size, samples.size)
            }

            // Output array: [outputFrames, NUM_CLASSES]
            val outputScores = Array(outputFrames) { FloatArray(NUM_CLASSES) }
            val outputs = mutableMapOf<Int, Any>(0 to outputScores)

            interp.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)

            // Average scores across frames
            val finalScores = FloatArray(NUM_CLASSES)
            val nFrames = maxOf(1, outputFrames)
            for (f in 0 until nFrames) {
                val frameScores = outputScores[f]
                for (i in 0 until NUM_CLASSES) {
                    finalScores[i] += frameScores[i] / nFrames
                }
            }

            return finalScores
        } catch (e: Exception) {
            Log.e(TAG, "YAMNet classification failed", e)
            return FloatArray(NUM_CLASSES)
        }
    }

    /**
     * Finds the top class and honest confidence score from a 521-element score vector.
     */
    fun getTopClass(scores: FloatArray): Pair<String, Float> {
        if (scores.isEmpty()) return Pair("Unknown", 0f)
        var maxIdx = 0
        var maxScore = scores[0]
        for (i in 1 until scores.size) {
            if (scores[i] > maxScore) {
                maxScore = scores[i]
                maxIdx = i
            }
        }
        val label = labels.getOrElse(maxIdx) { "Unknown" }
        return Pair(label, maxScore)
    }

    fun close() {
        try {
            interpreter?.close()
        } catch (_: Exception) {}
        interpreter = null
        isInitialized = false
    }
}
