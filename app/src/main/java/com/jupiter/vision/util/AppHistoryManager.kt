package com.jupiter.vision.util

import android.content.Context
import android.content.SharedPreferences

object AppHistoryManager {
    private const val PREFS_NAME = "jupiter_app_history"
    private const val KEY_RECENT_PACKAGES = "recent_packages"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun recordAppLaunch(context: Context, packageName: String?) {
        if (packageName.isNullOrBlank() || packageName == context.packageName) return
        val prefs = getPrefs(context)
        val currentStr = prefs.getString(KEY_RECENT_PACKAGES, "") ?: ""
        val currentList = if (currentStr.isEmpty()) mutableListOf() else currentStr.split(",").toMutableList()

        currentList.remove(packageName)
        currentList.add(0, packageName)

        val updated = currentList.take(15).joinToString(",")
        prefs.edit().putString(KEY_RECENT_PACKAGES, updated).apply()
    }

    fun getRecentApps(context: Context, limit: Int = 5): List<String> {
        val prefs = getPrefs(context)
        val currentStr = prefs.getString(KEY_RECENT_PACKAGES, "") ?: ""
        if (currentStr.isEmpty()) return emptyList()
        return currentStr.split(",").filter { it.isNotBlank() }.take(limit)
    }
}
