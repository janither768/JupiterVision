package com.jupiter.vision.model

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

data class VenueItem(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var type: String, // "JOB", "HOUSE", "GYM", "OTHER"
    var lat: Double = 37.7749,
    var lon: Double = -122.4194,
    var schedules: MutableMap<String, Pair<String, String>> = mutableMapOf() // Day -> (Leave, Arrive)
)

data class WeekScheduleItem(
    var wakeTime: String = "06:30",
    var sleepTime: String = "22:30",
    var otherEvent: String = ""
)

object VisionProfileRepository {
    private const val PREFS_NAME = "jupitervision_profile_prefs"
    private const val KEY_FIRST_RUN = "is_first_run"
    private const val KEY_NAME = "user_name"
    private const val KEY_BIRTHDAY = "user_birthday"
    private const val KEY_VENUES = "user_venues_json"
    private const val KEY_CALENDAR = "user_calendar_json"
    private const val KEY_DISABLED_UNTIL = "disabled_until_midnight_millis"
    private const val KEY_CORRECTION = "last_correction"

    val DAYS_OF_WEEK = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")

    fun isFirstRun(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FIRST_RUN, true)
    }

    fun setFirstRunCompleted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FIRST_RUN, false).apply()
    }

    fun getName(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_NAME, "") ?: ""
    }

    fun setName(context: Context, name: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_NAME, name.uppercase().trim()).apply()
    }

    fun getBirthday(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_BIRTHDAY, "") ?: ""
    }

    fun setBirthday(context: Context, bday: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_BIRTHDAY, bday).apply()
    }

    fun isEngineDisabledToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val until = prefs.getLong(KEY_DISABLED_UNTIL, 0L)
        return System.currentTimeMillis() < until
    }

    fun disableEngineUntilMidnight(context: Context) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_DISABLED_UNTIL, cal.timeInMillis)
            .apply()
    }

    fun enableEngine(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_DISABLED_UNTIL, 0L)
            .apply()
    }

    fun recordCorrection(context: Context, correction: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CORRECTION, correction)
            .apply()
    }

    fun getLastCorrection(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_CORRECTION, "") ?: ""
    }

    fun getVenues(context: Context): MutableList<VenueItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_VENUES, null)
        if (jsonStr.isNullOrEmpty()) {
            // Default: Home already guessed via GPS
            val defaultHome = VenueItem(
                id = "home_default",
                name = "Home",
                type = "HOUSE",
                lat = 37.7749,
                lon = -122.4194,
                schedules = DAYS_OF_WEEK.associateWith { Pair("07:45", "16:00") }.toMutableMap()
            )
            val defaultWork = VenueItem(
                id = "work_default",
                name = "Work",
                type = "JOB",
                lat = 37.7833,
                lon = -122.4167,
                schedules = DAYS_OF_WEEK.take(5).associateWith { Pair("08:15", "15:30") }.toMutableMap()
            )
            val initial = mutableListOf(defaultHome, defaultWork)
            saveVenues(context, initial)
            return initial
        }

        val result = mutableListOf<VenueItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val name = obj.optString("name", "Venue")
                val type = obj.optString("type", "OTHER")
                val lat = obj.optDouble("lat", 37.7749)
                val lon = obj.optDouble("lon", -122.4194)
                val schedObj = obj.optJSONObject("schedules")
                val schedMap = mutableMapOf<String, Pair<String, String>>()
                if (schedObj != null) {
                    for (k in schedObj.keys()) {
                        val pairObj = schedObj.getJSONObject(k)
                        schedMap[k] = Pair(pairObj.optString("leave", "07:45"), pairObj.optString("arrive", "08:15"))
                    }
                }
                result.add(VenueItem(id, name, type, lat, lon, schedMap))
            }
        } catch (_: Exception) {}
        return result
    }

    fun saveVenues(context: Context, venues: List<VenueItem>) {
        val arr = JSONArray()
        for (v in venues) {
            val obj = JSONObject()
            obj.put("id", v.id)
            obj.put("name", v.name)
            obj.put("type", v.type)
            obj.put("lat", v.lat)
            obj.put("lon", v.lon)
            val schedObj = JSONObject()
            for ((day, times) in v.schedules) {
                val pairObj = JSONObject()
                pairObj.put("leave", times.first)
                pairObj.put("arrive", times.second)
                schedObj.put(day, pairObj)
            }
            obj.put("schedules", schedObj)
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_VENUES, arr.toString())
            .apply()
    }

    fun getWeekCalendar(context: Context): MutableMap<String, WeekScheduleItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CALENDAR, null)
        val map = mutableMapOf<String, WeekScheduleItem>()
        if (jsonStr.isNullOrEmpty()) {
            for (day in DAYS_OF_WEEK) {
                map[day] = WeekScheduleItem(wakeTime = "06:30", sleepTime = "22:30", otherEvent = "")
            }
            saveWeekCalendar(context, map)
            return map
        }

        try {
            val obj = JSONObject(jsonStr)
            for (day in DAYS_OF_WEEK) {
                if (obj.has(day)) {
                    val itemObj = obj.getJSONObject(day)
                    map[day] = WeekScheduleItem(
                        wakeTime = itemObj.optString("wake", "06:30"),
                        sleepTime = itemObj.optString("sleep", "22:30"),
                        otherEvent = itemObj.optString("other", "")
                    )
                } else {
                    map[day] = WeekScheduleItem()
                }
            }
        } catch (_: Exception) {
            for (day in DAYS_OF_WEEK) {
                map[day] = WeekScheduleItem()
            }
        }
        return map
    }

    fun saveWeekCalendar(context: Context, calendar: Map<String, WeekScheduleItem>) {
        val obj = JSONObject()
        for ((day, item) in calendar) {
            val itemObj = JSONObject()
            itemObj.put("wake", item.wakeTime)
            itemObj.put("sleep", item.sleepTime)
            itemObj.put("other", item.otherEvent)
            obj.put(day, itemObj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CALENDAR, obj.toString())
            .apply()
    }
}
