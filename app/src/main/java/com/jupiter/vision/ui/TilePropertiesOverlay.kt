package com.jupiter.vision.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jupiter.vision.model.TileModel
import com.jupiter.vision.ui.theme.InterFontFamily
import kotlin.math.roundToInt

enum class PropertiesState { LIST, RESIZE, APP_PICKER }

@Composable
fun TilePropertiesOverlay(
    tile: TileModel,
    sourceBounds: Rect,
    palette: List<Color>,
    onColorChange: (Color) -> Unit,
    onSizeChange: (Int, Int) -> Unit, // colSpan, rowSpan
    onDismiss: () -> Unit,
) {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    var propertiesState by remember { mutableStateOf(PropertiesState.LIST) }

    // Grown target bounds preserving original aspect ratio
    val targetWidthPx = screenWidthPx * 0.86f
    val aspect = if (sourceBounds.height > 0) sourceBounds.width / sourceBounds.height else 1f
    val targetHeightPx = (targetWidthPx / aspect).coerceIn(
        targetWidthPx * 0.65f,
        screenHeightPx * 0.75f
    )

    val targetLeftPx = (screenWidthPx - targetWidthPx) / 2f
    val origCenterY = sourceBounds.center.y
    val unclampedTop = origCenterY - (targetHeightPx / 2f)
    val marginY = with(density) { 48.dp.toPx() }
    val targetTopPx = unclampedTop.coerceIn(marginY, (screenHeightPx - targetHeightPx - marginY).coerceAtLeast(marginY))

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(tile.id) { isVisible = true }

    val bezierEase = remember { CubicBezierEasing(0.4f, 0f, 0.2f, 1f) }
    val progress by animateFloatAsState(
        targetValue = if (isVisible && propertiesState == PropertiesState.LIST) 1f else 0f,
        animationSpec = tween(320, easing = bezierEase),
        label = "propGrow"
    )

    val currLeft = sourceBounds.left + (targetLeftPx - sourceBounds.left) * progress
    val currTop = sourceBounds.top + (targetTopPx - sourceBounds.top) * progress
    val currWidth = sourceBounds.width + (targetWidthPx - sourceBounds.width) * progress
    val currHeight = sourceBounds.height + (targetHeightPx - sourceBounds.height) * progress

    val currentRealSizeLabel = when {
        tile.isMicro -> "0.5 × 0.5"
        tile.colSpan == 4 && tile.rowSpan == 4 -> "2 × 2"
        tile.colSpan == 4 && tile.rowSpan == 2 -> "2 × 1"
        tile.colSpan == 2 && tile.rowSpan == 4 -> "1 × 2"
        else -> "1 × 1"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Dim overlay covering screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (propertiesState == PropertiesState.RESIZE) 0.50f else 0.78f * progress))
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (propertiesState == PropertiesState.RESIZE) {
                            propertiesState = PropertiesState.LIST
                        } else {
                            onDismiss()
                        }
                    }
                }
        )

        if (propertiesState == PropertiesState.LIST) {
            // Grown Tile in Place
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
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${tile.label.uppercase()} PROPERTIES",
                            color = Color.White,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Android-Settings-Style List (Single line rows)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        // Row 1: Color
                        SettingsRow(
                            label = "Color",
                            onClick = {
                                val currentIdx = palette.indexOfFirst { it == tile.accentColor }
                                val nextColor = palette[(if (currentIdx == -1) 0 else currentIdx + 1) % palette.size]
                                onColorChange(nextColor)
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(tile.accentColor, CircleShape)
                                    .border(1.5.dp, Color.White, CircleShape)
                            )
                        }

                        // Row 2: App
                        SettingsRow(
                            label = "App",
                            onClick = { /* App target */ }
                        ) {
                            Text(
                                text = tile.packageName ?: tile.label,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                                fontFamily = InterFontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Row 3: Size
                        SettingsRow(
                            label = "Size",
                            onClick = { propertiesState = PropertiesState.RESIZE }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentRealSizeLabel,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontFamily = InterFontFamily
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Back / Settle Button at Center Bottom
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(0.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .clickable { onDismiss() }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "BACK",
                                color = Color.White,
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        } else if (propertiesState == PropertiesState.RESIZE) {
            // Resize Mode: Tile in place with 4 directional arrows + center back button
            val tileLeft = sourceBounds.left
            val tileTop = sourceBounds.top
            val tileW = sourceBounds.width
            val tileH = sourceBounds.height

            Box(
                modifier = Modifier
                    .offset { IntOffset(tileLeft.roundToInt(), tileTop.roundToInt()) }
                    .size(with(density) { tileW.toDp() }, with(density) { tileH.toDp() })
                    .clipToBounds()
                    .background(tile.accentColor)
                    .border(2.dp, Color.White)
            ) {
                // Up arrow (grows vertically to 2x2)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(0.dp))
                        .background(Color.Black.copy(alpha = 0.50f))
                        .clickable {
                            val newRowSpan = if (tile.rowSpan == 2) 4 else 2
                            onSizeChange(tile.colSpan, newRowSpan)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowDropUp, contentDescription = "Grow Up", tint = Color.White)
                }

                // Down arrow (toggles rowSpan between 2 and 4)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(0.dp))
                        .background(Color.Black.copy(alpha = 0.50f))
                        .clickable {
                            val newRowSpan = if (tile.rowSpan == 2) 4 else 2
                            onSizeChange(tile.colSpan, newRowSpan)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Grow Down", tint = Color.White)
                }

                // Left arrow (toggles colSpan between 2 and 4)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 2.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(0.dp))
                        .background(Color.Black.copy(alpha = 0.50f))
                        .clickable {
                            val newColSpan = if (tile.colSpan == 2) 4 else 2
                            onSizeChange(newColSpan, tile.rowSpan)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Grow Left", tint = Color.White)
                }

                // Right arrow (toggles colSpan between 2 and 4)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(0.dp))
                        .background(Color.Black.copy(alpha = 0.50f))
                        .clickable {
                            val newColSpan = if (tile.colSpan == 2) 4 else 2
                            onSizeChange(newColSpan, tile.rowSpan)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Grow Right", tint = Color.White)
                }

                // Center Back Button
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable { propertiesState = PropertiesState.LIST },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    onClick: () -> Unit,
    indicator: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color.White,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp
        )
        indicator()
    }
}
