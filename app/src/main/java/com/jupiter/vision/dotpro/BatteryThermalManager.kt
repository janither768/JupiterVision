package com.jupiter.vision.dotpro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 5. BATTERY AND THERMAL TIERS
 *
 * Tier 0 — full operation (default)
 * - All stages active. YAMNet cadence as defined.
 *
 * Tier 1 — battery below 30%
 * - AMBIENT cadence stretches from 5–10 s to 15 s.
 * - Per-band gain stage updates at half rate.
 *
 * Tier 2 — battery below 15% OR thermal status MODERATE
 * - AMBIENT YAMNet suspended. Only ACTIVE spikes fire YAMNet.
 * - Compressor and limiter stay on. Normalizer stays on. State tracking continues.
 *
 * Tier 3 — battery below 8% OR thermal status SEVERE
 * - YAMNet fully idle.
 * - Audio capture continues. Normalizer and state tracking continue.
 *
 * Tier 4 — thermal status CRITICAL
 * - Reduced DSP rate / 16 kHz direct capture.
 * - YAMNet idle. Module reports state only.
 *
 * Tier transitions are hysteretic — do not flap between tiers on boundaries.
 */
class BatteryThermalManager(private val context: Context) {

    companion object {
        private const val TAG = "BatteryThermalManager"
    }

    private val _batteryTier = MutableStateFlow(0)
    val batteryTier: StateFlow<Int> = _batteryTier.asStateFlow()

    private val _batteryPercentage = MutableStateFlow(100)
    val batteryPercentage: StateFlow<Int> = _batteryPercentage.asStateFlow()

    private val _thermalStatusName = MutableStateFlow("NONE")
    val thermalStatusName: StateFlow<String> = _thermalStatusName.asStateFlow()

    private val _isPowerSaveMode = MutableStateFlow(false)
    val isPowerSaveMode: StateFlow<Boolean> = _isPowerSaveMode.asStateFlow()

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var batteryReceiver: BroadcastReceiver? = null

    init {
        registerBatteryReceiver()
        registerThermalListener()
        evaluateTier()
    }

    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        _batteryPercentage.value = (level * 100) / scale
                        evaluateTier()
                    }
                } else if (intent?.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                    _isPowerSaveMode.value = powerManager?.isPowerSaveMode == true
                    evaluateTier()
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        context.registerReceiver(batteryReceiver, filter)
        _isPowerSaveMode.value = powerManager?.isPowerSaveMode == true
    }

    private fun registerThermalListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            val listener = PowerManager.OnThermalStatusChangedListener { status ->
                val name = when (status) {
                    PowerManager.THERMAL_STATUS_NONE -> "NONE"
                    PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
                    PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
                    PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
                    PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
                    PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
                    else -> "UNKNOWN"
                }
                _thermalStatusName.value = name
                evaluateTier()
            }
            thermalListener = listener
            try {
                powerManager.addThermalStatusListener(context.mainExecutor, listener)
                val current = powerManager.currentThermalStatus
                _thermalStatusName.value = when (current) {
                    PowerManager.THERMAL_STATUS_NONE -> "NONE"
                    PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
                    PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
                    PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
                    PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
                    PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
                    else -> "NONE"
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register thermal status listener", e)
            }
        }
    }

    /**
     * Evaluates battery and thermal tiers with hysteresis:
     * Drops quickly on deteriorating power/thermal; recovers only with safety margin.
     */
    @Synchronized
    private fun evaluateTier() {
        val battery = _batteryPercentage.value
        val thermal = _thermalStatusName.value
        val currentTier = _batteryTier.value

        val isThermalCritical = thermal == "CRITICAL" || thermal == "EMERGENCY" || thermal == "SHUTDOWN"
        val isThermalSevere = thermal == "SEVERE"
        val isThermalModerate = thermal == "MODERATE"

        val targetTier = when {
            isThermalCritical -> 4
            battery < 8 || isThermalSevere -> 3
            battery < 15 || isThermalModerate || _isPowerSaveMode.value -> 2
            battery < 30 -> 1
            else -> 0
        }

        // Hysteretic transition logic
        val nextTier = if (targetTier > currentTier) {
            // Immediate degradation
            targetTier
        } else if (targetTier < currentTier) {
            // Check hysteresis recovery margin
            when (currentTier) {
                4 -> if (!isThermalCritical) 3 else 4
                3 -> if (battery >= 11 && !isThermalSevere && !isThermalCritical) 2 else 3
                2 -> if (battery >= 18 && !isThermalModerate && !isThermalSevere && !isThermalCritical && !_isPowerSaveMode.value) 1 else 2
                1 -> if (battery >= 33 && !isThermalModerate && !isThermalSevere && !isThermalCritical) 0 else 1
                else -> 0
            }
        } else {
            currentTier
        }

        if (nextTier != currentTier) {
            Log.i(TAG, "Tier transition: $currentTier -> $nextTier (battery: $battery%, thermal: $thermal)")
            _batteryTier.value = nextTier
        }
    }

    fun release() {
        try {
            if (batteryReceiver != null) {
                context.unregisterReceiver(batteryReceiver)
                batteryReceiver = null
            }
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null && thermalListener != null) {
            try {
                powerManager.removeThermalStatusListener(thermalListener!!)
            } catch (_: Exception) {}
            thermalListener = null
        }
    }
}
