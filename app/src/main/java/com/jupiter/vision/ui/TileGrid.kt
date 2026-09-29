package com.jupiter.vision.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jupiter.vision.model.AppInfo
import com.jupiter.vision.model.ColorEngine
import com.jupiter.vision.model.TileMode
import com.jupiter.vision.model.TileModel
import com.jupiter.vision.util.Packer
import com.jupiter.vision.util.SystemControls
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * JUPITERVISION 2112.15 (Phase 3) — GRID ON CANVAS
 * - Ambient Ghost Echo sonar ping animation for square icon tiles (1.5s ease-out-quart ripple with 150ms echo delay)
 * - Liquid mesh drift effect from gradients (tile color, ColorDodge, faint ~0.2 opacity, parallaxed with scroll)
 * - Intelligent Foldering System (2x2 transparent dark grey, alternating zig-zag vertical layout)
 * - Folder icon mode: big 2-row white text + drifting dimmed icons; content mode: center-aligned icons
 * - Folder migration mode in Focus mode with 2x2 grid lines and white/dark-grey MIGRATE button
 * - Always-on music player controls on canvas when playing
 * - Clock Content Mode with next alarm countdown
 */

class TileAnimState(
    var currentX: Float,
    var currentY: Float,
    var currentW: Float,
    var currentH: Float,
    var targetX: Float,
    var targetY: Float,
    var targetW: Float,
    var targetH: Float,
    var startX: Float,
    var startY: Float,
    var startW: Float,
    var startH: Float,
    var animStartTimeNanos: Long = 0L,
    var isAnimating: Boolean = false,
    var flipProgress: Float = 0f,
    var targetFlip: Float = 0f,
    var flipStartTimeNanos: Long = 0L,
    // Folder specific pagination, 5s auto-scroll, and 10s auto-return state
    var folderCurrentPage: Int = 0,
    var folderTargetPage: Int = 0,
    var folderPageSlideOffset: Float = 0f,
    var folderSlideDirection: Int = 1,
    var folderSlideStartTimeNanos: Long = 0L,
    var folderLastInteractionTimeSec: Float = 0f,
    var folderLastAutoScrollTimeSec: Float = 0f
)

@Composable
fun TileGrid(
    systemTiles: List<TileModel>,
    thirdPartyTiles: List<TileModel>,
    contentModes: Map<String, TileMode>,
    galleryImages: List<Bitmap> = emptyList(),
    wallpaperBitmap: Bitmap? = null,
    isFocusActive: Boolean = false,
    isSwapMode: Boolean = false,
    isMusicPlaying: Boolean = false,
    currentMusicTrackTitle: String = "",
    migratingFolderTile: TileModel? = null,
    onMigrateFolderConfirm: (Int, Int) -> Unit = { _, _ -> },
    onCancelMigration: () -> Unit = {},
    onMusicPrev: () -> Unit = {},
    onMusicPlayPause: () -> Unit = {},
    onMusicNext: () -> Unit = {},
    onTileClick: (TileModel) -> Unit,
    onOpenMiniApp: (TileModel, Rect) -> Unit,
    onOpenProperties: (TileModel, Rect) -> Unit,
    gutter: Dp = 4.dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val bezierEasing = remember { CubicBezierEasing(0.4f, 0f, 0.2f, 1f) }

    // Folder migration selection state
    var selectedMigrationSlot by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(migratingFolderTile?.id) {
        if (migratingFolderTile != null) {
            selectedMigrationSlot = Pair(migratingFolderTile.gridCol, migratingFolderTile.gridRow)
        } else {
            selectedMigrationSlot = null
        }
    }

    // Layout constants in px
    val gutterPx = with(density) { gutter.toPx() }
    val horizontalPaddingPx = with(density) { 8.dp.toPx() }
    val topPaddingPx = with(density) { 10.dp.toPx() }
    val zoneGapPx = with(density) { 14.dp.toPx() }
    val gestureThresholdPx = with(density) { 55.dp.toPx() }

    // Scroll state: single continuous float read in draw pass
    var scrollY by remember { mutableFloatStateOf(0f) }
    var maxScrollY by remember { mutableFloatStateOf(0f) }
    val flingAnim = remember { Animatable(0f) }
    var flingJob by remember { mutableStateOf<Job?>(null) }

    // Touch interaction states
    var pressedTileId by remember { mutableStateOf<String?>(null) }
    var isHeld by remember { mutableStateOf(false) }
    var heldTileId by remember { mutableStateOf<String?>(null) }
    var dragAccumX by remember { mutableFloatStateOf(0f) }

    // Animation states map per tile
    val animStateMap = remember { mutableStateMapOf<String, TileAnimState>() }

    // Cached TextPaints and icon paints
    val textPaints = remember { CanvasGridPaints(density.density) }

    // Ghost Echo Sonar Ping Animation State (Square Icon Tiles)
    var lastPingTimeSec by remember { mutableFloatStateOf(-5f) }
    var activePingTileId by remember { mutableStateOf<String?>(null) }
    var activePingStartTimeSec by remember { mutableFloatStateOf(0f) }
    var lastPingedTileId by remember { mutableStateOf<String?>(null) }
    val pingEaseOutQuart = remember { CubicBezierEasing(0.25f, 1f, 0.5f, 1f) }

    // Frame clock driven loop
    var frameTimeNanos by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { time ->
                frameTimeNanos = time
            }
        }
    }

    // Zoom pulse animation float or Bezier-eased Swap pulse
    val pulseScale = remember(frameTimeNanos, isFocusActive, isSwapMode) {
        if (isSwapMode) {
            val periodSec = 1.6
            val t = (frameTimeNanos / 1_000_000_000.0) % periodSec
            val rawSine = (sin(t / periodSec * Math.PI * 2.0 - Math.PI / 2.0) + 1.0) / 2.0
            val eased = bezierEasing.transform(rawSine.toFloat())
            1.0f - (eased * 0.10f)
        } else if (isFocusActive) {
            1.0f
        } else {
            val periodSec = 2.2f
            val t = (frameTimeNanos / 1_000_000_000.0) % periodSec
            val frac = (sin(t / periodSec * Math.PI * 2.0 - Math.PI / 2.0) + 1.0) / 2.0
            1.0f + (frac * 0.18f).toFloat()
        }
    }

    // Gallery continuous scroll offset float (slowed down for smooth cinematic glide)
    val galleryScrollOffset = remember(frameTimeNanos) {
        val speedPxPerSec = 6f
        ((frameTimeNanos / 1_000_000_000.0) * speedPxPerSec).toFloat() % 10000f
    }

    // Windows 10 post-install screen fluid light phase
    val fluidTimeSec = remember(frameTimeNanos) {
        (frameTimeNanos / 1_000_000_000.0).toFloat()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(systemTiles, thirdPartyTiles, maxScrollY, isSwapMode, isMusicPlaying, migratingFolderTile, selectedMigrationSlot) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        flingJob?.cancel()

                        val touchDownX = down.position.x
                        val touchDownY = down.position.y
                        val gridY = touchDownY + scrollY

                        if (migratingFolderTile != null) {
                            val availableW = size.width - horizontalPaddingPx * 2f
                            val unitPx = (availableW - (Packer.UNITS_PER_ROW - 1) * gutterPx) / Packer.UNITS_PER_ROW

                            var maxSysRow = 0
                            for (tile in systemTiles) {
                                val r = tile.fixedRow ?: tile.gridRow
                                val endR = r + tile.rowSpan
                                if (endR > maxSysRow) maxSysRow = endR
                            }
                            val sysZoneHeight = maxSysRow * (unitPx + gutterPx)
                            val thirdPartyBaseY = topPaddingPx + sysZoneHeight + zoneGapPx

                            val btnW = 104f * textPaints.densityScale
                            val btnH = 38f * textPaints.densityScale

                            var pressedMigrate = false
                            val currentSel = selectedMigrationSlot
                            if (currentSel != null) {
                                val (selC, selR) = currentSel
                                val slotX = horizontalPaddingPx + selC * (unitPx + gutterPx)
                                val slotY = thirdPartyBaseY + selR * (unitPx + gutterPx) - scrollY
                                val slotW = 4 * unitPx + 3 * gutterPx
                                val slotH = 4 * unitPx + 3 * gutterPx
                                val btnX = slotX + (slotW - btnW) * 0.5f
                                val btnY = slotY + (slotH - btnH) * 0.5f

                                if (touchDownX in (btnX - 20f)..(btnX + btnW + 20f) &&
                                    touchDownY in (btnY - 14f)..(btnY + btnH + 14f)
                                ) {
                                    pressedMigrate = true
                                }
                            }

                            var isScrollingMigration = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val drag = change.positionChange()
                                if (abs(drag.y) > 4f) {
                                    isScrollingMigration = true
                                }
                                if (isScrollingMigration) {
                                    scrollY = (scrollY - drag.y).coerceIn(0f, maxScrollY)
                                    change.consume()
                                }
                                if (!change.pressed) {
                                    if (!isScrollingMigration) {
                                        if (pressedMigrate && currentSel != null) {
                                            onMigrateFolderConfirm(currentSel.first, currentSel.second)
                                        } else {
                                            val candidateCols = listOf(0, 4)
                                            val candidateRows = listOf(0, 4, 8, 12, 16, 20)
                                            for (r in candidateRows) {
                                                val slotY = thirdPartyBaseY + r * (unitPx + gutterPx) - scrollY
                                                val slotH = 4 * unitPx + 3 * gutterPx
                                                for (c in candidateCols) {
                                                    val slotX = horizontalPaddingPx + c * (unitPx + gutterPx)
                                                    val slotW = 4 * unitPx + 3 * gutterPx
                                                    if (touchDownX in slotX..(slotX + slotW) && touchDownY in slotY..(slotY + slotH)) {
                                                        selectedMigrationSlot = Pair(c, r)
                                                        break
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    break
                                }
                            }
                            return@awaitEachGesture
                        }

                        // Find hit tile
                        val hitTile = findTileAt(
                            systemTiles = systemTiles,
                            thirdPartyTiles = thirdPartyTiles,
                            animStateMap = animStateMap,
                            x = touchDownX,
                            y = gridY
                        )

                        pressedTileId = hitTile?.id
                        isHeld = false
                        heldTileId = null
                        dragAccumX = 0f

                        val pointerId = down.id
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        var holdTriggered = false
                        var isScrolling = false
                        var isFolderSwipe = false
                        var folderSwipeDragX = 0f

                        val holdTimerJob = coroutineScope.launch {
                            if (!isSwapMode) {
                                delay(450L)
                                if (pressedTileId != null && !isScrolling && !isFolderSwipe) {
                                    isHeld = true
                                    heldTileId = pressedTileId
                                    holdTriggered = true
                                }
                            }
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            velocityTracker.addPosition(change.uptimeMillis, change.position)

                            val drag = change.positionChange()

                            if (isHeld) {
                                dragAccumX += drag.x
                                change.consume()
                            } else {
                                if (hitTile != null && hitTile.isFolder && !isScrolling) {
                                    val pageCount = max(1, (hitTile.folderApps.size + 3) / 4)
                                    if (pageCount > 1 && !isFolderSwipe) {
                                        if (abs(drag.x) > 6f && abs(drag.x) > abs(drag.y) * 1.3f) {
                                            isFolderSwipe = true
                                            holdTimerJob.cancel()
                                        }
                                    }
                                }
                                if (isFolderSwipe) {
                                    folderSwipeDragX += drag.x
                                    change.consume()
                                } else {
                                    if (!isScrolling) {
                                        if (abs(drag.y) > 4f || abs(drag.x) > 4f) {
                                            holdTimerJob.cancel()
                                            isScrolling = true
                                            pressedTileId = null
                                        }
                                    }
                                    if (isScrolling) {
                                        scrollY = (scrollY - drag.y).coerceIn(0f, maxScrollY)
                                        change.consume()
                                    }
                                }
                            }

                            if (!change.pressed) {
                                holdTimerJob.cancel()
                                if (isHeld && heldTileId != null) {
                                    val tile = (systemTiles + thirdPartyTiles).firstOrNull { it.id == heldTileId }
                                    val animState = animStateMap[heldTileId]
                                    if (tile != null && animState != null) {
                                        val screenRect = Rect(
                                            animState.currentX,
                                            animState.currentY - scrollY,
                                            animState.currentX + animState.currentW,
                                            animState.currentY - scrollY + animState.currentH
                                        )
                                        if (dragAccumX > gestureThresholdPx) {
                                            onOpenMiniApp(tile, screenRect)
                                        } else if (dragAccumX < -gestureThresholdPx) {
                                            onOpenProperties(tile, screenRect)
                                        }
                                    }
                                } else if (isFolderSwipe && hitTile != null && hitTile.isFolder) {
                                    val state = animStateMap[hitTile.id]
                                    if (state != null) {
                                        val pageCount = max(1, (hitTile.folderApps.size + 3) / 4)
                                        if (pageCount > 1) {
                                            if (folderSwipeDragX < -25f) {
                                                // Swipe left -> Next Page
                                                state.folderTargetPage = (state.folderCurrentPage + 1) % pageCount
                                                state.folderSlideDirection = 1
                                                state.folderPageSlideOffset = 1f
                                                state.folderSlideStartTimeNanos = frameTimeNanos
                                                state.folderCurrentPage = state.folderTargetPage
                                                state.folderLastInteractionTimeSec = fluidTimeSec
                                            } else if (folderSwipeDragX > 25f) {
                                                // Swipe right -> Prev Page
                                                state.folderTargetPage = (state.folderCurrentPage - 1 + pageCount) % pageCount
                                                state.folderSlideDirection = -1
                                                state.folderPageSlideOffset = -1f
                                                state.folderSlideStartTimeNanos = frameTimeNanos
                                                state.folderCurrentPage = state.folderTargetPage
                                                state.folderLastInteractionTimeSec = fluidTimeSec
                                            }
                                        }
                                    }
                                } else if (!isScrolling && hitTile != null && !holdTriggered) {
                                    if (hitTile.isFolder) {
                                        val state = animStateMap[hitTile.id]
                                        if (state != null) {
                                            state.folderLastInteractionTimeSec = fluidTimeSec
                                            if (state.targetFlip > 0.5f || state.flipProgress > 0.20f) {
                                                val localX = touchDownX - state.currentX
                                                val localY = gridY - state.currentY
                                                val app = findFolderAppAt(hitTile, localX, localY, state.currentW, state.currentH, textPaints.densityScale, currentPage = state.folderCurrentPage)
                                                if (app != null) {
                                                    onTileClick(
                                                        TileModel(
                                                            id = "app_${app.packageName}",
                                                            label = app.label,
                                                            packageName = app.packageName,
                                                            accentColor = hitTile.accentColor
                                                        )
                                                    )
                                                } else {
                                                    // Tapped empty quadrant -> close folder
                                                    state.targetFlip = 0f
                                                    state.flipStartTimeNanos = frameTimeNanos
                                                }
                                            } else {
                                                // When folder is pressed in icon mode: open folder (un-dim apps, slide text)
                                                state.targetFlip = 1f
                                                state.flipStartTimeNanos = frameTimeNanos
                                            }
                                        }
                                    } else {
                                        // Check if tapped on music controls when music is actively playing
                                        val role = (hitTile.role ?: hitTile.id).lowercase()
                                        if (isMusicPlaying && (role.contains("music") || role.contains("mus"))) {
                                            val state = animStateMap[hitTile.id]
                                            if (state != null) {
                                                val localX = touchDownX - state.currentX
                                                val width = state.currentW
                                                if (localX < width * 0.33f) {
                                                    onMusicPrev()
                                                } else if (localX < width * 0.66f) {
                                                    onMusicPlayPause()
                                                } else {
                                                    onMusicNext()
                                                }
                                            } else {
                                                onTileClick(hitTile)
                                            }
                                        } else {
                                            onTileClick(hitTile)
                                        }
                                    }
                                } else if (isScrolling) {
                                    // Fling momentum on release
                                    val velY = velocityTracker.calculateVelocity().y
                                    if (abs(velY) > 150f) {
                                        flingJob = coroutineScope.launch {
                                            val target = (scrollY - velY * 0.35f).coerceIn(0f, maxScrollY)
                                            flingAnim.snapTo(scrollY)
                                            flingAnim.animateTo(
                                                targetValue = target,
                                                animationSpec = tween(
                                                    durationMillis = 400,
                                                    easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)
                                                )
                                            ) {
                                                scrollY = value
                                            }
                                        }
                                    }
                                }

                                pressedTileId = null
                                isHeld = false
                                heldTileId = null
                                dragAccumX = 0f
                                break
                            }
                        }
                    }
                }
        ) {
            val canvasW = size.width
            val canvasH = size.height
            val units = Packer.UNITS_PER_ROW
            val availableW = canvasW - horizontalPaddingPx * 2f
            val unitPx = (availableW - (units - 1) * gutterPx) / units

            // 1. Calculate and update System Zone tile targets
            var maxSysRow = 0
            for (tile in systemTiles) {
                val c = tile.fixedCol ?: tile.gridCol
                val r = tile.fixedRow ?: tile.gridRow
                val endR = r + tile.rowSpan
                if (endR > maxSysRow) maxSysRow = endR

                val targetX = horizontalPaddingPx + c * (unitPx + gutterPx)
                val targetY = topPaddingPx + r * (unitPx + gutterPx)
                val targetW = tile.colSpan * unitPx + (tile.colSpan - 1) * gutterPx
                val targetH = tile.rowSpan * unitPx + (tile.rowSpan - 1) * gutterPx

                // If music is playing, lock music tile in content/player mode
                val isMusicTile = (tile.role ?: tile.id).lowercase().contains("mus")
                val targetFlip = if (tile.isFolder) {
                    animStateMap[tile.id]?.targetFlip ?: 0f
                } else if (isMusicTile && isMusicPlaying) {
                    1f
                } else if (contentModes[tile.id] == TileMode.CONTENT) {
                    1f
                } else {
                    0f
                }

                updateTileAnimState(
                    tile = tile,
                    targetX = targetX,
                    targetY = targetY,
                    targetW = targetW,
                    targetH = targetH,
                    targetFlip = targetFlip,
                    animStateMap = animStateMap,
                    frameTimeNanos = frameTimeNanos,
                    bezierEasing = bezierEasing
                )
            }

            val sysZoneHeight = maxSysRow * (unitPx + gutterPx)

            // 2. Calculate and update Third Party Zone tile targets
            var max3rdRow = 0
            val thirdPartyBaseY = topPaddingPx + sysZoneHeight + zoneGapPx
            for (tile in thirdPartyTiles) {
                val c = tile.fixedCol ?: tile.gridCol
                val r = tile.fixedRow ?: tile.gridRow
                val endR = r + tile.rowSpan
                if (endR > max3rdRow) max3rdRow = endR

                val targetX = horizontalPaddingPx + c * (unitPx + gutterPx)
                val targetY = thirdPartyBaseY + r * (unitPx + gutterPx)
                val targetW = tile.colSpan * unitPx + (tile.colSpan - 1) * gutterPx
                val targetH = tile.rowSpan * unitPx + (tile.rowSpan - 1) * gutterPx

                val targetFlip = if (tile.isFolder) {
                    animStateMap[tile.id]?.targetFlip ?: 0f
                } else if (contentModes[tile.id] == TileMode.CONTENT) {
                    1f
                } else {
                    0f
                }

                updateTileAnimState(
                    tile = tile,
                    targetX = targetX,
                    targetY = targetY,
                    targetW = targetW,
                    targetH = targetH,
                    targetFlip = targetFlip,
                    animStateMap = animStateMap,
                    frameTimeNanos = frameTimeNanos,
                    bezierEasing = bezierEasing
                )
            }

            val totalContentHeight = thirdPartyBaseY + max3rdRow * (unitPx + gutterPx) + with(density) { 90.dp.toPx() }
            val newMaxScroll = max(0f, totalContentHeight - canvasH)
            if (maxScrollY != newMaxScroll) {
                maxScrollY = newMaxScroll
            }

            // 3. Clip strictly to the scrollable area below the header.
            clipRect(0f, 0f, canvasW, canvasH) {
                val allTiles = systemTiles + thirdPartyTiles

                // Ghost Echo Sonar Ping: Every 5s, randomly select exactly one square tile from visible scrolled viewport (EXCLUDING FOLDERS)
                if (fluidTimeSec >= lastPingTimeSec + 5.0f) {
                    val visibleSquareTiles = allTiles.filter { t ->
                        val isSquare = (t.colSpan == t.rowSpan) && !t.isInvisible && !t.isFolder && t.role != "folder"
                        if (!isSquare) return@filter false
                        val st = animStateMap[t.id] ?: return@filter false
                        val ty = st.currentY - scrollY
                        val th = st.currentH
                        ty + th > 10f && ty < canvasH - 10f
                    }
                    if (visibleSquareTiles.isNotEmpty()) {
                        val pool = if (visibleSquareTiles.size > 1) {
                            visibleSquareTiles.filter { it.id != lastPingedTileId }
                        } else visibleSquareTiles
                        val chosen = (if (pool.isNotEmpty()) pool else visibleSquareTiles).random()
                        activePingTileId = chosen.id
                        activePingStartTimeSec = fluidTimeSec
                        lastPingedTileId = chosen.id
                        lastPingTimeSec = fluidTimeSec
                    } else {
                        lastPingTimeSec = fluidTimeSec
                    }
                }

                // Cleanup after 1.65 seconds (1.5s wave + 150ms echo delay)
                if (activePingTileId != null && (fluidTimeSec - activePingStartTimeSec) >= 1.65f) {
                    activePingTileId = null
                }

                for (tile in allTiles) {
                    val state = animStateMap[tile.id] ?: continue
                    val tx = state.currentX
                    val ty = state.currentY - scrollY
                    val tw = state.currentW
                    val th = state.currentH

                    // Bottom edge culling only
                    if (ty > canvasH + 10f) continue

                    val isCurrentPressed = (tile.id == pressedTileId)
                    val isCurrentHeld = (tile.id == heldTileId)

                    val pressScale = if (isCurrentHeld) 0.90f else if (isCurrentPressed) 0.92f else 1.0f
                    val pressAlpha = if (isCurrentHeld) 0.68f else if (isCurrentPressed) 0.82f else 1.0f

                    val effectivePulseScale = if (isSwapMode) pulseScale else if (tile.isMicro || tile.isInvisible) 1.0f else pulseScale

                    val pingElapsed = if (tile.id == activePingTileId) {
                        (fluidTimeSec - activePingStartTimeSec).coerceAtLeast(0f)
                    } else null

                    if (tile.isFolder) {
                        val pageCount = max(1, (tile.folderApps.size + 3) / 4)
                        // In folder icon mode, auto page scroll per 5 secs
                        if (pageCount > 1 && state.flipProgress < 0.1f) {
                            if (state.folderLastAutoScrollTimeSec == 0f) {
                                state.folderLastAutoScrollTimeSec = fluidTimeSec
                            } else if (fluidTimeSec - state.folderLastAutoScrollTimeSec >= 5.0f) {
                                state.folderLastAutoScrollTimeSec = fluidTimeSec
                                state.folderTargetPage = (state.folderCurrentPage + 1) % pageCount
                                state.folderSlideDirection = 1
                                state.folderPageSlideOffset = 1f
                                state.folderSlideStartTimeNanos = frameTimeNanos
                                state.folderCurrentPage = state.folderTargetPage
                            }
                        }

                        // Slide-in animation easing (320ms bezier ease-out)
                        if (state.folderPageSlideOffset != 0f) {
                            val slideDurationNanos = 320_000_000L
                            val elapsed = frameTimeNanos - state.folderSlideStartTimeNanos
                            if (elapsed >= slideDurationNanos) {
                                state.folderPageSlideOffset = 0f
                            } else {
                                val frac = (elapsed.toDouble() / slideDurationNanos).toFloat().coerceIn(0f, 1f)
                                val eased = bezierEasing.transform(frac)
                                val initial = if (state.folderSlideDirection > 0) 1f else -1f
                                state.folderPageSlideOffset = initial * (1f - eased)
                            }
                        }

                        // If folder is not used for 10 secs, return to icon mode
                        if (state.targetFlip > 0.5f || state.flipProgress > 0.5f) {
                            if (state.folderLastInteractionTimeSec > 0f && (fluidTimeSec - state.folderLastInteractionTimeSec) >= 10.0f) {
                                state.targetFlip = 0f
                                state.flipStartTimeNanos = frameTimeNanos
                                state.folderLastAutoScrollTimeSec = fluidTimeSec
                            }
                        }
                    }

                    drawSingleTile(
                        tile = tile,
                        x = tx,
                        y = ty,
                        w = tw,
                        h = th,
                        pulseScale = effectivePulseScale,
                        pressScale = pressScale,
                        pressAlpha = pressAlpha,
                        isHeld = isCurrentHeld,
                        dragDeltaX = if (isCurrentHeld) dragAccumX else 0f,
                        gestureThresholdPx = gestureThresholdPx,
                        flipProgress = state.flipProgress,
                        targetFlip = state.targetFlip,
                        galleryImages = galleryImages,
                        galleryScrollOffset = galleryScrollOffset,
                        wallpaperBitmap = wallpaperBitmap,
                        canvasW = canvasW,
                        canvasH = canvasH,
                        fluidTimeSec = fluidTimeSec,
                        pingElapsed = pingElapsed,
                        pingEasing = pingEaseOutQuart,
                        scrollY = scrollY,
                        isMusicPlaying = isMusicPlaying,
                        currentMusicTrackTitle = currentMusicTrackTitle,
                        isSwapMode = isSwapMode,
                        paints = textPaints,
                        animState = state
                    )
                }

                // If in folder migration mode, render 2x2 grid lines & highlight overlay
                if (migratingFolderTile != null) {
                    drawMigrationOverlay(
                        canvasW = canvasW,
                        canvasH = canvasH,
                        thirdPartyBaseY = thirdPartyBaseY,
                        unitPx = unitPx,
                        gutterPx = gutterPx,
                        horizontalPaddingPx = horizontalPaddingPx,
                        scrollY = scrollY,
                        selectedSlot = selectedMigrationSlot,
                        paints = textPaints
                    )
                }
            }
        }
    }
}

private fun updateTileAnimState(
    tile: TileModel,
    targetX: Float,
    targetY: Float,
    targetW: Float,
    targetH: Float,
    targetFlip: Float,
    animStateMap: MutableMap<String, TileAnimState>,
    frameTimeNanos: Long,
    bezierEasing: CubicBezierEasing
) {
    var state = animStateMap[tile.id]
    if (state == null) {
        state = TileAnimState(
            currentX = targetX,
            currentY = targetY,
            currentW = targetW,
            currentH = targetH,
            targetX = targetX,
            targetY = targetY,
            targetW = targetW,
            targetH = targetH,
            startX = targetX,
            startY = targetY,
            startW = targetW,
            startH = targetH,
            animStartTimeNanos = frameTimeNanos,
            isAnimating = false,
            flipProgress = targetFlip,
            targetFlip = targetFlip,
            flipStartTimeNanos = frameTimeNanos
        )
        animStateMap[tile.id] = state
        return
    }

    val animDurationNanos = 320_000_000L

    // Position / Size reflow animation
    if (abs(state.targetX - targetX) > 0.5f || abs(state.targetY - targetY) > 0.5f ||
        abs(state.targetW - targetW) > 0.5f || abs(state.targetH - targetH) > 0.5f
    ) {
        state.startX = state.currentX
        state.startY = state.currentY
        state.startW = state.currentW
        state.startH = state.currentH
        state.targetX = targetX
        state.targetY = targetY
        state.targetW = targetW
        state.targetH = targetH
        state.animStartTimeNanos = frameTimeNanos
        state.isAnimating = true
    }

    if (state.isAnimating) {
        val elapsed = frameTimeNanos - state.animStartTimeNanos
        if (elapsed >= animDurationNanos) {
            state.currentX = state.targetX
            state.currentY = state.targetY
            state.currentW = state.targetW
            state.currentH = state.targetH
            state.isAnimating = false
        } else {
            val rawFraction = (elapsed.toDouble() / animDurationNanos).toFloat().coerceIn(0f, 1f)
            val eased = bezierEasing.transform(rawFraction)
            state.currentX = state.startX + (state.targetX - state.startX) * eased
            state.currentY = state.startY + (state.targetY - state.startY) * eased
            state.currentW = state.startW + (state.targetW - state.startW) * eased
            state.currentH = state.startH + (state.targetH - state.startH) * eased
        }
    }

    // Flip progress animation
    if (state.targetFlip != targetFlip) {
        state.targetFlip = targetFlip
        state.flipStartTimeNanos = frameTimeNanos
    }

    if (abs(state.flipProgress - state.targetFlip) > 0.005f) {
        val elapsed = frameTimeNanos - state.flipStartTimeNanos
        val rawFraction = (elapsed.toDouble() / animDurationNanos).toFloat().coerceIn(0f, 1f)
        val eased = bezierEasing.transform(rawFraction)
        state.flipProgress = if (state.targetFlip > 0.5f) eased else (1f - eased)
    } else {
        state.flipProgress = state.targetFlip
    }
}

private fun findTileAt(
    systemTiles: List<TileModel>,
    thirdPartyTiles: List<TileModel>,
    animStateMap: Map<String, TileAnimState>,
    x: Float,
    y: Float
): TileModel? {
    val all = systemTiles + thirdPartyTiles
    for (tile in all.reversed()) {
        val state = animStateMap[tile.id] ?: continue
        if (x >= state.currentX && x <= state.currentX + state.currentW &&
            y >= state.currentY && y <= state.currentY + state.currentH
        ) {
            return tile
        }
    }
    return null
}

private fun DrawScope.drawSingleTile(
    tile: TileModel,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    pulseScale: Float,
    pressScale: Float,
    pressAlpha: Float,
    isHeld: Boolean,
    dragDeltaX: Float,
    gestureThresholdPx: Float,
    flipProgress: Float,
    targetFlip: Float,
    galleryImages: List<Bitmap>,
    galleryScrollOffset: Float,
    wallpaperBitmap: Bitmap? = null,
    canvasW: Float = 0f,
    canvasH: Float = 0f,
    fluidTimeSec: Float,
    pingElapsed: Float?,
    pingEasing: CubicBezierEasing,
    scrollY: Float,
    isMusicPlaying: Boolean,
    currentMusicTrackTitle: String,
    isSwapMode: Boolean,
    paints: CanvasGridPaints,
    animState: TileAnimState? = null
) {
    if (tile.isInvisible) return

    val cx = x + w * 0.5f
    val cy = y + h * 0.5f

    clipRect(x, y, x + w, y + h) {
        // Base flat color background with pulse scale & touch shrink
        scale(
            scaleX = pulseScale * pressScale,
            scaleY = pulseScale * pressScale,
            pivot = Offset(cx, cy)
        ) {
            if (tile.isFolder) {
                // Folder background: dark grey and transparent (0.8 opacity) - EXCLUDED from wallpaper per tile
                drawRect(
                    color = Color(0xFF202228).copy(alpha = 0.80f * pressAlpha),
                    topLeft = Offset(x, y),
                    size = Size(w, h)
                )
            } else {
                // Wallpaper per tile image engine (EXCEPT folder tiles)
                if (wallpaperBitmap != null && !wallpaperBitmap.isRecycled && canvasW > 0f && canvasH > 0f) {
                    val bw = wallpaperBitmap.width.toFloat()
                    val bh = wallpaperBitmap.height.toFloat()

                    // Normalized screen coordinates of tile center
                    val normX = (cx / canvasW).coerceIn(0f, 1f)
                    val normY = ((cy + scrollY * 0.35f) / canvasH).coerceIn(0f, 1f)

                    // Slight different scaling on each nearby tiles
                    val hash = abs(tile.gridCol * 31 + tile.gridRow * 17 + tile.id.hashCode())
                    val scaleOffset = (((hash % 7) - 3) * 0.05f) // -0.15f .. +0.15f
                    val tileScale = (1.0f + scaleOffset).coerceIn(0.80f, 1.25f)

                    val sampleW = ((w / canvasW) * bw * 1.35f * tileScale).coerceIn(8f, bw)
                    val sampleH = ((h / canvasH) * bh * 1.35f * tileScale).coerceIn(8f, bh)

                    val sampleCenterX = normX * bw
                    val sampleCenterY = normY * bh

                    val srcLeft = (sampleCenterX - sampleW * 0.5f).roundToInt().coerceIn(0, (bw - 1).toInt())
                    val srcTop = (sampleCenterY - sampleH * 0.5f).roundToInt().coerceIn(0, (bh - 1).toInt())
                    val srcRight = (sampleCenterX + sampleW * 0.5f).roundToInt().coerceIn(srcLeft + 1, bw.toInt())
                    val srcBottom = (sampleCenterY + sampleH * 0.5f).roundToInt().coerceIn(srcTop + 1, bh.toInt())

                    val srcRect = android.graphics.Rect(srcLeft, srcTop, srcRight, srcBottom)
                    val dstRect = android.graphics.Rect(x.roundToInt(), y.roundToInt(), (x + w).roundToInt(), (y + h).roundToInt())

                    paints.bitmapPaint.alpha = (pressAlpha * 255).roundToInt().coerceIn(0, 255)
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawBitmap(wallpaperBitmap, srcRect, dstRect, paints.bitmapPaint)
                    }

                    // Tint overlay with tile accent color
                    drawRect(
                        color = tile.accentColor.copy(alpha = 0.62f * pressAlpha),
                        topLeft = Offset(x, y),
                        size = Size(w, h)
                    )
                } else {
                    drawRect(
                        color = tile.accentColor.copy(alpha = pressAlpha),
                        topLeft = Offset(x, y),
                        size = Size(w, h)
                    )
                }
            }
        }

        // 2. Parallax micro-shift during Swap Mode
        val parallaxOffset = if (isSwapMode) {
            val pSin = sin(fluidTimeSec * 3.5f + (x * 0.05f))
            Offset(pSin * 2.5f, -pSin * 1.5f)
        } else Offset.Zero

        translate(left = parallaxOffset.x, top = parallaxOffset.y) {
            if (tile.isFolder) {
                drawFolderTile(
                    tile = tile,
                    x = x,
                    y = y,
                    w = w,
                    h = h,
                    flipProgress = flipProgress,
                    targetFlip = targetFlip,
                    fluidTimeSec = fluidTimeSec,
                    alpha = pressAlpha,
                    paints = paints,
                    animState = animState
                )
            } else {
                // Draw modes: Icon mode and Content mode with sweep reveal
                if (flipProgress <= 0.001f) {
                    drawIconMode(tile, x, y, w, h, pressAlpha, isSwapMode, paints)
                } else if (flipProgress >= 0.999f) {
                    drawContentMode(
                        tile = tile,
                        x = x,
                        y = y,
                        w = w,
                        h = h,
                        galleryImages = galleryImages,
                        galleryScrollOffset = galleryScrollOffset,
                        isMusicPlaying = isMusicPlaying,
                        currentMusicTrackTitle = currentMusicTrackTitle,
                        fluidTimeSec = fluidTimeSec,
                        alpha = pressAlpha,
                        paints = paints
                    )
                } else {
                    // Sweeping reveal transition: Content sweeps in from right toward left
                    val splitX = x + w * (1f - flipProgress)
                    clipRect(x, y, splitX, y + h) {
                        drawIconMode(tile, x, y, w, h, pressAlpha, isSwapMode, paints)
                    }
                    clipRect(splitX, y, x + w, y + h) {
                        drawContentMode(
                            tile = tile,
                            x = x,
                            y = y,
                            w = w,
                            h = h,
                            galleryImages = galleryImages,
                            galleryScrollOffset = galleryScrollOffset,
                            isMusicPlaying = isMusicPlaying,
                            currentMusicTrackTitle = currentMusicTrackTitle,
                            fluidTimeSec = fluidTimeSec,
                            alpha = pressAlpha,
                            paints = paints
                        )
                    }
                }
            }

            // Ghost Echo Sonar Ping Animation (Square Icon Tiles - EXCLUDES FOLDERS)
            if (!tile.isFolder && tile.role != "folder" && pingElapsed != null && pingElapsed in 0f..1.65f) {
                drawGhostEchoPing(
                    tile = tile,
                    x = x,
                    y = y,
                    w = w,
                    h = h,
                    elapsedSec = pingElapsed,
                    easing = pingEasing,
                    paints = paints
                )
            }
        }

        // 4. Notification badge (strictly real activity only)
        if (tile.hasActivity && !tile.isMicro && flipProgress < 0.5f) {
            drawRect(
                color = Color(0xFFFF3B30),
                topLeft = Offset(x + w - 10f * paints.densityScale, y + 4f * paints.densityScale),
                size = Size(6f * paints.densityScale, 6f * paints.densityScale)
            )
        }

        // 5. Held border and slide affordance arrows
        if (isHeld) {
            drawRect(
                color = Color.White.copy(alpha = 0.92f),
                topLeft = Offset(x, y),
                size = Size(w, h),
                style = Stroke(width = 2.dp.toPx())
            )

            // Right arrow (Slide RIGHT = Mini-App)
            val rightDragRatio = (dragDeltaX / gestureThresholdPx).coerceIn(-0.4f, 1.0f)
            val rightAlpha = if (rightDragRatio >= 0) (0.80f + 0.20f * rightDragRatio) else (0.80f * (1.0f + rightDragRatio * 2.5f)).coerceAtLeast(0f)
            val rightOffset = (4f + 14f * rightDragRatio.coerceAtLeast(0f)) * paints.densityScale
            val arrowPillSize = 24f * paints.densityScale
            val rightPillLeft = x + w - arrowPillSize - rightOffset
            val rightPillTop = cy - arrowPillSize * 0.5f

            drawRect(
                color = Color.Black.copy(alpha = 0.70f * rightAlpha),
                topLeft = Offset(rightPillLeft, rightPillTop),
                size = Size(arrowPillSize, arrowPillSize)
            )
            drawArrow(rightPillLeft + arrowPillSize * 0.5f, cy, arrowPillSize * 0.4f, isRight = true, alpha = rightAlpha, paints = paints)

            // Left arrow (Slide LEFT = Properties)
            val leftDragRatio = (-dragDeltaX / gestureThresholdPx).coerceIn(-0.4f, 1.0f)
            val leftAlpha = if (leftDragRatio >= 0) (0.80f + 0.20f * leftDragRatio) else (0.80f * (1.0f + leftDragRatio * 2.5f)).coerceAtLeast(0f)
            val leftOffset = (4f + 14f * leftDragRatio.coerceAtLeast(0f)) * paints.densityScale
            val leftPillLeft = x + leftOffset
            val leftPillTop = cy - arrowPillSize * 0.5f

            drawRect(
                color = Color.Black.copy(alpha = 0.70f * leftAlpha),
                topLeft = Offset(leftPillLeft, leftPillTop),
                size = Size(arrowPillSize, arrowPillSize)
            )
            drawArrow(leftPillLeft + arrowPillSize * 0.5f, cy, arrowPillSize * 0.4f, isRight = false, alpha = leftAlpha, paints = paints)
        }
    }
}

/**
 * Intelligent Folder Tile Rendering:
 * - Tile background is dark grey and transparent (0.8 opacity).
 * - In Icon mode: Text at its current place. Inside it, app icons shown in a grid closely put together, dimmed, no animation.
 * - In Content mode: App icons un-dimmed. Text slides to the right.
 * - From Content to Icon: Text slides in from the left to its place, app icons get dimmed again.
 */
private fun DrawScope.drawFolderTile(
    tile: TileModel,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    flipProgress: Float,
    targetFlip: Float,
    fluidTimeSec: Float,
    alpha: Float,
    paints: CanvasGridPaints,
    animState: TileAnimState? = null
) {
    val pageCount = max(1, (tile.folderApps.size + 3) / 4)
    val curPage = (animState?.folderCurrentPage ?: 0).coerceIn(0, pageCount - 1)
    val slideOffset = animState?.folderPageSlideOffset ?: 0f
    val slidePx = slideOffset * (w * 0.85f)

    // 1. Grid of apps inside: closely put together, dimmed in Icon mode (0.35f), un-dimmed in Content mode (1.0f)
    val iconAlpha = (0.35f + 0.65f * flipProgress).coerceIn(0f, 1f) * alpha
    if (tile.folderApps.isNotEmpty()) {
        val apps = tile.folderApps.drop(curPage * 4).take(4)
        val iconSize = 34f * paints.densityScale
        val colGap = 12f * paints.densityScale
        val rowGap = 10f * paints.densityScale
        val totalGridW = 2 * iconSize + colGap
        val totalGridH = 2 * iconSize + rowGap + 12f * paints.densityScale
        val startGridX = x + (w - totalGridW) * 0.5f + slidePx
        val startGridY = y + (h - totalGridH) * 0.5f

        paints.bitmapPaint.alpha = (iconAlpha * 255).roundToInt().coerceIn(0, 255)
        val labelAlpha = flipProgress.coerceIn(0f, 1f) * alpha
        paints.folderAppLabelPaint.alpha = (labelAlpha * 255).roundToInt().coerceIn(0, 255)

        apps.forEachIndexed { idx, app ->
            val col = idx % 2
            val row = idx / 2
            val ix = startGridX + col * (iconSize + colGap)
            val iy = startGridY + row * (iconSize + rowGap + 12f * paints.densityScale)

            val bmp = app.iconBitmap
            if (bmp != null && !bmp.isRecycled) {
                val rect = android.graphics.Rect(
                    ix.roundToInt(),
                    iy.roundToInt(),
                    (ix + iconSize).roundToInt(),
                    (iy + iconSize).roundToInt()
                )
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawBitmap(bmp, null, rect, paints.bitmapPaint)
                }
            }
            // If apps that are considered to be in a folder is not found on device, don't put placeholders.
            // Just use empty icon slots in folder icon mode.

            // In Content mode, display labels
            if (labelAlpha > 0.05f) {
                val label = if (app.label.length > 7) app.label.take(6) + "…" else app.label
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        label,
                        ix + iconSize * 0.5f,
                        iy + iconSize + 11f * paints.densityScale,
                        paints.folderAppLabelPaint
                    )
                }
            }
        }
    }

    // Dot indicators when there are more than 4 apps (multiple pages)
    if (pageCount > 1) {
        val dotRadius = 2.5f * paints.densityScale
        val dotSpacing = 8f * paints.densityScale
        val totalDotsW = (pageCount - 1) * dotSpacing
        val dotsStartX = x + (w - totalDotsW) * 0.5f
        val dotsY = y + h - 8f * paints.densityScale

        for (p in 0 until pageCount) {
            val dotX = dotsStartX + p * dotSpacing
            val isCurrent = (p == curPage)
            val dotAlpha = if (isCurrent) (0.95f * alpha) else (0.35f * alpha)
            drawCircle(
                color = Color.White.copy(alpha = dotAlpha),
                radius = if (isCurrent) dotRadius * 1.25f else dotRadius,
                center = Offset(dotX, dotsY)
            )
        }
    }

    // 2. Folder title text:
    // Icon mode: text at its current place (x + 16dp, y + 36dp)
    // Icon -> Content: text slides to right and disappears
    // Content -> Icon: text slides in from left to its place
    val textAlpha = (1f - flipProgress).coerceIn(0f, 1f) * alpha
    if (textAlpha > 0.005f) {
        val slideX = if (targetFlip >= 0.5f) {
            // Going to Content mode: slides to the right
            flipProgress * (w * 0.85f)
        } else {
            // Going to Icon mode: slides in from the left
            -flipProgress * (w * 0.85f)
        }
        val textStartX = x + 16f * paints.densityScale + slideX
        val textStartY = y + 36f * paints.densityScale

        paints.folderTitlePaint.alpha = (textAlpha * 255).roundToInt().coerceIn(0, 255)

        drawIntoCanvas { canvas ->
            val r1 = tile.folderTitleRow1.ifBlank { tile.label.take(6) }
            val r2 = tile.folderTitleRow2.ifBlank { tile.label.drop(6) }
            canvas.nativeCanvas.drawText(r1, textStartX, textStartY, paints.folderTitlePaint)
            canvas.nativeCanvas.drawText(r2, textStartX, textStartY + 26f * paints.densityScale, paints.folderTitlePaint)
        }
    }
}

/**
 * 2x2 Grid Lines & Migration Overlay for Folder Position Repositioning
 */
private fun DrawScope.drawMigrationOverlay(
    canvasW: Float,
    canvasH: Float,
    thirdPartyBaseY: Float,
    unitPx: Float,
    gutterPx: Float,
    horizontalPaddingPx: Float,
    scrollY: Float,
    selectedSlot: Pair<Int, Int>?,
    paints: CanvasGridPaints
) {
    // Slight dim effect over the screen
    drawRect(
        color = Color.Black.copy(alpha = 0.45f),
        topLeft = Offset.Zero,
        size = Size(canvasW, canvasH)
    )

    val candidateCols = listOf(0, 4)
    val candidateRows = listOf(0, 4, 8, 12, 16, 20)
    val gridStrokePx = 1.5f * paints.densityScale
    val btnW = 104f * paints.densityScale
    val btnH = 38f * paints.densityScale

    for (r in candidateRows) {
        val slotY = thirdPartyBaseY + r * (unitPx + gutterPx) - scrollY
        val slotH = 4 * unitPx + 3 * gutterPx

        if (slotY + slotH < -20f || slotY > canvasH + 20f) continue

        for (c in candidateCols) {
            val slotX = horizontalPaddingPx + c * (unitPx + gutterPx)
            val slotW = 4 * unitPx + 3 * gutterPx

            val isSelected = (selectedSlot == Pair(c, r))

            if (isSelected) {
                // Highlighted 2x2 square: accent border + white overlay
                drawRect(
                    color = Color.White.copy(alpha = 0.12f),
                    topLeft = Offset(slotX, slotY),
                    size = Size(slotW, slotH)
                )
                drawRect(
                    color = Color(0xFFFFBA2A),
                    topLeft = Offset(slotX, slotY),
                    size = Size(slotW, slotH),
                    style = Stroke(width = 3f * paints.densityScale)
                )

                // Centre aligned 'migrate' button with dark grey text, white BG
                val btnX = slotX + (slotW - btnW) * 0.5f
                val btnY = slotY + (slotH - btnH) * 0.5f

                drawRect(
                    color = Color.White,
                    topLeft = Offset(btnX, btnY),
                    size = Size(btnW, btnH)
                )

                val textY = btnY + btnH * 0.5f - ((paints.migrateBtnTextPaint.descent() + paints.migrateBtnTextPaint.ascent()) / 2f)
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText("MIGRATE", btnX + btnW * 0.5f, textY, paints.migrateBtnTextPaint)
                }
            } else {
                // 2x2 grid lines
                drawRect(
                    color = Color.White.copy(alpha = 0.35f),
                    topLeft = Offset(slotX, slotY),
                    size = Size(slotW, slotH),
                    style = Stroke(width = gridStrokePx)
                )
            }
        }
    }
}

fun findFolderAppAt(
    tile: TileModel,
    localX: Float,
    localY: Float,
    w: Float,
    h: Float,
    densityScale: Float,
    currentPage: Int = 0
): AppInfo? {
    if (!tile.isFolder || tile.folderApps.isEmpty()) return null
    val apps = tile.folderApps.drop(currentPage * 4).take(4)
    if (apps.isEmpty()) return null
    val iconSize = 34f * densityScale
    val colGap = 12f * densityScale
    val rowGap = 10f * densityScale
    val totalGridW = 2 * iconSize + colGap
    val totalGridH = 2 * iconSize + rowGap + 12f * densityScale
    val startGridX = (w - totalGridW) * 0.5f
    val startGridY = (h - totalGridH) * 0.5f

    // 1. Direct hit with generous touch padding around each icon
    val pad = 14f * densityScale
    apps.forEachIndexed { idx, app ->
        val col = idx % 2
        val row = idx / 2
        val ix = startGridX + col * (iconSize + colGap)
        val iy = startGridY + row * (iconSize + rowGap + 12f * densityScale)
        if (localX in (ix - pad)..(ix + iconSize + pad) && localY in (iy - pad)..(iy + iconSize + 16f * densityScale + pad)) {
            return app
        }
    }

    // 2. Quadrant fallback: map touch to the corresponding quadrant app
    val midX = w * 0.5f
    val midY = h * 0.5f
    val qCol = if (localX < midX) 0 else 1
    val qRow = if (localY < midY) 0 else 1
    val qIdx = qRow * 2 + qCol
    if (qIdx in apps.indices) {
        return apps[qIdx]
    }

    return null
}

/**
 * Ambient Ghost Echo Sonar Ping Animation for square icon tiles:
 * - Ping Wave 1: 0.0s to 1.5s, scales icon alpha mask up to 120% of tile dimensions, opacity 0.2 -> 0.
 * - Ping Wave 2 (Echo): begins after 150ms delay (0.15s to 1.65s), creates a ripple/wake effect.
 * - Easing: cubic-bezier(0.25, 1, 0.5, 1) (ease-out-quart) fluid liquid dissipation.
 */
private fun DrawScope.drawGhostEchoPing(
    tile: TileModel,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    elapsedSec: Float,
    easing: CubicBezierEasing,
    paints: CanvasGridPaints
) {
    val cx = x + w * 0.5f
    val cy = y + h * 0.5f
    val iconSizePx = when {
        tile.isMacro -> 58f * paints.densityScale
        tile.colSpan >= 4 && tile.rowSpan >= 2 -> 42f * paints.densityScale
        tile.colSpan == 2 && tile.rowSpan >= 4 -> 42f * paints.densityScale
        tile.colSpan == 2 && tile.rowSpan == 2 -> 34f * paints.densityScale
        tile.isMicro -> 18f * paints.densityScale
        else -> 28f * paints.densityScale
    }
    val iconCenterX = cx
    val iconCenterY = cy - (if (!tile.isMicro && tile.colSpan <= 2) 4f * paints.densityScale else 0f)

    val maxTileDim = max(w, h)
    val targetSizePx = maxTileDim * 1.2f

    val role = (tile.role ?: tile.id).lowercase()
    val isFlashlight = role.contains("flash") || role.contains("torch") || tile.id == "sys_flash"
    val bmp = tile.rawIconBitmap ?: tile.iconBitmap?.asAndroidBitmap()

    fun drawMask(currentSize: Float, alpha: Float) {
        if (alpha <= 0.001f || currentSize <= 0f) return

        if (isFlashlight) {
            drawFlashlightGhost(iconCenterX, iconCenterY, currentSize, alpha, paints)
        } else if (bmp != null && !bmp.isRecycled) {
            val left = (iconCenterX - currentSize * 0.5f).roundToInt()
            val top = (iconCenterY - currentSize * 0.5f).roundToInt()
            val right = (iconCenterX + currentSize * 0.5f).roundToInt()
            val bottom = (iconCenterY + currentSize * 0.5f).roundToInt()
            val dstRect = android.graphics.Rect(left, top, right, bottom)
            paints.ghostIconPaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawBitmap(bmp, null, dstRect, paints.ghostIconPaint)
            }
        } else {
            // Text glyph fallback
            val text = if (tile.isMicro) tile.label.take(1) else tile.label.take(2).uppercase()
            val baseSize = if (tile.isMicro) 12f * paints.densityScale else 20f * paints.densityScale
            val scaledTextSize = baseSize * (currentSize / iconSizePx)
            paints.ghostGlyphPaint.textSize = scaledTextSize
            paints.ghostGlyphPaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
            val fontMetrics = paints.ghostGlyphPaint.fontMetrics
            val textY = iconCenterY - (fontMetrics.descent + fontMetrics.ascent) / 2f
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(text, iconCenterX, textY, paints.ghostGlyphPaint)
            }
        }
    }

    // Wave 1: 0.0s to 1.5s
    val t1 = (elapsedSec / 1.5f).coerceIn(0f, 1f)
    if (t1 < 1.0f) {
        val eased1 = easing.transform(t1)
        val alpha1 = 0.20f * (1.0f - t1)
        val size1 = iconSizePx + (targetSizePx - iconSizePx) * eased1
        drawMask(size1, alpha1)
    }

    // Wave 2 (Echo Delay: begins after 150ms)
    val elapsed2 = elapsedSec - 0.15f
    if (elapsed2 >= 0f) {
        val t2 = (elapsed2 / 1.5f).coerceIn(0f, 1f)
        if (t2 < 1.0f) {
            val eased2 = easing.transform(t2)
            val alpha2 = 0.20f * (1.0f - t2)
            val size2 = iconSizePx + (targetSizePx - iconSizePx) * eased2
            drawMask(size2, alpha2)
        }
    }
}

private fun DrawScope.drawFlashlightGhost(
    cx: Float,
    cy: Float,
    size: Float,
    alpha: Float,
    paints: CanvasGridPaints
) {
    val s = size * 0.5f
    val paint = paints.solidWhitePaint
    paint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)

    val path = Path().apply {
        moveTo(cx - s * 0.45f, cy - s * 0.75f)
        lineTo(cx + s * 0.45f, cy - s * 0.75f)
        lineTo(cx + s * 0.30f, cy - s * 0.20f)
        lineTo(cx + s * 0.22f, cy + s * 0.75f)
        lineTo(cx - s * 0.22f, cy + s * 0.75f)
        lineTo(cx - s * 0.30f, cy - s * 0.20f)
        close()
    }

    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawPath(path, paint)
    }
}

private fun DrawScope.drawArrow(
    cx: Float,
    cy: Float,
    size: Float,
    isRight: Boolean,
    alpha: Float,
    paints: CanvasGridPaints
) {
    val d = if (isRight) 1f else -1f
    val half = size * 0.5f
    val path = Path().apply {
        moveTo(cx - half * d, cy - half)
        lineTo(cx + half * d, cy)
        lineTo(cx - half * d, cy + half)
    }
    val paint = paints.arrowPaint
    paint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawPath(path, paint)
    }
}

private fun DrawScope.drawIconMode(
    tile: TileModel,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    alpha: Float,
    isSwapMode: Boolean,
    paints: CanvasGridPaints
) {
    val cx = x + w * 0.5f
    val cy = y + h * 0.5f
    val role = (tile.role ?: tile.id).lowercase()

    // Icon size calculation
    val iconSizePx = when {
        tile.isMacro -> 58f * paints.densityScale
        tile.colSpan >= 4 && tile.rowSpan >= 2 -> 42f * paints.densityScale
        tile.colSpan == 2 && tile.rowSpan >= 4 -> 42f * paints.densityScale
        tile.colSpan == 2 && tile.rowSpan == 2 -> 34f * paints.densityScale
        tile.isMicro -> 18f * paints.densityScale
        else -> 28f * paints.densityScale
    }

    // 1. Flashlight / Torch Icon Rendering (CRISP VECTOR)
    if (role.contains("flash") || role.contains("torch") || tile.id == "sys_flash") {
        drawFlashlightIcon(cx, cy, iconSizePx, alpha, paints)
    } else {
        val iconBmp = tile.rawIconBitmap ?: tile.iconBitmap?.asAndroidBitmap()
        if (iconBmp != null && !iconBmp.isRecycled) {
            val left = cx - iconSizePx * 0.5f
            val top = (cy - iconSizePx * 0.5f) - (if (!tile.isMicro && tile.colSpan <= 2) 4f * paints.densityScale else 0f)
            val dstRect = android.graphics.Rect(left.roundToInt(), top.roundToInt(), (left + iconSizePx).roundToInt(), (top + iconSizePx).roundToInt())
            paints.bitmapPaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawBitmap(iconBmp, null, dstRect, paints.bitmapPaint)
            }
        } else {
            // Text glyph fallback
            val text = if (tile.isMicro) tile.label.take(1) else tile.label.take(2).uppercase()
            val paint = if (tile.isMicro) paints.microGlyphPaint else paints.glyphPaint
            paint.alpha = (alpha * 240).roundToInt().coerceIn(0, 255)
            val fontMetrics = paint.fontMetrics
            val textY = cy - (fontMetrics.descent + fontMetrics.ascent) / 2f
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(text, cx, textY, paint)
            }
        }
    }

    // Tile label at bottom left (except micro tiles)
    if (!tile.isMicro) {
        val labelPaint = when {
            tile.isMacro -> paints.labelLargePaint
            tile.colSpan >= 4 -> paints.labelMediumPaint
            else -> paints.labelSmallPaint
        }
        labelPaint.alpha = (alpha * 235).roundToInt().coerceIn(0, 255)
        val text = tile.label.uppercase()
        val textX = x + 6f * paints.densityScale
        val textY = y + h - 5f * paints.densityScale

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(text, textX, textY, labelPaint)
        }
    }
}

private fun DrawScope.drawFlashlightIcon(
    cx: Float,
    cy: Float,
    size: Float,
    alpha: Float,
    paints: CanvasGridPaints
) {
    val s = size * 0.5f
    val paint = paints.solidWhitePaint
    paint.alpha = (alpha * 245).roundToInt().coerceIn(0, 255)

    val path = Path().apply {
        // Flashlight head / cone
        moveTo(cx - s * 0.45f, cy - s * 0.75f)
        lineTo(cx + s * 0.45f, cy - s * 0.75f)
        lineTo(cx + s * 0.30f, cy - s * 0.20f)
        lineTo(cx + s * 0.22f, cy + s * 0.75f)
        lineTo(cx - s * 0.22f, cy + s * 0.75f)
        lineTo(cx - s * 0.30f, cy - s * 0.20f)
        close()
    }

    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawPath(path, paint)
        canvas.nativeCanvas.drawCircle(cx, cy + s * 0.15f, s * 0.12f, paints.blackDotPaint)
    }
}

private fun DrawScope.drawContentMode(
    tile: TileModel,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    galleryImages: List<Bitmap>,
    galleryScrollOffset: Float,
    isMusicPlaying: Boolean,
    currentMusicTrackTitle: String,
    fluidTimeSec: Float,
    alpha: Float,
    paints: CanvasGridPaints
) {
    val role = (tile.role ?: tile.id).lowercase()
    val headerH = (if (tile.isMacro) 25f else 21f) * paints.densityScale

    // 1. Header Bar: Tile color for the header
    drawRect(
        color = tile.accentColor.copy(alpha = alpha),
        topLeft = Offset(x, y),
        size = Size(w, headerH)
    )

    // 2. Icon at top left corner (small size, icon to its left)
    val smallIconSize = (if (tile.isMacro) 15f else 12.5f) * paints.densityScale
    val iconX = x + 5f * paints.densityScale
    val iconY = y + (headerH - smallIconSize) * 0.5f

    if (role.contains("flash") || role.contains("torch") || tile.id == "sys_flash") {
        drawFlashlightIcon(iconX + smallIconSize * 0.5f, iconY + smallIconSize * 0.5f, smallIconSize, alpha, paints)
    } else {
        val iconBmp = tile.rawIconBitmap ?: tile.iconBitmap?.asAndroidBitmap()
        if (iconBmp != null && !iconBmp.isRecycled) {
            val dstRect = android.graphics.Rect(
                iconX.roundToInt(),
                iconY.roundToInt(),
                (iconX + smallIconSize).roundToInt(),
                (iconY + smallIconSize).roundToInt()
            )
            paints.bitmapPaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawBitmap(iconBmp, null, dstRect, paints.bitmapPaint)
            }
        } else {
            paints.microGlyphPaint.alpha = (alpha * 240).roundToInt().coerceIn(0, 255)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    tile.label.take(1).uppercase(),
                    iconX + smallIconSize * 0.5f,
                    iconY + smallIconSize * 0.72f,
                    paints.microGlyphPaint
                )
            }
        }
    }

    // 3. Header Name next to icon (white text, uppercase)
    val headerText = tile.label.uppercase()
    paints.contentHeaderWhitePaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
    val headerTextX = iconX + smallIconSize + 5f * paints.densityScale
    val headerTextY = y + headerH * 0.5f - ((paints.contentHeaderWhitePaint.descent() + paints.contentHeaderWhitePaint.ascent()) / 2f)

    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawText(headerText, headerTextX, headerTextY, paints.contentHeaderWhitePaint)
    }

    // 4. White BG to its content area
    val contentY = y + headerH
    val contentH = h - headerH
    drawRect(
        color = Color.White.copy(alpha = alpha),
        topLeft = Offset(x, contentY),
        size = Size(w, contentH)
    )

    // 5. Draw specific content inside the white content area with crisp dark typography
    // Gallery Content
    if (role.contains("gallery") || role.contains("gal")) {
        if (galleryImages.isNotEmpty()) {
            val cx = x + w * 0.5f
            val cy = contentY + contentH * 0.5f
            val thumbSize = (if (contentH > 50f) 46f else 34f) * paints.densityScale
            val gap = 5f * paints.densityScale
            val step = thumbSize + gap

            paints.bitmapPaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)

            clipRect(x, contentY, x + w, contentY + contentH) {
                val numRows = max(1, (contentH / step).toInt())
                val rowOffsetStart = -((numRows - 1) * step * 0.5f)
                val numCols = (w / step).toInt() + 4
                val startColOffset = -(w * 0.45f)

                for (row in 0 until numRows) {
                    val rowY = cy + rowOffsetStart + row * step - thumbSize * 0.5f
                    val rowStagger = if (row % 2 == 1) step * 0.5f else 0f
                    val effectiveScroll = (galleryScrollOffset + rowStagger) % step

                    for (col in -1..numCols) {
                        val colX = cx + startColOffset + col * step - effectiveScroll - thumbSize * 0.5f
                        val imgIndex = abs(row * 7 + col + (galleryScrollOffset / step).toInt()) % galleryImages.size
                        val bmp = galleryImages[imgIndex]
                        if (!bmp.isRecycled) {
                            val dst = android.graphics.Rect(
                                colX.roundToInt(),
                                rowY.roundToInt(),
                                (colX + thumbSize).roundToInt(),
                                (rowY + thumbSize).roundToInt()
                            )
                            drawIntoCanvas { canvas ->
                                canvas.nativeCanvas.drawBitmap(bmp, null, dst, paints.bitmapPaint)
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // Music Player Content
    if (role.contains("music") || role.contains("mus")) {
        val trackName = if (currentMusicTrackTitle.isNotBlank()) currentMusicTrackTitle
        else if (tile.contentLines.isNotEmpty()) tile.contentLines.first()
        else "Audio Player Active"

        val truncated = if (trackName.length > 26) trackName.take(24) + "…" else trackName
        paints.contentDarkHeadingPaint.alpha = (alpha * 245).roundToInt().coerceIn(0, 255)
        val textStartX = x + 7f * paints.densityScale
        var textStartY = contentY + 13f * paints.densityScale

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(truncated, textStartX, textStartY, paints.contentDarkHeadingPaint)
        }

        // Draw animated sound waves
        val btnAreaY = y + h - 14f * paints.densityScale
        val waveWidth = 3f * paints.densityScale
        for (b in 0 until 5) {
            val barH = (3f + 7f * abs(sin(fluidTimeSec * 6f + b * 1.2f))) * paints.densityScale
            drawRect(
                color = tile.accentColor.copy(alpha = 0.90f * alpha),
                topLeft = Offset(x + 9f * paints.densityScale + b * (waveWidth + 2f * paints.densityScale), btnAreaY - barH * 0.5f),
                size = Size(waveWidth, barH)
            )
        }

        // Previous [⏮], Play/Pause [⏸] or [▶], Next [⏭] in dark ink text on white background
        val cx = x + w * 0.5f
        paints.contentDarkCtrlPaint.alpha = (alpha * 245).roundToInt().coerceIn(0, 255)
        val ctrlText = if (isMusicPlaying) "  ⏮    ⏸    ⏭  " else "  ⏮    ▶    ⏭  "
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(ctrlText, cx + 4f * paints.densityScale, y + h - 5f * paints.densityScale, paints.contentDarkCtrlPaint)
        }
        return
    }

    // Clock Content Mode
    if (role.contains("clock") || role.contains("time")) {
        val textStartX = x + 7f * paints.densityScale
        var textStartY = contentY + 11f * paints.densityScale

        paints.contentDarkSecondaryPaint.alpha = (alpha * 230).roundToInt().coerceIn(0, 255)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText("NEXT ALARM", textStartX, textStartY, paints.contentDarkSecondaryPaint)
        }

        textStartY += 13f * paints.densityScale
        paints.contentDarkHeadingPaint.alpha = (alpha * 245).roundToInt().coerceIn(0, 255)

        if (tile.contentLines.isNotEmpty()) {
            for (line in tile.contentLines.take(3)) {
                val truncated = if (line.length > 28) line.take(26) + "…" else line
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(truncated, textStartX, textStartY, paints.contentDarkHeadingPaint)
                }
                textStartY += 11f * paints.densityScale
                if (textStartY > y + h - 5f) break
            }
        } else {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText("No upcoming alarm", textStartX, textStartY, paints.contentDarkHeadingPaint)
            }
        }
        return
    }

    // Flashlight / Torch Content Mode
    if (role.contains("flash") || role.contains("torch") || tile.id == "sys_flash") {
        val textStartX = x + 7f * paints.densityScale
        val textStartY = contentY + 13f * paints.densityScale
        paints.contentDarkHeadingPaint.alpha = (alpha * 245).roundToInt().coerceIn(0, 255)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText("TORCH // ACTIVE", textStartX, textStartY, paints.contentDarkHeadingPaint)
        }
        paints.contentDarkSecondaryPaint.alpha = (alpha * 220).roundToInt().coerceIn(0, 255)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText("Tap tile to toggle torch", textStartX, textStartY + 12f * paints.densityScale, paints.contentDarkSecondaryPaint)
        }
        return
    }

    // General Apps / Notifications Content Mode
    val textStartX = x + 7f * paints.densityScale
    var textStartY = contentY + 12f * paints.densityScale

    if (tile.contentLines.isNotEmpty()) {
        tile.contentLines.take(3).forEachIndexed { i, line ->
            val truncated = if (line.length > 28) line.take(26) + "…" else line
            val paint = if (i == 0) paints.contentDarkHeadingPaint else paints.contentDarkBodyPaint
            paint.alpha = (alpha * 240).roundToInt().coerceIn(0, 255)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(truncated, textStartX, textStartY, paint)
            }
            textStartY += 12f * paints.densityScale
            if (textStartY > y + h - 5f) return@forEachIndexed
        }
    } else {
        paints.contentDarkSecondaryPaint.alpha = (alpha * 220).roundToInt().coerceIn(0, 255)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText("No new notifications", textStartX, textStartY, paints.contentDarkSecondaryPaint)
        }
    }
}

class CanvasGridPaints(val densityScale: Float) {
    val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Ghost Echo Sonar Ping Paints (tint white alpha mask)
    val ghostIconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
    }

    val ghostGlyphPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    val solidWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }

    val blackDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(200, 30, 30, 35)
        style = Paint.Style.FILL
    }

    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * densityScale
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    val microGlyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textSize = 12f * densityScale
        textAlign = Paint.Align.CENTER
    }

    val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textSize = 20f * densityScale
        textAlign = Paint.Align.CENTER
    }

    val labelSmallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = 9f * densityScale
        letterSpacing = 0.06f
    }

    val labelMediumPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = 10.5f * densityScale
        letterSpacing = 0.07f
    }

    val labelLargePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = 12f * densityScale
        letterSpacing = 0.08f
    }

    val contentHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textSize = 11f * densityScale
    }

    val contentHeaderWhitePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textSize = 9.5f * densityScale
        letterSpacing = 0.05f
    }

    val contentDarkHeadingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#18181B")
        typeface = Typeface.DEFAULT_BOLD
        textSize = 10f * densityScale
    }

    val contentDarkBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#3F3F46")
        typeface = Typeface.DEFAULT
        textSize = 8.5f * densityScale
    }

    val contentDarkSecondaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#71717A")
        typeface = Typeface.DEFAULT
        textSize = 8f * densityScale
    }

    val contentDarkCtrlPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#18181B")
        typeface = Typeface.DEFAULT_BOLD
        textSize = 10.5f * densityScale
    }

    val contentBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = Typeface.DEFAULT
        textSize = 9.5f * densityScale
    }

    val folderTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textSize = 21f * densityScale
        letterSpacing = 0.08f
    }

    val folderAppLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = Typeface.DEFAULT
        textSize = 8.5f * densityScale
        textAlign = Paint.Align.CENTER
    }

    val migrateBtnTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#202228")
        typeface = Typeface.DEFAULT_BOLD
        textSize = 12f * densityScale
        letterSpacing = 0.08f
        textAlign = Paint.Align.CENTER
    }
}
