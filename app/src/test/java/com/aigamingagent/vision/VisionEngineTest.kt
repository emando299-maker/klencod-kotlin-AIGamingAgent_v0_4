package com.aigamingagent.vision

import com.aigamingagent.ludo.PlayerColor.BLUE
import com.aigamingagent.ludo.PlayerColor.GREEN
import com.aigamingagent.ludo.PlayerColor.RED
import com.aigamingagent.ludo.PlayerColor.YELLOW
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the real pipeline on three real screenshots (1080x2408, Samsung A23):
 * start_all_base = every piece still in its base; play_a / play_b = mid-game.
 * Order matters: the first image teaches the engine each colour's piece look.
 */
class VisionEngineTest {
    private fun load(name: String): PixelImage {
        val stream = javaClass.getResourceAsStream("/screens/$name") ?: error("missing $name")
        val bi = ImageIO.read(stream)
        val px = IntArray(bi.width * bi.height)
        bi.getRGB(0, 0, bi.width, bi.height, px, 0, bi.width)
        return PixelImage(bi.width, bi.height, px)
    }

    private fun cells(r: VisionResult, color: com.aigamingagent.ludo.PlayerColor) =
        r.pieces.filter { it.color == color }.map { it.cell.row to it.cell.col }.toSet()

    @Test fun learnsFromStartAndReadsMidGameBoards() {
        val engine = VisionEngine()

        val start = engine.analyze(load("start_all_base.jpg"))
        assertNotNull(start.board)
        assertTrue(start.boardConfidence >= 0.75)
        assertEquals(34.33, start.board!!.cell, 1.0)          // working resolution (factor 2)
        assertEquals(12.5, start.board!!.x0, 3.0)
        assertEquals(343.4, start.board!!.y0, 4.0)
        assertEquals(4, start.baseCounts[RED]); assertEquals(4, start.baseCounts[YELLOW])
        assertEquals(0, start.baseCounts[BLUE]); assertEquals(0, start.baseCounts[GREEN])
        assertEquals(setOf(RED, YELLOW), start.activeColors)
        assertEquals(listOf(-1, -1, -1, -1), start.state!!.positions(RED))
        assertEquals(listOf(-1, -1, -1, -1), start.state!!.positions(YELLOW))

        val a = engine.analyze(load("play_a.jpg"))
        assertEquals(setOf(1 to 8, 12 to 8, 14 to 6, 14 to 7), cells(a, RED))
        assertEquals(setOf(8 to 1, 8 to 2, 13 to 6), cells(a, YELLOW))
        assertEquals(listOf(0, 21, 24, 25), a.state!!.positions(RED))
        assertEquals(listOf(-1, 0, 8, 9), a.state!!.positions(YELLOW))
        assertTrue(a.confidence > 0.76)

        val b = engine.analyze(load("play_b.jpg"))
        assertEquals(setOf(1 to 8, 3 to 8, 14 to 6), cells(b, RED))
        assertEquals(setOf(10 to 6), cells(b, YELLOW))
        assertEquals(listOf(-1, 0, 2, 25), b.state!!.positions(RED))
        assertEquals(listOf(-1, -1, -1, 3), b.state!!.positions(YELLOW))
    }

    @Test fun withoutTemplatesNoStateIsInvented() {
        // First frame is mid-game: no piece look learned for RED -> must not fabricate a state.
        val r = VisionEngine().analyze(load("play_a.jpg"))
        assertNotNull(r.board)
        assertTrue(r.pieces.none { it.color == RED })
        assertEquals(null, r.state) // only one colour known -> state refused
    }
}
