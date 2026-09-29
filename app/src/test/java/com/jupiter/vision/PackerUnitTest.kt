package com.jupiter.vision

import androidx.compose.ui.graphics.Color
import com.jupiter.vision.model.ColorEngine
import com.jupiter.vision.model.TileModel
import com.jupiter.vision.util.Packer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackerUnitTest {

    @Test
    fun testPackerEmptyList() {
        val placements = Packer.pack(emptyList())
        assertTrue(placements.isEmpty())
    }

    @Test
    fun testPackerFitsFourStandardTilesInOneRow() {
        val tiles = listOf(
            TileModel(id = "1", label = "A", colSpan = 2, rowSpan = 2),
            TileModel(id = "2", label = "B", colSpan = 2, rowSpan = 2),
            TileModel(id = "3", label = "C", colSpan = 2, rowSpan = 2),
            TileModel(id = "4", label = "D", colSpan = 2, rowSpan = 2)
        )
        val placements = Packer.pack(tiles)
        assertEquals(4, placements.size)
        assertEquals(0, placements[0].col)
        assertEquals(0, placements[0].row)
        assertEquals(2, placements[1].col)
        assertEquals(0, placements[1].row)
        assertEquals(4, placements[2].col)
        assertEquals(0, placements[2].row)
        assertEquals(6, placements[3].col)
        assertEquals(0, placements[3].row)
    }

    @Test
    fun testPackerFixedPositions() {
        val tiles = listOf(
            TileModel(id = "phone", label = "PHONE", fixedCol = 0, fixedRow = 0, colSpan = 2, rowSpan = 2),
            TileModel(id = "msg", label = "MSG", fixedCol = 2, fixedRow = 0, colSpan = 2, rowSpan = 2),
            TileModel(id = "gallery", label = "GALLERY", fixedCol = 4, fixedRow = 0, colSpan = 4, rowSpan = 2)
        )
        val placements = Packer.pack(tiles)
        assertEquals(3, placements.size)
        assertEquals(0, placements[0].col)
        assertEquals(0, placements[0].row)
        assertEquals(2, placements[1].col)
        assertEquals(0, placements[1].row)
        assertEquals(4, placements[2].col)
        assertEquals(0, placements[2].row)
    }

    @Test
    fun testColorEngineDefaultPalette() {
        val defaultColors = ColorEngine.fromWallpaper(null)
        assertEquals(3, defaultColors.size)
        assertEquals(Color(0xFF007AFF), defaultColors[0])
    }

    @Test
    fun testReflowTileGrowsRightAndPushesNeighbor() {
        val tiles = listOf(
            TileModel(id = "tileA", label = "A", fixedCol = 0, fixedRow = 0, gridCol = 0, gridRow = 0, colSpan = 2, rowSpan = 2),
            TileModel(id = "tileB", label = "B", fixedCol = 2, fixedRow = 0, gridCol = 2, gridRow = 0, colSpan = 2, rowSpan = 2)
        )
        // Grow tileA to the right (w = 4)
        val result = com.jupiter.vision.util.Packer.reflowTileDirection(
            tiles,
            "tileA",
            com.jupiter.vision.util.ResizeDirection.RIGHT
        )
        org.junit.Assert.assertNotNull(result)
        val reflowedA = result!!.first { it.id == "tileA" }
        val reflowedB = result.first { it.id == "tileB" }

        // A grew to colSpan 4
        assertEquals(4, reflowedA.colSpan)
        assertEquals(0, reflowedA.gridCol)
        assertEquals(0, reflowedA.gridRow)

        // B was pushed and no longer overlaps A
        val bCol = reflowedB.gridCol
        val bRow = reflowedB.gridRow
        val overlaps = (bCol < reflowedA.gridCol + reflowedA.colSpan) &&
                (bCol + reflowedB.colSpan > reflowedA.gridCol) &&
                (bRow < reflowedA.gridRow + reflowedA.rowSpan) &&
                (bRow + reflowedB.rowSpan > reflowedA.gridRow)
        org.junit.Assert.assertFalse("Displaced neighbor B must not overlap A", overlaps)
    }

    @Test
    fun testHardSizeRulesEnforced() {
        val tiles = listOf(
            TileModel(id = "tileA", label = "A", fixedCol = 0, fixedRow = 0, gridCol = 0, gridRow = 0, colSpan = 4, rowSpan = 4)
        )
        // tileA is already 2x2 real (4x4 units) — cannot grow wider than 2
        val rightResult = com.jupiter.vision.util.Packer.reflowTileDirection(
            tiles,
            "tileA",
            com.jupiter.vision.util.ResizeDirection.RIGHT
        )
        org.junit.Assert.assertNull("Cannot grow wider than 2x2 cap", rightResult)

        val downResult = com.jupiter.vision.util.Packer.reflowTileDirection(
            tiles,
            "tileA",
            com.jupiter.vision.util.ResizeDirection.DOWN
        )
        org.junit.Assert.assertNull("Cannot grow taller than 2x2 cap", downResult)
    }

    @Test
    fun testDirectionalShrinking() {
        val tiles = listOf(
            TileModel(id = "tileA", label = "A", fixedCol = 0, fixedRow = 0, gridCol = 0, gridRow = 0, colSpan = 4, rowSpan = 4)
        )
        // Pressing Left on 2x2 contracts width to 1x2 (colSpan = 2)
        val leftResult = com.jupiter.vision.util.Packer.reflowTileDirection(
            tiles,
            "tileA",
            com.jupiter.vision.util.ResizeDirection.LEFT
        )
        org.junit.Assert.assertNotNull(leftResult)
        assertEquals(2, leftResult!!.first().colSpan)
        assertEquals(4, leftResult.first().rowSpan)

        // Pressing Up on 2x2 contracts height to 2x1 (rowSpan = 2)
        val upResult = com.jupiter.vision.util.Packer.reflowTileDirection(
            tiles,
            "tileA",
            com.jupiter.vision.util.ResizeDirection.UP
        )
        org.junit.Assert.assertNotNull(upResult)
        assertEquals(4, upResult!!.first().colSpan)
        assertEquals(2, upResult.first().rowSpan)
    }
}
