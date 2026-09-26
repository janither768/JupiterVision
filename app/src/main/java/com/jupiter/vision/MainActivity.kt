package com.jupiter.vision

import android.Manifest
import android.app.WallpaperManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect as AndroidRect
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.jupiter.vision.util.AtmosphereEngine
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
    val config = LocalConfiguration.current
    val density = LocalDensity.current

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

    // Pre-rendered Atmosphere frames
    var atmosphereFrames by remember { mutableStateOf<List<ImageBitmap>?>(null) }
    LaunchedEffect(Unit) {
        atmosphereFrames = AtmosphereEngine.getOrGenerateFrames()
    }
    val atmosphereIndex by AtmosphereEngine.rememberGlobalFrameIndex()

    // Wallpaper source & 50% feathered extension
    val rawWallpaper = remember(wallpaperTrigger) {
        loadWallpaperBitmap(ctx) ?: createCosmicWallpaper(1080, 1920)
    }
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }.toInt().coerceAtLeast(1)
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }.toInt().coerceAtLeast(1)
    val targetWallpaperHeightPx = screenHeightPx * 2

    val extendedWallpaper = remember(rawWallpaper, screenWidthPx, targetWallpaperHeightPx) {
        rawWallpaper?.let { raw ->
            val extendedBmp = extendWallpaperVertically(raw, screenWidthPx, targetWallpaperHeightPx)
            extendedBmp.asImageBitmap()
        }
    }

    // Vivid saturated blue-forward palette
    val palette = remember(rawWallpaper) { ColorEngine.fromWallpaper(rawWallpaper) }

    // Real-time pure white Clock
    var timeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            timeString = timeFmt.format(Date())
            delay(1000)
        }
    }

    // System and Third-Party Zones State
    val systemTiles = remember { mutableStateListOf<TileModel>() }
    val thirdPartyTiles = remember { mutableStateListOf<TileModel>() }

    // Resolve system apps & third party apps with real notifications and dynamic growth
    LaunchedEffect(palette, deviceImages, deviceAudio, realNotifications) {
        val systemResolutions = AppLoader.resolveSystemRoles(ctx)

        val skeleton = listOf(
            SystemSlotConfig("phone", "PHONE", 0, 0, 2, 2, false),
            SystemSlotConfig("msg", "MSG", 2, 0, 2, 2, true),
            SystemSlotConfig("gallery", "GALLERY", 4, 0, 4, 2, deviceImages.isNotEmpty()),
            SystemSlotConfig("cam", "CAM", 0, 2, 2, 2, false),
            SystemSlotConfig("set", "SETTINGS", 2, 2, 2, 2, false),
            SystemSlotConfig("music", "MUSIC", 4, 2, 4, 2, deviceAudio.isNotEmpty()),
            SystemSlotConfig("flash", "FLASH", 0, 4, 1, 1, false, isMicro = true),
            SystemSlotConfig("calc", "CALC", 1, 4, 1, 1, false, isMicro = true),
            SystemSlotConfig("notes", "NOTES", 2, 4, 2, 2, false),
            SystemSlotConfig("new_sys", "SYSTEM", 4, 4, 4, 2, false)
        )

        val sysList = mutableListOf<TileModel>()
        val reservedPackages = mutableSetOf<String>()

        skeleton.forEachIndexed { i, slot ->
            val res = systemResolutions[slot.role]
            val pkg = res?.packageName
            if (pkg != null) {
                reservedPackages.add(pkg)
            }

            val isAvailable = slot.role == "flash" || pkg != null

            if (isAvailable) {
                val label = if (slot.role == "new_sys") (res?.label ?: "SYSTEM") else slot.defaultLabel

                val realPkgNotifs = if (pkg != null) realNotifications[pkg] ?: emptyList() else emptyList()
                val hasRealNotif = realPkgNotifs.isNotEmpty()

                val contentLines = when (slot.role) {
                    "msg" -> {
                        if (hasRealNotif) realPkgNotifs.take(3).map {
                            if (it.title.isNotBlank()) "${it.title}: ${it.text}" else it.text
                        } else emptyList()
                    }
                    "music" -> deviceAudio.take(4).map { it.title }
                    else -> {
                        if (hasRealNotif) realPkgNotifs.take(3).map {
                            if (it.title.isNotBlank()) "${it.title}: ${it.text}" else it.text
                        } else emptyList()
                    }
                }

                sysList.add(
                    TileModel(
                        id = "sys_${slot.role}",
                        label = label,
                        packageName = pkg,
                        role = slot.role,
                        fixedCol = slot.col,
                        fixedRow = slot.row,
                        colSpan = slot.colSpan,
                        rowSpan = slot.rowSpan,
                        isMicro = slot.isMicro,
                        isSystem = true,
                        hasActivity = slot.hasActivity || hasRealNotif,
                        contentLines = contentLines,
                        accentColor = palette[i % palette.size],
                        iconBitmap = res?.icon
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
    LaunchedEffect(systemTiles, thirdPartyTiles, flipIntervalSec, deviceImages, deviceAudio, realNotifications) {
        if (flipIntervalSec > 0) {
            val all = systemTiles + thirdPartyTiles
            while (true) {
                delay(flipIntervalSec * 1000L)
                contentModes = all.associate { t ->
                    val hasLiveNotif = t.packageName != null && (realNotifications[t.packageName]?.isNotEmpty() == true)
                    val canFlip = when (t.role) {
                        "gallery" -> deviceImages.isNotEmpty()
                        "music" -> deviceAudio.isNotEmpty()
                        else -> t.hasActivity || hasLiveNotif
                    }
                    val isCurrentlyIcon = (contentModes[t.id] ?: TileMode.ICON) == TileMode.ICON
                    t.id to if (canFlip && (isCurrentlyIcon || hasLiveNotif)) TileMode.CONTENT else TileMode.ICON
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

    BackHandler(enabled = openPanel != PanelKind.NONE || focusedTile != null || propertiesTile != null) {
        if (focusedTile != null) {
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

        val scrollState = rememberScrollState()

        // Main Scrollable Container
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
        ) {
            // Header: Pure white branding + Pure white clock
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "JUPITERVISION 2112.8",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    color = Color.White.copy(alpha = 0.90f)
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = timeString.ifEmpty { "12:00" },
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.90f)
                )
            }

            // Tile Grid with Atmosphere, Gestures (Slide Right Mini-App, Slide Left Properties)
            TileGrid(
                systemTiles = systemTiles,
                thirdPartyTiles = thirdPartyTiles,
                contentModes = contentModes,
                wallpaper = extendedWallpaper,
                atmosphereFrames = atmosphereFrames,
                atmosphereIndex = atmosphereIndex,
                galleryImages = deviceImages,
                scrollOffset = scrollState.value,
                isFocusActive = (focusedTile != null || propertiesTile != null),
                onTileClick = { tile ->
                    if (focusedTile != null) {
                        focusedTile = null
                        focusedBounds = null
                    } else if (propertiesTile != null) {
                        propertiesTile = null
                        propertiesBounds = null
                    } else if (tile.role == "flash" || tile.id == "torch") {
                        SystemControls.toggleTorch(ctx)
                    } else if (tile.packageName != null) {
                        AppHistoryManager.recordAppLaunch(ctx, tile.packageName)
                        SystemControls.launchPackage(ctx, tile.packageName)
                    } else {
                        SystemControls.launchSystemAction(ctx, tile.role ?: tile.id)
                    }
                },
                onOpenMiniApp = { tile, bounds ->
                    if (!tile.isMicro && !tile.isInvisible) {
                        focusedTile = tile
                        focusedBounds = bounds
                    }
                },
                onOpenProperties = { tile, bounds ->
                    if (!tile.isMicro && !tile.isInvisible) {
                        propertiesTile = tile
                        propertiesBounds = bounds
                    }
                },
                gutter = gutterDp.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(80.dp))
        }

        // Edge gesture zones with continuous real-time swipe tracking
        EdgePanelZones(
            openPanel = openPanel,
            onPanelChange = { openPanel = it },
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        // Panel Overlay: Pure Minimalist Controls & App Lister
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
                onDismiss = {
                    focusedTile = null
                    focusedBounds = null
                }
            )
        }

        // Tile Properties & Size Editor Overlay
        if (propertiesTile != null && propertiesBounds != null) {
            val currentTile = propertiesTile!!
            TilePropertiesOverlay(
                tile = currentTile,
                sourceBounds = propertiesBounds!!,
                palette = palette,
                onColorChange = { newColor ->
                    val idxSys = systemTiles.indexOfFirst { it.id == currentTile.id }
                    if (idxSys != null && idxSys >= 0) {
                        systemTiles[idxSys] = systemTiles[idxSys].copy(accentColor = newColor)
                        propertiesTile = systemTiles[idxSys]
                    } else {
                        val idxThird = thirdPartyTiles.indexOfFirst { it.id == currentTile.id }
                        if (idxThird != null && idxThird >= 0) {
                            thirdPartyTiles[idxThird] = thirdPartyTiles[idxThird].copy(accentColor = newColor)
                            propertiesTile = thirdPartyTiles[idxThird]
                        }
                    }
                },
                onSizeChange = { newColSpan, newRowSpan ->
                    val isMicro = (newColSpan == 1 && newRowSpan == 1)
                    val isMacro = (newColSpan >= 4 && newRowSpan >= 4)
                    val idxSys = systemTiles.indexOfFirst { it.id == currentTile.id }
                    if (idxSys != null && idxSys >= 0) {
                        systemTiles[idxSys] = systemTiles[idxSys].copy(
                            colSpan = newColSpan,
                            rowSpan = newRowSpan,
                            isMicro = isMicro,
                            isMacro = isMacro
                        )
                        propertiesTile = systemTiles[idxSys]
                    } else {
                        val idxThird = thirdPartyTiles.indexOfFirst { it.id == currentTile.id }
                        if (idxThird != null && idxThird >= 0) {
                            thirdPartyTiles[idxThird] = thirdPartyTiles[idxThird].copy(
                                colSpan = newColSpan,
                                rowSpan = newRowSpan,
                                isMicro = isMicro,
                                isMacro = isMacro
                            )
                            propertiesTile = thirdPartyTiles[idxThird]
                        }
                    }
                },
                onDismiss = {
                    propertiesTile = null
                    propertiesBounds = null
                }
            )
        }
    }
}

private data class SystemSlotConfig(
    val role: String,
    val defaultLabel: String,
    val col: Int,
    val row: Int,
    val colSpan: Int,
    val rowSpan: Int,
    val hasActivity: Boolean,
    val isMicro: Boolean = false
)

/**
 * Wallpaper replication with 50% top and bottom feather band to eliminate seams.
 */
private fun extendWallpaperVertically(
    sourceBitmap: Bitmap,
    screenWidth: Int,
    targetHeight: Int,
    seed: Long = 42L
): Bitmap {
    val scaledSource = if (sourceBitmap.width != screenWidth) {
        val h = (screenWidth * sourceBitmap.height.toFloat() / sourceBitmap.width).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(sourceBitmap, screenWidth, h, true)
    } else {
        sourceBitmap
    }

    val srcW = scaledSource.width
    val srcH = scaledSource.height

    if (srcH >= targetHeight) {
        return scaledSource
    }

    val result = Bitmap.createBitmap(screenWidth, targetHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val random = Random(seed)

    var yCursor = (srcH - (0.35f * srcH).toInt()).coerceAtLeast(0)
    val minSliceH = (0.30f * srcH).toInt().coerceAtLeast(50)
    val maxSliceH = (0.55f * srcH).toInt().coerceAtLeast(minSliceH + 1)

    while (yCursor < targetHeight + 100) {
        val sliceH = minSliceH + random.nextInt(maxSliceH - minSliceH + 1)
        val maxSrcY = (srcH - sliceH).coerceAtLeast(0)
        val srcY = if (maxSrcY > 0) random.nextInt(maxSrcY) else 0
        val actualSliceH = sliceH.coerceAtMost(targetHeight - yCursor + 100)

        if (actualSliceH <= 0) break

        val flipVertical = random.nextBoolean()

        val sliceBmp = Bitmap.createBitmap(screenWidth, actualSliceH, Bitmap.Config.ARGB_8888)
        val sliceCanvas = Canvas(sliceBmp)

        val srcRect = AndroidRect(0, srcY, srcW, srcY + actualSliceH)
        val dstRect = AndroidRect(0, 0, screenWidth, actualSliceH)

        val basePaint = Paint(Paint.FILTER_BITMAP_FLAG)
        if (flipVertical) {
            sliceCanvas.save()
            sliceCanvas.scale(1f, -1f, screenWidth / 2f, actualSliceH / 2f)
            sliceCanvas.drawBitmap(scaledSource, srcRect, dstRect, basePaint)
            sliceCanvas.restore()
        } else {
            sliceCanvas.drawBitmap(scaledSource, srcRect, dstRect, basePaint)
        }

        // Feather band 50% on top and bottom for smooth soft blend
        val featherPaint = Paint().apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
            shader = LinearGradient(
                0f, 0f, 0f, actualSliceH.toFloat(),
                intArrayOf(
                    AndroidColor.TRANSPARENT,
                    AndroidColor.argb(150, 255, 255, 255),
                    AndroidColor.TRANSPARENT
                ),
                floatArrayOf(0f, 0.50f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        sliceCanvas.drawRect(0f, 0f, screenWidth.toFloat(), actualSliceH.toFloat(), featherPaint)

        canvas.drawBitmap(sliceBmp, 0f, yCursor.toFloat(), null)
        sliceBmp.recycle()

        val stepY = (actualSliceH * 0.40f).toInt().coerceAtLeast(20)
        yCursor += stepY
    }

    val topPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(scaledSource, 0f, 0f, topPaint)

    return result
}

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
