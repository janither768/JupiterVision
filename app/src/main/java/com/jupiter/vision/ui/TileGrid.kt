package com.jupiter.vision.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jupiter.vision.model.TileMode
import com.jupiter.vision.model.TileModel
import com.jupiter.vision.util.Packer
import kotlin.math.roundToInt

@Composable
fun TileGrid(
    systemTiles: List<TileModel>,
    thirdPartyTiles: List<TileModel>,
    contentModes: Map<String, TileMode>,
    wallpaper: ImageBitmap?,
    atmosphereFrames: List<ImageBitmap>? = null,
    atmosphereIndex: Int = 0,
    galleryImages: List<Bitmap> = emptyList(),
    scrollOffset: Int = 0,
    isFocusActive: Boolean = false,
    onTileClick: (TileModel) -> Unit,
    onOpenMiniApp: (TileModel, Rect) -> Unit,
    onOpenProperties: (TileModel, Rect) -> Unit,
    gutter: Dp = 4.dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Region A: System Zone
        SystemZone(
            tiles = systemTiles,
            contentModes = contentModes,
            wallpaper = wallpaper,
            atmosphereFrames = atmosphereFrames,
            atmosphereIndex = atmosphereIndex,
            galleryImages = galleryImages,
            isFocusActive = isFocusActive,
            onTileClick = onTileClick,
            onOpenMiniApp = onOpenMiniApp,
            onOpenProperties = onOpenProperties,
            gutter = gutter
        )

        // Region B: Gap
        Spacer(Modifier.height(12.dp))

        // Region C: Third-party Smart Zone
        if (thirdPartyTiles.isNotEmpty()) {
            ThirdPartyZone(
                tiles = thirdPartyTiles,
                contentModes = contentModes,
                wallpaper = wallpaper,
                atmosphereFrames = atmosphereFrames,
                atmosphereIndex = atmosphereIndex,
                galleryImages = galleryImages,
                isFocusActive = isFocusActive,
                onTileClick = onTileClick,
                onOpenMiniApp = onOpenMiniApp,
                onOpenProperties = onOpenProperties,
                gutter = gutter
            )
        }
    }
}

@Composable
private fun SystemZone(
    tiles: List<TileModel>,
    contentModes: Map<String, TileMode>,
    wallpaper: ImageBitmap?,
    atmosphereFrames: List<ImageBitmap>?,
    atmosphereIndex: Int,
    galleryImages: List<Bitmap>,
    isFocusActive: Boolean,
    onTileClick: (TileModel) -> Unit,
    onOpenMiniApp: (TileModel, Rect) -> Unit,
    onOpenProperties: (TileModel, Rect) -> Unit,
    gutter: Dp,
) {
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val g = with(density) { gutter.roundToPx() }
        val units = Packer.UNITS_PER_ROW
        val unit = (constraints.maxWidth - g * (units - 1)) / units.toFloat()

        Layout(
            content = {
                tiles.forEach { tile ->
                    key(tile.id) {
                        val c = tile.fixedCol ?: 0
                        val r = tile.fixedRow ?: 0
                        val tilePixelX = (c * (unit + g)).roundToInt()
                        val tilePixelY = (r * (unit + g)).roundToInt()

                        TileContainer(
                            tile = tile,
                            contentMode = contentModes[tile.id] ?: TileMode.ICON,
                            wallpaper = wallpaper,
                            atmosphereFrames = atmosphereFrames,
                            atmosphereIndex = atmosphereIndex,
                            galleryImages = galleryImages,
                            tilePixelX = tilePixelX,
                            tilePixelY = tilePixelY,
                            gridX = c,
                            gridY = r,
                            isFocusActive = isFocusActive,
                            onClick = { onTileClick(tile) },
                            onOpenMiniApp = { rect -> onOpenMiniApp(tile, rect) },
                            onOpenProperties = { rect -> onOpenProperties(tile, rect) }
                        )
                    }
                }
            }
        ) { measurables, constraints ->
            val placeables = measurables.mapIndexed { i, m ->
                val t = tiles[i]
                val w = (t.colSpan * unit + (t.colSpan - 1) * g).roundToInt().coerceAtLeast(0)
                val h = (t.rowSpan * unit + (t.rowSpan - 1) * g).roundToInt().coerceAtLeast(0)
                m.measure(Constraints.fixed(w, h))
            }

            val totalH = (6 * unit + 5 * g).roundToInt().coerceAtLeast(0)

            layout(constraints.maxWidth, totalH) {
                placeables.forEachIndexed { i, p ->
                    val t = tiles[i]
                    val c = t.fixedCol ?: 0
                    val r = t.fixedRow ?: 0
                    val x = (c * (unit + g)).roundToInt()
                    val y = (r * (unit + g)).roundToInt()
                    p.place(x, y)
                }
            }
        }
    }
}

@Composable
private fun ThirdPartyZone(
    tiles: List<TileModel>,
    contentModes: Map<String, TileMode>,
    wallpaper: ImageBitmap?,
    atmosphereFrames: List<ImageBitmap>?,
    atmosphereIndex: Int,
    galleryImages: List<Bitmap>,
    isFocusActive: Boolean = false,
    onTileClick: (TileModel) -> Unit,
    onOpenMiniApp: (TileModel, Rect) -> Unit,
    onOpenProperties: (TileModel, Rect) -> Unit,
    gutter: Dp,
) {
    val placements = remember(tiles) { Packer.pack(tiles) }
    val density = LocalDensity.current
    val bezierEase = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val g = with(density) { gutter.roundToPx() }
        val units = Packer.UNITS_PER_ROW
        val unit = (constraints.maxWidth - g * (units - 1)) / units.toFloat()

        val maxRow = placements.mapIndexed { i, p ->
            p.row + tiles[i].rowSpan
        }.maxOrNull() ?: 1

        val totalH = (maxRow * unit + (maxRow - 1) * g).roundToInt().coerceAtLeast(0)
        val systemZoneHeightPx = (6 * unit + 5 * g + with(density) { 12.dp.roundToPx() }).roundToInt()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { totalH.toDp() })
        ) {
            tiles.forEachIndexed { i, tile ->
                val col = if (i < placements.size) placements[i].col else 0
                val row = if (i < placements.size) placements[i].row else 0

                val targetX = (col * (unit + g)).roundToInt()
                val targetY = (row * (unit + g)).roundToInt()
                val targetW = (tile.colSpan * unit + (tile.colSpan - 1) * g).roundToInt().coerceAtLeast(0)
                val targetH = (tile.rowSpan * unit + (tile.rowSpan - 1) * g).roundToInt().coerceAtLeast(0)

                val animX by animateIntAsState(
                    targetValue = targetX,
                    animationSpec = tween(380, easing = bezierEase),
                    label = "tx_${tile.id}"
                )
                val animY by animateIntAsState(
                    targetValue = targetY,
                    animationSpec = tween(380, easing = bezierEase),
                    label = "ty_${tile.id}"
                )
                val animW by animateIntAsState(
                    targetValue = targetW,
                    animationSpec = tween(380, easing = bezierEase),
                    label = "tw_${tile.id}"
                )
                val animH by animateIntAsState(
                    targetValue = targetH,
                    animationSpec = tween(380, easing = bezierEase),
                    label = "th_${tile.id}"
                )

                val tilePixelX = targetX
                val tilePixelY = systemZoneHeightPx + targetY

                key(tile.id) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(animX, animY) }
                            .size(
                                width = with(density) { animW.toDp() },
                                height = with(density) { animH.toDp() }
                            )
                    ) {
                        TileContainer(
                            tile = tile,
                            contentMode = contentModes[tile.id] ?: TileMode.ICON,
                            wallpaper = wallpaper,
                            atmosphereFrames = atmosphereFrames,
                            atmosphereIndex = atmosphereIndex,
                            galleryImages = galleryImages,
                            tilePixelX = tilePixelX,
                            tilePixelY = tilePixelY,
                            gridX = col,
                            gridY = row + 6,
                            isFocusActive = isFocusActive,
                            onClick = { onTileClick(tile) },
                            onOpenMiniApp = { rect -> onOpenMiniApp(tile, rect) },
                            onOpenProperties = { rect -> onOpenProperties(tile, rect) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TileContainer(
    tile: TileModel,
    contentMode: TileMode,
    wallpaper: ImageBitmap?,
    atmosphereFrames: List<ImageBitmap>?,
    atmosphereIndex: Int,
    galleryImages: List<Bitmap>,
    tilePixelX: Int,
    tilePixelY: Int,
    gridX: Int = 0,
    gridY: Int = 0,
    isFocusActive: Boolean = false,
    onClick: () -> Unit,
    onOpenMiniApp: (Rect) -> Unit,
    onOpenProperties: (Rect) -> Unit,
) {
    var cachedBounds = remember { Rect.Zero }

    Box(
        modifier = Modifier
            .onGloballyPositioned { coords ->
                cachedBounds = coords.boundsInRoot()
            }
    ) {
        TileView(
            tile = tile,
            contentMode = contentMode,
            wallpaper = wallpaper,
            atmosphereFrames = atmosphereFrames,
            atmosphereIndex = atmosphereIndex,
            tileOffsetX = tilePixelX,
            tileOffsetY = tilePixelY,
            gridX = gridX,
            gridY = gridY,
            isFocusActive = isFocusActive,
            galleryImages = galleryImages,
            onClick = onClick,
            onOpenMiniApp = { onOpenMiniApp(cachedBounds) },
            onOpenProperties = { onOpenProperties(cachedBounds) }
        )
    }
}
