package com.jupiter.vision.engine

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import com.jupiter.vision.model.AppInfo
import com.jupiter.vision.model.VisionProfileRepository
import java.util.Calendar

/**
 * JUPITERVISION OS — VISION ENGINE
 * Deterministic state machine, Lifestyle Cycle generator, and Contextual App resolver.
 * 
 * Strict Priority Order:
 * 1. Night (sleep time always overrides everything)
 * 2. Gym
 * 3. Work
 * 4. Home
 * 5. Settle (5-minute transitional state upon arrival)
 * 6. Transit (commute window)
 * 7. Focus (inferred unlabeled work period)
 * 8. Learning (first 7 days, overrides Idle)
 * 9. Idle (fallback)
 */
object VisionEngine {

    enum class VisionState(val displayName: String, val statusLine: String) {
        NIGHT("NIGHT MODE", "NIGHT MODE"),
        GYM("GYM", "GYM SESSION"),
        WORK("WORK", "WORK PERIOD"),
        HOME("HOME", "HOME"),
        SETTLE("SETTLE", "SETTLING..."),
        TRANSIT("TRANSIT", "TRANSIT — ETA 12 MIN"),
        FOCUS("FOCUS MODE", "FOCUS MODE"),
        LEARNING("LEARNING DAY 3/7", "LEARNING... DAY 3 OF 7"),
        IDLE("IDLE", "IDLE")
    }

    data class EngineSnapshot(
        val state: VisionState,
        val statusText: String,
        val topBarStatus: String,
        val contextualApps: List<AppInfo>,
        val isAppRowVisible: Boolean,
        val settleSecondsRemaining: Int = 300,
        val transitEtaMinutes: Int = 12,
        val learningDay: Int = 3
    )

    private const val PREFS_NAME = "vision_engine_prefs"
    private const val KEY_INSTALL_TIME = "engine_install_time"
    private const val KEY_WORK_DEVIATION = "work_deviation_mins"
    private const val KEY_CONSECUTIVE_DEVIATIONS = "consecutive_deviations"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getLearningDay(context: Context): Int {
        val prefs = getPrefs(context)
        val installTime = prefs.getLong(KEY_INSTALL_TIME, 0L)
        if (installTime == 0L) {
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_INSTALL_TIME, now).apply()
            return 1
        }
        val days = ((System.currentTimeMillis() - installTime) / (1000L * 60 * 60 * 24)).toInt() + 1
        return days.coerceIn(1, 7)
    }

    fun evaluateState(
        context: Context,
        installedApps: List<AppInfo>,
        calendar: Calendar = Calendar.getInstance()
    ): EngineSnapshot {
        // Check if engine is temporarily disabled for today
        if (VisionProfileRepository.isEngineDisabledToday(context)) {
            val idleApps = filterContextualApps(installedApps, VisionState.IDLE, 720)
            return EngineSnapshot(
                state = VisionState.IDLE,
                statusText = "IDLE (DISABLED FOR TODAY)",
                topBarStatus = "IDLE",
                contextualApps = idleApps,
                isAppRowVisible = true,
                learningDay = getLearningDay(context)
            )
        }

        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val nowMinutes = hour * 60 + minute

        val learningDay = getLearningDay(context)
        val isFirstWeek = learningDay <= 7

        val dayKey = when (dayOfWeek) {
            Calendar.MONDAY -> "MONDAY"
            Calendar.TUESDAY -> "TUESDAY"
            Calendar.WEDNESDAY -> "WEDNESDAY"
            Calendar.THURSDAY -> "THURSDAY"
            Calendar.FRIDAY -> "FRIDAY"
            Calendar.SATURDAY -> "SATURDAY"
            else -> "SUNDAY"
        }

        val calendarMap = VisionProfileRepository.getWeekCalendar(context)
        val todaySched = calendarMap[dayKey]
        fun parseTimeToMinutes(timeStr: String?, defaultMins: Int): Int {
            if (timeStr.isNullOrEmpty()) return defaultMins
            return try {
                val parts = timeStr.split(":")
                parts[0].trim().toInt() * 60 + parts[1].trim().toInt()
            } catch (_: Exception) {
                defaultMins
            }
        }

        val wakeMinutes = parseTimeToMinutes(todaySched?.wakeTime, 6 * 60)
        val sleepMinutes = parseTimeToMinutes(todaySched?.sleepTime, 22 * 60)

        // Weekday definition: Monday to Friday
        val isWeekday = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
        val isSaturday = dayOfWeek == Calendar.SATURDAY
        val isSunday = dayOfWeek == Calendar.SUNDAY

        // 1. Night first (sleep time always overrides everything)
        if (nowMinutes >= sleepMinutes || nowMinutes < wakeMinutes) {
            applySystemAction(context, VisionState.NIGHT)
            return EngineSnapshot(
                state = VisionState.NIGHT,
                statusText = "NIGHT MODE",
                topBarStatus = "NIGHT MODE",
                contextualApps = emptyList(),
                isAppRowVisible = false,
                learningDay = learningDay
            )
        }

        // 2. Gym (e.g. Saturday 10:00 - 12:00, or Mon/Wed 17:30 - 19:00)
        val isGymWindow = (isSaturday && nowMinutes in (10 * 60)..(12 * 60)) ||
                (isWeekday && nowMinutes in (17 * 60 + 30)..(19 * 60))
        if (isGymWindow) {
            applySystemAction(context, VisionState.GYM)
            val gymApps = filterContextualApps(installedApps, VisionState.GYM, nowMinutes)
            return EngineSnapshot(
                state = VisionState.GYM,
                statusText = "GYM SESSION",
                topBarStatus = "GYM",
                contextualApps = gymApps,
                isAppRowVisible = true,
                learningDay = learningDay
            )
        }

        // 3. Work (Monday - Friday 08:35 -> 15:30)
        val isWorkWindow = isWeekday && nowMinutes in (8 * 60 + 35)..(15 * 60 + 30)
        if (isWorkWindow) {
            applySystemAction(context, VisionState.WORK)
            val workApps = filterContextualApps(installedApps, VisionState.WORK, nowMinutes)
            return EngineSnapshot(
                state = VisionState.WORK,
                statusText = "WORK PERIOD",
                topBarStatus = "WORK",
                contextualApps = workApps,
                isAppRowVisible = true,
                learningDay = learningDay
            )
        }

        // 5. Settle (5-minute transitional arrival states: 08:30-08:35 arriving at work, or 16:00-16:05 arriving at home)
        val isWorkSettle = isWeekday && nowMinutes in (8 * 60 + 30)..(8 * 60 + 34)
        val isHomeSettle = isWeekday && nowMinutes in (16 * 60)..(16 * 60 + 4)
        if (isWorkSettle || isHomeSettle) {
            val elapsedSecs = (calendar.get(Calendar.SECOND) + (minute % 5) * 60)
            val remainingSecs = (300 - elapsedSecs).coerceIn(0, 300)
            return EngineSnapshot(
                state = VisionState.SETTLE,
                statusText = "SETTLING...",
                topBarStatus = "SETTLE",
                contextualApps = emptyList(),
                isAppRowVisible = false,
                settleSecondsRemaining = remainingSecs,
                learningDay = learningDay
            )
        }

        // 6. Transit (Commute window: 08:00 - 08:30 to Work, or 15:30 - 16:00 to Home)
        val isMorningTransit = isWeekday && nowMinutes in (8 * 60)..(8 * 60 + 29)
        val isEveningTransit = isWeekday && nowMinutes in (15 * 60 + 30)..(15 * 60 + 59)
        if (isMorningTransit || isEveningTransit) {
            val transitMins = if (isMorningTransit) (8 * 60 + 30 - nowMinutes).coerceIn(2, 25)
            else (16 * 60 - nowMinutes).coerceIn(2, 25)
            val transitApps = filterContextualApps(installedApps, VisionState.TRANSIT, nowMinutes)
            return EngineSnapshot(
                state = VisionState.TRANSIT,
                statusText = "TRANSIT — ETA $transitMins MIN",
                topBarStatus = "TRANSIT",
                contextualApps = transitApps,
                isAppRowVisible = true,
                transitEtaMinutes = transitMins,
                learningDay = learningDay
            )
        }

        // 4. Home (Post-commute relaxation 16:05 -> 22:00, or Weekend non-gym, or Morning wake 06:00 -> 08:00)
        val isHomeWindow = (isWeekday && (nowMinutes in (6 * 60)..(7 * 60 + 59) || nowMinutes >= 16 * 60 + 5)) ||
                (!isWeekday && !isGymWindow)
        if (isHomeWindow) {
            applySystemAction(context, VisionState.HOME)
            val homeApps = filterContextualApps(installedApps, VisionState.HOME, nowMinutes)
            return EngineSnapshot(
                state = VisionState.HOME,
                statusText = "HOME",
                topBarStatus = "HOME",
                contextualApps = homeApps,
                isAppRowVisible = true,
                learningDay = learningDay
            )
        }

        // 7. Focus (Inferred work period fallback)
        if (isWeekday && nowMinutes in (9 * 60)..(17 * 60)) {
            val focusApps = filterContextualApps(installedApps, VisionState.FOCUS, nowMinutes)
            return EngineSnapshot(
                state = VisionState.FOCUS,
                statusText = "FOCUS MODE",
                topBarStatus = "FOCUS MODE",
                contextualApps = focusApps,
                isAppRowVisible = true,
                learningDay = learningDay
            )
        }

        // 8. Learning (First 7 days observation mode overrides Idle)
        if (isFirstWeek) {
            val learningApps = filterContextualApps(installedApps, VisionState.LEARNING, nowMinutes)
            return EngineSnapshot(
                state = VisionState.LEARNING,
                statusText = "LEARNING... DAY $learningDay OF 7",
                topBarStatus = "LEARNING DAY $learningDay/7",
                contextualApps = learningApps,
                isAppRowVisible = true,
                learningDay = learningDay
            )
        }

        // 9. Idle (fallback)
        val idleApps = filterContextualApps(installedApps, VisionState.IDLE, nowMinutes)
        return EngineSnapshot(
            state = VisionState.IDLE,
            statusText = "IDLE",
            topBarStatus = "IDLE",
            contextualApps = idleApps,
            isAppRowVisible = true,
            learningDay = learningDay
        )
    }

    private fun filterContextualApps(
        allApps: List<AppInfo>,
        state: VisionState,
        nowMinutes: Int
    ): List<AppInfo> {
        if (allApps.isEmpty()) return emptyList()

        fun matchApps(keywords: List<String>): List<AppInfo> {
            val matches = mutableListOf<AppInfo>()
            for (kw in keywords) {
                val found = allApps.firstOrNull { app ->
                    val name = app.label.lowercase()
                    val pkg = app.packageName.lowercase()
                    name.contains(kw) || pkg.contains(kw)
                }
                if (found != null && !matches.contains(found)) {
                    matches.add(found)
                }
            }
            return matches
        }

        val prioritized = when (state) {
            VisionState.WORK -> {
                // Software Engineer & Productivity Work Apps
                matchApps(listOf("github", "git", "terminal", "termux", "code", "slack", "teams", "mail", "gmail", "docs", "drive", "chrome"))
            }
            VisionState.HOME -> {
                // Media, entertainment, and social
                matchApps(listOf("youtube", "netflix", "spotify", "music", "message", "whatsapp", "instagram", "gallery", "photo", "browser"))
            }
            VisionState.TRANSIT -> {
                // Navigation, Audio, Quick messaging
                matchApps(listOf("maps", "waze", "transit", "spotify", "music", "podcast", "message", "phone", "dialer"))
            }
            VisionState.GYM -> {
                // Audio & fitness/health tracking
                matchApps(listOf("spotify", "music", "fit", "health", "workout", "timer", "clock", "strava"))
            }
            VisionState.FOCUS -> {
                // Productivity only
                matchApps(listOf("notes", "calendar", "mail", "gmail", "slack", "drive", "docs", "calculator", "tasks"))
            }
            VisionState.LEARNING -> {
                if (nowMinutes < 10 * 60) {
                    // Morning
                    matchApps(listOf("clock", "alarm", "spotify", "music", "message", "whatsapp", "news"))
                } else if (nowMinutes < 17 * 60) {
                    // Workday
                    matchApps(listOf("slack", "mail", "gmail", "calendar", "docs", "chrome", "notes"))
                } else {
                    // Evening
                    matchApps(listOf("youtube", "netflix", "spotify", "message", "gallery", "browser"))
                }
            }
            VisionState.IDLE, VisionState.NIGHT, VisionState.SETTLE -> {
                matchApps(listOf("phone", "message", "camera", "gallery", "spotify", "settings"))
            }
        }

        // Fill up to 5-6 apps from installed apps if prioritized list is under 5
        val result = prioritized.toMutableList()
        for (app in allApps) {
            if (result.size >= 6) break
            if (!result.contains(app)) {
                result.add(app)
            }
        }
        return result.take(6)
    }

    private fun applySystemAction(context: Context, state: VisionState) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            when (state) {
                VisionState.WORK -> {
                    // Vibrate mode during work
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }
                VisionState.HOME -> {
                    // Normal sound mode at home
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                }
                VisionState.GYM, VisionState.NIGHT -> {
                    // Vibrate or Silent
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }
                else -> {}
            }
        } catch (_: Throwable) {
            // Silently handle if permission or system policy is not granted
        }
    }
}
