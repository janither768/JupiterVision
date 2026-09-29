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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.jupiter.vision.util.Packer
import com.jupiter.vision.util.ResizeDirection
import kotlin.math.roundToInt

enum class PropertiesState { LIST, RESIZE }

@Composable
fun TilePropertiesOverlay(
    tile: TileModel,
    sourceBounds: Rect,
    palette: List<Color>,
    onColorChange: (Color) -> Unit,
    onDirectionalResize: (ResizeDirection) -> Unit,
    onStartSwap: (TileModel) -> Unit,
    onMigrateFolder: (TileModel) -> Unit = {},
    onDismiss: () -> Unit,
    gutterPx: Int = 12
) {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    var propertiesState by remember { mutableStateOf(PropertiesState.LIST) }
    val bezierEase = remember { CubicBezierEasing(0.4f, 0f, 0.2f, 1f) }

    // List mode target bounds (compact, refined, non-laggy)
    val listTargetWidthPx = (screenWidthPx * 0.84f).coerceAtMost(with(density) { 340.dp.toPx() })
    val listTargetHeightPx = (screenHeightPx * 0.54f).coerceIn(with(density) { 300.dp.toPx() }, with(density) { 430.dp.toPx() })

    var isVisible by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }
    LaunchedEffect(tile.id) { isVisible = true }

    val listProgress by animateFloatAsState(
        targetValue = if (isVisible && !isClosing && propertiesState == PropertiesState.LIST) 1f else 0f,
        animationSpec = tween(280, easing = bezierEase),
        label = "propGrow",
        finishedListener = { valProg ->
            if (isClosing && valProg <= 0.01f) {
                onDismiss()
            }
        }
    )

    val triggerDismiss = {
        if (!isClosing) {
            isClosing = true
        }
    }

    val initialScaleX = (sourceBounds.width / listTargetWidthPx).coerceAtLeast(0.01f)
    val initialScaleY = (sourceBounds.height / listTargetHeightPx).coerceAtLeast(0.01f)
    val scaleX = initialScaleX + (1f - initialScaleX) * listProgress
    val scaleY = initialScaleY + (1f - initialScaleY) * listProgress
    val transX = (sourceBounds.center.x - screenWidthPx / 2f) * (1f - listProgress)
    val transY = (sourceBounds.center.y - screenHeightPx / 2f) * (1f - listProgress)

    // Accurate Grid & Tile positioning for Live Resize Outline Synchronization
    val horizontalPaddingPx = with(density) { 8.dp.toPx() }
    val units = Packer.UNITS_PER_ROW
    val availableW = screenWidthPx - horizontalPaddingPx * 2f
    val unitPx = (availableW - (units - 1) * gutterPx) / units

    val targetW = (tile.colSpan * unitPx + (tile.colSpan - 1) * gutterPx).coerceAtLeast(1f)
    val targetH = (tile.rowSpan * unitPx + (tile.rowSpan - 1) * gutterPx).coerceAtLeast(1f)
    val targetX = horizontalPaddingPx + (tile.fixedCol ?: tile.gridCol) * (unitPx + gutterPx)
    val targetY = sourceBounds.top

    // Smoothly animate resize bounds with bezier
    val animResizeX by animateFloatAsState(
        targetValue = targetX,
        animationSpec = tween(320, easing = bezierEase),
        label = "resizeX"
    )
    val animResizeY by animateFloatAsState(
        targetValue = targetY,
        animationSpec = tween(320, easing = bezierEase),
        label = "resizeY"
    )
    val animResizeW by animateFloatAsState(
        targetValue = targetW,
        animationSpec = tween(320, easing = bezierEase),
        label = "resizeW"
    )
    val animResizeH by animateFloatAsState(
        targetValue = targetH,
        animationSpec = tween(320, easing = bezierEase),
        label = "resizeH"
    )

    val currentRealSizeLabel = when {
        tile.isMicro -> "0.5 × 0.5"
        tile.colSpan == 4 && tile.rowSpan == 4 -> "2 × 2"
        tile.colSpan == 4 && tile.rowSpan == 2 -> "2 × 1"
        tile.colSpan == 2 && tile.rowSpan == 4 -> "1 × 2"
        else -> "1 × 1"
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Dim backdrop proportional to growth progress
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (propertiesState == PropertiesState.RESIZE) 0.35f else 0.80f * listProgress))
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (propertiesState == PropertiesState.RESIZE) {
                            propertiesState = PropertiesState.LIST
                        } else {
                            triggerDismiss()
                        }
                    }
                }
        )

        if (propertiesState == PropertiesState.LIST) {
            // High-performance GPU-accelerated Matrix Overlay (zero layout lag, no border)
            Box(
                modifier = Modifier
                    .size(with(density) { listTargetWidthPx.toDp() }, with(density) { listTargetHeightPx.toDp() })
                    .graphicsLayer {
                        this.scaleX = scaleX
                        this.scaleY = scaleY
                        this.translationX = transX
                        this.translationY = transY
                        this.alpha = if (listProgress < 0.05f) listProgress * 20f else 1f
                        this.clip = true
                        this.shape = RoundedCornerShape(12.dp)
                    }
                    .background(tile.accentColor)
                    .pointerInput(tile.id) { detectTapGestures { } }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header: Title on left, SWAP button and Close on right
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
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // SWAP or MIGRATE BUTTON (Folder tiles have migrate button replacing swap button)
                        if (tile.isFolder) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(0.dp))
                                    .background(Color.White)
                                    .clickable {
                                        triggerDismiss()
                                        onMigrateFolder(tile)
                                    }
                                    .padding(horizontal = 9.dp, vertical = 4.5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "Migrate",
                                    tint = Color(0xFF202228),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "MIGRATE",
                                    color = Color(0xFF202228),
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(0.dp))
                                    .background(Color.Black.copy(alpha = 0.40f))
                                    .clickable {
                                        onStartSwap(tile)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "Swap",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "SWAP",
                                    color = Color.White,
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        IconButton(onClick = { triggerDismiss() }, modifier = Modifier.size(26.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Settings List with individual row backgrounds
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                    .size(20.dp)
                                    .background(tile.accentColor, CircleShape)
                                    .border(1.5.dp, Color.White, CircleShape)
                            )
                        }

                        // Row 2: App Target
                        SettingsRow(
                            label = "App",
                            onClick = { }
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

                    // Back Button at Center Bottom
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(0.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .clickable { triggerDismiss() }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "DONE",
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
            // Resize Mode: Positioned over the live tile, with 2 OPPOSITE ARROWS FOR EACH EDGE
            Box(
                modifier = Modifier
                    .offset { IntOffset(animResizeX.roundToInt(), animResizeY.roundToInt()) }
                    .size(with(density) { animResizeW.toDp() }, with(density) { animResizeH.toDp() })
                    .border(2.5.dp, Color.White)
            ) {
                // TOP EDGE: Expand Up (Up Arrow) & Contract Down (Down Arrow)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ResizeArrowButton(
                        icon = Icons.Default.ArrowDropUp,
                        contentDescription = "Expand Up",
                        onClick = { onDirectionalResize(ResizeDirection.TOP_EXPAND) }
                    )
                    ResizeArrowButton(
                        icon = Icons.Default.ArrowDropDown,
                        contentDescription = "Contract from Top",
                        onClick = { onDirectionalResize(ResizeDirection.TOP_CONTRACT) }
                    )
                }

                // BOTTOM EDGE: Expand Down (Down Arrow) & Contract Up (Up Arrow)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ResizeArrowButton(
                        icon = Icons.Default.ArrowDropDown,
                        contentDescription = "Expand Down",
                        onClick = { onDirectionalResize(ResizeDirection.BOTTOM_EXPAND) }
                    )
                    ResizeArrowButton(
                        icon = Icons.Default.ArrowDropUp,
                        contentDescription = "Contract from Bottom",
                        onClick = { onDirectionalResize(ResizeDirection.BOTTOM_CONTRACT) }
                    )
                }

                // LEFT EDGE: Expand Left (Left Arrow) & Contract Right (Right Arrow)
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ResizeArrowButton(
                        icon = Icons.Default.ChevronLeft,
                        contentDescription = "Expand Left",
                        onClick = { onDirectionalResize(ResizeDirection.LEFT_EXPAND) }
                    )
                    ResizeArrowButton(
                        icon = Icons.Default.ChevronRight,
                        contentDescription = "Contract from Left",
                        onClick = { onDirectionalResize(ResizeDirection.LEFT_CONTRACT) }
                    )
                }

                // RIGHT EDGE: Expand Right (Right Arrow) & Contract Left (Left Arrow)
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ResizeArrowButton(
                        icon = Icons.Default.ChevronRight,
                        contentDescription = "Expand Right",
                        onClick = { onDirectionalResize(ResizeDirection.RIGHT_EXPAND) }
                    )
                    ResizeArrowButton(
                        icon = Icons.Default.ChevronLeft,
                        contentDescription = "Contract from Right",
                        onClick = { onDirectionalResize(ResizeDirection.RIGHT_CONTRACT) }
                    )
                }

                // Center Back Button
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.85f))
                        .clickable { propertiesState = PropertiesState.LIST },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ResizeArrowButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(0.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .border(0.5.dp, Color.White.copy(alpha = 0.35f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
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
            .background(Color.Black.copy(alpha = 0.35f))
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
