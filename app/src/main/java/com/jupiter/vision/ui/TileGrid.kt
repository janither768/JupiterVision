package com.jupiter.vision.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jupiter.vision.model.TileMode
import com.jupiter.vision.model.TileModel
import com.jupiter.vision.util.Packer
import kotlin.math.roundToInt

@Composable
fun TileGrid(
    tiles: List<TileModel>,
    contentModes: Map<String, TileMode>,
    wallpaper: ImageBitmap?,
    onTileClick: (TileModel) -> Unit,
    onTileLongPress: (TileModel) -> Unit,
    modifier: Modifier = Modifier,
    gutter: Dp = 4.dp,
) {
    val placements = remember(tiles) { Packer.pack(tiles) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Layout(
            content = {
                tiles.forEach { tile ->
                    key(tile.id) {
                        TileGridItem(
                            tile = tile,
                            contentMode = contentModes[tile.id] ?: TileMode.ICON,
                            wallpaper = wallpaper,
                            onClick = { onTileClick(tile) },
                            onLongPress = { onTileLongPress(tile) },
                        )
                    }
                }
            }
        ) { measurables, constraints ->
            val g = gutter.roundToPx()
            val units = Packer.UNITS_PER_ROW
            // Derives unit size from actual available width
            val unit = (constraints.maxWidth - g * (units - 1)) / units.toFloat()

            val placeables = measurables.mapIndexed { i, m ->
                val t = tiles[i]
                val w = (t.colSpan * unit + (t.colSpan - 1) * g).roundToInt().coerceAtLeast(0)
                val h = (t.rowSpan * unit + (t.rowSpan - 1) * g).roundToInt().coerceAtLeast(0)
                m.measure(Constraints.fixed(w, h))
            }

            val maxRow = placements.mapIndexed { i, p ->
                p.row + tiles[i].rowSpan
            }.maxOrNull() ?: 1

            val totalH = (maxRow * unit + (maxRow - 1) * g).roundToInt().coerceAtLeast(0)

            layout(constraints.maxWidth, totalH) {
                placeables.forEachIndexed { i, p ->
                    val (c, r) = placements[i]
                    val x = (c * unit + c * g).roundToInt()
                    val y = (r * unit + r * g).roundToInt()
                    p.place(x, y)
                }
            }
        }
    }
}

@Composable
private fun TileGridItem(
    tile: TileModel,
    contentMode: TileMode,
    wallpaper: ImageBitmap?,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    TileViewWithOffset(
        tile = tile,
        contentMode = contentMode,
        wallpaper = wallpaper,
        onClick = onClick,
        onLongPress = onLongPress,
    )
}

/**
 * Measures each tile's on-screen pixel offset in grid space via onGloballyPositioned
 * so TileView's wallpaper slice can perfectly align with the global wallpaper backdrop.
 */
@Composable
private fun TileViewWithOffset(
    tile: TileModel,
    contentMode: TileMode,
    wallpaper: ImageBitmap?,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    var offsetX by remember { mutableIntStateOf(0) }
    var offsetY by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier.onGloballyPositioned { coords ->
            offsetX = coords.positionInParent().x.toInt()
            offsetY = coords.positionInParent().y.toInt()
        }
    ) {
        TileView(
            tile = tile,
            contentMode = contentMode,
            wallpaper = wallpaper,
            tileOffsetX = offsetX,
            tileOffsetY = offsetY,
            onClick = onClick,
            onLongPress = onLongPress,
        )
    }
}
