package com.jupiter.vision.util

import com.jupiter.vision.model.TileModel

data class Placement(val col: Int, val row: Int)

/**
 * Top-left-first packer. 8 units per row (= 4 columns of 1x1).
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
            var placed = false
            outer@ for (r in 0..2000) {
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
}
