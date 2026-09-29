package com.jupiter.vision.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jupiter.vision.model.TileModel
import com.jupiter.vision.ui.theme.InterFontFamily
import com.jupiter.vision.util.AppHistoryManager
import com.jupiter.vision.util.AppLoader
import com.jupiter.vision.util.JupiterAudioPlayer
import com.jupiter.vision.util.JupiterNotificationListener
import com.jupiter.vision.util.NotificationItem
import com.jupiter.vision.util.SystemControls
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * FOCUS MODE UI OVERHAUL — 2112.16
 * - Tile expands to the mini app overlay through bezier animation over the grid.
 * - Beneath it, the tile grid dims.
 * - Header matches the tile color, with app or folder name, buttons, launch icon next to name.
 * - Rest of the expanded tile becomes mid dark grey and transparent (0.9 opacity).
 * - Panel 0: App options available, and under that notifications.
 * - Swiping right: Mini app available for that app slides in.
 * - Swiping left: Slides back to app options and notifications.
 * - Mini app at default has light color mode (clean white UIs).
 * - Touching beyond the edges sends the tile back to its original size and normal state.
 */
@Composable
fun FocusOverlay(
    tile: TileModel?,
    sourceBounds: Rect?,
    galleryImages: List<Bitmap> = emptyList(),
    audioTracks: List<AppLoader.AudioTrackInfo> = emptyList(),
    notifications: Map<String, List<NotificationItem>> = emptyMap(),
    onMigrateFolder: (TileModel) -> Unit = {},
    onDismiss: () -> Unit,
) {
    if (tile == null || sourceBounds == null) return
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    // Expanded tile target dimensions (compact, smaller, refined)
    val targetWidthPx = (screenWidthPx * 0.84f).coerceAtMost(with(density) { 360.dp.toPx() })
    val targetHeightPx = (screenHeightPx * 0.58f).coerceIn(with(density) { 330.dp.toPx() }, with(density) { 470.dp.toPx() })

    val targetCenterX = screenWidthPx / 2f
    val targetCenterY = screenHeightPx / 2f

    var isVisible by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }
    LaunchedEffect(tile.id) { isVisible = true }

    // Bezier expansion animation - GPU accelerated transform
    val bezierEase = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) }
    val progress by animateFloatAsState(
        targetValue = if (isVisible && !isClosing) 1f else 0f,
        animationSpec = tween(280, easing = bezierEase),
        label = "tileBezierExpansion",
        finishedListener = { valProgress ->
            if (isClosing && valProgress <= 0.01f) {
                onDismiss()
            }
        }
    )

    val triggerDismiss = {
        if (!isClosing) {
            isClosing = true
        }
    }

    val initialScaleX = (sourceBounds.width / targetWidthPx).coerceAtLeast(0.01f)
    val initialScaleY = (sourceBounds.height / targetHeightPx).coerceAtLeast(0.01f)
    val scaleX = initialScaleX + (1f - initialScaleX) * progress
    val scaleY = initialScaleY + (1f - initialScaleY) * progress
    val transX = (sourceBounds.center.x - targetCenterX) * (1f - progress)
    val transY = (sourceBounds.center.y - targetCenterY) * (1f - progress)

    // Navigation state between Panel 0 (Options & Notifications) and Panel 1 (Light Mini App)
    var currentPanel by remember { mutableIntStateOf(0) } // 0 = Options/Notifs, 1 = Mini App
    var dragDeltaX by remember { mutableFloatStateOf(0f) }

    val panelSlideProgress by animateFloatAsState(
        targetValue = if (currentPanel == 1) 1f else 0f,
        animationSpec = tween(260, easing = bezierEase),
        label = "panelSlideProgress"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 1. Grid beneath it dims
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f * progress))
                .pointerInput(Unit) {
                    // Touching beyond edges sends tile back to original size and normal state
                    detectTapGestures { triggerDismiss() }
                }
        )

        // 2. High-performance GPU-accelerated Matrix Overlay (zero layout lag, no border)
        Box(
            modifier = Modifier
                .size(with(density) { targetWidthPx.toDp() }, with(density) { targetHeightPx.toDp() })
                .graphicsLayer {
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                    this.translationX = transX
                    this.translationY = transY
                    this.alpha = if (progress < 0.05f) progress * 20f else 1f
                    this.clip = true
                    this.shape = RoundedCornerShape(12.dp)
                }
                .pointerInput(tile.id) {
                    detectTapGestures { /* consume tap inside tile */ }
                }
                .pointerInput(tile.id) {
                    // Full area swiping: swipe left for mini app, swipe right for options & notifications
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragDeltaX < -25f) {
                                currentPanel = 1
                            } else if (dragDeltaX > 25f) {
                                currentPanel = 0
                            }
                            dragDeltaX = 0f
                        },
                        onDragCancel = { dragDeltaX = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            dragDeltaX += dragAmount
                        }
                    )
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header gets the tile color, app or folder name, buttons, launch icon next to name
                val headerColor = if (tile.isFolder) Color(0xFF202228) else tile.accentColor

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .background(headerColor)
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Back button if in Mini App, or tile icon, app/folder name, launch icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (currentPanel == 1) {
                            IconButton(
                                onClick = { currentPanel = 0 },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        } else if (tile.iconBitmap != null) {
                            Image(
                                bitmap = tile.iconBitmap,
                                contentDescription = tile.label,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        } else {
                            Icon(
                                imageVector = if (tile.isFolder) Icons.Default.Folder else Icons.Default.Apps,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        }

                        Text(
                            text = if (tile.isFolder) "${tile.label.uppercase()} FOLDER" else tile.label.uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.1.sp,
                            fontFamily = InterFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.width(4.dp))

                        // Launch icon next to name
                        IconButton(
                            onClick = {
                                if (tile.packageName != null) {
                                    AppHistoryManager.recordAppLaunch(context, tile.packageName)
                                    SystemControls.launchPackage(context, tile.packageName)
                                } else {
                                    SystemControls.launchSystemAction(context, tile.role ?: tile.id)
                                }
                                triggerDismiss()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Launch",
                                tint = Color.White.copy(alpha = 0.90f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Center: Subtle panel dots indicator (tap or swipe anywhere to switch)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = if (currentPanel == 0) 12.dp else 5.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (currentPanel == 0) Color.White else Color.White.copy(alpha = 0.35f))
                                .clickable { currentPanel = 0 }
                        )
                        Box(
                            modifier = Modifier
                                .size(width = if (currentPanel == 1) 12.dp else 5.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (currentPanel == 1) Color.White else Color.White.copy(alpha = 0.35f))
                                .clickable { currentPanel = 1 }
                        )
                    }

                    // Right header buttons: Migrate if folder, close button
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (tile.isFolder) {
                            Surface(
                                color = Color.White.copy(alpha = 0.20f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .clickable { onMigrateFolder(tile) }
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "MIGRATE",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    fontFamily = InterFontFamily
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }

                        IconButton(
                            onClick = { triggerDismiss() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Rest of the expanded tile: mid dark grey and transparent (0.9 opacity)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xE6252731)) // Mid dark grey with 0.9 opacity
                        .clipToBounds()
                ) {
                    // Sliding layout between Panel 0 (Options & Notifications) and Panel 1 (Light Mini App)
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Panel 0: App Options & Notifications (Slides out to left when Panel 1 slides in)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = -panelSlideProgress * size.width
                                    alpha = (1f - panelSlideProgress).coerceIn(0f, 1f)
                                }
                        ) {
                            AppOptionsAndNotificationsPanel(
                                tile = tile,
                                notifications = notifications,
                                onLaunchApp = {
                                    if (tile.packageName != null) {
                                        AppHistoryManager.recordAppLaunch(context, tile.packageName)
                                        SystemControls.launchPackage(context, tile.packageName)
                                    } else {
                                        SystemControls.launchSystemAction(context, tile.role ?: tile.id)
                                    }
                                    triggerDismiss()
                                },
                                onOpenSettings = {
                                    tile.packageName?.let { pkg ->
                                        launchSystemSettings(context, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
                                    } ?: launchSystemSettings(context, Settings.ACTION_SETTINGS)
                                },
                                onClearCache = {
                                    tile.packageName?.let { pkg ->
                                        launchSystemSettings(context, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
                                    } ?: launchSystemSettings(context, Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                                }
                            )
                        }

                        // Panel 1: Mini App (Slides in from right when swiped left; default light color mode / white UIs)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = (1f - panelSlideProgress) * size.width
                                    alpha = panelSlideProgress.coerceIn(0f, 1f)
                                }
                        ) {
                            LightMiniAppContainer(
                                tile = tile,
                                galleryImages = galleryImages,
                                audioTracks = audioTracks
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Panel 0: The options available for app is listed, and under that notifications.
 */
@Composable
private fun AppOptionsAndNotificationsPanel(
    tile: TileModel,
    notifications: Map<String, List<NotificationItem>>,
    onLaunchApp: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearCache: () -> Unit
) {
    val context = LocalContext.current

    // Notifications relevant to this app or folder
    val appNotifs = remember(notifications, tile) {
        if (tile.isFolder) {
            val folderPkgs = tile.folderApps.map { it.packageName }.toSet()
            notifications.filterKeys { it in folderPkgs }.values.flatten()
        } else if (tile.packageName != null) {
            notifications[tile.packageName] ?: emptyList()
        } else {
            val role = (tile.role ?: tile.id).lowercase()
            notifications.entries.firstOrNull {
                it.key.contains(role, ignoreCase = true)
            }?.value ?: emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Section 1: Options available for app
        Text(
            text = "APP OPTIONS",
            color = Color.White.copy(alpha = 0.65f),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FocusOptionCard(
                title = "Launch",
                subtitle = "Open App",
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                onClick = onLaunchApp,
                modifier = Modifier.weight(1f)
            )

            FocusOptionCard(
                title = "Details",
                subtitle = "App Info",
                icon = Icons.Default.Info,
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f)
            )

            FocusOptionCard(
                title = "Manage",
                subtitle = "Storage",
                icon = Icons.Default.CleaningServices,
                onClick = onClearCache,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))

        // Section 2: Under that notifications
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "NOTIFICATIONS",
                color = Color.White.copy(alpha = 0.65f),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp
            )

            if (appNotifs.isNotEmpty()) {
                Surface(
                    color = Color(0xFFFF3B30),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "${appNotifs.size}",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (appNotifs.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(appNotifs, key = { it.key }) { notif ->
                        NotificationItemDarkRow(
                            notif = notif,
                            onClick = {
                                AppHistoryManager.recordAppLaunch(context, notif.packageName)
                                SystemControls.launchPackage(context, notif.packageName)
                            }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x33101016))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "No active notifications",
                            color = Color.White.copy(alpha = 0.50f),
                            fontFamily = InterFontFamily,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Panel 1: Mini App at default has LIGHT COLOR MODE (white UIs).
 */
@Composable
private fun LightMiniAppContainer(
    tile: TileModel,
    galleryImages: List<Bitmap>,
    audioTracks: List<AppLoader.AudioTrackInfo>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F5F8)) // Clean white UI background
    ) {
        // Mini app content in light mode (clean white UI cards, area swipeable)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            val role = (tile.role ?: tile.id).lowercase()
            when {
                tile.isFolder || role == "folder" -> {
                    LightFolderMiniApp(tile = tile)
                }
                role.contains("gal") || role.contains("photo") || role.contains("image") -> {
                    LightGalleryMiniApp(images = galleryImages, accentColor = tile.accentColor)
                }
                role.contains("music") || role.contains("mus") -> {
                    LightMusicMiniApp(initialTracks = audioTracks, accentColor = tile.accentColor)
                }
                role.contains("msg") || role.contains("messag") -> {
                    LightMessagesMiniApp()
                }
                role.contains("mail") || role.contains("email") -> {
                    LightMailMiniApp()
                }
                role.contains("set") -> {
                    LightSettingsMiniApp()
                }
                role.contains("cam") -> {
                    LightCameraMiniApp()
                }
                else -> {
                    LightGenericMiniApp(tile = tile)
                }
            }
        }
    }
}

/**
 * Focus Option Card in Dark Theme for Panel 0
 */
@Composable
private fun FocusOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0x66181822),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.90f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title.uppercase(),
                color = Color.White,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.60f),
                fontFamily = InterFontFamily,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Notification Item Row in Dark Theme for Panel 0
 */
@Composable
private fun NotificationItemDarkRow(
    notif: NotificationItem,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0x66181822),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = Color(0xFFFF3B30),
                modifier = Modifier
                    .size(14.dp)
                    .padding(top = 2.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (notif.title.isNotBlank()) {
                    Text(
                        text = notif.title,
                        color = Color.White,
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (notif.text.isNotBlank()) {
                    Text(
                        text = notif.text,
                        color = Color.White.copy(alpha = 0.85f),
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/* =========================================================================
 * LIGHT COLOR MODE MINI APPS (WHITE UIs)
 * ========================================================================= */

/**
 * Light Mode Music Mini-App (White UIs)
 */
@Composable
private fun LightMusicMiniApp(
    initialTracks: List<AppLoader.AudioTrackInfo>,
    accentColor: Color
) {
    val context = LocalContext.current
    var tracks by remember { mutableStateOf(initialTracks) }
    val playbackState by JupiterAudioPlayer.playbackState.collectAsState()

    LaunchedEffect(Unit) {
        if (tracks.isEmpty()) {
            tracks = AppLoader.queryDeviceAudio(context)
        }
        JupiterAudioPlayer.setPlaylist(tracks)
    }

    val currentTrack = playbackState.currentTrack ?: tracks.firstOrNull()
    val hasTrack = currentTrack != null

    Column(modifier = Modifier.fillMaxSize()) {
        // Player Control Card in White
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0x1F000000)),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.20f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTrack?.title ?: "No Track Playing",
                            color = Color(0xFF16181D),
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentTrack?.artist ?: "Local Audio",
                            color = Color(0xFF6B7280),
                            fontFamily = InterFontFamily,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }

                // Progress Bar
                Slider(
                    value = playbackState.progressFraction,
                    onValueChange = { fraction -> JupiterAudioPlayer.seekTo(fraction) },
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = Color(0x26000000)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                )

                // Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { JupiterAudioPlayer.playPrevious(context) },
                        enabled = hasTrack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = if (hasTrack) Color(0xFF22242B) else Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    Surface(
                        color = accentColor,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .size(36.dp)
                            .clickable(enabled = hasTrack) {
                                JupiterAudioPlayer.togglePlayPause(context)
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    IconButton(
                        onClick = { JupiterAudioPlayer.playNext(context) },
                        enabled = hasTrack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = if (hasTrack) Color(0xFF22242B) else Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Tracks List
        Text(
            text = "PLAYLIST",
            color = Color(0xFF6B7280),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .border(1.dp, Color(0x1F000000), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No audio tracks detected",
                    color = Color(0xFF6B7280),
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    val isSelected = currentTrack?.id == track.id
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) accentColor.copy(alpha = 0.12f) else Color.White
                        ),
                        border = BorderStroke(1.dp, if (isSelected) accentColor.copy(alpha = 0.5f) else Color(0x1A000000)),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { JupiterAudioPlayer.playTrack(context, track) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (isSelected) accentColor else Color(0xFF6B7280),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = track.title,
                                color = if (isSelected) accentColor else Color(0xFF16181D),
                                fontFamily = InterFontFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Light Mode Gallery Mini-App (White UIs)
 */
@Composable
private fun LightGalleryMiniApp(
    images: List<Bitmap>,
    accentColor: Color
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0x1F000000)),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "GALLERY PHOTOS",
                        color = Color(0xFF16181D),
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${images.size} items detected",
                        color = Color(0xFF6B7280),
                        fontFamily = InterFontFamily,
                        fontSize = 10.sp
                    )
                }

                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            type = "image/*"
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try { context.startActivity(intent) } catch (_: Throwable) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("OPEN ALL", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (images.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(images) { bmp ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0x1F000000)),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .border(1.dp, Color(0x1F000000), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No images found on device storage",
                    color = Color(0xFF6B7280),
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Light Mode Messages Mini-App (White UIs)
 */
@Composable
private fun LightMessagesMiniApp() {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0x1F000000)),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "QUICK COMPOSE",
                    color = Color(0xFF16181D),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Start new SMS conversation or send chat message to contacts.",
                    color = Color(0xFF6B7280),
                    fontFamily = InterFontFamily,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("smsto:")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try { context.startActivity(intent) } catch (_: Throwable) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22242B)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("NEW CONVERSATION", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Light Mode Mail Mini-App (White UIs)
 */
@Composable
private fun LightMailMiniApp() {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0x1F000000)),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "COMPOSE EMAIL",
                    color = Color(0xFF16181D),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Open system mail client to compose a new message.",
                    color = Color(0xFF6B7280),
                    fontFamily = InterFontFamily,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try { context.startActivity(intent) } catch (_: Throwable) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22242B)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("NEW EMAIL DRAFT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Light Mode Settings Mini-App (White UIs)
 */
@Composable
private fun LightSettingsMiniApp() {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LightSettingActionCard(
            title = "Wi-Fi & Internet",
            subtitle = "Manage network connections",
            icon = Icons.Default.Wifi,
            onClick = { launchSystemSettings(context, Settings.ACTION_WIFI_SETTINGS) }
        )
        LightSettingActionCard(
            title = "Bluetooth Devices",
            subtitle = "Pair headphones, speakers & hardware",
            icon = Icons.Default.Bluetooth,
            onClick = { launchSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS) }
        )
        LightSettingActionCard(
            title = "Display & Brightness",
            subtitle = "Wallpaper, dark mode & font sizing",
            icon = Icons.Default.BrightnessMedium,
            onClick = { launchSystemSettings(context, Settings.ACTION_DISPLAY_SETTINGS) }
        )
        LightSettingActionCard(
            title = "Sound & Volume",
            subtitle = "Media, alarms and ringtone levels",
            icon = Icons.Default.VolumeUp,
            onClick = { launchSystemSettings(context, Settings.ACTION_SOUND_SETTINGS) }
        )
        LightSettingActionCard(
            title = "All Installed Applications",
            subtitle = "Permissions, storage & notifications",
            icon = Icons.Default.Apps,
            onClick = { launchSystemSettings(context, Settings.ACTION_APPLICATION_SETTINGS) }
        )
    }
}

@Composable
private fun LightSettingActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0x1F000000)),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF22242B),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color(0xFF16181D),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF6B7280),
                    fontFamily = InterFontFamily,
                    fontSize = 9.sp
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.LightGray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Light Mode Camera Mini-App (White UIs)
 */
@Composable
private fun LightCameraMiniApp() {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0x1F000000)),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try { context.startActivity(intent) } catch (_: Throwable) {}
                }
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color(0xFF22242B), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("STILL CAMERA", color = Color(0xFF16181D), fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Capture high-resolution still photos", color = Color(0xFF6B7280), fontFamily = InterFontFamily, fontSize = 10.sp)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0x1F000000)),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val intent = Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try { context.startActivity(intent) } catch (_: Throwable) {}
                }
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF22242B), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("VIDEO RECORDING", color = Color(0xFF16181D), fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Record videos with hardware camera", color = Color(0xFF6B7280), fontFamily = InterFontFamily, fontSize = 10.sp)
                }
            }
        }
    }
}

/**
 * Light Mode Folder Mini-App (White UIs)
 */
@Composable
private fun LightFolderMiniApp(tile: TileModel) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0x1F000000)),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF22242B), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "${tile.label.uppercase()} APPS",
                        color = Color(0xFF16181D),
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${tile.folderApps.size} apps inside folder",
                        color = Color(0xFF6B7280),
                        fontFamily = InterFontFamily,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(tile.folderApps) { app ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0x1F000000)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            AppHistoryManager.recordAppLaunch(context, app.packageName)
                            SystemControls.launchPackage(context, app.packageName)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (app.iconBitmap != null && !app.iconBitmap.isRecycled) {
                            Image(
                                bitmap = app.iconBitmap.asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            Surface(
                                color = Color(0xFFE5E7EB),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = app.label.take(1).uppercase(),
                                        color = Color(0xFF22242B),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = app.label,
                            color = Color(0xFF16181D),
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Light Mode Generic App Mini-App (White UIs)
 */
@Composable
private fun LightGenericMiniApp(tile: TileModel) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0x1F000000)),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = tile.label.uppercase(),
                    color = Color(0xFF16181D),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = tile.packageName ?: "System Application Component",
                    color = Color(0xFF6B7280),
                    fontFamily = InterFontFamily,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (tile.packageName != null) {
                            AppHistoryManager.recordAppLaunch(context, tile.packageName)
                            SystemControls.launchPackage(context, tile.packageName)
                        } else {
                            SystemControls.launchSystemAction(context, tile.role ?: tile.id)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = tile.accentColor),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("LAUNCH APP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun launchSystemSettings(context: Context, action: String, packageName: String? = null) {
    try {
        val intent = if (packageName != null) {
            Intent(action).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
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
