package com.jupiter.vision.util

import androidx.compose.ui.graphics.Color
import com.jupiter.vision.model.AppInfo
import com.jupiter.vision.model.ColorEngine
import com.jupiter.vision.model.TileModel

data class Placement(val col: Int, val row: Int)

/**
 * Packing engine for 8-unit wide grid.
 * Adheres strictly to layout rules:
 * - Allowed sizes: 1x1 micro (1x1 units), 1x1 real (2x2 units), 2x1 real (4x2 units), 1x2 real (2x4 units), 2x2 real (4x4 units).
 * - No 3x1, no 1x0.5, cap 2x2 real (4x4 units).
 * - When an app has a live notification: if it's 1x1, it grows to 2x1 (or 2x2) and activates content mode.
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
            for (rr in r until r + h) for (cc in c until c + w) rows[rr][cc] = true
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
     * Packs third-party apps into composed, mixed-size tiles without ragged edges.
     */
    fun packThirdPartyApps(
        apps: List<AppInfo>,
        palette: List<Color>,
        activityMap: Map<String, List<String>> = emptyMap(),
        startIndex: Int = 0
    ): List<TileModel> {
        if (apps.isEmpty()) return emptyList()

        val total = apps.size
        val highThreshold = (total * 0.20f).toInt().coerceAtLeast(1)
        val medThreshold = (total * 0.60f).toInt().coerceAtLeast(highThreshold)

        val unplaced = apps.mapIndexed { index, app ->
            val hasActive = activityMap.containsKey(app.packageName) ||
                    activityMap.keys.any { key -> app.packageName.contains(key, ignoreCase = true) || app.label.contains(key, ignoreCase = true) }
            val priority = when {
                hasActive -> 4 // Active notification
                index < 2 && total >= 6 -> 5 // Macro candidate (4x4)
                index < highThreshold -> 3 // High (4x2 / 2x2)
                index < medThreshold -> 2  // Medium (2x2)
                else -> 1                  // Low (1x1 micro or 2x2)
            }
            app to priority
        }.toMutableList()

        val rows = mutableListOf<BooleanArray>()
        fun ensure(r: Int) { while (rows.size <= r) rows.add(BooleanArray(UNITS_PER_ROW)) }
        fun isOcc(c: Int, r: Int): Boolean {
            ensure(r)
            return rows[r][c]
        }
        fun fits(c: Int, r: Int, w: Int, h: Int): Boolean {
            if (c + w > UNITS_PER_ROW) return false
            for (rr in r until r + h) {
                ensure(rr)
                for (cc in c until c + w) if (rows[rr][cc]) return false
            }
            return true
        }
        fun occupy(c: Int, r: Int, w: Int, h: Int) {
            for (rr in r until r + h) for (cc in c until c + w) rows[rr][cc] = true
        }

        val result = mutableListOf<TileModel>()
        var colorIdx = startIndex

        for (r in 0..4000) {
            if (unplaced.isEmpty()) break
            for (c in 0 until UNITS_PER_ROW) {
                if (unplaced.isEmpty()) break
                if (isOcc(c, r)) continue

                val availW = (c until UNITS_PER_ROW).takeWhile { !isOcc(it, r) }.size
                if (availW <= 0) continue

                val isRightCol = (c >= 6)

                var chosenIndex = -1
                var chosenW = 2
                var chosenH = 2

                // 1. Check macro candidate (4x4)
                if (c == 0 || c == 4) {
                    val macroIdx = unplaced.indexOfFirst { it.second == 5 }
                    if (macroIdx != -1 && fits(c, r, 4, 4)) {
                        chosenIndex = macroIdx
                        chosenW = 4
                        chosenH = 4
                    }
                }

                // 2. Check active notification candidate (grows to 4x2 or 2x2, activates content mode)
                if (chosenIndex == -1) {
                    val activeIdx = unplaced.indexOfFirst { it.second == 4 }
                    if (activeIdx != -1) {
                        val targetSizes = listOf(Pair(4, 2), Pair(2, 2))
                        for ((cw, ch) in targetSizes) {
                            if (cw <= availW && fits(c, r, cw, ch)) {
                                chosenIndex = activeIdx
                                chosenW = cw
                                chosenH = ch
                                break
                            }
                        }
                    }
                }

                // 3. Normal priority packing
                if (chosenIndex == -1) {
                    if (isRightCol) {
                        chosenW = 1
                        chosenH = 1
                        val lowIdx = unplaced.indexOfLast { it.second == 1 }
                        chosenIndex = if (lowIdx != -1) lowIdx else 0
                    } else {
                        for (i in unplaced.indices) {
                            val (_, priority) = unplaced[i]
                            val candidateSizes = when (priority) {
                                5 -> listOf(Pair(4, 4), Pair(4, 2), Pair(2, 2))
                                4 -> listOf(Pair(4, 2), Pair(2, 2))
                                3 -> listOf(Pair(4, 2), Pair(2, 2))
                                2 -> listOf(Pair(2, 2), Pair(4, 2))
                                else -> listOf(Pair(2, 2), Pair(1, 1))
                            }

                            for ((cw, ch) in candidateSizes) {
                                if (cw <= availW && fits(c, r, cw, ch)) {
                                    chosenIndex = i
                                    chosenW = cw
                                    chosenH = ch
                                    break
                                }
                            }
                            if (chosenIndex != -1) break
                        }

                        if (chosenIndex == -1 && unplaced.isNotEmpty()) {
                            chosenIndex = 0
                            chosenW = when {
                                availW >= 4 -> 4
                                availW >= 2 -> 2
                                else -> 1
                            }
                            chosenH = if (chosenW == 1) 1 else 2
                            if (!fits(c, r, chosenW, chosenH)) {
                                chosenH = 1
                            }
                        }
                    }
                }

                if (chosenIndex != -1 && chosenIndex < unplaced.size) {
                    val (app, priority) = unplaced.removeAt(chosenIndex)
                    occupy(c, r, chosenW, chosenH)

                    val isMacro = (chosenW >= 4 && chosenH >= 4)
                    val isMicro = (chosenW == 1 && chosenH == 1)

                    val fallbackAccent = palette[colorIdx % palette.size]
                    val accent = if (isMacro) {
                        ColorEngine.fromAppIconForMacro(app.iconBitmap, fallback = fallbackAccent)
                    } else {
                        ColorEngine.fromAppIcon(app.iconBitmap, fallback = fallbackAccent)
                    }
                    colorIdx++

                    val activeLines = if (priority == 4) {
                        activityMap[app.packageName]
                            ?: activityMap.entries.firstOrNull { app.packageName.contains(it.key, ignoreCase = true) || app.label.contains(it.key, ignoreCase = true) }?.value
                            ?: emptyList()
                    } else emptyList()

                    result.add(
                        TileModel(
                            id = "app_${app.packageName.replace('.', '_')}_${result.size}",
                            label = app.label,
                            packageName = app.packageName,
                            colSpan = chosenW,
                            rowSpan = chosenH,
                            isMicro = isMicro,
                            isMacro = isMacro,
                            hasActivity = (priority == 4 || activeLines.isNotEmpty()),
                            contentLines = activeLines,
                            accentColor = accent,
                            iconBitmap = app.iconImageBitmap
                        )
                    )
                }
            }
        }

        return result
    }
}
