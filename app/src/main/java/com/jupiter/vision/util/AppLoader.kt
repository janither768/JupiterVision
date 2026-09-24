package com.jupiter.vision.util

import android.content.Context
import android.content.Intent
import androidx.core.graphics.drawable.toBitmap
import com.jupiter.vision.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppLoader {

    suspend fun loadInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = try {
            pm.queryIntentActivities(mainIntent, 0)
        } catch (_: Exception) {
            emptyList()
        }

        val myPackage = context.packageName

        resolveInfos
            .filter { it.activityInfo != null && it.activityInfo.packageName != myPackage }
            .map { resolveInfo ->
                val label = try {
                    resolveInfo.loadLabel(pm)?.toString() ?: resolveInfo.activityInfo.name
                } catch (_: Exception) {
                    resolveInfo.activityInfo.packageName
                }
                val pkgName = resolveInfo.activityInfo.packageName
                val iconBitmap = try {
                    resolveInfo.loadIcon(pm)?.toBitmap(width = 96, height = 96)
                } catch (_: Exception) {
                    null
                }
                AppInfo(
                    label = label,
                    packageName = pkgName,
                    iconBitmap = iconBitmap
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
