// WeatherRepository.kt - Passive Open-Meteo client providing temperature, weather codes, and icons with 30-minute caching.
package com.jupiter.vision.data.weather

import android.util.Log
import com.jupiter.vision.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

data class WeatherData(
    val temperature: Int,
    val condition: String,
    val iconRes: Int
)

@Singleton
class WeatherRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private var cachedData: WeatherData? = null
    private var lastFetchTimestamp: Long = 0L
    private var lastLat: Double = Double.NaN
    private var lastLng: Double = Double.NaN

    private val cacheTtlMillis = TimeUnit.MINUTES.toMillis(30)

    suspend fun getWeather(lat: Double, lng: Double): WeatherData? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedData != null && (now - lastFetchTimestamp < cacheTtlMillis)) {
            if (!lastLat.isNaN() && !lastLng.isNaN() &&
                abs(lat - lastLat) < 0.05 && abs(lng - lastLng) < 0.05
            ) {
                return@withContext cachedData
            }
        }

        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current_weather=true",
            lat,
            lng
        )

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "JupitervisionOS/2112.17")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Open-Meteo HTTP error: ${response.code}")
                return@withContext cachedData
            }

            val body = response.body?.string() ?: return@withContext cachedData
            val root = JSONObject(body)
            val current = root.optJSONObject("current_weather") ?: return@withContext cachedData

            val temp = current.getDouble("temperature").roundToInt()
            val weatherCode = current.getInt("weathercode")

            val (condition, iconRes) = mapWeatherCode(weatherCode)
            val result = WeatherData(
                temperature = temp,
                condition = condition,
                iconRes = iconRes
            )

            cachedData = result
            lastFetchTimestamp = now
            lastLat = lat
            lastLng = lng

            Log.d(TAG, "Weather fetched successfully: $temp° $condition (code=$weatherCode)")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch weather: ${e.message}")
            cachedData
        }
    }

    private fun mapWeatherCode(code: Int): Pair<String, Int> {
        return when (code) {
            0 -> "CLEAR" to R.drawable.ic_weather_clear
            1, 2, 3 -> "PARTLY CLOUDY" to R.drawable.ic_weather_cloud_sun
            45, 48 -> "FOG" to R.drawable.ic_weather_cloud
            in 51..67 -> "RAIN" to R.drawable.ic_weather_rain
            in 71..77 -> "SNOW" to R.drawable.ic_weather_snow
            in 80..82 -> "SHOWERS" to R.drawable.ic_weather_rain
            in 95..99 -> "THUNDER" to R.drawable.ic_weather_thunder
            else -> "CLEAR" to R.drawable.ic_weather_clear
        }
    }

    companion object {
        private const val TAG = "WeatherRepository"
    }
}
