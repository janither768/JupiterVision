package com.jupiter.vision.util

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast

object SystemControls {

    private var isTorchOn = false

    fun setDeviceWallpaper(context: Context, bitmap: Bitmap): Boolean {
        return try {
            val wm = WallpaperManager.getInstance(context)
            wm.setBitmap(bitmap)
            Toast.makeText(context, "Device wallpaper updated to Jupiter Vision!", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Throwable) {
            Toast.makeText(context, "Could not set wallpaper: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun toggleTorch(context: Context): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cameraManager != null) {
                val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return false
                isTorchOn = !isTorchOn
                cameraManager.setTorchMode(cameraId, isTorchOn)
                Toast.makeText(
                    context,
                    if (isTorchOn) "Flashlight ON" else "Flashlight OFF",
                    Toast.LENGTH_SHORT
                ).show()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Flashlight unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun getBatteryLevel(context: Context): Int {
        return try {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                (level * 100 / scale)
            } else {
                85
            }
        } catch (_: Exception) {
            85
        }
    }

    fun launchSystemAction(context: Context, actionKey: String) {
        try {
            val intent = when (actionKey.lowercase()) {
                "phone" -> Intent(Intent.ACTION_DIAL)
                "clock", "time" -> Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
                "msg" -> Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MESSAGING)
                }
                "mail" -> Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_EMAIL)
                }
                "cam" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                "gal" -> Intent(Intent.ACTION_VIEW).apply {
                    type = "image/*"
                }
                "set" -> Intent(Settings.ACTION_SETTINGS)
                "torch", "fl" -> {
                    toggleTorch(context)
                    return
                }
                "calc" -> Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALCULATOR)
                }
                "music", "mus" -> Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MUSIC)
                }
                "notes", "nts" -> Intent(Intent.ACTION_CREATE_NOTE).apply {
                    type = "text/plain"
                }
                "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
                "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
                "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
                "notifications" -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                else -> Intent(Settings.ACTION_SETTINGS)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (err: Exception) {
                Toast.makeText(context, "Action not supported on this device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun launchPackage(context: Context, packageName: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Cannot launch $packageName", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
