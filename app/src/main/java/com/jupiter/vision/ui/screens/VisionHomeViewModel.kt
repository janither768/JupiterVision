/**
 * TESTING CHECKLIST:
 * - Time ticks every second without full-screen recomposition.
 * - Location label updates when venue changes.
 * - App list updates when phase changes.
 * - Settings statuses update on system events.
 * - Screen lock button locks the device.
 * - Wallpaper reveal matches the tile grid's wallpaper.
 */
package com.jupiter.vision.ui.screens

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.vision.R
import com.jupiter.vision.data.wallpaper.WallpaperProvider
import com.jupiter.vision.data.weather.WeatherRepository
import com.jupiter.vision.engine.VisionEngineFacade
import com.jupiter.vision.receiver.VisionDeviceAdminReceiver
import com.jupiter.vision.util.AppLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

data class AppItem(
    val icon: Drawable,
    val label: String,
    val packageName: String
)

data class VisionHomeUiState(
    val locationLabel: String = "UNKNOWN",
    val mapTile: ImageBitmap? = null,
    val isStaticMode: Boolean = false,
    val mapHeading: Float = 0f,
    val compassHeading: Float = 0f,
    val mapSubTileOffsetX: Float = 0f,
    val mapSubTileOffsetY: Float = 0f,
    val currentTime: String = "12:00:00",
    val currentDate: String = "MON, 12 JUN",
    val weatherText: String = "--° --",
    val weatherIconRes: Int = R.drawable.ic_weather_clear,
    val appList: List<AppItem> = emptyList(),
    val soundMode: String = "RING",
    val networkStatus: String = "WIFI",
    val batteryPercent: Int = 100,
    val storagePercent: Int = 50,
    val wallpaper: ImageBitmap? = null
)

@HiltViewModel
class VisionHomeViewModel @Inject constructor(
    private val engineFacade: VisionEngineFacade,
    private val weatherRepository: WeatherRepository,
    private val wallpaperProvider: WallpaperProvider,
    private val application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(VisionHomeUiState())
    val uiState: StateFlow<VisionHomeUiState> = _uiState.asStateFlow()

    // Independent lightweight state for second ticks to avoid full recomposition
    private val _timeState = MutableStateFlow(formatCurrentTime())
    val timeState: StateFlow<String> = _timeState.asStateFlow()

    private val _dateState = MutableStateFlow(formatCurrentDate())
    val dateState: StateFlow<String> = _dateState.asStateFlow()

    private val okHttpClient = OkHttpClient()

    // In-memory cache for map tiles with 5-minute TTL
    private data class CachedTile(val bitmap: ImageBitmap, val timestamp: Long)
    private val mapTileCache = LruCache<String, CachedTile>(20)
    private val singleTileCache = LruCache<String, Bitmap>(150)
    private val tileTtlMillis = 5 * 60 * 1000L

    private var lastAppListRefreshTime = 0L

    init {
        // Start passive tracking
        engineFacade.startLocationTracking()

        // 1. Immediate location resolution from GPS or cached location
        viewModelScope.launch {
            val immediateLoc = engineFacade.getCurrentLocation()
            if (immediateLoc != null) {
                val currentVenue = engineFacade.currentVenue.value
                val venueLabel = if (currentVenue != null && currentVenue.label.isNotBlank()) currentVenue.label else null
                updateLocationAndMap(immediateLoc.latitude, immediateLoc.longitude, venueLabel)
            } else {
                val currentVenue = engineFacade.currentVenue.value
                val lat = currentVenue?.latitude ?: 37.7749
                val lng = currentVenue?.longitude ?: -122.4194
                val label = currentVenue?.label ?: "SAN FRANCISCO, CA"
                updateLocationAndMap(lat, lng, label)
            }
        }

        // 2. Observe raw location fixes from GPS/network
        viewModelScope.launch {
            engineFacade.lastLocation.collect { loc ->
                if (loc != null) {
                    val currentVenue = engineFacade.currentVenue.value
                    val venueLabel = if (currentVenue != null && currentVenue.label.isNotBlank()) currentVenue.label else null
                    updateLocationAndMap(loc.latitude, loc.longitude, venueLabel)
                }
            }
        }

        // 3. Observe venue changes from engine facade
        viewModelScope.launch {
            engineFacade.currentVenue.collect { venue ->
                if (venue != null) {
                    val label = if (venue.label.isNotBlank()) venue.label else "LOCATION ${venue.id}"
                    updateLocationAndMap(venue.latitude, venue.longitude, label)
                }
            }
        }

        // 4. Observe phase changes to refresh app list with debounce (min 2s)
        viewModelScope.launch {
            engineFacade.currentPhase.collect {
                val now = System.currentTimeMillis()
                if (now - lastAppListRefreshTime >= 2000L) {
                    lastAppListRefreshTime = now
                    refreshAppList()
                }
            }
        }

        // 3. Separate coroutine ticking every 1 second for tabular figures time & date
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val timeStr = formatCurrentTime()
                val dateStr = formatCurrentDate()
                _timeState.value = timeStr
                _dateState.value = dateStr
                _uiState.update { it.copy(currentTime = timeStr, currentDate = dateStr) }
                delay(1000L)
            }
        }

        // 4. Separate coroutine ticking every 30 minutes for weather refresh
        viewModelScope.launch {
            while (isActive) {
                val venue = engineFacade.currentVenue.value
                val lat = venue?.latitude ?: 37.7749
                val lng = venue?.longitude ?: -122.4194
                refreshWeather(lat, lng)
                delay(30 * 60 * 1000L)
            }
        }

        // 5. Observe wallpaper changes from WallpaperProvider
        viewModelScope.launch {
            wallpaperProvider.wallpaper.collect { wp ->
                _uiState.update { it.copy(wallpaper = wp) }
            }
        }

        // 6. BroadcastReceivers for ringer mode and battery
        registerSystemReceivers()

        // 7. ConnectivityManager network callback
        registerNetworkCallback()

        // 8. One-time fetch of storage usage via StatFs
        fetchStorageUsage()

        // 9. Initial app list load
        refreshAppList()
    }

    /**
     * Resolves apps from the engine facade into UI AppItem models.
     * Excludes system utility apps like SystemUI, package installer, etc.
     */
    private fun refreshAppList() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val packageNames = engineFacade.getCurrentAppList()
                val pm = application.packageManager
                val resolvedApps = mutableListOf<AppItem>()
                val seenPackages = mutableSetOf<String>()

                for (pkg in packageNames) {
                    if (isExcludedPackage(pkg) || seenPackages.contains(pkg)) continue
                    val launchIntent = pm.getLaunchIntentForPackage(pkg) ?: continue
                    try {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        val icon = pm.getApplicationIcon(appInfo)
                        val label = pm.getApplicationLabel(appInfo).toString()
                        resolvedApps.add(AppItem(icon = icon, label = label, packageName = pkg))
                        seenPackages.add(pkg)
                    } catch (_: PackageManager.NameNotFoundException) {
                        // Skip uninstalled packages
                    }
                }

                // If fewer than 5 apps, backfill with user launchable apps from the device
                if (resolvedApps.size < 5) {
                    val installed = AppLoader.loadInstalledApps(application)
                    for (app in installed) {
                        if (resolvedApps.size >= 5) break
                        if (seenPackages.contains(app.packageName) || isExcludedPackage(app.packageName)) continue
                        try {
                            val appInfo = pm.getApplicationInfo(app.packageName, 0)
                            val icon = pm.getApplicationIcon(appInfo)
                            resolvedApps.add(AppItem(icon = icon, label = app.label, packageName = app.packageName))
                            seenPackages.add(app.packageName)
                        } catch (_: Throwable) {}
                    }
                }

                _uiState.update { it.copy(appList = resolvedApps.take(5)) }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing app list: ${e.message}")
            }
        }
    }

    /**
     * Refreshes weather data from Open-Meteo.
     */
    private suspend fun refreshWeather(lat: Double, lng: Double) {
        val weather = weatherRepository.getWeather(lat, lng)
        if (weather != null) {
            val text = "${weather.temperature}° ${weather.condition}"
            _uiState.update {
                it.copy(
                    weatherText = text,
                    weatherIconRes = weather.iconRes
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    weatherText = "--° --",
                    weatherIconRes = R.drawable.ic_weather_clear
                )
            }
        }
    }

    private var lastKnownLat: Double = Double.NaN
    private var lastKnownLng: Double = Double.NaN

    private fun updateLocationAndMap(lat: Double, lng: Double, venueLabel: String? = null) {
        if (!lat.isFinite() || !lng.isFinite()) return
        lastKnownLat = lat
        lastKnownLng = lng

        val isStatic = _uiState.value.isStaticMode
        val zoom = if (isStatic) 15 else 17
        val n = 1 shl zoom
        val centerTileX = ((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val latRad = Math.toRadians(lat)
        val centerTileY = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n)
            .toInt().coerceIn(0, n - 1)

        val exactTileX = (lng + 180.0) / 360.0 * n
        val exactTileY = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n
        val subX = ((exactTileX - (centerTileX + 0.5)) * 256.0).toFloat().coerceIn(-128f, 128f)
        val subY = ((exactTileY - (centerTileY + 0.5)) * 256.0).toFloat().coerceIn(-128f, 128f)

        _uiState.update { it.copy(mapSubTileOffsetX = subX, mapSubTileOffsetY = subY) }

        viewModelScope.launch {
            val label = venueLabel ?: resolveLocationName(lat, lng)
            _uiState.update { it.copy(locationLabel = label) }

            val tile = fetchMapTile(lat, lng, zoom = zoom)
            if (tile != null) {
                _uiState.update { it.copy(mapTile = tile) }
            }

            refreshWeather(lat, lng)
        }
    }

    private suspend fun resolveLocationName(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(application, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            val addr = addresses?.firstOrNull()
            if (addr != null) {
                val city = addr.locality ?: addr.subAdminArea ?: addr.subLocality ?: addr.adminArea
                val country = addr.countryName
                when {
                    !city.isNullOrBlank() && !addr.adminArea.isNullOrBlank() && city != addr.adminArea -> "$city, ${addr.adminArea}"
                    !city.isNullOrBlank() -> city
                    !addr.featureName.isNullOrBlank() -> addr.featureName
                    !country.isNullOrBlank() -> country
                    else -> formatCoordinates(lat, lng)
                }
            } else {
                formatCoordinates(lat, lng)
            }
        } catch (_: Throwable) {
            formatCoordinates(lat, lng)
        }
    }

    private fun formatCoordinates(lat: Double, lng: Double): String {
        val latDir = if (lat >= 0) "N" else "S"
        val lngDir = if (lng >= 0) "E" else "W"
        return String.format(Locale.US, "%.4f° %s, %.4f° %s", kotlin.math.abs(lat), latDir, kotlin.math.abs(lng), lngDir)
    }

    /**
     * Fetches CartoDB Dark Matter tiles stitched into a high-res full-bleed dark mode map bitmap.
     * Guaranteed zero unloaded parts: 5x5 tile grid centered exactly on current coordinates,
     * solid Dark Matter base canvas, multi-attempt subdomain retries, and proper tile validation.
     * Mini map mode in live tracking uses zoom 17; static mode uses zoom 15.
     */
    suspend fun fetchMapTile(lat: Double, lng: Double, zoom: Int = 15): ImageBitmap? = withContext(Dispatchers.IO) {
        val n = 1 shl zoom
        val centerTileX = ((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val latRad = Math.toRadians(lat)
        val centerTileY = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n)
            .toInt().coerceIn(0, n - 1)

        val cacheKey = "dark/$zoom/$centerTileX/$centerTileY"
        val cached = mapTileCache.get(cacheKey)
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.timestamp < tileTtlMillis)) {
            return@withContext cached.bitmap
        }

        // Stitch a symmetric 5x5 grid of tiles centered on (centerTileX, centerTileY)
        // Center tile is at index (2, 2) in this 1280x1280 canvas.
        val cols = listOf(centerTileX - 2, centerTileX - 1, centerTileX, centerTileX + 1, centerTileX + 2)
        val rows = listOf(centerTileY - 2, centerTileY - 1, centerTileY, centerTileY + 1, centerTileY + 2)

        val stitchedBitmap = Bitmap.createBitmap(256 * cols.size, 256 * rows.size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(stitchedBitmap)

        // Pre-paint canvas with dark map background so no empty/unloaded holes can ever appear
        canvas.drawColor(android.graphics.Color.parseColor("#181818"))

        val subdomains = listOf("a", "b", "c", "d")
        var successfulTiles = 0

        // Parallel fetch of all 25 tiles with subdomain retries
        try {
            coroutineScope {
                val deferredTiles = cols.flatMapIndexed { colIdx, tx ->
                    rows.mapIndexed { rowIdx, ty ->
                        async(Dispatchers.IO) {
                            val clampedX = tx.coerceIn(0, n - 1)
                            val clampedY = ty.coerceIn(0, n - 1)
                            val tileKey = "$zoom/$clampedX/$clampedY"

                            val cachedTile = singleTileCache.get(tileKey)
                            if (cachedTile != null) {
                                return@async Triple(colIdx, rowIdx, cachedTile)
                            }

                            // Try up to 3 times across different subdomains
                            for (attempt in 0..2) {
                                val subIndex = (kotlin.math.abs(clampedX + clampedY) + attempt) % subdomains.size
                                val sub = subdomains[subIndex]
                                val url = "https://$sub.basemaps.cartocdn.com/rastertiles/dark_all/$zoom/$clampedX/$clampedY.png?key=$CARTO_API_KEY"
                                try {
                                    val request = Request.Builder()
                                        .url(url)
                                        .header("User-Agent", "JupitervisionOS/2112.17")
                                        .build()

                                    val response = okHttpClient.newCall(request).execute()
                                    if (response.isSuccessful) {
                                        val bytes = response.body?.bytes()
                                        // Ignore ONLY the unauthenticated dummy watermark tile (~2513 bytes)
                                        // Valid dark tiles (including ocean at 103 bytes) are accepted
                                        if (bytes != null && bytes.isNotEmpty() && bytes.size != 2513) {
                                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            if (bmp != null) {
                                                singleTileCache.put(tileKey, bmp)
                                                return@async Triple(colIdx, rowIdx, bmp)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    if (attempt == 2) {
                                        Log.w(TAG, "Tile $clampedX,$clampedY failed: ${e.message}")
                                    }
                                }
                            }
                            null
                        }
                    }
                }

                val loaded = deferredTiles.awaitAll()
                for (item in loaded) {
                    if (item != null) {
                        successfulTiles++
                        canvas.drawBitmap(item.third, (item.first * 256).toFloat(), (item.second * 256).toFloat(), null)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Parallel tile fetch error: ${t.message}")
        }

        val imgBitmap = stitchedBitmap.asImageBitmap()
        if (successfulTiles >= 15) {
            mapTileCache.put(cacheKey, CachedTile(imgBitmap, now))
        }
        imgBitmap
    }

    private var ringerReceiver: BroadcastReceiver? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private fun registerSystemReceivers() {
        try {
            val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            updateSoundMode(audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL)

            val ringerFilter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
            val rReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val mode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
                    updateSoundMode(mode)
                }
            }
            ringerReceiver = rReceiver
            ContextCompat.registerReceiver(
                application,
                rReceiver,
                ringerFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register ringer receiver: ${t.message}")
        }

        try {
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val bReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    if (level >= 0 && scale > 0) {
                        val percent = (level * 100) / scale
                        _uiState.update { it.copy(batteryPercent = percent) }
                    }
                }
            }
            batteryReceiver = bReceiver
            ContextCompat.registerReceiver(
                application,
                bReceiver,
                batteryFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register battery receiver: ${t.message}")
        }
    }

    private fun updateSoundMode(mode: Int) {
        val modeStr = when (mode) {
            AudioManager.RINGER_MODE_SILENT -> "SILENT"
            AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
            else -> "RING"
        }
        _uiState.update { it.copy(soundMode = modeStr) }
    }

    private fun registerNetworkCallback() {
        val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            _uiState.update { it.copy(networkStatus = "OFFLINE") }
            return
        }

        updateNetworkStatus(cm)

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    updateNetworkStatus(cm)
                }

                override fun onLost(network: Network) {
                    updateNetworkStatus(cm)
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    updateNetworkStatus(cm)
                }
            }
            networkCallback = callback
            cm.registerNetworkCallback(request, callback)
        } catch (t: Throwable) {
            Log.w(TAG, "registerNetworkCallback warning: ${t.message}")
            updateNetworkStatus(cm)
        }
    }

    private fun updateNetworkStatus(cm: ConnectivityManager) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val active = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(active)
                if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    val status = when {
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                        else -> "WIFI"
                    }
                    _uiState.update { it.copy(networkStatus = status) }
                    return
                }
            }

            @Suppress("DEPRECATION")
            val networkInfo = cm.activeNetworkInfo
            val status = if (networkInfo != null && networkInfo.isConnected) {
                when (networkInfo.type) {
                    ConnectivityManager.TYPE_WIFI -> "WIFI"
                    ConnectivityManager.TYPE_MOBILE -> "CELLULAR"
                    else -> "WIFI"
                }
            } else {
                "OFFLINE"
            }
            _uiState.update { it.copy(networkStatus = status) }
        } catch (_: Throwable) {
            _uiState.update { it.copy(networkStatus = "OFFLINE") }
        }
    }

    override fun onCleared() {
        super.onCleared()
        unregisterOrientationSensor()
        try {
            ringerReceiver?.let { application.unregisterReceiver(it) }
        } catch (_: Throwable) {}
        try {
            batteryReceiver?.let { application.unregisterReceiver(it) }
        } catch (_: Throwable) {}
        try {
            networkCallback?.let {
                val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                cm?.unregisterNetworkCallback(it)
            }
        } catch (_: Throwable) {}
    }

    private fun fetchStorageUsage() {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.totalBytes
            val available = stat.availableBytes
            if (total > 0L) {
                val used = (((total - available) * 100L) / total).toInt()
                _uiState.update { it.copy(storagePercent = used) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Storage stat error: ${e.message}")
        }
    }

    fun onAppClicked(packageName: String) {
        val pm = application.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            application.startActivity(launchIntent)
        }
    }

    fun onScreenLockClicked() {
        val dpm = application.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(application, VisionDeviceAdminReceiver::class.java)

        if (dpm != null && dpm.isAdminActive(adminComponent)) {
            dpm.lockNow()
        } else {
            // Prompt user to enable Device Admin policy for screen lock
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Jupitervision OS requires device admin authority to lock the screen."
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                application.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch add device admin: ${e.message}")
            }
        }
    }

    fun onUsageAccessClicked() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            application.startActivity(intent)
        } catch (_: Exception) {}
    }

    private var isLiveActive = false
    private var liveMapJob: Job? = null

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)
    private val accelSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private var isSensorRegistered = false
    private var currentFilteredHeading = 0f

    private val gravityValues = FloatArray(3)
    private val geoValues = FloatArray(3)
    private var hasGravity = false
    private var hasGeo = false

    private val orientationListener = object : SensorEventListener {
        private val rotationMatrix = FloatArray(9)
        private val remappedMatrix = FloatArray(9)
        private val orientationValues = FloatArray(3)

        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null || !isLiveActive) return
            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    SensorManager.AXIS_X,
                    SensorManager.AXIS_Z,
                    remappedMatrix
                )
                SensorManager.getOrientation(remappedMatrix, orientationValues)
                var azimuth = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                azimuth = (azimuth + 360f) % 360f
                updateSmoothHeading(azimuth)
            } else if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
                val azimuth = (event.values[0] + 360f) % 360f
                updateSmoothHeading(azimuth)
            } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(event.values, 0, gravityValues, 0, 3)
                hasGravity = true
                computeOrientationFromAccelMag()
            } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(event.values, 0, geoValues, 0, 3)
                hasGeo = true
                computeOrientationFromAccelMag()
            }
        }

        private fun computeOrientationFromAccelMag() {
            if (hasGravity && hasGeo) {
                if (SensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, geoValues)) {
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        SensorManager.AXIS_X,
                        SensorManager.AXIS_Z,
                        remappedMatrix
                    )
                    SensorManager.getOrientation(remappedMatrix, orientationValues)
                    var azimuth = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                    azimuth = (azimuth + 360f) % 360f
                    updateSmoothHeading(azimuth)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun updateSmoothHeading(newHeading: Float) {
        var diff = (newHeading - currentFilteredHeading) % 360f
        if (diff < -180f) diff += 360f
        if (diff > 180f) diff -= 360f
        currentFilteredHeading = (currentFilteredHeading + diff * 0.25f + 360f) % 360f
        _uiState.update {
            it.copy(
                compassHeading = currentFilteredHeading,
                mapHeading = currentFilteredHeading
            )
        }
    }

    private fun registerOrientationSensor() {
        if (!isSensorRegistered) {
            if (rotationSensor != null) {
                sensorManager?.registerListener(
                    orientationListener,
                    rotationSensor,
                    SensorManager.SENSOR_DELAY_UI
                )
                isSensorRegistered = true
                Log.d(TAG, "Orientation sensor registered (rotation vector/orientation)")
            } else if (accelSensor != null && magSensor != null) {
                sensorManager?.registerListener(orientationListener, accelSensor, SensorManager.SENSOR_DELAY_UI)
                sensorManager?.registerListener(orientationListener, magSensor, SensorManager.SENSOR_DELAY_UI)
                isSensorRegistered = true
                Log.d(TAG, "Orientation sensors registered (accel + mag fallback)")
            }
        }
    }

    private fun unregisterOrientationSensor() {
        if (isSensorRegistered) {
            sensorManager?.unregisterListener(orientationListener)
            isSensorRegistered = false
            hasGravity = false
            hasGeo = false
            Log.d(TAG, "Orientation sensor unregistered (paused)")
        }
    }

    /**
     * Sets map mode explicitly to Static mode or Live mode.
     */
    fun setMapMode(staticMode: Boolean) {
        if (_uiState.value.isStaticMode == staticMode) return
        _uiState.update { it.copy(isStaticMode = staticMode) }
        Log.d(TAG, "Map mode changed: isStaticMode = $staticMode")
        if (staticMode) {
            viewModelScope.launch {
                val loc = engineFacade.getCurrentLocation()
                val lat = if (loc != null) loc.latitude else lastKnownLat
                val lng = if (loc != null) loc.longitude else lastKnownLng
                if (lat.isFinite() && lng.isFinite()) {
                    val tile = fetchMapTile(lat, lng, zoom = 15)
                    if (tile != null) _uiState.update { it.copy(mapTile = tile) }
                }
            }
        } else {
            if (isLiveActive) {
                viewModelScope.launch {
                    val loc = engineFacade.getCurrentLocation()
                    val lat = if (loc != null) loc.latitude else lastKnownLat
                    val lng = if (loc != null) loc.longitude else lastKnownLng
                    if (lat.isFinite() && lng.isFinite()) {
                        val venue = engineFacade.currentVenue.value
                        updateLocationAndMap(lat, lng, venue?.label)
                    }
                }
            }
        }
    }

    /**
     * Toggles between Live Map mode and Static Image Map mode.
     */
    fun toggleMapMode() {
        setMapMode(!_uiState.value.isStaticMode)
    }

    /**
     * Controls whether high-frequency live map updates are running.
     * Active only when user is on either page of the launcher.
     * Turns off completely when in the background.
     */
    fun setLiveTrackingActive(active: Boolean) {
        if (isLiveActive == active) return
        isLiveActive = active
        if (active) {
            Log.d(TAG, "Live map activated (user on launcher page)")
            engineFacade.startLiveLocationTracking()
            registerOrientationSensor()
            liveMapJob?.cancel()
            liveMapJob = viewModelScope.launch {
                // 1. Initial quick fix
                val initialLoc = engineFacade.getCurrentLocation()
                if (initialLoc != null) {
                    val venue = engineFacade.currentVenue.value
                    updateLocationAndMap(initialLoc.latitude, initialLoc.longitude, venue?.label)
                }

                // 2. Observe live location changes
                engineFacade.lastLocation.collect { loc ->
                    if (loc != null) {
                        val prevLat = lastKnownLat
                        val prevLng = lastKnownLng
                        val moved = !prevLat.isFinite() || !prevLng.isFinite() ||
                                kotlin.math.abs(loc.latitude - prevLat) > 0.00001 ||
                                kotlin.math.abs(loc.longitude - prevLng) > 0.00001

                        if (moved) {
                            val venue = engineFacade.currentVenue.value
                            val label = if (venue != null && venue.label.isNotBlank()) venue.label else null
                            updateLocationAndMap(loc.latitude, loc.longitude, label)
                        }

                        // React to direction/bearing when moving
                        if (loc.hasBearing() && loc.bearing != 0f) {
                            updateSmoothHeading(loc.bearing)
                        }
                    }
                }
            }
        } else {
            Log.d(TAG, "Live map deactivated (launcher in background)")
            engineFacade.stopLiveLocationTracking()
            unregisterOrientationSensor()
            liveMapJob?.cancel()
            liveMapJob = null
        }
    }

    fun refresh() {
        wallpaperProvider.refresh()
        refreshAppList()
        viewModelScope.launch {
            val loc = engineFacade.getCurrentLocation()
            if (loc != null) {
                val currentVenue = engineFacade.currentVenue.value
                val venueLabel = if (currentVenue != null && currentVenue.label.isNotBlank()) currentVenue.label else null
                updateLocationAndMap(loc.latitude, loc.longitude, venueLabel)
            } else {
                val venue = engineFacade.currentVenue.value
                if (venue != null) {
                    val label = if (venue.label.isNotBlank()) venue.label else "LOCATION ${venue.id}"
                    updateLocationAndMap(venue.latitude, venue.longitude, label)
                }
            }
        }
    }

    companion object {
        private const val TAG = "VisionHomeViewModel"
        private const val CARTO_API_KEY = "cb1_48to_1_37e353aa4f51c7831d847df8"

        private fun isExcludedPackage(packageName: String): Boolean {
            val lower = packageName.lowercase()
            return lower == "android" ||
                    lower.startsWith("com.android.systemui") ||
                    lower.contains("packageinstaller") ||
                    lower.contains("permissioncontroller") ||
                    lower.startsWith("com.google.android.packageinstaller") ||
                    lower.startsWith("com.google.android.permissioncontroller") ||
                    lower.startsWith("com.android.providers.") ||
                    lower.startsWith("com.android.settings.intelligence") ||
                    lower.startsWith("com.google.android.overlay") ||
                    lower.startsWith("com.android.internal")
        }

        private fun formatCurrentTime(): String {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return sdf.format(Date())
        }

        private fun formatCurrentDate(): String {
            val sdf = SimpleDateFormat("EEE, d MMM", Locale.US)
            return sdf.format(Date()).uppercase()
        }
    }
}
