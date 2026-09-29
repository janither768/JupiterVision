package com.jupiter.vision.util

import androidx.compose.ui.graphics.Color
import com.jupiter.vision.model.AppInfo
import com.jupiter.vision.model.ColorEngine
import com.jupiter.vision.model.TileModel
import kotlin.math.abs
import kotlin.math.max

data class Placement(val col: Int, val row: Int)

enum class ResizeDirection {
    UP, DOWN, LEFT, RIGHT,
    TOP_EXPAND, TOP_CONTRACT,
    BOTTOM_EXPAND, BOTTOM_CONTRACT,
    LEFT_EXPAND, LEFT_CONTRACT,
    RIGHT_EXPAND, RIGHT_CONTRACT
}

/**
 * JUPITERVISION 2112.14 — Packing & Bidirectional Live Reflow Engine
 */
object Packer {
    const val UNITS_PER_ROW = 8

    fun pack(tiles: List<TileModel>): List<Placement> {
        val rows = mutableListOf<BooleanArray>()
        fun ensure(r: Int) { while (rows.size <= r) rows.add(BooleanArray(UNITS_PER_ROW)) }
        fun fits(c: Int, r: Int, w: Int, h: Int): Boolean {
            if (c + w > UNITS_PER_ROW) return false
            for (rr in r until r + h) {
                ensure(rr)
                for (cc in c until c + w) if (rows[rr][cc]) return false
            }
            return true
        }
        fun occupy(c: Int, r: Int, w: Int, h: Int) {
            for (rr in r until r + h) {
                ensure(rr)
                for (cc in c until c + w) rows[rr][cc] = true
            }
        }
        val out = mutableListOf<Placement>()
        for (t in tiles) {
            if (t.fixedCol != null && t.fixedRow != null) {
                occupy(t.fixedCol, t.fixedRow, t.colSpan, t.rowSpan)
                out.add(Placement(t.fixedCol, t.fixedRow))
                continue
            }

            var placed = false
            outer@ for (r in 0..4000) {
                for (c in 0..(UNITS_PER_ROW - t.colSpan)) {
                    if (fits(c, r, t.colSpan, t.rowSpan)) {
                        occupy(c, r, t.colSpan, t.rowSpan)
                        out.add(Placement(c, r))
                        placed = true
                        break@outer
                    }
                }
            }
            if (!placed) out.add(Placement(0, 0))
        }
        return out
    }

    /**
     * Bidirectional live reflow for edge arrows.
     */
    fun reflowTileDirection(
        tiles: List<TileModel>,
        targetTileId: String,
        direction: ResizeDirection
    ): List<TileModel>? {
        val target = tiles.firstOrNull { it.id == targetTileId } ?: return null
        val c = target.fixedCol ?: target.gridCol
        val r = target.fixedRow ?: target.gridRow
        val w = target.colSpan
        val h = target.rowSpan

        var newC = c
        var newR = r
        var newW = w
        var newH = h

        when (direction) {
            ResizeDirection.RIGHT, ResizeDirection.RIGHT_EXPAND -> {
                when {
                    w == 1 && h == 1 -> { newW = 2; newH = 2 }
                    w == 2 && h == 2 -> { newW = 4; newH = 2 }
                    w == 2 && h == 4 -> { newW = 4; newH = 4 }
                    w == 4 -> { newW = 2 }
                    else -> return null
                }
                if (newC + newW > UNITS_PER_ROW) {
                    newC = UNITS_PER_ROW - newW
                }
            }
            ResizeDirection.RIGHT_CONTRACT -> {
                when {
                    w == 4 && h == 4 -> { newW = 2; newH = 4 }
                    w == 4 && h == 2 -> { newW = 2; newH = 2 }
                    w == 2 && h == 2 -> { newW = 1; newH = 1 }
                    else -> { newW = 2; newH = 2 }
                }
            }
            ResizeDirection.LEFT, ResizeDirection.LEFT_EXPAND -> {
                when {
                    w == 1 && h == 1 -> {
                        newW = 2; newH = 2
                        newC = max(0, c - 1)
                    }
                    w == 2 && h == 2 -> {
                        newW = 4; newH = 2
                        newC = max(0, c - 2)
                    }
                    w == 2 && h == 4 -> {
                        newW = 4; newH = 4
                        newC = max(0, c - 2)
                    }
                    else -> {
                        newW = 2
                    }
                }
            }
            ResizeDirection.LEFT_CONTRACT -> {
                when {
                    w == 4 && h == 4 -> { newW = 2; newH = 4; newC = minOf(UNITS_PER_ROW - 2, c + 2) }
                    w == 4 && h == 2 -> { newW = 2; newH = 2; newC = minOf(UNITS_PER_ROW - 2, c + 2) }
                    w == 2 && h == 2 -> { newW = 1; newH = 1 }
                    else -> return null
                }
            }
            ResizeDirection.DOWN, ResizeDirection.BOTTOM_EXPAND -> {
                when {
                    w == 1 && h == 1 -> { newW = 2; newH = 2 }
                    w == 2 && h == 2 -> { newW = 2; newH = 4 }
                    w == 4 && h == 2 -> { newW = 4; newH = 4 }
                    h == 4 -> { newH = 2 }
                    else -> return null
                }
            }
            ResizeDirection.BOTTOM_CONTRACT -> {
                when {
                    w == 4 && h == 4 -> { newW = 4; newH = 2 }
                    w == 2 && h == 4 -> { newW = 2; newH = 2 }
                    w == 2 && h == 2 -> { newW = 1; newH = 1 }
                    else -> { newW = 2; newH = 2 }
                }
            }
            ResizeDirection.UP, ResizeDirection.TOP_EXPAND -> {
                when {
                    w == 1 && h == 1 -> {
                        newW = 2; newH = 2
                        newR = max(0, r - 1)
                    }
                    w == 2 && h == 2 -> {
                        newW = 2; newH = 4
                        newR = max(0, r - 2)
                    }
                    w == 4 && h == 2 -> {
                        newW = 4; newH = 4
                        newR = max(0, r - 2)
                    }
                    else -> {
                        newH = 2
                    }
                }
            }
            ResizeDirection.TOP_CONTRACT -> {
                when {
                    w == 4 && h == 4 -> { newW = 4; newH = 2; newR = r + 2 }
                    w == 2 && h == 4 -> { newW = 2; newH = 2; newR = r + 2 }
                    w == 2 && h == 2 -> { newW = 1; newH = 1 }
                    else -> return null
                }
            }
        }

        return reflowTileChange(tiles, targetTileId, newC, newR, newW, newH)
    }

    fun reflowTileChange(
        tiles: List<TileModel>,
        targetTileId: String,
        targetCol: Int,
        targetRow: Int,
        targetColSpan: Int,
        targetRowSpan: Int
    ): List<TileModel>? {
        if (targetColSpan < 1 || targetRowSpan < 1) return null
        if (targetColSpan > 4 || targetRowSpan > 4) return null
        if (targetColSpan == 3 || targetRowSpan == 3) return null
        if (targetColSpan == 2 && targetRowSpan == 1) return null
        if (targetColSpan == 1 && targetRowSpan == 2) return null
        if (targetCol < 0 || targetCol + targetColSpan > UNITS_PER_ROW) return null
        if (targetRow < 0) return null

        val targetIndex = tiles.indexOfFirst { it.id == targetTileId }
        if (targetIndex == -1) return null
        val targetTile = tiles[targetIndex]

        val isMicro = targetColSpan == 1 && targetRowSpan == 1
        val isMacro = targetColSpan == 4 && targetRowSpan == 4
        val updatedTarget = targetTile.copy(
            colSpan = targetColSpan,
            rowSpan = targetRowSpan,
            gridCol = targetCol,
            gridRow = targetRow,
            fixedCol = targetCol,
            fixedRow = targetRow,
            isMicro = isMicro,
            isMacro = isMacro
        )

        val intactTiles = mutableListOf<TileModel>()
        val displacedTiles = mutableListOf<TileModel>()

        for (i in tiles.indices) {
            if (i == targetIndex) continue
            val t = tiles[i]
            val tc = t.fixedCol ?: t.gridCol
            val tr = t.fixedRow ?: t.gridRow
            if (rectsIntersect(tc, tr, t.colSpan, t.rowSpan, targetCol, targetRow, targetColSpan, targetRowSpan)) {
                displacedTiles.add(t)
            } else {
                intactTiles.add(t)
            }
        }

        if (displacedTiles.isEmpty()) {
            return tiles.map { if (it.id == targetTileId) updatedTarget else it }
        }

        val maxGridRows = 120
        val grid = Array(maxGridRows) { BooleanArray(UNITS_PER_ROW) }

        fun canFit(c: Int, r: Int, w: Int, h: Int): Boolean {
            if (c + w > UNITS_PER_ROW || r + h > maxGridRows) return false
            for (rr in r until r + h) {
                for (cc in c until c + w) {
                    if (grid[rr][cc]) return false
                }
            }
            return true
        }

        fun mark(c: Int, r: Int, w: Int, h: Int) {
            for (rr in r until r + h) {
                for (cc in c until c + w) {
                    grid[rr][cc] = true
                }
            }
        }

        mark(targetCol, targetRow, targetColSpan, targetRowSpan)

        for (t in intactTiles) {
            val tc = (t.fixedCol ?: t.gridCol).coerceIn(0, UNITS_PER_ROW - t.colSpan)
            val tr = (t.fixedRow ?: t.gridRow).coerceAtLeast(0)
            mark(tc, tr, t.colSpan, t.rowSpan)
        }

        val reflowedDisplaced = mutableListOf<TileModel>()

        for (d in displacedTiles) {
            val origC = d.fixedCol ?: d.gridCol
            val origR = d.fixedRow ?: d.gridRow

            val candidateSizes = mutableListOf<Pair<Int, Int>>()
            candidateSizes.add(Pair(d.colSpan, d.rowSpan))

            if (d.colSpan == 4 && d.rowSpan == 4) {
                candidateSizes.add(Pair(4, 2))
                candidateSizes.add(Pair(2, 4))
                candidateSizes.add(Pair(2, 2))
            } else if (d.colSpan == 4 && d.rowSpan == 2) {
                candidateSizes.add(Pair(2, 2))
            } else if (d.colSpan == 2 && d.rowSpan == 4) {
                candidateSizes.add(Pair(2, 2))
            } else if (d.colSpan == 2 && d.rowSpan == 2) {
                candidateSizes.add(Pair(1, 1))
            }

            var bestSlot: Pair<Int, Int>? = null
            var chosenSize: Pair<Int, Int>? = null
            var lowestScore = Float.MAX_VALUE

            for (size in candidateSizes) {
                val (w, h) = size
                val minR = max(0, origR - 4)
                val maxR = origR + 24
                for (r in minR..maxR) {
                    for (c in 0..(UNITS_PER_ROW - w)) {
                        if (canFit(c, r, w, h)) {
                            val dist = abs(c - origC) + abs(r - origR) * 1.4f
                            val sizePenalty = if (w == d.colSpan && h == d.rowSpan) 0f else 3.5f
                            val score = dist + sizePenalty
                            if (score < lowestScore) {
                                lowestScore = score
                                bestSlot = Pair(c, r)
                                chosenSize = size
                            }
                        }
                    }
                }
                if (bestSlot != null && chosenSize == Pair(d.colSpan, d.rowSpan)) {
                    break
                }
            }

            if (bestSlot == null || chosenSize == null) {
                return null
            }

            val (placedC, placedR) = bestSlot
            val (placedW, placedH) = chosenSize
            mark(placedC, placedR, placedW, placedH)

            val isDisplacedMicro = placedW == 1 && placedH == 1
            val isDisplacedMacro = placedW == 4 && placedH == 4

            reflowedDisplaced.add(
                d.copy(
                    colSpan = placedW,
                    rowSpan = placedH,
                    gridCol = placedC,
                    gridRow = placedR,
                    fixedCol = placedC,
                    fixedRow = placedR,
                    isMicro = isDisplacedMicro,
                    isMacro = isDisplacedMacro
                )
            )
        }

        val result = mutableListOf<TileModel>()
        result.add(updatedTarget)
        result.addAll(intactTiles)
        result.addAll(reflowedDisplaced)
        return result
    }

    private fun rectsIntersect(
        c1: Int, r1: Int, w1: Int, h1: Int,
        c2: Int, r2: Int, w2: Int, h2: Int
    ): Boolean {
        return !(c1 + w1 <= c2 || c2 + w2 <= c1 || r1 + h1 <= r2 || r2 + h2 <= r1)
    }

    fun packThirdPartyApps(
        apps: List<AppInfo>,
        palette: List<Color>,
        activityMap: Map<String, List<String>> = emptyMap(),
        startIndex: Int = 0
    ): List<TileModel> {
        val result = mutableListOf<TileModel>()
        if (apps.isEmpty()) return result

        val (folderTiles, standaloneApps) = IntelligentFolderEngine.partitionAppsAndFolders(apps)

        val rows = mutableListOf<BooleanArray>()
        fun ensure(r: Int) { while (rows.size <= r) rows.add(BooleanArray(UNITS_PER_ROW)) }
        fun fits(c: Int, r: Int, w: Int, h: Int): Boolean {
            if (c + w > UNITS_PER_ROW) return false
            for (rr in r until r + h) {
                ensure(rr)
                for (cc in c until c + w) if (rows[rr][cc]) return false
            }
            return true
        }
        fun occupy(c: Int, r: Int, w: Int, h: Int) {
            for (rr in r until r + h) {
                ensure(rr)
                for (cc in c until c + w) rows[rr][cc] = true
            }
        }

        var appIdx = 0
        var tileColorCount = 0

        fun createStandaloneTile(app: AppInfo, c: Int, r: Int, w: Int, h: Int): TileModel {
            val notifs = activityMap[app.packageName] ?: emptyList()
            val color = if (app.iconBitmap != null) {
                ColorEngine.fromAppIcon(app.iconBitmap, palette[(startIndex + tileColorCount) % palette.size])
            } else {
                palette[(startIndex + tileColorCount) % palette.size]
            }
            tileColorCount++
            return TileModel(
                id = "app_${app.packageName}",
                label = app.label,
                packageName = app.packageName,
                role = null,
                fixedCol = c,
                fixedRow = r,
                gridCol = c,
                gridRow = r,
                colSpan = w,
                rowSpan = h,
                isMicro = (w == 1 && h == 1),
                isMacro = (w == 4 && h == 4),
                isSystem = false,
                hasActivity = notifs.isNotEmpty(),
                contentLines = notifs,
                accentColor = color,
                iconBitmap = app.iconImageBitmap,
                rawIconBitmap = app.iconBitmap
            )
        }

        var currentRow = 0
        var folderIdx = 0
        var zigZagStep = 0

        fun fill4x4WithApps(startCol: Int, startRow: Int) {
            val remaining = standaloneApps.size - appIdx
            if (remaining >= 4) {
                val subSlots = listOf(Pair(0, 0), Pair(2, 0), Pair(0, 2), Pair(2, 2))
                for (sub in subSlots) {
                    val app = standaloneApps[appIdx++]
                    val sc = startCol + sub.first
                    val sr = startRow + sub.second
                    occupy(sc, sr, 2, 2)
                    result.add(createStandaloneTile(app, sc, sr, 2, 2))
                }
            } else if (remaining == 3) {
                val app1 = standaloneApps[appIdx++]
                occupy(startCol, startRow, 4, 2)
                result.add(createStandaloneTile(app1, startCol, startRow, 4, 2))

                val app2 = standaloneApps[appIdx++]
                occupy(startCol, startRow + 2, 2, 2)
                result.add(createStandaloneTile(app2, startCol, startRow + 2, 2, 2))

                val app3 = standaloneApps[appIdx++]
                occupy(startCol + 2, startRow + 2, 2, 2)
                result.add(createStandaloneTile(app3, startCol + 2, startRow + 2, 2, 2))
            } else if (remaining == 2) {
                val app1 = standaloneApps[appIdx++]
                occupy(startCol, startRow, 4, 2)
                result.add(createStandaloneTile(app1, startCol, startRow, 4, 2))

                val app2 = standaloneApps[appIdx++]
                occupy(startCol, startRow + 2, 4, 2)
                result.add(createStandaloneTile(app2, startCol, startRow + 2, 4, 2))
            } else if (remaining == 1) {
                val app1 = standaloneApps[appIdx++]
                occupy(startCol, startRow, 4, 4)
                result.add(createStandaloneTile(app1, startCol, startRow, 4, 4))
            }
        }

        while (folderIdx < folderTiles.size) {
            val fTile = folderTiles[folderIdx]
            val remainingApps = standaloneApps.size - appIdx
            val remainingFolders = folderTiles.size - folderIdx

            // "The rule for zig zag should be checked only if there's enough apps to put inside empty spaces.
            // There should not be spaces mid-grid default. If there's no enough apps, it could go anyway."
            val hasEnoughAppsForZigZag = remainingApps >= 2 || (remainingApps == 1 && remainingFolders == 1)

            if (hasEnoughAppsForZigZag) {
                val folderCol = if (zigZagStep % 2 == 0) 0 else 4
                val normalCol = if (zigZagStep % 2 == 0) 4 else 0

                occupy(folderCol, currentRow, 4, 4)
                result.add(
                    fTile.copy(
                        gridCol = folderCol,
                        gridRow = currentRow,
                        fixedCol = folderCol,
                        fixedRow = currentRow
                    )
                )

                fill4x4WithApps(normalCol, currentRow)

                currentRow += 4
                folderIdx++
                zigZagStep++
            } else {
                // Not enough apps to fill opposite space: place folders side-by-side (cols 0 and 4) if another exists
                if (folderIdx + 1 < folderTiles.size) {
                    val fTileNext = folderTiles[folderIdx + 1]
                    occupy(0, currentRow, 4, 4)
                    result.add(
                        fTile.copy(
                            gridCol = 0,
                            gridRow = currentRow,
                            fixedCol = 0,
                            fixedRow = currentRow
                        )
                    )
                    occupy(4, currentRow, 4, 4)
                    result.add(
                        fTileNext.copy(
                            gridCol = 4,
                            gridRow = currentRow,
                            fixedCol = 4,
                            fixedRow = currentRow
                        )
                    )
                    currentRow += 4
                    folderIdx += 2
                } else {
                    occupy(0, currentRow, 4, 4)
                    result.add(
                        fTile.copy(
                            gridCol = 0,
                            gridRow = currentRow,
                            fixedCol = 0,
                            fixedRow = currentRow
                        )
                    )
                    // If any remaining standalone app exists, put in opposite space
                    if (appIdx < standaloneApps.size) {
                        fill4x4WithApps(4, currentRow)
                    }
                    currentRow += 4
                    folderIdx++
                }
            }
        }

        // Place remaining standalone apps row-by-row, column-by-column without any spaces mid-grid
        for (r in currentRow..4000 step 2) {
            if (appIdx >= standaloneApps.size) break
            for (c in 0 until UNITS_PER_ROW step 2) {
                if (appIdx >= standaloneApps.size) break
                ensure(r + 1)
                if (rows[r][c]) continue // already occupied

                val remaining = standaloneApps.size - appIdx
                val canFit4x2 = (c + 4 <= UNITS_PER_ROW) && fits(c, r, 4, 2)
                val use4x2 = canFit4x2 && (remaining == 1 || (tileColorCount % 4 == 0 && remaining >= 3))

                if (use4x2) {
                    val app = standaloneApps[appIdx++]
                    occupy(c, r, 4, 2)
                    result.add(createStandaloneTile(app, c, r, 4, 2))
                } else if (fits(c, r, 2, 2)) {
                    val app = standaloneApps[appIdx++]
                    occupy(c, r, 2, 2)
                    result.add(createStandaloneTile(app, c, r, 2, 2))
                }
            }
        }

        return result
    }

    /**
     * Migrates a folder to a target 2x2 grid square (col 0 or 4, row R)
     * while preserving the zig-zag placement rules and adjusting app tiles.
     */
    fun migrateFolder(
        tiles: List<TileModel>,
        folderId: String,
        targetCol: Int,
        targetRow: Int
    ): List<TileModel> {
        val folder = tiles.firstOrNull { it.id == folderId } ?: return tiles
        val normalizedCol = if (targetCol < 2) 0 else 4
        val normalizedRow = (targetRow / 4) * 4

        val otherFolders = tiles.filter { it.isFolder && it.id != folderId }
        val standaloneTiles = tiles.filter { !it.isFolder }

        // Reorder folders so the moved folder occupies the target slot, and others follow zig-zag
        val newFolders = mutableListOf<TileModel>()
        val movedFolder = folder.copy(
            gridCol = normalizedCol,
            gridRow = normalizedRow,
            fixedCol = normalizedCol,
            fixedRow = normalizedRow
        )

        // Build list of target slots for folders ensuring zig-zag rule
        var currentZigCol = if (normalizedCol == 0) 4 else 0
        var currentZigRow = if (normalizedRow == 0) 4 else 0
        val remainingOtherFolders = otherFolders.toMutableList()

        val allPlacedFolders = mutableListOf<TileModel>()
        allPlacedFolders.add(movedFolder)

        for (other in remainingOtherFolders) {
            if (currentZigRow == normalizedRow) {
                currentZigRow += 4
            }
            allPlacedFolders.add(
                other.copy(
                    gridCol = currentZigCol,
                    gridRow = currentZigRow,
                    fixedCol = currentZigCol,
                    fixedRow = currentZigRow
                )
            )
            currentZigCol = if (currentZigCol == 0) 4 else 0
            currentZigRow += 4
        }

        // Sort folders by row
        val sortedFolders = allPlacedFolders.sortedBy { it.gridRow }

        // Now place normal app tiles around the folders
        val rows = mutableListOf<BooleanArray>()
        fun ensure(r: Int) { while (rows.size <= r) rows.add(BooleanArray(UNITS_PER_ROW)) }
        fun fits(c: Int, r: Int, w: Int, h: Int): Boolean {
            if (c + w > UNITS_PER_ROW) return false
            for (rr in r until r + h) {
                ensure(rr)
                for (cc in c until c + w) if (rows[rr][cc]) return false
            }
            return true
        }
        fun occupy(c: Int, r: Int, w: Int, h: Int) {
            for (rr in r until r + h) {
                ensure(rr)
                for (cc in c until c + w) rows[rr][cc] = true
            }
        }

        for (f in sortedFolders) {
            occupy(f.gridCol, f.gridRow, 4, 4)
        }

        val result = mutableListOf<TileModel>()
        result.addAll(sortedFolders)

        var tileIdx = 0
        // Fill slots opposite to folders first without leaving holes
        for (f in sortedFolders) {
            val normalCol = if (f.gridCol == 0) 4 else 0
            val remaining = standaloneTiles.size - tileIdx
            if (remaining >= 4) {
                val subSlots = listOf(Pair(0, 0), Pair(2, 0), Pair(0, 2), Pair(2, 2))
                for (sub in subSlots) {
                    val tile = standaloneTiles[tileIdx++]
                    val sc = normalCol + sub.first
                    val sr = f.gridRow + sub.second
                    occupy(sc, sr, 2, 2)
                    result.add(
                        tile.copy(
                            gridCol = sc,
                            gridRow = sr,
                            fixedCol = sc,
                            fixedRow = sr,
                            colSpan = 2,
                            rowSpan = 2,
                            isMicro = false,
                            isMacro = false
                        )
                    )
                }
            } else if (remaining == 3) {
                val t1 = standaloneTiles[tileIdx++]
                occupy(normalCol, f.gridRow, 4, 2)
                result.add(t1.copy(gridCol = normalCol, gridRow = f.gridRow, fixedCol = normalCol, fixedRow = f.gridRow, colSpan = 4, rowSpan = 2, isMicro = false, isMacro = false))

                val t2 = standaloneTiles[tileIdx++]
                occupy(normalCol, f.gridRow + 2, 2, 2)
                result.add(t2.copy(gridCol = normalCol, gridRow = f.gridRow + 2, fixedCol = normalCol, fixedRow = f.gridRow + 2, colSpan = 2, rowSpan = 2, isMicro = false, isMacro = false))

                val t3 = standaloneTiles[tileIdx++]
                occupy(normalCol + 2, f.gridRow + 2, 2, 2)
                result.add(t3.copy(gridCol = normalCol + 2, gridRow = f.gridRow + 2, fixedCol = normalCol + 2, fixedRow = f.gridRow + 2, colSpan = 2, rowSpan = 2, isMicro = false, isMacro = false))
            } else if (remaining == 2) {
                val t1 = standaloneTiles[tileIdx++]
                occupy(normalCol, f.gridRow, 4, 2)
                result.add(t1.copy(gridCol = normalCol, gridRow = f.gridRow, fixedCol = normalCol, fixedRow = f.gridRow, colSpan = 4, rowSpan = 2, isMicro = false, isMacro = false))

                val t2 = standaloneTiles[tileIdx++]
                occupy(normalCol, f.gridRow + 2, 4, 2)
                result.add(t2.copy(gridCol = normalCol, gridRow = f.gridRow + 2, fixedCol = normalCol, fixedRow = f.gridRow + 2, colSpan = 4, rowSpan = 2, isMicro = false, isMacro = false))
            } else if (remaining == 1) {
                val t1 = standaloneTiles[tileIdx++]
                occupy(normalCol, f.gridRow, 4, 4)
                result.add(t1.copy(gridCol = normalCol, gridRow = f.gridRow, fixedCol = normalCol, fixedRow = f.gridRow, colSpan = 4, rowSpan = 4, isMicro = false, isMacro = true))
            }
        }

        // Place remaining standalone tiles below
        for (r in 0..4000) {
            for (c in 0..(UNITS_PER_ROW - 2)) {
                if (tileIdx < standaloneTiles.size && fits(c, r, 2, 2)) {
                    val tile = standaloneTiles[tileIdx++]
                    occupy(c, r, 2, 2)
                    result.add(
                        tile.copy(
                            gridCol = c,
                            gridRow = r,
                            fixedCol = c,
                            fixedRow = r,
                            colSpan = 2,
                            rowSpan = 2,
                            isMicro = false,
                            isMacro = false
                        )
                    )
                }
            }
            if (tileIdx >= standaloneTiles.size) break
        }

        return result
    }
}
