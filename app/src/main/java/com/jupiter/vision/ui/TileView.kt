package com.jupiter.vision.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jupiter.vision.model.TileMode
import com.jupiter.vision.model.TileModel
import com.jupiter.vision.ui.theme.InterFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TileView(
    tile: TileModel,
    contentMode: TileMode,
    wallpaper: ImageBitmap?,
    atmosphereFrames: List<ImageBitmap>? = null,
    atmosphereIndex: Int = 0,
    tileOffsetX: Int,
    tileOffsetY: Int,
    gridX: Int = 0,
    gridY: Int = 0,
    isFocusActive: Boolean = false,
    galleryImages: List<Bitmap> = emptyList(),
    onClick: () -> Unit,
    onOpenMiniApp: () -> Unit,
    onOpenProperties: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val ease = remember { CubicBezierEasing(0.4f, 0f, 0.2f, 1f) }
    val coroutineScope = rememberCoroutineScope()

    // Tap feedback & Held gesture tracking states
    var isPressed by remember { mutableStateOf(false) }
    var isHeld by remember { mutableStateOf(false) }
    var dragDeltaX by remember { mutableFloatStateOf(0f) }
    val gestureThresholdPx = with(density) { 55.dp.toPx() }

    // Content mode transition
    val transition = updateTransition(targetState = contentMode, label = "mode")
    val contentModeScale by transition.animateFloat(
        transitionSpec = { tween(320, easing = ease) },
        label = "scale"
    ) { mode -> if (mode == TileMode.CONTENT) 1f else 0.96f }

    val isTransparentTile = tile.isInvisible

    // Zoom pulse: "every tile pulses except 0.5x0.5 micro tiles. 2x2 and larger all pulse, each on its own phase."
    val canPulse = !isFocusActive && !tile.isMicro && contentMode == TileMode.ICON
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScaleRaw by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseScale = if (canPulse) pulseScaleRaw else 1.0f

    // Interactive scale & alpha from tap feedback & held gesture
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed || isHeld) 0.95f else 1.0f,
        animationSpec = tween(140, easing = ease),
        label = "pressScale"
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed || isHeld) 0.88f else 1.0f,
        animationSpec = tween(140, easing = ease),
        label = "pressAlpha"
    )

    val currentAtmosphere = if (!atmosphereFrames.isNullOrEmpty()) {
        atmosphereFrames[atmosphereIndex % atmosphereFrames.size]
    } else null

    Box(
        modifier = modifier
            .clipToBounds()
            .then(
                if (!isTransparentTile) {
                    Modifier.drawWithCache {
                        val tileAccent = tile.accentColor
                        val glossBrush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color.White.copy(alpha = 0.02f),
                                Color.Transparent
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width * 0.75f, size.height * 0.75f)
                        )
                        val vignetteBrush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                tileAccent.copy(alpha = 0.25f),
                                tileAccent
                            ),
                            center = Offset(size.width, 0f),
                            radius = size.maxDimension * 0.92f
                        )

                        onDrawBehind {
                            // 1. Base vibrant saturated color
                            drawRect(tileAccent)

                            // 2. Wallpaper slice with Multiply Blend Mode (no washed out dilution)
                            if (wallpaper != null) {
                                val clampedOffsetX = tileOffsetX.coerceIn(
                                    0,
                                    (wallpaper.width - size.width.toInt()).coerceAtLeast(0)
                                )
                                val clampedOffsetY = tileOffsetY.coerceIn(
                                    0,
                                    (wallpaper.height - size.height.toInt()).coerceAtLeast(0)
                                )

                                clipRect(0f, 0f, size.width, size.height) {
                                    scale(
                                        scale = pulseScale,
                                        pivot = Offset(size.width * 0.5f, size.height * 0.5f)
                                    ) {
                                        drawImage(
                                            image = wallpaper,
                                            dstOffset = IntOffset(-clampedOffsetX, -clampedOffsetY),
                                            alpha = 0.60f,
                                            blendMode = BlendMode.Multiply, // Multiply blend preserves vibrant base
                                            filterQuality = FilterQuality.Low
                                        )
                                    }
                                }
                            }

                            // 3. Shared Atmospheric Pre-rendered asset overlay
                            if (currentAtmosphere != null) {
                                val driftX = (gridX * 23) % 180
                                val driftY = (gridY * 31) % 240
                                val atmoOffsetX = (tileOffsetX + driftX).coerceIn(
                                    0,
                                    (currentAtmosphere.width - size.width.toInt()).coerceAtLeast(0)
                                )
                                val atmoOffsetY = (tileOffsetY + driftY).coerceIn(
                                    0,
                                    (currentAtmosphere.height - size.height.toInt()).coerceAtLeast(0)
                                )

                                clipRect(0f, 0f, size.width, size.height) {
                                    drawImage(
                                        image = currentAtmosphere,
                                        dstOffset = IntOffset(-atmoOffsetX, -atmoOffsetY),
                                        alpha = 0.35f,
                                        filterQuality = FilterQuality.Low
                                    )
                                }
                            }

                            // 4. Corner vignette & reflection gloss
                            drawRect(brush = vignetteBrush)
                            drawRect(brush = glossBrush)
                        }
                    }
                } else Modifier
            )
    ) {
        // Pointer Input implementing Tap Feedback + Held Gesture Model (Slide Left / Slide Right)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val baseScale = if (isTransparentTile) 1f else contentModeScale
                    scaleX = baseScale * pressScale
                    scaleY = baseScale * pressScale
                    alpha = pressAlpha
                }
                .pointerInput(tile.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        isHeld = false
                        dragDeltaX = 0f
                        val pointerId = down.id
                        var holdTriggered = false

                        // Start 1000ms timer for Held State
                        val timerJob = coroutineScope.launch {
                            delay(1000L)
                            if (isPressed) {
                                isHeld = true
                                holdTriggered = true
                            }
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                            if (change.isConsumed) break

                            if (isHeld) {
                                val drag = change.positionChange()
                                dragDeltaX += drag.x
                                change.consume()
                            } else {
                                val drag = change.positionChange()
                                if (abs(drag.x) > 12f || abs(drag.y) > 12f) {
                                    // Scrolled away before hold -> cancel tap state
                                    timerJob.cancel()
                                    isPressed = false
                                }
                            }

                            if (!change.pressed) {
                                timerJob.cancel()
                                if (isHeld) {
                                    // Held state gesture resolve:
                                    // Slide RIGHT -> Mini-App
                                    // Slide LEFT -> Tile Properties
                                    if (dragDeltaX > gestureThresholdPx) {
                                        onOpenMiniApp()
                                    } else if (dragDeltaX < -gestureThresholdPx) {
                                        onOpenProperties()
                                    }
                                } else if (isPressed && !holdTriggered) {
                                    onClick()
                                }
                                isPressed = false
                                isHeld = false
                                dragDeltaX = 0f
                                break
                            }
                        }
                    }
                }
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = contentMode,
                transitionSpec = {
                    if (targetState == TileMode.CONTENT) {
                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(320, easing = ease)
                        ) + fadeIn(animationSpec = tween(220))).togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> -fullWidth },
                                animationSpec = tween(320, easing = ease)
                            ) + fadeOut(animationSpec = tween(220))
                        )
                    } else {
                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> -fullWidth },
                            animationSpec = tween(320, easing = ease)
                        ) + fadeIn(animationSpec = tween(220))).togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> fullWidth },
                                animationSpec = tween(320, easing = ease)
                            ) + fadeOut(animationSpec = tween(220))
                        )
                    }
                },
                label = "mode_reveal",
                modifier = Modifier.fillMaxSize()
            ) { mode ->
                when (mode) {
                    TileMode.ICON -> IconModeContent(tile)
                    TileMode.CONTENT -> ContentModeContent(tile, galleryImages)
                }
            }

            // Held State Indicators:
            // Slide RIGHT indicator arrow (reveals on finger moving right)
            if (isHeld && dragDeltaX > 8f) {
                val rightProgress = (dragDeltaX / gestureThresholdPx).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = (8 * (1f - rightProgress)).dp)
                        .size(28.dp)
                        .graphicsLayer { alpha = rightProgress }
                        .clip(RoundedCornerShape(0.dp))
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Mini-App",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Slide LEFT indicator arrow (reveals on finger moving left)
            if (isHeld && dragDeltaX < -8f) {
                val leftProgress = (-dragDeltaX / gestureThresholdPx).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = (8 * (1f - leftProgress)).dp)
                        .size(28.dp)
                        .graphicsLayer { alpha = leftProgress }
                        .clip(RoundedCornerShape(0.dp))
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Properties",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Live Activity / Notification Badge
        if (!isTransparentTile && contentMode == TileMode.ICON && tile.hasActivity && !tile.isMicro) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(8.dp)
                    .clip(RoundedCornerShape(0.dp))
                    .drawWithCache {
                        onDrawBehind {
                            drawRect(Color(0xFFFF3B30))
                        }
                    }
            )
        }
    }
}

@Composable
private fun IconModeContent(tile: TileModel) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        val iconSize = when {
            tile.isMacro || (tile.colSpan >= 4 && tile.rowSpan >= 4) -> 64.dp
            tile.colSpan >= 4 && tile.rowSpan >= 2 -> 46.dp
            tile.colSpan == 2 && tile.rowSpan >= 4 -> 46.dp
            tile.colSpan == 2 && tile.rowSpan == 2 -> 36.dp
            tile.isMicro -> 18.dp
            else -> 30.dp
        }

        if (tile.iconBitmap != null) {
            Image(
                bitmap = tile.iconBitmap,
                contentDescription = tile.label,
                modifier = Modifier
                    .size(iconSize)
                    .then(
                        if (!tile.isMicro && !tile.isMacro && tile.colSpan <= 2 && tile.rowSpan <= 2) {
                            Modifier.padding(bottom = 8.dp)
                        } else Modifier
                    )
            )
        } else {
            val systemIcon = getSystemTileIcon(tile.role ?: tile.id)
            if (systemIcon != null) {
                Icon(
                    imageVector = systemIcon,
                    contentDescription = tile.label,
                    tint = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier
                        .size(iconSize)
                        .then(
                            if (!tile.isMicro && !tile.isMacro && tile.colSpan <= 2 && tile.rowSpan <= 2) {
                                Modifier.padding(bottom = 8.dp)
                            } else Modifier
                        )
                )
            } else {
                Text(
                    text = if (tile.isMicro) tile.label.take(1) else tile.label.take(2).uppercase(),
                    color = Color.White.copy(alpha = 0.95f),
                    fontWeight = FontWeight.Bold,
                    fontSize = if (tile.isMicro) 12.sp else 22.sp,
                    fontFamily = InterFontFamily,
                    modifier = if (!tile.isMicro && tile.colSpan <= 2) Modifier.padding(bottom = 8.dp) else Modifier
                )
            }
        }

        // Labels for all tiles except micro tiles
        if (!tile.isMicro) {
            val labelFontSize = when {
                tile.isMacro -> 12.sp
                tile.colSpan >= 4 -> 10.5.sp
                else -> 9.sp
            }
            Text(
                text = tile.label.uppercase(),
                color = Color.White.copy(alpha = 0.92f),
                fontWeight = FontWeight.SemiBold,
                fontSize = labelFontSize,
                fontFamily = InterFontFamily,
                letterSpacing = 0.8.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 5.dp, bottom = 4.dp, end = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ContentModeContent(
    tile: TileModel,
    galleryImages: List<Bitmap>
) {
    val role = (tile.role ?: tile.id).lowercase()

    // 1. Gallery Content Mode
    if (role.contains("gallery") || role.contains("gal")) {
        DiagonalGalleryPortfolio(
            images = galleryImages,
            accentColor = tile.accentColor,
            itemSize = 34.dp
        )
        return
    }

    // 2. Music Content Mode
    if (role.contains("music") || role.contains("mus")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = "MUSIC",
                color = Color.White.copy(alpha = 0.95f),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                fontFamily = InterFontFamily,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            if (tile.contentLines.isNotEmpty()) {
                tile.contentLines.take(4).forEach { title ->
                    Text(
                        text = title,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 10.sp,
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = "No audio library",
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 10.sp,
                    fontFamily = InterFontFamily
                )
            }
        }
        return
    }

    // 3. Real Notifications & Live Content
    if (tile.contentLines.isNotEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = Color(0xFFFF3B30),
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = tile.label.uppercase(),
                    color = Color.White.copy(alpha = 0.95f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    fontFamily = InterFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(1.dp))
            tile.contentLines.take(3).forEach { line ->
                Text(
                    text = line,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 9.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = InterFontFamily,
                    lineHeight = 12.sp
                )
            }
        }
        return
    }

    // 4. Empty State
    val emptyText = when {
        role.contains("mail") || role.contains("email") -> "No new mail"
        role.contains("msg") || role.contains("messag") -> "No new messages"
        else -> "No new notifications"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (tile.iconBitmap != null) {
                Image(
                    bitmap = tile.iconBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    alpha = 0.40f
                )
            } else {
                val sysIcon = getSystemTileIcon(tile.role ?: tile.id)
                if (sysIcon != null) {
                    Icon(
                        imageVector = sysIcon,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.40f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = emptyText,
                color = Color.White.copy(alpha = 0.50f),
                fontFamily = InterFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

private fun getSystemTileIcon(id: String): ImageVector? {
    return when (id.lowercase()) {
        "phone" -> Icons.Default.Phone
        "msg" -> Icons.AutoMirrored.Filled.Message
        "mail" -> Icons.Default.Email
        "cam" -> Icons.Default.CameraAlt
        "gal", "gallery" -> Icons.Default.Image
        "set", "settings" -> Icons.Default.Settings
        "torch", "fl", "flash" -> Icons.Default.FlashlightOn
        "calc" -> Icons.Default.Calculate
        "music", "mus" -> Icons.Default.LibraryMusic
        "notes", "nts" -> Icons.Default.NoteAlt
        else -> null
    }
}
