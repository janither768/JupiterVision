// BootReceiver.kt - BroadcastReceiver launching VisionEngineService after device reboot.
package com.jupiter.vision.engine.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.d(TAG, "Device booted or app replaced: starting VisionEngineService")
            try {
                VisionEngineService.start(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start VisionEngineService on boot: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
