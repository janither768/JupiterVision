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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlin.math.roundToInt

/**
 * Square-Shaped Mini-App Overlay (1:1 Aspect Ratio) in Android-Settings-List Style
 */
@Composable
fun FocusOverlay(
    tile: TileModel?,
    sourceBounds: Rect?,
    galleryImages: List<Bitmap> = emptyList(),
    audioTracks: List<AppLoader.AudioTrackInfo> = emptyList(),
    onDismiss: () -> Unit,
) {
    if (tile == null || sourceBounds == null) return
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    // Strictly square-shaped mini-app
    val targetWidthPx = screenWidthPx * 0.86f
    val targetHeightPx = targetWidthPx // 1:1 square

    val targetLeftPx = (screenWidthPx - targetWidthPx) / 2f
    val origCenterY = sourceBounds.center.y
    val unclampedTop = origCenterY - (targetHeightPx / 2f)
    val marginY = with(density) { 48.dp.toPx() }
    val targetTopPx = unclampedTop.coerceIn(marginY, (screenHeightPx - targetHeightPx - marginY).coerceAtLeast(marginY))

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(tile.id) { isVisible = true }

    val ease = remember { CubicBezierEasing(0.4f, 0f, 0.2f, 1f) }
    val progress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(320, easing = ease),
        label = "focusAnim"
    )

    val currLeft = sourceBounds.left + (targetLeftPx - sourceBounds.left) * progress
    val currTop = sourceBounds.top + (targetTopPx - sourceBounds.top) * progress
    val currWidth = sourceBounds.width + (targetWidthPx - sourceBounds.width) * progress
    val currHeight = sourceBounds.height + (targetHeightPx - sourceBounds.height) * progress

    Box(modifier = Modifier.fillMaxSize()) {
        // Dim overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f * progress))
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                }
        )

        // Square-shaped Mini App Container
        Box(
            modifier = Modifier
                .offset { IntOffset(currLeft.roundToInt(), currTop.roundToInt()) }
                .size(with(density) { currWidth.toDp() }, with(density) { currHeight.toDp() })
                .clipToBounds()
                .background(tile.accentColor)
                .pointerInput(tile.id) { detectTapGestures { } }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Header: App icon + Label + Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (tile.packageName != null) {
                                    AppHistoryManager.recordAppLaunch(context, tile.packageName)
                                    SystemControls.launchPackage(context, tile.packageName)
                                } else {
                                    SystemControls.launchSystemAction(context, tile.role ?: tile.id)
                                }
                                onDismiss()
                            }
                    ) {
                        if (tile.iconBitmap != null) {
                            Image(
                                bitmap = tile.iconBitmap,
                                contentDescription = tile.label,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = tile.label.uppercase(),
                            color = Color.White.copy(alpha = 0.95f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            letterSpacing = 1.2.sp,
                            fontFamily = InterFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Launch",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Mini-App Content inside square (Android-Settings-List Style)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                    val role = (tile.role ?: tile.id).lowercase()
                    when {
                        role.contains("msg") || role.contains("messag") -> {
                            MessagesMiniApp(tile.packageName)
                        }
                        role.contains("mail") || role.contains("email") -> {
                            MailMiniApp(tile.packageName)
                        }
                        role.contains("gal") || role.contains("photo") || role.contains("image") -> {
                            DiagonalGalleryPortfolio(
                                images = galleryImages,
                                accentColor = tile.accentColor,
                                itemSize = 64.dp
                            )
                        }
                        role.contains("music") || role.contains("mus") -> {
                            RealMusicMiniApp(initialTracks = audioTracks)
                        }
                        role.contains("set") -> {
                            SettingsShortcutsMiniApp()
                        }
                        role.contains("cam") -> {
                            CameraShortcutsMiniApp()
                        }
                        else -> {
                            GenericAppMiniApp(tile)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shared Multi-Row Diagonal Gallery Portfolio
 */
@Composable
fun DiagonalGalleryPortfolio(
    images: List<Bitmap>,
    accentColor: Color,
    itemSize: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    val scrollState1 = rememberLazyListState(initialFirstVisibleItemIndex = 100)
    val scrollState2 = rememberLazyListState(initialFirstVisibleItemIndex = 112)
    val scrollState3 = rememberLazyListState(initialFirstVisibleItemIndex = 124)

    LaunchedEffect(scrollState1, images) {
        while (true) {
            delay(40)
            scrollState1.scrollBy(-0.35f)
            scrollState2.scrollBy(-0.35f)
            scrollState3.scrollBy(-0.35f)
            if (scrollState1.firstVisibleItemIndex < 5) scrollState1.scrollToItem(100)
            if (scrollState2.firstVisibleItemIndex < 5) scrollState2.scrollToItem(112)
            if (scrollState3.firstVisibleItemIndex < 5) scrollState3.scrollToItem(124)
        }
    }

    val placeholderColors = remember(accentColor) {
        listOf(
            accentColor.copy(alpha = 0.95f),
            accentColor.copy(alpha = 0.70f),
            accentColor.copy(alpha = 0.50f),
            accentColor.copy(alpha = 0.85f),
            accentColor.copy(alpha = 0.60f),
            accentColor.copy(alpha = 0.40f)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .graphicsLayer {
                    rotationZ = -15f
                    scaleX = 1.35f
                    scaleY = 1.35f
                }
        ) {
            PortfolioStripRow(images, placeholderColors, scrollState1, itemSize, offsetIndex = 0)
            PortfolioStripRow(images, placeholderColors, scrollState2, itemSize, offsetIndex = 3)
            PortfolioStripRow(images, placeholderColors, scrollState3, itemSize, offsetIndex = 6)
        }
    }
}

@Composable
private fun PortfolioStripRow(
    images: List<Bitmap>,
    placeholderColors: List<Color>,
    state: LazyListState,
    itemSize: Dp,
    offsetIndex: Int
) {
    if (images.isNotEmpty()) {
        val repeatCount = 30
        val totalItems = images.size * repeatCount

        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(totalItems) { idx ->
                val bmp = images[(idx + offsetIndex) % images.size]
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(itemSize)
                        .background(Color.Black)
                )
            }
        }
    } else {
        val totalItems = placeholderColors.size * 30
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(totalItems) { idx ->
                val col = placeholderColors[(idx + offsetIndex) % placeholderColors.size]
                Box(
                    modifier = Modifier
                        .size(itemSize)
                        .background(col)
                )
            }
        }
    }
}

/**
 * Real Interactive Music Player Mini-App in Android-Settings-List Style
 */
@Composable
private fun RealMusicMiniApp(initialTracks: List<AppLoader.AudioTrackInfo>) {
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
        // Compact Player Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { JupiterAudioPlayer.playPrevious(context) },
                enabled = hasTrack,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = if (hasTrack) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = {
                    if (hasTrack) JupiterAudioPlayer.togglePlayPause(context)
                },
                enabled = hasTrack,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    tint = if (hasTrack) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = { JupiterAudioPlayer.playNext(context) },
                enabled = hasTrack,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = if (hasTrack) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrack?.title ?: "No Track Selected",
                    color = if (hasTrack) Color.White else Color.White.copy(alpha = 0.45f),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Progress bar
        Slider(
            value = playbackState.progressFraction,
            onValueChange = { fraction -> JupiterAudioPlayer.seekTo(fraction) },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.20f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .padding(horizontal = 4.dp)
        )

        Spacer(Modifier.height(4.dp))

        // Tracks in Single-Line Settings Row Style
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No audio files found",
                    color = Color.White.copy(alpha = 0.50f),
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    val isSelected = currentTrack?.id == track.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .background(if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.25f))
                            .clickable { JupiterAudioPlayer.playTrack(context, track) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = track.title,
                            color = Color.White,
                            fontFamily = InterFontFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = track.formattedDuration,
                            color = Color.White.copy(alpha = 0.50f),
                            fontFamily = InterFontFamily,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Messages Mini-App (Android-Settings Single Line Row Style)
 */
@Composable
private fun MessagesMiniApp(packageName: String?) {
    val allNotifs by JupiterNotificationListener.notificationsFlow.collectAsState()
    val appNotifs = remember(allNotifs, packageName) {
        if (packageName != null) {
            allNotifs[packageName] ?: emptyList()
        } else {
            allNotifs.values.flatten().filter {
                it.packageName.contains("msg", ignoreCase = true) ||
                it.packageName.contains("sms", ignoreCase = true)
            }
        }
    }

    if (appNotifs.isNotEmpty()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(appNotifs, key = { it.key }) { notif ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.28f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = notif.title.ifBlank { "Message" },
                            color = Color.White,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (notif.text.isNotBlank()) {
                            Text(
                                text = notif.text,
                                color = Color.White.copy(alpha = 0.70f),
                                fontFamily = InterFontFamily,
                                fontSize = 10.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No new messages",
                color = Color.White.copy(alpha = 0.50f),
                fontFamily = InterFontFamily,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Mail Mini-App (Android-Settings Single Line Row Style)
 */
@Composable
private fun MailMiniApp(packageName: String?) {
    val allNotifs by JupiterNotificationListener.notificationsFlow.collectAsState()
    val appNotifs = remember(allNotifs, packageName) {
        if (packageName != null) {
            allNotifs[packageName] ?: emptyList()
        } else {
            allNotifs.values.flatten().filter {
                it.packageName.contains("mail", ignoreCase = true) ||
                it.packageName.contains("gmail", ignoreCase = true)
            }
        }
    }

    if (appNotifs.isNotEmpty()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(appNotifs, key = { it.key }) { notif ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.28f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = notif.title.ifBlank { "Mail" },
                            color = Color.White,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (notif.text.isNotBlank()) {
                            Text(
                                text = notif.text,
                                color = Color.White.copy(alpha = 0.70f),
                                fontFamily = InterFontFamily,
                                fontSize = 10.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No new mail",
                color = Color.White.copy(alpha = 0.50f),
                fontFamily = InterFontFamily,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Settings Shortcuts Mini-App (Android-Settings Single Line Row Style)
 */
@Composable
private fun SettingsShortcutsMiniApp() {
    val context = LocalContext.current
    val shortcuts = remember {
        listOf(
            MiniAppRowItem("Wi-Fi", Icons.Default.Wifi, Intent(Settings.ACTION_WIFI_SETTINGS)),
            MiniAppRowItem("Bluetooth", Icons.Default.Bluetooth, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)),
            MiniAppRowItem("Display", Icons.Default.BrightnessMedium, Intent(Settings.ACTION_DISPLAY_SETTINGS)),
            MiniAppRowItem("Sound", Icons.Default.VolumeUp, Intent(Settings.ACTION_SOUND_SETTINGS)),
            MiniAppRowItem("Applications", Icons.Default.Apps, Intent(Settings.ACTION_APPLICATION_SETTINGS)),
            MiniAppRowItem("Notification Access", Icons.Default.Notifications, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(shortcuts) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable {
                        try {
                            item.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(item.intent)
                        } catch (_: Throwable) {}
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontFamily = InterFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

/**
 * Camera Shortcuts Mini-App (Android-Settings Single Line Row Style)
 */
@Composable
private fun CameraShortcutsMiniApp() {
    val context = LocalContext.current
    val shortcuts = remember {
        listOf(
            MiniAppRowItem("Take Photo", Icons.Default.CameraAlt, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
            MiniAppRowItem("Record Video", Icons.Default.Videocam, Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA)),
            MiniAppRowItem("Front Camera", Icons.Default.PhotoCamera, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                putExtra("android.intent.extras.CAMERA_FACING", 1)
            })
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(shortcuts) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable {
                        try {
                            item.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(item.intent)
                        } catch (_: Throwable) {}
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontFamily = InterFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

/**
 * Generic App Mini-App (Android-Settings Single Line Row Style)
 */
@Composable
private fun GenericAppMiniApp(tile: TileModel) {
    val context = LocalContext.current
    val allNotifs by JupiterNotificationListener.notificationsFlow.collectAsState()
    val appNotifs = remember(allNotifs, tile.packageName) {
        if (tile.packageName != null) allNotifs[tile.packageName] ?: emptyList()
        else emptyList()
    }

    val shortcuts = remember(tile.packageName) {
        buildList {
            if (tile.packageName != null) {
                val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${tile.packageName}")
                }
                add(MiniAppRowItem("App Info & Permissions", Icons.Default.Apps, appDetailsIntent))
                add(MiniAppRowItem("Notification Preferences", Icons.Default.Notifications, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, tile.packageName)
                }))
            }
            add(MiniAppRowItem("System Display", Icons.Default.BrightnessMedium, Intent(Settings.ACTION_DISPLAY_SETTINGS)))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (appNotifs.isNotEmpty()) {
            items(appNotifs, key = { it.key }) { notif ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.28f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = notif.title.ifBlank { "Notification" },
                            color = Color.White,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (notif.text.isNotBlank()) {
                            Text(
                                text = notif.text,
                                color = Color.White.copy(alpha = 0.70f),
                                fontFamily = InterFontFamily,
                                fontSize = 10.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                }
            }
        }
        items(shortcuts) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable {
                        try {
                            item.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(item.intent)
                        } catch (_: Throwable) {}
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontFamily = InterFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

private data class MiniAppRowItem(val title: String, val icon: ImageVector, val intent: Intent)
