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
}
