package com.jupiter.vision.tracemachine.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.ui.geometry.Offset
import com.jupiter.vision.tracemachine.model.AnchorPoint
import com.jupiter.vision.tracemachine.model.TracePoint
import com.jupiter.vision.tracemachine.model.TraceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 2.2 SENSOR LAYER & 2.3 ESTIMATOR
 *
 * Motion sensors only:
 * - TYPE_STEP_COUNTER if reported, otherwise software step detector on TYPE_ACCELEROMETER.
 * - TYPE_GYROSCOPE for yaw.
 * - TYPE_ACCELEROMETER for swing axis, gravity, and stillness detection.
 *
 * SWING-AXIS LATCH:
 * - First 8 clean forward steps finds dominant oscillation axis of dynamic accel in phone frame.
 * - Latched permanently for session. Never continuously re-estimated.
 *
 * PER-STEP YAW:
 * - Integrates gyro around latched vertical gravity axis ONLY between consecutive steps.
 * - Delta applied to heading on each step, then integrator reset to zero. Zero continuous drift.
 *
 * ZERO-VELOCITY UPDATE (ZUPT):
 * - If accel variance and gyro magnitude are low for 3.0 continuous seconds:
 *   resets velocity, suppresses yaw, drops an anchor, resets stepsSinceAnchor counter.
 *
 * MANUAL ANCHOR:
 * - Pin at current position + resets stepsSinceAnchor counter.
 */
class TraceEstimatorEngine(private val context: Context) : SensorEventListener {

    companion object {
        private const val DEFAULT_STRIDE_METERS = 0.70f
        private const val CALIBRATION_STEPS_TARGET = 8
        private const val STEP_REFRACTORY_MS = 250L
        private const val STEP_PEAK_THRESHOLD = 11.77f // ~1.2g magnitude
        private const val STEP_VALLEY_THRESHOLD = 9.2f
        private const val ZUPT_DURATION_MS = 3000L
        private const val ZUPT_ACCEL_VAR_MAX = 0.08f
        private const val ZUPT_GYRO_MAG_MAX = 0.08f
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val _traceStateFlow = MutableStateFlow(TraceState())
    val traceStateFlow: StateFlow<TraceState> = _traceStateFlow.asStateFlow()

    // Internal In-Memory Trail State
    private val trailPoints = mutableListOf<TracePoint>()
    private val anchors = mutableListOf<AnchorPoint>()
    private var currentX = 0f
    private var currentY = 0f
    private var currentHeadingRad = 0f // 0 rad = traveling "up" (0, -1) in Canvas
    private var stepsSinceAnchor = 0
    private var totalSteps = 0

    // Swing-Axis & Gravity Latch (Phone Frame)
    private var isSwingAxisLatched = false
    private var latchedGravityAxisX = 0f
    private var latchedGravityAxisY = 0f
    private var latchedGravityAxisZ = 1f
    private var latchedSwingAxisX = 0f
    private var latchedSwingAxisY = 1f
    private var latchedSwingAxisZ = 0f

    // Calibration Accumulator (3x3 Covariance Matrix of dynamic accel)
    private var covXX = 0.0
    private var covXY = 0.0
    private var covXZ = 0.0
    private var covYY = 0.0
    private var covYZ = 0.0
    private var covZZ = 0.0
    private var gravSumX = 0.0
    private var gravSumY = 0.0
    private var gravSumZ = 0.0
    private var calibSampleCount = 0

    // Gyro Per-Step Integrator
    private var lastGyroTimestampNs: Long = 0L
    private var stepYawIntegratorRad: Float = 0f

    // Software Step Detector State
    private var lastStepTimestampMs: Long = 0L
    private var stepArmed = false

    // Hardware Step Counter Baseline
    private var hardwareStepBaseline: Float? = null

    // ZUPT Stillness Detector State
    private val zuptWindowSamples = 60 // ~3.0s at 20Hz
    private val zuptAccelHistory = FloatArray(zuptWindowSamples)
    private val zuptGyroHistory = FloatArray(zuptWindowSamples)
    private var zuptIndex = 0
    private var zuptCount = 0
    private var stillStartTimeMs: Long = 0L
    private var isZuptActive = false

    var isRunning = false
        private set

    @Synchronized
    fun startSession() {
        if (isRunning) return
        resetInternalState()
        isRunning = true

        // Register available sensors
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val stepCounter = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        accel?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyro?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        stepCounter?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }

        // Initial starting point at origin
        trailPoints.add(TracePoint(0f, 0f, 0, System.currentTimeMillis()))
        updateStateFlow()
    }

    @Synchronized
    fun stopSession() {
        if (!isRunning) return
        isRunning = false
        sensorManager?.unregisterListener(this)
        resetInternalState()
        updateStateFlow()
    }

    @Synchronized
    private fun resetInternalState() {
        trailPoints.clear()
        anchors.clear()
        currentX = 0f
        currentY = 0f
        currentHeadingRad = 0f
        stepsSinceAnchor = 0
        totalSteps = 0
        isSwingAxisLatched = false
        covXX = 0.0
        covXY = 0.0
        covXZ = 0.0
        covYY = 0.0
        covYZ = 0.0
        covZZ = 0.0
        gravSumX = 0.0
        gravSumY = 0.0
        gravSumZ = 0.0
        calibSampleCount = 0
        lastGyroTimestampNs = 0L
        stepYawIntegratorRad = 0f
        lastStepTimestampMs = 0L
        stepArmed = false
        hardwareStepBaseline = null
        zuptIndex = 0
        zuptCount = 0
        stillStartTimeMs = 0L
        isZuptActive = false
    }

    /**
     * Drops a manual anchor pin at current position and resets confidence counter.
     */
    @Synchronized
    fun dropManualAnchor() {
        if (!isRunning) return
        val now = System.currentTimeMillis()
        anchors.add(
            AnchorPoint(
                x = currentX,
                y = currentY,
                index = maxOf(0, trailPoints.size - 1),
                isManual = true,
                timestampMs = now
            )
        )
        stepsSinceAnchor = 0
        updateStateFlow()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isRunning || event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                handleAccelerometer(event)
            }
            Sensor.TYPE_GYROSCOPE -> {
                handleGyroscope(event)
            }
            Sensor.TYPE_STEP_COUNTER -> {
                handleHardwareStepCounter(event)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun handleAccelerometer(event: SensorEvent) {
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]
        val mag = sqrt(ax * ax + ay * ay + az * az)
        val now = System.currentTimeMillis()

        // 1. ZUPT Stillness Tracking
        zuptAccelHistory[zuptIndex % zuptWindowSamples] = mag
        if (zuptCount < zuptWindowSamples) zuptCount++
        zuptIndex++

        checkZuptStillness(now)

        // 2. Calibration of Swing Axis during first steps
        if (!isSwingAxisLatched) {
            gravSumX += ax
            gravSumY += ay
            gravSumZ += az
            calibSampleCount++

            val meanX = gravSumX / calibSampleCount
            val meanY = gravSumY / calibSampleCount
            val meanZ = gravSumZ / calibSampleCount

            val dX = ax - meanX
            val dY = ay - meanY
            val dZ = az - meanZ

            covXX += dX * dX
            covXY += dX * dY
            covXZ += dX * dZ
            covYY += dY * dY
            covYZ += dY * dZ
            covZZ += dZ * dZ
        }

        // 3. Software Step Detection (if hardware step counter not yet used)
        if (hardwareStepBaseline == null) {
            if (mag > STEP_PEAK_THRESHOLD) {
                stepArmed = true
            } else if (stepArmed && mag < STEP_VALLEY_THRESHOLD) {
                stepArmed = false
                if (now - lastStepTimestampMs >= STEP_REFRACTORY_MS) {
                    lastStepTimestampMs = now
                    onStepDetected(now)
                }
            }
        }
    }

    private fun handleGyroscope(event: SensorEvent) {
        val gx = event.values[0]
        val gy = event.values[1]
        val gz = event.values[2]
        val gyroMag = sqrt(gx * gx + gy * gy + gz * gz)
        zuptGyroHistory[zuptIndex % zuptWindowSamples] = gyroMag

        if (lastGyroTimestampNs == 0L) {
            lastGyroTimestampNs = event.timestamp
            return
        }

        val dtSec = (event.timestamp - lastGyroTimestampNs) * 1e-9f
        lastGyroTimestampNs = event.timestamp

        // When in ZUPT stillness, suppress yaw integration
        if (isZuptActive) {
            stepYawIntegratorRad = 0f
            return
        }

        // Project gyro rotation onto vertical gravity axis
        val yawRate = if (isSwingAxisLatched) {
            gx * latchedGravityAxisX + gy * latchedGravityAxisY + gz * latchedGravityAxisZ
        } else {
            gz // Default phone Z-axis fallback before calibration
        }

        stepYawIntegratorRad += yawRate * dtSec
    }

    private fun handleHardwareStepCounter(event: SensorEvent) {
        val currentCount = event.values[0]
        val now = System.currentTimeMillis()
        if (hardwareStepBaseline == null) {
            hardwareStepBaseline = currentCount
            return
        }

        val base = hardwareStepBaseline ?: return
        val delta = (currentCount - base).toInt()
        if (delta > totalSteps) {
            hardwareStepBaseline = currentCount - (totalSteps + 1)
            onStepDetected(now)
        }
    }

    @Synchronized
    private fun onStepDetected(timestampMs: Long) {
        totalSteps++
        stepsSinceAnchor++

        // Latch Swing-Axis after target calibration steps
        if (!isSwingAxisLatched && totalSteps >= CALIBRATION_STEPS_TARGET && calibSampleCount > 10) {
            latchSwingAxis()
        }

        // Apply per-step integrated yaw to heading, then RESET integrator to zero
        currentHeadingRad += stepYawIntegratorRad
        stepYawIntegratorRad = 0f

        // Initial heading starts at 0, traveling "up" in Canvas coordinates (0, -1)
        val stride = DEFAULT_STRIDE_METERS
        val dx = sin(currentHeadingRad) * stride
        val dy = -cos(currentHeadingRad) * stride

        currentX += dx
        currentY += dy

        trailPoints.add(
            TracePoint(
                x = currentX,
                y = currentY,
                stepsSinceAnchor = stepsSinceAnchor,
                timestampMs = timestampMs
            )
        )

        updateStateFlow()
    }

    private fun checkZuptStillness(now: Long) {
        if (zuptCount < zuptWindowSamples) return

        var sumAccel = 0f
        var sumSqAccel = 0f
        var sumGyro = 0f
        for (i in 0 until zuptWindowSamples) {
            val a = zuptAccelHistory[i]
            val g = zuptGyroHistory[i]
            sumAccel += a
            sumSqAccel += (a * a)
            sumGyro += g
        }

        val meanAccel = sumAccel / zuptWindowSamples
        val varAccel = (sumSqAccel / zuptWindowSamples) - (meanAccel * meanAccel)
        val meanGyro = sumGyro / zuptWindowSamples

        val isStillNow = varAccel < ZUPT_ACCEL_VAR_MAX && meanGyro < ZUPT_GYRO_MAG_MAX

        if (isStillNow) {
            if (stillStartTimeMs == 0L) {
                stillStartTimeMs = now
            } else if (now - stillStartTimeMs >= ZUPT_DURATION_MS) {
                if (!isZuptActive) {
                    isZuptActive = true
                    // Emit automatic anchor point and reset confidence
                    anchors.add(
                        AnchorPoint(
                            x = currentX,
                            y = currentY,
                            index = maxOf(0, trailPoints.size - 1),
                            isManual = false,
                            timestampMs = now
                        )
                    )
                    stepsSinceAnchor = 0
                    stepYawIntegratorRad = 0f
                    updateStateFlow()
                }
            }
        } else {
            stillStartTimeMs = 0L
            isZuptActive = false
        }
    }

    /**
     * Computes dominant principal oscillation axis of walking dynamics
     * via power iteration on covariance matrix. Latched permanently.
     */
    private fun latchSwingAxis() {
        // Vertical Gravity Unit Vector
        val gravNorm = sqrt(gravSumX * gravSumX + gravSumY * gravSumY + gravSumZ * gravSumZ)
        if (gravNorm > 1e-4) {
            latchedGravityAxisX = (gravSumX / gravNorm).toFloat()
            latchedGravityAxisY = (gravSumY / gravNorm).toFloat()
            latchedGravityAxisZ = (gravSumZ / gravNorm).toFloat()
        }

        // Power iteration for dominant dynamic eigenvector
        var vx = 1.0
        var vy = 1.0
        var vz = 1.0
        for (iter in 0 until 15) {
            val nx = covXX * vx + covXY * vy + covXZ * vz
            val ny = covXY * vx + covYY * vy + covYZ * vz
            val nz = covXZ * vx + covYZ * vy + covZZ * vz
            val norm = sqrt(nx * nx + ny * ny + nz * nz)
            if (norm > 1e-6) {
                vx = nx / norm
                vy = ny / norm
                vz = nz / norm
            }
        }

        latchedSwingAxisX = vx.toFloat()
        latchedSwingAxisY = vy.toFloat()
        latchedSwingAxisZ = vz.toFloat()
        isSwingAxisLatched = true
    }

    private fun updateStateFlow() {
        _traceStateFlow.value = TraceState(
            points = trailPoints.toList(),
            anchors = anchors.toList(),
            currentPosition = Offset(currentX, currentY),
            currentHeadingRad = currentHeadingRad,
            stepsSinceAnchor = stepsSinceAnchor,
            totalSteps = totalSteps,
            isSessionActive = isRunning,
            isCalibratingSwingAxis = !isSwingAxisLatched,
            isZuptStill = isZuptActive,
            strideLengthMeters = DEFAULT_STRIDE_METERS
        )
    }
}
