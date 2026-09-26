package com.jupiter.vision.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jupiter.vision.model.AppInfo
import com.jupiter.vision.ui.theme.InterFontFamily
import com.jupiter.vision.util.AppHistoryManager
import com.jupiter.vision.util.AppLoader
import com.jupiter.vision.util.JupiterNotificationListener
import com.jupiter.vision.util.SystemControls
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class PanelKind { NONE, CONTROLS, APPS }

/**
 * Real-time continuous gesture edge tracking for Control Center & App Lister:
 * Directly tracks finger movement in real-time from edge swipe, sliding smoothly in and out.
 */
@Composable
fun EdgePanelZones(
    openPanel: PanelKind,
    onPanelChange: (PanelKind) -> Unit,
    onProgressUpdate: ((PanelKind, Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val maxDragPx = screenWidthPx * 0.85f

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(42.dp)
    ) {
        // Upper 50% — Controls Zone (Real-time swipe tracking)
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .pointerInput(openPanel) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalDx = 0f
                        var totalDy = 0f
                        var isDragging = false
                        val pointerId = down.id
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            velocityTracker.addPosition(change.uptimeMillis, change.position)

                            if (change.isConsumed) break

                            val drag = change.positionChange()
                            totalDx += drag.x
                            totalDy += drag.y

                            if (!isDragging && (totalDx < -10f || abs(totalDy) > 10f)) {
                                if (totalDx < -10f && abs(totalDx) > abs(totalDy) * 0.7f) {
                                    isDragging = true
                                    onPanelChange(PanelKind.CONTROLS)
                                }
                            }

                            if (isDragging) {
                                change.consume()
                                val fraction = (-totalDx / maxDragPx).coerceIn(0f, 1f)
                                onProgressUpdate?.invoke(PanelKind.CONTROLS, fraction)
                            }

                            if (!change.pressed) {
                                if (isDragging) {
                                    val vel = velocityTracker.calculateVelocity().x
                                    val fraction = (-totalDx / maxDragPx).coerceIn(0f, 1f)
                                    if (vel < -400f || fraction > 0.30f || totalDx < -60f) {
                                        onPanelChange(PanelKind.CONTROLS)
                                        onProgressUpdate?.invoke(PanelKind.CONTROLS, 1f)
                                    } else {
                                        onPanelChange(PanelKind.NONE)
                                        onProgressUpdate?.invoke(PanelKind.CONTROLS, 0f)
                                    }
                                } else if (abs(totalDx) < 14f && abs(totalDy) < 14f) {
                                    onPanelChange(if (openPanel == PanelKind.CONTROLS) PanelKind.NONE else PanelKind.CONTROLS)
                                }
                                break
                            }
                        }
                    }
                },
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(48.dp)
                    .background(Color.White.copy(alpha = 0.35f))
            )
        }

        // Lower 50% — App Lister Zone (Real-time swipe tracking)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .pointerInput(openPanel) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalDx = 0f
                        var totalDy = 0f
                        var isDragging = false
                        val pointerId = down.id
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            velocityTracker.addPosition(change.uptimeMillis, change.position)

                            if (change.isConsumed) break

                            val drag = change.positionChange()
                            totalDx += drag.x
                            totalDy += drag.y

                            if (!isDragging && (totalDx < -10f || abs(totalDy) > 10f)) {
                                if (totalDx < -10f && abs(totalDx) > abs(totalDy) * 0.7f) {
                                    isDragging = true
                                    onPanelChange(PanelKind.APPS)
                                }
                            }

                            if (isDragging) {
                                change.consume()
                            }

                            if (!change.pressed) {
                                if (isDragging) {
                                    val vel = velocityTracker.calculateVelocity().x
                                    val fraction = (-totalDx / maxDragPx).coerceIn(0f, 1f)
                                    if (vel < -400f || fraction > 0.30f || totalDx < -60f) {
                                        onPanelChange(PanelKind.APPS)
                                    } else {
                                        onPanelChange(PanelKind.NONE)
                                    }
                                } else if (abs(totalDx) < 14f && abs(totalDy) < 14f) {
                                    onPanelChange(if (openPanel == PanelKind.APPS) PanelKind.NONE else PanelKind.APPS)
                                }
                                break
                            }
                        }
                    }
                },
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(48.dp)
                    .background(Color.White.copy(alpha = 0.35f))
            )
        }
    }
}

@Composable
fun PanelSurface(
    panel: PanelKind,
    onClose: () -> Unit,
    onSetDeviceWallpaper: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val coroutineScope = rememberCoroutineScope()

    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var recentAppPackages by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    val bezierEase = remember { CubicBezierEasing(0.16f, 1f, 0.3f, 1f) }
    val slideAnim = remember { Animatable(0f) }
    var activeDisplayPanel by remember { mutableStateOf(panel) }

    LaunchedEffect(panel) {
        if (panel != PanelKind.NONE) {
            activeDisplayPanel = panel
            if (panel == PanelKind.APPS) {
                installedApps = AppLoader.loadInstalledApps(context)
                recentAppPackages = AppHistoryManager.getRecentApps(context, limit = 5)
            }
            slideAnim.animateTo(1f, tween(300, easing = bezierEase))
        } else {
            slideAnim.animateTo(0f, tween(240, easing = bezierEase))
            activeDisplayPanel = PanelKind.NONE
        }
    }

    if (activeDisplayPanel == PanelKind.NONE && slideAnim.value <= 0.001f) return

    val currentFraction = slideAnim.value
    val offsetX = ((1f - currentFraction) * screenWidthPx).roundToInt()

    // Full screen overlay with interactive drag-to-dismiss tracking
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = currentFraction * 0.88f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    if (delta > 0) { // Dragging right towards closed
                        coroutineScope.launch {
                            val newFraction = (slideAnim.value - (delta / screenWidthPx)).coerceIn(0f, 1f)
                            slideAnim.snapTo(newFraction)
                        }
                    } else if (delta < 0) { // Dragging left towards open
                        coroutineScope.launch {
                            val newFraction = (slideAnim.value - (delta / screenWidthPx)).coerceIn(0f, 1f)
                            slideAnim.snapTo(newFraction)
                        }
                    }
                },
                onDragStopped = { velocity ->
                    if (velocity > 300f || slideAnim.value < 0.65f) {
                        onClose()
                    } else {
                        coroutineScope.launch {
                            slideAnim.animateTo(1f, tween(200, easing = bezierEase))
                        }
                    }
                }
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX, 0) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* consume clicks inside panel */ }
        ) {
            when (activeDisplayPanel) {
                PanelKind.CONTROLS -> ControlsCenterPanel(
                    onClose = onClose,
                    onSetDeviceWallpaper = onSetDeviceWallpaper
                )
                PanelKind.APPS -> AppListerPanel(
                    apps = installedApps,
                    recentPackages = recentAppPackages,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    onClose = onClose
                )
                PanelKind.NONE -> {}
            }
        }
    }
}

/**
 * Control Center — Minimalist Pure White Industrial Style:
 * "The control center, should not be cyber high tech. Never do that. It follow the style I've been designing.
 * Quick setting buttons like WiFi, Bluetooth, and all other icons shouldn't have boxes. Only the icon, and label. Only white colored."
 */
@Composable
private fun ControlsCenterPanel(
    onClose: () -> Unit,
    onSetDeviceWallpaper: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var brightness by remember { mutableFloatStateOf(0.70f) }
    var volumeLevel by remember { mutableFloatStateOf(0.60f) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    LaunchedEffect(Unit) {
        if (audioManager != null) {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            if (max > 0) volumeLevel = (cur / max).coerceIn(0f, 1f)
        }
    }

    val isNotifActive by JupiterNotificationListener.isServiceConnected.collectAsState()
    val isNotifGranted = remember(isNotifActive) {
        JupiterNotificationListener.isNotificationAccessGranted(context)
    }

    val quickSettings = remember(isNotifGranted) {
        listOf(
            QuickSettingItem("Wi-Fi", Icons.Default.Wifi) {
                launchIntent(context, Intent(Settings.ACTION_WIFI_SETTINGS))
            },
            QuickSettingItem("Bluetooth", Icons.Default.Bluetooth) {
                launchIntent(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            },
            QuickSettingItem("Flashlight", Icons.Default.FlashlightOn) {
                SystemControls.toggleTorch(context)
            },
            QuickSettingItem("Do Not Disturb", Icons.Default.DoNotDisturbOn) {
                launchIntent(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            },
            QuickSettingItem("Airplane", Icons.Default.AirplanemodeActive) {
                launchIntent(context, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            },
            QuickSettingItem("Mobile Data", Icons.Default.NetworkCell) {
                launchIntent(context, Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))
            },
            QuickSettingItem("Auto-Rotate", Icons.Default.ScreenRotation) {
                launchIntent(context, Intent(Settings.ACTION_DISPLAY_SETTINGS))
            },
            QuickSettingItem("Battery Saver", Icons.Default.BatterySaver) {
                launchIntent(context, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            },
            QuickSettingItem("Notifications", Icons.Default.NotificationsActive) {
                JupiterNotificationListener.openNotificationAccessSettings(context)
            },
            QuickSettingItem("Camera", Icons.Default.Videocam) {
                launchIntent(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            },
            QuickSettingItem("Wallpaper", Icons.Default.Wallpaper) {
                onSetDeviceWallpaper?.invoke()
            },
            QuickSettingItem("Settings", Icons.Default.Settings) {
                launchIntent(context, Intent(Settings.ACTION_SETTINGS))
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // Clean Minimal Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONTROLS",
                color = Color.White.copy(alpha = 0.95f),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 2.sp
            )
            Text(
                text = "SYSTEM",
                color = Color.White.copy(alpha = 0.50f),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.height(20.dp))

        // Clean Sliders (no boxes, minimalist white lines)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Brightness
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.BrightnessLow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.70f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White.copy(alpha = 0.90f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.20f)
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Default.BrightnessHigh,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Volume
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.VolumeDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.70f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Slider(
                    value = volumeLevel,
                    onValueChange = { vol ->
                        volumeLevel = vol
                        audioManager?.let { am ->
                            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val target = (vol * max).roundToInt()
                            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                        }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White.copy(alpha = 0.90f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.20f)
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Quick settings items: NO BOXES, only pure white icon and white label in clean grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(quickSettings) { item ->
                ControlPureItem(item = item)
            }
        }
    }
}

/**
 * Pure minimalist quick setting item — No bounding box, only pure white icon & text.
 */
@Composable
private fun ControlPureItem(item: QuickSettingItem) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { item.onClick() }
            .padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (isPressed) Color.White else Color.White.copy(alpha = 0.88f),
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.label,
            color = if (isPressed) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

private data class QuickSettingItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

private fun launchIntent(context: Context, intent: Intent) {
    try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Throwable) {
        try {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        } catch (_: Throwable) {}
    }
}

/**
 * App Lister Panel with Top 5 Recently Used Apps
 */
@Composable
private fun AppListerPanel(
    apps: List<AppInfo>,
    recentPackages: List<String>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val filtered = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    val recentApps = remember(apps, recentPackages) {
        val appMap = apps.associateBy { it.packageName }
        recentPackages.mapNotNull { appMap[it] }.take(5)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 14.dp)
    ) {
        // Line-style search bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .drawBehind {
                    drawLine(
                        color = Color.White.copy(alpha = 0.40f),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(bottom = 8.dp)
        ) {
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(Color.White),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search all applications...",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 13.sp,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    innerTextField()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Top 5 Recently Used Apps
        if (recentApps.isNotEmpty() && searchQuery.isBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "RECENT",
                    color = Color.White.copy(alpha = 0.60f),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(recentApps, key = { "recent_${it.packageName}" }) { app ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(56.dp)
                                .clickable {
                                    AppHistoryManager.recordAppLaunch(context, app.packageName)
                                    SystemControls.launchPackage(context, app.packageName)
                                    onClose()
                                }
                        ) {
                            if (app.iconImageBitmap != null) {
                                Image(
                                    bitmap = app.iconImageBitmap,
                                    contentDescription = app.label,
                                    modifier = Modifier.size(36.dp)
                                )
                            } else if (app.iconBitmap != null) {
                                Image(
                                    bitmap = app.iconBitmap.asImageBitmap(),
                                    contentDescription = app.label,
                                    modifier = Modifier.size(36.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color.White.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = app.label.take(1).uppercase(),
                                        color = Color.White,
                                        fontFamily = InterFontFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = app.label,
                                color = Color.White.copy(alpha = 0.85f),
                                fontFamily = InterFontFamily,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.10f))
            )
        }

        Spacer(Modifier.height(6.dp))

        // Vertically scrolling list of all installed launchable apps
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable {
                            AppHistoryManager.recordAppLaunch(context, app.packageName)
                            SystemControls.launchPackage(context, app.packageName)
                            onClose()
                        }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (app.iconImageBitmap != null) {
                        Image(
                            bitmap = app.iconImageBitmap,
                            contentDescription = app.label,
                            modifier = Modifier.size(34.dp)
                        )
                    } else if (app.iconBitmap != null) {
                        Image(
                            bitmap = app.iconBitmap.asImageBitmap(),
                            contentDescription = app.label,
                            modifier = Modifier.size(34.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = app.label.take(1).uppercase(),
                                color = Color.White.copy(alpha = 0.90f),
                                fontFamily = InterFontFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Text(
                        text = app.label,
                        color = Color.White.copy(alpha = 0.90f),
                        fontFamily = InterFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
