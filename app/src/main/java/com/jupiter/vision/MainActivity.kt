package com.jupiter.vision

import android.Manifest
import android.app.AlarmManager
import android.app.WallpaperManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import com.jupiter.vision.engine.VisionEngine
import com.jupiter.vision.model.VenueItem
import com.jupiter.vision.model.VisionProfileRepository
import com.jupiter.vision.model.WeekScheduleItem
import com.jupiter.vision.ui.vision.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jupiter.vision.model.ColorEngine
import com.jupiter.vision.model.TileMode
import com.jupiter.vision.model.TileModel
import com.jupiter.vision.ui.EdgePanelZones
import com.jupiter.vision.ui.FocusOverlay
import com.jupiter.vision.ui.PanelKind
import com.jupiter.vision.ui.PanelSurface
import com.jupiter.vision.ui.TileGrid
import com.jupiter.vision.ui.TilePropertiesOverlay
import com.jupiter.vision.ui.screens.AllAppsScreen
import com.jupiter.vision.ui.screens.SettingsScreen
import com.jupiter.vision.ui.theme.InterFontFamily
import com.jupiter.vision.ui.theme.JupiterVisionTheme
import com.jupiter.vision.util.AppHistoryManager
import com.jupiter.vision.util.AppLoader
import com.jupiter.vision.util.ColorEnginePreferences
import com.jupiter.vision.util.JupiterAudioPlayer
import com.jupiter.vision.util.JupiterNotificationListener
import com.jupiter.vision.util.Packer
import com.jupiter.vision.util.SystemControls
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (_: Throwable) {
            // Guard for legacy/custom ROMs
        }
        ColorEnginePreferences.init(this)
        setContent {
            JupiterVisionTheme {
                val navController = rememberNavController()
                var gutterDp by remember { mutableIntStateOf(4) }
                var flipCadenceSec by remember { mutableIntStateOf(4) }
                var globalWallpaperTrigger by remember { mutableIntStateOf(0) }

                val context = LocalContext.current
                val onSetDeviceWallpaper = {
                    val cosmic = createCosmicWallpaper(1080, 1920)
                    val ok = SystemControls.setDeviceWallpaper(context, cosmic)
                    if (ok) {
                        globalWallpaperTrigger++
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                        HomeScreen(
                            onNavigateToAllApps = { navController.navigate("all_apps") },
                            onNavigateToSettings = { navController.navigate("settings") },
                            gutterDp = gutterDp,
                            flipIntervalSec = flipCadenceSec,
                            wallpaperTrigger = globalWallpaperTrigger,
                            onWallpaperTriggerChange = { globalWallpaperTrigger = it },
                            onSetDeviceWallpaper = onSetDeviceWallpaper
                        )
                    }

                    composable("all_apps") {
                        AllAppsScreen(
                            onBack = { navController.popBackStack() },
                            onPinApp = {}
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            currentGutter = gutterDp,
                            onGutterChange = { gutterDp = it },
                            currentFlipInterval = flipCadenceSec,
                            onFlipIntervalChange = { flipCadenceSec = it },
                            onBack = { navController.popBackStack() },
                            onSetDeviceWallpaper = onSetDeviceWallpaper
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    onNavigateToAllApps: () -> Unit,
    onNavigateToSettings: () -> Unit,
    gutterDp: Int = 4,
    flipIntervalSec: Int = 4,
    wallpaperTrigger: Int = 0,
    onWallpaperTriggerChange: (Int) -> Unit = {},
    onSetDeviceWallpaper: () -> Unit = {}
) {
    val ctx = LocalContext.current

    // Permissions check
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onWallpaperTriggerChange(wallpaperTrigger + 1)
    }

    LaunchedEffect(Unit) {
        val perms = buildList {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    // Load media data
    var deviceImages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var deviceAudio by remember { mutableStateOf<List<AppLoader.AudioTrackInfo>>(emptyList()) }

    LaunchedEffect(wallpaperTrigger) {
        deviceImages = AppLoader.queryDeviceImages(ctx, limit = 20)
        deviceAudio = AppLoader.queryDeviceAudio(ctx, limit = 40)
    }

    // Real Notification catching state
    val realNotifications by JupiterNotificationListener.notificationsFlow.collectAsState()

    // Real Audio playback state
    val playbackState by JupiterAudioPlayer.playbackState.collectAsState()

    // Color Engine State
    val isColorEngineEnabled by ColorEnginePreferences.colorEngineFlow.collectAsState()

    // Wallpaper source for backdrop and Color Engine extraction
    val rawWallpaper = remember(wallpaperTrigger) {
        loadWallpaperBitmap(ctx) ?: createCosmicWallpaper(1080, 1920)
    }

    // Active palette based on Color Engine toggle state
    val palette = remember(rawWallpaper, isColorEngineEnabled) {
        if (isColorEngineEnabled) {
            ColorEngine.fromWallpaper(rawWallpaper)
        } else {
            ColorEngine.Default
        }
    }

    // System and Third-Party Zones State
    val systemTiles = remember { mutableStateListOf<TileModel>() }
    val thirdPartyTiles = remember { mutableStateListOf<TileModel>() }

    // Swap Apps Mode State
    var swappingSourceTile by remember { mutableStateOf<TileModel?>(null) }

    // Folder Migration Mode State
    var migratingFolderTile by remember { mutableStateOf<TileModel?>(null) }

    // Real-time pure white Clock with seconds
    var timeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        while (true) {
            timeString = timeFmt.format(Date())
            delay(500)
        }
    }

    // Battery, Date, Weather Telemetry
    val batteryPercent = remember {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 85
    }
    val dateString = remember(timeString) {
        SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()).uppercase()
    }
    val weatherTemp = "22°C"

    // All installed apps loaded for Vision Engine
    var allInstalledApps by remember { mutableStateOf<List<com.jupiter.vision.model.AppInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        allInstalledApps = AppLoader.loadInstalledApps(ctx)
    }

    // Vision Profile & Engine State
    var isFirstRun by remember { mutableStateOf(VisionProfileRepository.isFirstRun(ctx)) }
    var currentVisionScreen by remember {
        mutableStateOf(if (isFirstRun) VisionScreen.FIRST_RUN_WELCOME else VisionScreen.NONE)
    }
    var showVisionMenu by remember { mutableStateOf(false) }
    var showBirthdayModal by remember { mutableStateOf(false) }
    var showCorrectionDialog by remember { mutableStateOf(false) }

    var userName by remember { mutableStateOf(VisionProfileRepository.getName(ctx)) }
    var userBirthday by remember { mutableStateOf(VisionProfileRepository.getBirthday(ctx)) }
    val venuesList = remember { mutableStateListOf<VenueItem>().apply { addAll(VisionProfileRepository.getVenues(ctx)) } }
    val weekCalendarMap = remember {
        val map = mutableStateMapOf<String, WeekScheduleItem>()
        map.putAll(VisionProfileRepository.getWeekCalendar(ctx))
        map
    }
    var editingVenue by remember { mutableStateOf<VenueItem?>(null) }
    var isNewVenue by remember { mutableStateOf(false) }

    var engineSnapshot by remember {
        mutableStateOf(VisionEngine.evaluateState(ctx, allInstalledApps))
    }
    LaunchedEffect(allInstalledApps, isFirstRun) {
        while (true) {
            engineSnapshot = VisionEngine.evaluateState(ctx, allInstalledApps)
            delay(15000L)
        }
    }

    // Resolve system apps & third party apps
    LaunchedEffect(palette, isColorEngineEnabled, deviceImages, deviceAudio, realNotifications) {
        val systemResolutions = AppLoader.resolveSystemRoles(ctx)

        val skeleton = listOf(
            SystemSlotConfig("phone", "PHONE", 0, 0, 2, 2),
            SystemSlotConfig("msg", "MESSAGES", 2, 0, 2, 2),
            SystemSlotConfig("gallery", "GALLERY", 4, 0, 4, 2),
            SystemSlotConfig("cam", "CAMERA", 0, 2, 2, 2),
            SystemSlotConfig("set", "SETTINGS", 2, 2, 2, 2),
            SystemSlotConfig("music", "MUSIC", 4, 2, 4, 2),
            SystemSlotConfig("flash", "FLASH", 0, 4, 1, 1, isMicro = true),
            SystemSlotConfig("calc", "CALC", 1, 4, 1, 1, isMicro = true),
            SystemSlotConfig("notes", "NOTES", 2, 4, 2, 2),
            SystemSlotConfig("clock", "CLOCK", 4, 4, 4, 2)
        )

        val sysList = mutableListOf<TileModel>()
        val reservedPackages = mutableSetOf<String>()

        skeleton.forEachIndexed { i, slot ->
            val res = systemResolutions[slot.role]
            val pkg = res?.packageName
            if (pkg != null) {
                reservedPackages.add(pkg)
            }

            val isAvailable = slot.role == "flash" || slot.role == "clock" || pkg != null

            if (isAvailable) {
                val label = slot.defaultLabel

                val realPkgNotifs = if (pkg != null) realNotifications[pkg] ?: emptyList() else emptyList()
                val hasRealNotif = realPkgNotifs.isNotEmpty()

                val contentLines = when (slot.role) {
                    "msg" -> {
                        if (hasRealNotif) realPkgNotifs.take(3).map {
                            if (it.title.isNotBlank()) "${it.title}: ${it.text}" else it.text
                        } else emptyList()
                    }
                    "music" -> deviceAudio.take(4).map { it.title }
                    "clock" -> getAlarmContentLines(ctx)
                    else -> {
                        if (hasRealNotif) realPkgNotifs.take(3).map {
                            if (it.title.isNotBlank()) "${it.title}: ${it.text}" else it.text
                        } else emptyList()
                    }
                }

                val accentColor = if (isColorEngineEnabled) {
                    ColorEngine.getSystemAppWallpaperColor(slot.role, rawWallpaper, palette, i)
                } else {
                    ColorEngine.getSystemAppDefaultColor(slot.role)
                }

                sysList.add(
                    TileModel(
                        id = "sys_${slot.role}",
                        label = label,
                        packageName = pkg,
                        role = slot.role,
                        fixedCol = slot.col,
                        fixedRow = slot.row,
                        gridCol = slot.col,
                        gridRow = slot.row,
                        colSpan = slot.colSpan,
                        rowSpan = slot.rowSpan,
                        isMicro = slot.isMicro,
                        isSystem = true,
                        hasActivity = hasRealNotif,
                        contentLines = contentLines,
                        accentColor = accentColor,
                        iconBitmap = res?.icon,
                        rawIconBitmap = res?.iconBitmap
                    )
                )
            }
        }

        systemTiles.clear()
        systemTiles.addAll(sysList)

        // Real Notifications map for third-party apps
        val allApps = AppLoader.loadInstalledApps(ctx)
        val thirdPartyApps = allApps.filter { it.packageName !in reservedPackages }
        val appActivityMap = mutableMapOf<String, List<String>>()

        for ((notifPkg, notifs) in realNotifications) {
            if (notifs.isNotEmpty()) {
                val lines = notifs.take(3).map {
                    if (it.title.isNotBlank()) "${it.title}: ${it.text}" else it.text
                }
                appActivityMap[notifPkg] = lines
            }
        }

        val packed = Packer.packThirdPartyApps(
            apps = thirdPartyApps,
            palette = palette,
            activityMap = appActivityMap,
            startIndex = sysList.size
        )
        thirdPartyTiles.clear()
        thirdPartyTiles.addAll(packed)
    }

    // Dynamic content modes cycling
    var contentModes by remember { mutableStateOf<Map<String, TileMode>>(emptyMap()) }
    LaunchedEffect(systemTiles, thirdPartyTiles, flipIntervalSec, deviceImages, deviceAudio, realNotifications, playbackState.isPlaying) {
        if (flipIntervalSec > 0) {
            val all = systemTiles + thirdPartyTiles
            while (true) {
                delay(flipIntervalSec * 1000L)
                contentModes = all.associate { t ->
                    val isMusicTile = (t.role ?: t.id).lowercase().contains("mus")
                    if (isMusicTile && playbackState.isPlaying) {
                        // Disable tile mode switching while music is playing
                        t.id to TileMode.CONTENT
                    } else if (t.isFolder) {
                        // Folders stay in ICON mode until pressed by the user
                        t.id to TileMode.ICON
                    } else {
                        val hasLiveNotif = t.packageName != null && (realNotifications[t.packageName]?.isNotEmpty() == true)
                        val canFlip = when (t.role) {
                            "gallery" -> deviceImages.isNotEmpty()
                            "music" -> deviceAudio.isNotEmpty()
                            "clock" -> true
                            else -> t.hasActivity || hasLiveNotif
                        }
                        val isCurrentlyIcon = (contentModes[t.id] ?: TileMode.ICON) == TileMode.ICON
                        t.id to if (canFlip && (isCurrentlyIcon || hasLiveNotif)) TileMode.CONTENT else TileMode.ICON
                    }
                }
            }
        }
    }

    // Focus / Mini-App mode state
    var focusedTile by remember { mutableStateOf<TileModel?>(null) }
    var focusedBounds by remember { mutableStateOf<Rect?>(null) }

    // Properties mode state
    var propertiesTile by remember { mutableStateOf<TileModel?>(null) }
    var propertiesBounds by remember { mutableStateOf<Rect?>(null) }

    // Edge Panels state
    var openPanel by remember { mutableStateOf(PanelKind.NONE) }

    BackHandler(enabled = currentVisionScreen != VisionScreen.NONE || showVisionMenu || showBirthdayModal || showCorrectionDialog || openPanel != PanelKind.NONE || focusedTile != null || propertiesTile != null || swappingSourceTile != null || migratingFolderTile != null) {
        if (showBirthdayModal) {
            showBirthdayModal = false
        } else if (showCorrectionDialog) {
            showCorrectionDialog = false
        } else if (showVisionMenu) {
            showVisionMenu = false
        } else if (currentVisionScreen == VisionScreen.VENUE_EDITOR) {
            currentVisionScreen = VisionScreen.VENUES_LIST
        } else if (currentVisionScreen == VisionScreen.VENUES_LIST || currentVisionScreen == VisionScreen.WEEK_CALENDAR) {
            currentVisionScreen = VisionScreen.VISION_PROFILE
        } else if (currentVisionScreen == VisionScreen.VISION_PROFILE || currentVisionScreen == VisionScreen.ENGINE_STATUS_DIAGNOSTIC) {
            currentVisionScreen = if (isFirstRun) VisionScreen.FIRST_RUN_WELCOME else VisionScreen.NONE
        } else if (migratingFolderTile != null) {
            migratingFolderTile = null
        } else if (swappingSourceTile != null) {
            swappingSourceTile = null
        } else if (focusedTile != null) {
            focusedTile = null
            focusedBounds = null
        } else if (propertiesTile != null) {
            propertiesTile = null
            propertiesBounds = null
        } else if (openPanel != PanelKind.NONE) {
            openPanel = PanelKind.NONE
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Static Full-Bleed Backdrop
        val backdropBmp = remember(rawWallpaper) { rawWallpaper?.asImageBitmap() }
        backdropBmp?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.22f,
                filterQuality = FilterQuality.Low
            )
        }

        // Main Container: Fixed Header + Single Canvas Grid
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header: Wordmark or Pure White Swap Banner or Migration prompt on left + Right-aligned time with seconds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when {
                        migratingFolderTile != null -> "touch the desire area to migrate folder"
                        swappingSourceTile != null -> "SELECT DESIRED APP TO SWAP"
                        else -> "JUPITERVISION 2112.16 II"
                    },
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (migratingFolderTile != null) 11.sp else 12.sp,
                    letterSpacing = if (migratingFolderTile != null) 0.5.sp else 2.sp,
                    color = Color.White
                )
                Text(
                    text = timeString.ifEmpty { "12:00:00" },
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp,
                    color = Color.White.copy(alpha = 0.90f)
                )
            }

            // Single Canvas Node Grid
            TileGrid(
                systemTiles = systemTiles,
                thirdPartyTiles = thirdPartyTiles,
                contentModes = contentModes,
                galleryImages = deviceImages,
                wallpaperBitmap = rawWallpaper,
                isFocusActive = (focusedTile != null || propertiesTile != null),
                isSwapMode = (swappingSourceTile != null),
                isMusicPlaying = playbackState.isPlaying,
                currentMusicTrackTitle = playbackState.currentTrack?.title ?: "",
                migratingFolderTile = migratingFolderTile,
                onMigrateFolderConfirm = { targetCol, targetRow ->
                    val folder = migratingFolderTile
                    if (folder != null) {
                        val updatedTiles = Packer.migrateFolder(
                            tiles = thirdPartyTiles.toList(),
                            folderId = folder.id,
                            targetCol = targetCol,
                            targetRow = targetRow
                        )
                        thirdPartyTiles.clear()
                        thirdPartyTiles.addAll(updatedTiles)
                    }
                    migratingFolderTile = null
                },
                onCancelMigration = {
                    migratingFolderTile = null
                },
                onMusicPrev = { JupiterAudioPlayer.playPrevious(ctx) },
                onMusicPlayPause = { JupiterAudioPlayer.togglePlayPause(ctx) },
                onMusicNext = { JupiterAudioPlayer.playNext(ctx) },
                onTileClick = { clickedTile ->
                    val src = swappingSourceTile
                    if (src != null) {
                        // Perform the App Swap between src and clickedTile
                        if (src.id != clickedTile.id) {
                            swapTileApps(
                                source = src,
                                target = clickedTile,
                                systemTiles = systemTiles,
                                thirdPartyTiles = thirdPartyTiles,
                                palette = palette
                            )
                        }
                        swappingSourceTile = null
                    } else if (focusedTile != null) {
                        focusedTile = null
                        focusedBounds = null
                    } else if (propertiesTile != null) {
                        propertiesTile = null
                        propertiesBounds = null
                    } else if (clickedTile.isFolder) {
                        val currentMode = contentModes[clickedTile.id] ?: TileMode.ICON
                        val newMode = if (currentMode == TileMode.CONTENT) TileMode.ICON else TileMode.CONTENT
                        contentModes = contentModes.toMutableMap().apply { put(clickedTile.id, newMode) }
                    } else if (clickedTile.role == "flash" || clickedTile.id == "torch") {
                        SystemControls.toggleTorch(ctx)
                    } else if (clickedTile.role == "cam" || clickedTile.id == "sys_cam") {
                        SystemControls.launchCamera(ctx, clickedTile.packageName)
                    } else if (clickedTile.packageName != null) {
                        AppHistoryManager.recordAppLaunch(ctx, clickedTile.packageName)
                        SystemControls.launchPackage(ctx, clickedTile.packageName)
                    } else {
                        SystemControls.launchSystemAction(ctx, clickedTile.role ?: clickedTile.id)
                    }
                },
                onOpenMiniApp = { tile, bounds ->
                    if (swappingSourceTile == null && migratingFolderTile == null && !tile.isMicro && !tile.isInvisible) {
                        focusedTile = tile
                        focusedBounds = bounds
                    }
                },
                onOpenProperties = { tile, bounds ->
                    if (swappingSourceTile == null && migratingFolderTile == null && !tile.isMicro && !tile.isInvisible) {
                        propertiesTile = tile
                        propertiesBounds = bounds
                    }
                },
                engineSnapshot = engineSnapshot,
                timeString = timeString,
                dateString = dateString,
                batteryPercent = batteryPercent,
                weatherTemp = weatherTemp,
                onVisionHomeAppClick = { app ->
                    AppHistoryManager.recordAppLaunch(ctx, app.packageName)
                    SystemControls.launchPackage(ctx, app.packageName)
                },
                onVisionHomeLongPress = {
                    showVisionMenu = true
                },
                gutter = gutterDp.dp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }

        // Edge gesture zones
        EdgePanelZones(
            openPanel = openPanel,
            onPanelChange = { openPanel = it },
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        // Panel Overlay
        PanelSurface(
            panel = openPanel,
            onClose = { openPanel = PanelKind.NONE },
            onSetDeviceWallpaper = onSetDeviceWallpaper
        )

        // Square-shaped Mini-App Overlay
        if (focusedTile != null && focusedBounds != null) {
            FocusOverlay(
                tile = focusedTile,
                sourceBounds = focusedBounds,
                galleryImages = deviceImages,
                audioTracks = deviceAudio,
                notifications = realNotifications,
                onMigrateFolder = { folder ->
                    migratingFolderTile = folder
                    focusedTile = null
                    focusedBounds = null
                },
                onDismiss = {
                    focusedTile = null
                    focusedBounds = null
                }
            )
        }

        // Tile Properties & Size Editor Overlay with 8 directional arrows and SWAP support
        if (propertiesTile != null && propertiesBounds != null) {
            val currentTile = propertiesTile!!
            TilePropertiesOverlay(
                tile = currentTile,
                sourceBounds = propertiesBounds!!,
                palette = palette,
                onColorChange = { newColor ->
                    val idxSys = systemTiles.indexOfFirst { it.id == currentTile.id }
                    if (idxSys >= 0) {
                        systemTiles[idxSys] = systemTiles[idxSys].copy(accentColor = newColor)
                        propertiesTile = systemTiles[idxSys]
                    } else {
                        val idxThird = thirdPartyTiles.indexOfFirst { it.id == currentTile.id }
                        if (idxThird >= 0) {
                            thirdPartyTiles[idxThird] = thirdPartyTiles[idxThird].copy(accentColor = newColor)
                            propertiesTile = thirdPartyTiles[idxThird]
                        }
                    }
                },
                onDirectionalResize = { direction ->
                    val idxSys = systemTiles.indexOfFirst { it.id == currentTile.id }
                    if (idxSys >= 0) {
                        val reflowed = Packer.reflowTileDirection(systemTiles, currentTile.id, direction)
                        if (reflowed != null) {
                            systemTiles.clear()
                            systemTiles.addAll(reflowed)
                            propertiesTile = reflowed.firstOrNull { it.id == currentTile.id }
                        }
                    } else {
                        val idxThird = thirdPartyTiles.indexOfFirst { it.id == currentTile.id }
                        if (idxThird >= 0) {
                            val reflowed = Packer.reflowTileDirection(thirdPartyTiles, currentTile.id, direction)
                            if (reflowed != null) {
                                thirdPartyTiles.clear()
                                thirdPartyTiles.addAll(reflowed)
                                propertiesTile = reflowed.firstOrNull { it.id == currentTile.id }
                            }
                        }
                    }
                },
                onStartSwap = { tileToSwap ->
                    swappingSourceTile = tileToSwap
                    propertiesTile = null
                    propertiesBounds = null
                },
                onDismiss = {
                    propertiesTile = null
                    propertiesBounds = null
                }
            )
        }

        // Vision Screens (First Run, Profile, Venues, Calendar, Diagnostics)
        if (currentVisionScreen != VisionScreen.NONE) {
            AnimatedContent(
                targetState = currentVisionScreen,
                transitionSpec = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(durationMillis = 300, easing = IndustrialBezier)
                    ) togetherWith slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -fullWidth },
                        animationSpec = tween(durationMillis = 300, easing = IndustrialBezier)
                    )
                },
                label = "VisionScreenTransition"
            ) { targetScreen ->
                when (targetScreen) {
                    VisionScreen.FIRST_RUN_WELCOME -> {
                        VisionFirstRunWelcomeScreen(
                            onBeginInterview = {
                                currentVisionScreen = VisionScreen.VISION_PROFILE
                            },
                            onSilentLearning = {
                                VisionProfileRepository.setFirstRunCompleted(ctx)
                                isFirstRun = false
                                currentVisionScreen = VisionScreen.NONE
                                engineSnapshot = VisionEngine.evaluateState(ctx, allInstalledApps)
                            }
                        )
                    }
                    VisionScreen.VISION_PROFILE -> {
                        VisionProfileMainScreen(
                            userName = userName,
                            onNameChange = {
                                userName = it
                                VisionProfileRepository.setName(ctx, it)
                            },
                            userBirthday = userBirthday,
                            onOpenBirthday = { showBirthdayModal = true },
                            onOpenVenues = { currentVisionScreen = VisionScreen.VENUES_LIST },
                            onOpenWeekCalendar = { currentVisionScreen = VisionScreen.WEEK_CALENDAR },
                            onSaveAndExit = {
                                currentVisionScreen = VisionScreen.ENGINE_WORKING_SAVING
                            }
                        )
                    }
                    VisionScreen.VENUES_LIST -> {
                        VenuesScreen(
                            venues = venuesList,
                            onAddVenue = {
                                editingVenue = VenueItem(name = "", type = "OTHER")
                                isNewVenue = true
                                currentVisionScreen = VisionScreen.VENUE_EDITOR
                            },
                            onSelectVenue = {
                                editingVenue = it
                                isNewVenue = false
                                currentVisionScreen = VisionScreen.VENUE_EDITOR
                            },
                            onBack = { currentVisionScreen = VisionScreen.VISION_PROFILE }
                        )
                    }
                    VisionScreen.VENUE_EDITOR -> {
                        editingVenue?.let { v ->
                            VenueEditorScreen(
                                venue = v,
                                isNew = isNewVenue,
                                onSave = { updated ->
                                    if (isNewVenue) {
                                        venuesList.add(updated)
                                    } else {
                                        val idx = venuesList.indexOfFirst { it.id == updated.id }
                                        if (idx >= 0) venuesList[idx] = updated
                                    }
                                    VisionProfileRepository.saveVenues(ctx, venuesList)
                                    currentVisionScreen = VisionScreen.VENUES_LIST
                                },
                                onDelete = { del ->
                                    venuesList.removeAll { it.id == del.id }
                                    VisionProfileRepository.saveVenues(ctx, venuesList)
                                    currentVisionScreen = VisionScreen.VENUES_LIST
                                },
                                onCancel = { currentVisionScreen = VisionScreen.VENUES_LIST }
                            )
                        }
                    }
                    VisionScreen.WEEK_CALENDAR -> {
                        WeekCalendarScreen(
                            calendarMap = weekCalendarMap,
                            onSaveSchedule = { day: String, item: WeekScheduleItem ->
                                weekCalendarMap[day] = item
                                VisionProfileRepository.saveWeekCalendar(ctx, weekCalendarMap)
                            },
                            onBack = { currentVisionScreen = VisionScreen.VISION_PROFILE }
                        )
                    }
                    VisionScreen.ENGINE_WORKING_SAVING -> {
                        VisionEngineWorkingTransitionScreen(
                            onFinished = {
                                VisionProfileRepository.setFirstRunCompleted(ctx)
                                isFirstRun = false
                                currentVisionScreen = VisionScreen.NONE
                                engineSnapshot = VisionEngine.evaluateState(ctx, allInstalledApps)
                            }
                        )
                    }
                    VisionScreen.ENGINE_STATUS_DIAGNOSTIC -> {
                        VisionEngineStatusScreen(
                            snapshot = engineSnapshot,
                            onClose = { currentVisionScreen = VisionScreen.NONE }
                        )
                    }
                    VisionScreen.NONE -> {}
                }
            }
        }

        // Birthday Modal Popup
        if (showBirthdayModal) {
            BirthdayModalPopup(
                currentBirthday = userBirthday,
                onDismiss = { showBirthdayModal = false },
                onSave = {
                    userBirthday = it
                    VisionProfileRepository.setBirthday(ctx, it)
                    showBirthdayModal = false
                }
            )
        }

        // Long Press Vision Home Menu
        if (showVisionMenu) {
            VisionHomeLongPressMenu(
                onOpenProfile = {
                    showVisionMenu = false
                    currentVisionScreen = VisionScreen.VISION_PROFILE
                },
                onOpenStatus = {
                    showVisionMenu = false
                    currentVisionScreen = VisionScreen.ENGINE_STATUS_DIAGNOSTIC
                },
                onOpenCorrection = {
                    showVisionMenu = false
                    showCorrectionDialog = true
                },
                onDisableForToday = {
                    showVisionMenu = false
                    VisionProfileRepository.disableEngineUntilMidnight(ctx)
                    engineSnapshot = VisionEngine.evaluateState(ctx, allInstalledApps)
                },
                onDismiss = { showVisionMenu = false }
            )
        }

        // Correction Dialog
        if (showCorrectionDialog) {
            VisionEngineCorrectionDialog(
                currentState = engineSnapshot.state,
                onCorrect = { correction ->
                    VisionProfileRepository.recordCorrection(ctx, correction)
                    showCorrectionDialog = false
                    engineSnapshot = VisionEngine.evaluateState(ctx, allInstalledApps)
                },
                onDismiss = { showCorrectionDialog = false }
            )
        }
    }
}

private fun swapTileApps(
    source: TileModel,
    target: TileModel,
    systemTiles: MutableList<TileModel>,
    thirdPartyTiles: MutableList<TileModel>,
    palette: List<Color>
) {
    val srcNewColor = if (target.rawIconBitmap != null) {
        ColorEngine.fromAppIcon(target.rawIconBitmap)
    } else if (target.role != null) {
        ColorEngine.getSystemAppDefaultColor(target.role)
    } else {
        source.accentColor
    }

    val targetNewColor = if (source.rawIconBitmap != null) {
        ColorEngine.fromAppIcon(source.rawIconBitmap)
    } else if (source.role != null) {
        ColorEngine.getSystemAppDefaultColor(source.role)
    } else {
        target.accentColor
    }

    fun updateInList(list: MutableList<TileModel>, id: String, transform: (TileModel) -> TileModel) {
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = transform(list[idx])
        }
    }

    val updatedSource = source.copy(
        label = target.label,
        packageName = target.packageName,
        role = target.role,
        iconBitmap = target.iconBitmap,
        rawIconBitmap = target.rawIconBitmap,
        contentLines = target.contentLines,
        hasActivity = target.hasActivity,
        accentColor = srcNewColor
    )

    val updatedTarget = target.copy(
        label = source.label,
        packageName = source.packageName,
        role = source.role,
        iconBitmap = source.iconBitmap,
        rawIconBitmap = source.rawIconBitmap,
        contentLines = source.contentLines,
        hasActivity = source.hasActivity,
        accentColor = targetNewColor
    )

    updateInList(systemTiles, source.id) { updatedSource }
    updateInList(thirdPartyTiles, source.id) { updatedSource }
    updateInList(systemTiles, target.id) { updatedTarget }
    updateInList(thirdPartyTiles, target.id) { updatedTarget }
}

private fun getAlarmContentLines(context: Context): List<String> {
    try {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val next = am?.nextAlarmClock
        if (next != null) {
            val trigger = next.triggerTime
            val diffMs = (trigger - System.currentTimeMillis()).coerceAtLeast(0)
            val diffH = diffMs / (1000 * 60 * 60)
            val diffM = (diffMs / (1000 * 60)) % 60
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(trigger))
            return listOf("NEXT: $time", "in ${diffH}h ${diffM}m", "Scheduled alarm")
        }
    } catch (_: Throwable) {}

    val dateStr = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    return listOf("No upcoming alarm", dateStr, "All timers clear")
}

private data class SystemSlotConfig(
    val role: String,
    val defaultLabel: String,
    val col: Int,
    val row: Int,
    val colSpan: Int = 2,
    val rowSpan: Int = 2,
    val isMicro: Boolean = false
)

private fun loadWallpaperBitmap(ctx: Context): Bitmap? {
    val wm = try {
        WallpaperManager.getInstance(ctx)
    } catch (_: Throwable) {
        null
    } ?: return null

    try {
        val d: Drawable? = wm.drawable
        if (d != null) {
            val bmp = drawableToBitmap(d)
            if (bmp != null) return bmp
        }
    } catch (_: Throwable) {}

    try {
        val d: Drawable? = wm.peekDrawable()
        if (d != null) {
            val bmp = drawableToBitmap(d)
            if (bmp != null) return bmp
        }
    } catch (_: Throwable) {}

    return null
}

private fun drawableToBitmap(d: Drawable): Bitmap? {
    return try {
        if (d is BitmapDrawable && d.bitmap != null && !d.bitmap.isRecycled) {
            return d.bitmap
        }
        val w = if (d.intrinsicWidth > 0) d.intrinsicWidth else 1080
        val h = if (d.intrinsicHeight > 0) d.intrinsicHeight else 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, c.width, c.height)
        d.draw(c)
        bmp
    } catch (_: Throwable) {
        null
    }
}

private fun createCosmicWallpaper(width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(
                AndroidColor.rgb(5, 8, 22),
                AndroidColor.rgb(10, 108, 255),
                AndroidColor.rgb(10, 15, 32)
            ),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    val glowPaint = Paint().apply {
        shader = RadialGradient(
            width * 0.85f, height * 0.25f, width * 0.9f,
            intArrayOf(
                AndroidColor.argb(120, 74, 158, 255),
                AndroidColor.argb(55, 30, 136, 229),
                AndroidColor.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawCircle(width * 0.85f, height * 0.25f, width * 0.9f, glowPaint)

    val starPaint = Paint().apply {
        color = AndroidColor.argb(130, 255, 255, 255)
    }
    val random = Random(42)
    for (i in 0 until 120) {
        val x = random.nextFloat() * width
        val y = random.nextFloat() * height
        val r = random.nextFloat() * 1.8f + 0.6f
        canvas.drawCircle(x, y, r, starPaint)
    }

    return bitmap
}
