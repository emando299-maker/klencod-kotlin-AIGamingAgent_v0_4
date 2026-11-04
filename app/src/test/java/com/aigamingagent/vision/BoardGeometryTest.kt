package com.aigamingagent.vision

import com.aigamingagent.ludo.LudoConfig
import com.aigamingagent.ludo.PlayerColor
import com.aigamingagent.vision.BoardGeometry.Companion.RING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Facts below were read from real Yalla Ludo screenshots (start globes and star squares). */
class BoardGeometryTest {
    @Test fun ringHas52UniqueCells() {
        assertEquals(52, RING.size)
        assertEquals(52, RING.toSet().size)
    }

    @Test fun startSquaresMatchScreenshots() {
        assertEquals(Cell(1, 8), RING[PlayerColor.RED.startIndex])
        assertEquals(Cell(8, 13), RING[PlayerColor.GREEN.startIndex])
        assertEquals(Cell(13, 6), RING[PlayerColor.YELLOW.startIndex])
        assertEquals(Cell(6, 1), RING[PlayerColor.BLUE.startIndex])
    }

    @Test fun starSquaresMatchDefaultSafeSquares() {
        val stars = listOf(Cell(6, 12), Cell(12, 8), Cell(8, 2), Cell(2, 6))
        val safe = LudoConfig().safeSquares
        stars.forEach { assertTrue(RING.indexOf(it) in safe) }
        assertEquals(setOf(8, 21, 34, 47), stars.map { RING.indexOf(it) }.toSet())
    }

    @Test fun relativePositions() {
        assertEquals(0, BoardGeometry.relOf(PlayerColor.RED, Cell(1, 8)))
        assertEquals(21, BoardGeometry.relOf(PlayerColor.RED, Cell(12, 8)))
        assertEquals(9, BoardGeometry.relOf(PlayerColor.YELLOW, Cell(8, 1)))
        assertEquals(51, BoardGeometry.relOf(PlayerColor.RED, Cell(1, 7)))
        assertNull(BoardGeometry.relOf(PlayerColor.RED, Cell(0, 8))) // cell just before RED start
        assertNull(BoardGeometry.relOf(PlayerColor.RED, Cell(1, 11)))   // not a board cell
    }

    @Test fun cellAndRelRoundTrip() {
        for (color in PlayerColor.values()) for (rel in 0..55) {
            assertEquals(rel, BoardGeometry.relOf(color, BoardGeometry.cellOf(color, rel)!!))
        }
    }
}
