package com.jupiter.vision

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jupiter.vision.model.AppInfo
import com.jupiter.vision.model.ColorEngine
import com.jupiter.vision.model.TileMode
import com.jupiter.vision.model.TileModel
import com.jupiter.vision.ui.EdgePanelZones
import com.jupiter.vision.ui.FocusOverlay
import com.jupiter.vision.ui.PanelKind
import com.jupiter.vision.ui.PanelSurface
import com.jupiter.vision.ui.TileGrid
import com.jupiter.vision.ui.screens.AllAppsScreen
import com.jupiter.vision.ui.screens.SettingsScreen
import com.jupiter.vision.ui.theme.JupiterVisionTheme
import com.jupiter.vision.util.SystemControls
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { _, t ->
            runCatching {
                java.io.File(filesDir, "jupiter_crash.txt")
                    .writeText(t.stackTraceToString())
            }
            android.util.Log.e("JUPITER_CRASH", "uncaught", t)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JupiterVisionTheme {
                val navController = rememberNavController()
                var gutterDp by remember { mutableIntStateOf(4) }
                var flipCadenceSec by remember { mutableIntStateOf(5) }

                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                        HomeScreen(
                            onNavigateToAllApps = { navController.navigate("all_apps") },
                            onNavigateToSettings = { navController.navigate("settings") },
                            gutterDp = gutterDp,
                            flipIntervalSec = flipCadenceSec
                        )
                    }

                    composable("all_apps") {
                        AllAppsScreen(
                            onBack = { navController.popBackStack() },
                            onPinApp = { appInfo ->
                                // Pin to home handled
                            }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            currentGutter = gutterDp,
                            onGutterChange = { gutterDp = it },
                            currentFlipInterval = flipCadenceSec,
                            onFlipIntervalChange = { flipCadenceSec = it },
                            onBack = { navController.popBackStack() }
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
    flipIntervalSec: Int = 5
) {
    val ctx = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current

    // Wallpaper: raw + scaled to screen width so tile slices line up
    val rawWallpaper = remember {
        loadWallpaperBitmap(ctx) ?: createCosmicWallpaper(1080, 1920)
    }
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }.toInt().coerceAtLeast(1)
    val scaledWallpaper = remember(rawWallpaper, screenWidthPx) {
        rawWallpaper?.let {
            val h = (screenWidthPx * it.height.toFloat() / it.width).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(it, screenWidthPx, h, true).asImageBitmap()
        }
    }

    // Palette (3 colors max via Palette or default cobalt-blue shades)
    val palette = remember(rawWallpaper) { ColorEngine.fromWallpaper(rawWallpaper) }

    // Tile set
    val tiles = remember(palette) {
        val base = listOf(
            Triple("phone", "PHONE", Pair(2, 2)),
            Triple("msg", "MSG", Pair(2, 2)),
            Triple("mail", "MAIL", Pair(4, 4)),
            Triple("cam", "CAM", Pair(2, 2)),
            Triple("gal", "GAL", Pair(4, 2)),
            Triple("set", "SET", Pair(2, 2)),
            Triple("torch", "FL", Pair(1, 1)),
            Triple("calc", "=", Pair(1, 1)),
            Triple("music", "MUS", Pair(2, 4)),
            Triple("notes", "NTS", Pair(2, 2)),
        )
        val initialList = base.mapIndexed { i, (id, label, span) ->
            TileModel(
                id = id,
                label = label,
                colSpan = span.first,
                rowSpan = span.second,
                isMicro = span.first == 1,
                hasActivity = id == "msg" || id == "mail",
                contentLines = when (id) {
                    "msg" -> listOf("Sarah: see you at 6", "Delivery: on the way")
                    "mail" -> listOf("Inbox · 3 new", "Re: invoice", "Weekly digest")
                    "gal" -> listOf("Camera Roll: 128 items", "Recent: space_09.png")
                    "music" -> listOf("Now playing", "— Jupiter Orbit —")
                    else -> emptyList()
                },
                accentColor = palette[i % palette.size],
            )
        }
        mutableStateListOf<TileModel>().apply { addAll(initialList) }
    }

    var contentModes by remember { mutableStateOf<Map<String, TileMode>>(emptyMap()) }
    var focused by remember { mutableStateOf<TileModel?>(null) }
    var openPanel by remember { mutableStateOf(PanelKind.NONE) }

    // Live clock and date strings
    var timeString by remember { mutableStateOf("") }
    var dateString by remember { mutableStateOf("") }
    var batteryLevel by remember { mutableIntStateOf(85) }

    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEE, MMM dd", Locale.getDefault())
        while (true) {
            val now = Date()
            timeString = timeFmt.format(now)
            dateString = dateFmt.format(now).uppercase()
            batteryLevel = SystemControls.getBatteryLevel(ctx)
            delay(1000)
        }
    }

    // Activity cycling
    LaunchedEffect(tiles, flipIntervalSec) {
        if (flipIntervalSec > 0) {
            while (true) {
                delay(flipIntervalSec * 1000L)
                contentModes = tiles.associate { t ->
                    t.id to if (t.hasActivity &&
                        (contentModes[t.id] ?: TileMode.ICON) == TileMode.ICON
                    ) TileMode.CONTENT else TileMode.ICON
                }
            }
        }
    }

    BackHandler(enabled = openPanel != PanelKind.NONE || focused != null) {
        if (openPanel != PanelKind.NONE) openPanel = PanelKind.NONE
        else if (focused != null) focused = null
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        // 1. Wallpaper backdrop (dimmed)
        scaledWallpaper?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.34f,
                filterQuality = FilterQuality.Medium
            )
        }

        // 2. Tile grid & header container
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            // Cyber Industrial Status Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C10).copy(alpha = 0.85f))
                    .border(1.dp, Color(0xFF22222A))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "JUPITER",
                            color = Color(0xFFFF6A00),
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "VISION",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = timeString,
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "$dateString // BAT $batteryLevel%",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { openPanel = PanelKind.CONTROLS },
                        modifier = Modifier.size(34.dp).testTag("header_controls_btn")
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Controls",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { openPanel = PanelKind.APPS },
                        modifier = Modifier.size(34.dp).testTag("header_apps_btn")
                    ) {
                        Icon(
                            Icons.Default.Apps,
                            contentDescription = "Apps",
                            tint = Color(0xFFFF6A00),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(34.dp).testTag("header_settings_btn")
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            TileGrid(
                tiles = tiles,
                contentModes = contentModes,
                wallpaper = scaledWallpaper,
                onTileClick = { tile ->
                    if (tile.packageName != null) {
                        SystemControls.launchPackage(ctx, tile.packageName)
                    } else {
                        SystemControls.launchSystemAction(ctx, tile.id)
                    }
                },
                onTileLongPress = { if (!it.isMicro) focused = it },
                gutter = gutterDp.dp,
                modifier = Modifier.fillMaxWidth().testTag("home_tile_grid")
            )
            Spacer(Modifier.height(64.dp))
        }

        // 3. Edge gesture & tap zones
        EdgePanelZones(
            openPanel = openPanel,
            onPanelChange = { openPanel = it },
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        // 4. Panel overlay
        PanelSurface(
            panel = openPanel,
            onClose = { openPanel = PanelKind.NONE },
            onPinApp = { appInfo ->
                val newTile = TileModel(
                    id = "pinned_${appInfo.packageName.replace('.', '_')}",
                    label = appInfo.label.take(5).uppercase(),
                    packageName = appInfo.packageName,
                    colSpan = 2,
                    rowSpan = 2,
                    accentColor = palette[tiles.size % palette.size]
                )
                tiles.add(newTile)
            },
            onOpenPreferences = onNavigateToSettings
        )

        // 5. Focus overlay
        FocusOverlay(
            tile = focused,
            onDismiss = { focused = null },
            onUpdateTile = { updated ->
                val idx = tiles.indexOfFirst { it.id == updated.id }
                if (idx != -1) {
                    tiles[idx] = updated
                    focused = updated
                }
            }
        )
    }
}

// Helpers

private fun loadWallpaperBitmap(ctx: android.content.Context): Bitmap? {
    return try {
        val wm = WallpaperManager.getInstance(ctx)
        val d: Drawable = wm.drawable ?: return null
        drawableToBitmap(d)
    } catch (_: Throwable) {
        null
    }
}

private fun drawableToBitmap(d: Drawable): Bitmap {
    if (d is BitmapDrawable) return d.bitmap
    val w = d.intrinsicWidth.coerceAtLeast(1)
    val h = d.intrinsicHeight.coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    d.setBounds(0, 0, c.width, c.height)
    d.draw(c)
    return bmp
}

/**
 * Creates an industrial deep-space/Jupiter procedural wallpaper when system wallpaper is unavailable.
 */
private fun createCosmicWallpaper(width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Base background gradient: Deep Obsidian -> Cobalt Navy
    val bgPaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(
                AndroidColor.rgb(5, 8, 18),
                AndroidColor.rgb(19, 41, 75),
                AndroidColor.rgb(12, 16, 28)
            ),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    // Glowing Jovian atmospheric arc
    val glowPaint = Paint().apply {
        shader = RadialGradient(
            width * 0.85f, height * 0.25f, width * 0.9f,
            intArrayOf(
                AndroidColor.argb(90, 0, 229, 255),
                AndroidColor.argb(40, 27, 58, 107),
                AndroidColor.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawCircle(width * 0.85f, height * 0.25f, width * 0.9f, glowPaint)

    // Subtle star field
    val starPaint = Paint().apply {
        color = AndroidColor.argb(120, 255, 255, 255)
    }
    val random = Random(42)
    for (i in 0 until 100) {
        val x = random.nextFloat() * width
        val y = random.nextFloat() * height
        val r = random.nextFloat() * 1.8f + 0.6f
        canvas.drawCircle(x, y, r, starPaint)
    }

    return bitmap
}
