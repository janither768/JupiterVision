package com.jupiter.vision.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jupiter.vision.model.TileMode
import com.jupiter.vision.model.TileModel

@Composable
fun TileView(
    tile: TileModel,
    contentMode: TileMode,
    wallpaper: ImageBitmap?,
    tileOffsetX: Int,          // tile position in grid pixels
    tileOffsetY: Int,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val base = LocalViewConfiguration.current
    val longMs = if (tile.isMicro) 400L else 1000L
    val cfg = remember(base, longMs) {
        object : ViewConfiguration by base {
            override val longPressTimeoutMillis: Long = longMs
        }
    }

    val ease = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val transition = updateTransition(targetState = contentMode, label = "mode")
    val scale by transition.animateFloat(
        transitionSpec = { tween(420, easing = ease) },
        label = "scale"
    ) { mode -> if (mode == TileMode.CONTENT) 1f else 0.92f }

    Box(
        modifier = modifier
            .clipToBounds()
            .drawBehind {
                // 1. Base tile color
                drawRect(tile.accentColor)

                // 2. Wallpaper slice — this tile's own piece of the global wallpaper
                if (wallpaper != null) {
                    clipRect(0f, 0f, size.width, size.height) {
                        drawImage(
                            image = wallpaper,
                            dstOffset = IntOffset(-tileOffsetX, -tileOffsetY),
                            alpha = 0.26f,
                            filterQuality = FilterQuality.Medium
                        )
                    }

                    // 3. Fade from top-right -> base color
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, tile.accentColor),
                            center = Offset(size.width, 0f),
                            radius = size.maxDimension * 1.15f
                        )
                    )
                }
            }
    ) {
        CompositionLocalProvider(LocalViewConfiguration provides cfg) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .pointerInput(tile.id) {
                        detectTapGestures(
                            onTap = { onClick() },
                            onLongPress = { if (!tile.isMicro) onLongPress() }
                        )
                    }
                    .clipToBounds(),
                contentAlignment = Alignment.CenterStart
            ) {
                when (contentMode) {
                    TileMode.ICON -> IconModeContent(tile)
                    TileMode.CONTENT -> ContentModeContent(tile)
                }
            }
        }

        // Live Activity Badge (strict 0.dp sharp square)
        if (contentMode == TileMode.ICON && tile.hasActivity && !tile.isMicro) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(8.dp)
                    .clip(RoundedCornerShape(0.dp))
                    .drawBehind { drawRect(Color(0xFFFF4D4D)) }
            )
        }
    }
}

@Composable
private fun IconModeContent(tile: TileModel) {
    val systemIcon = getSystemTileIcon(tile.id)
    Box(
        Modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (systemIcon != null && !tile.isMicro) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = systemIcon,
                    contentDescription = tile.label,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(if (tile.colSpan >= 4 && tile.rowSpan >= 4) 40.dp else 24.dp)
                )
                Text(
                    text = tile.label.take(4).uppercase(),
                    color = Color.White.copy(alpha = 0.88f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        } else {
            Text(
                text = if (tile.isMicro) tile.label else tile.label.take(3).uppercase(),
                color = Color.White.copy(alpha = 0.94f),
                fontWeight = FontWeight.Bold,
                fontSize = if (tile.isMicro) 11.sp else 18.sp,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun ContentModeContent(tile: TileModel) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tile.contentLines.take(3).forEachIndexed { i, line ->
            Text(
                text = line,
                color = Color.White.copy(alpha = if (i == 0) 0.95f else 0.68f),
                fontSize = if (i == 0) 12.sp else 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
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
        "gal" -> Icons.Default.Image
        "set" -> Icons.Default.Settings
        "torch", "fl" -> Icons.Default.FlashlightOn
        "calc" -> Icons.Default.Calculate
        "music", "mus" -> Icons.Default.LibraryMusic
        "notes", "nts" -> Icons.Default.NoteAlt
        else -> null
    }
}
