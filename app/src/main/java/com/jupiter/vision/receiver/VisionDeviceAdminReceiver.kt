// VisionDeviceAdminReceiver.kt - Device Admin receiver granting lock screen capability for Jupitervision OS.
package com.jupiter.vision.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class VisionDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Device Admin enabled for Jupitervision OS")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "Device Admin disabled for Jupitervision OS")
    }

    companion object {
        private const val TAG = "VisionDeviceAdmin"
    }
}
