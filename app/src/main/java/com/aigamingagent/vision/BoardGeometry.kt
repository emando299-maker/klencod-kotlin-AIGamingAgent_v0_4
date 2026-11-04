package com.aigamingagent.vision

import com.aigamingagent.ludo.PlayerColor

data class Cell(val row: Int, val col: Int)

/**
 * Yalla Ludo board: a 15x15 grid. Layout read from real screenshots (portrait):
 * BLUE base top-left, RED top-right, GREEN bottom-right, YELLOW bottom-left; play direction clockwise.
 * (x0, y0) = top-left of the grid in image pixels, [cell] = cell size in pixels.
 */
class BoardGeometry(val x0: Double, val y0: Double, val cell: Double) {
    fun centerX(col: Int) = x0 + (col + 0.5) * cell
    fun centerY(row: Int) = y0 + (row + 0.5) * cell

    /** Centre of base slot [index] (0..3) of [color], in image pixels. */
    fun slotCenter(color: PlayerColor, index: Int): Pair<Double, Double> {
        val (bc, br) = BASE_ORIGIN.getValue(color)
        val (sx, sy) = SLOT_OFFSETS[index]
        return (x0 + (bc + sx) * cell) to (y0 + (br + sy) * cell)
    }

    companion object {
        /** The 52 shared track cells, clockwise, index 0 = RED start square. */
        val RING: List<Cell> = buildList {
            for (r in 1..5) add(Cell(r, 8))
            for (c in 9..14) add(Cell(6, c))
            add(Cell(7, 14))
            for (c in 14 downTo 9) add(Cell(8, c))
            for (r in 9..14) add(Cell(r, 8))
            add(Cell(14, 7))
            for (r in 14 downTo 9) add(Cell(r, 6))
            for (c in 5 downTo 0) add(Cell(8, c))
            add(Cell(7, 0))
            for (c in 0..5) add(Cell(6, c))
            for (r in 5 downTo 0) add(Cell(r, 6))
            add(Cell(0, 7))
            add(Cell(0, 8))
        }

        /** Own home-column cells, ordered from the entrance towards the centre (rel 51..55). */
        val HOME: Map<PlayerColor, List<Cell>> = mapOf(
            PlayerColor.RED to (1..5).map { Cell(it, 7) },
            PlayerColor.GREEN to (13 downTo 9).map { Cell(7, it) },
            PlayerColor.YELLOW to (13 downTo 9).map { Cell(it, 7) },
            PlayerColor.BLUE to (1..5).map { Cell(7, it) }
        )

        /** Top-left (col,row) of each 6x6 base. */
        val BASE_ORIGIN: Map<PlayerColor, Pair<Int, Int>> = mapOf(
            PlayerColor.BLUE to (0 to 0),
            PlayerColor.RED to (9 to 0),
            PlayerColor.YELLOW to (0 to 9),
            PlayerColor.GREEN to (9 to 9)
        )

        /** Slot centres inside a base, in cell units from the base's top-left corner. */
        val SLOT_OFFSETS: List<Pair<Double, Double>> =
            listOf(2.05 to 2.0, 3.97 to 2.0, 2.05 to 3.9, 3.97 to 3.9)

        private val ringIndex: Map<Cell, Int> = RING.withIndex().associate { it.value to it.index }

        /** Relative position (0..55) of a piece of [color] standing on [cell], or null if impossible. */
        fun relOf(color: PlayerColor, cell: Cell): Int? {
            ringIndex[cell]?.let { idx ->
                val rel = (idx - color.startIndex + 52) % 52
                return if (rel <= 50) rel else null
            }
            val p = HOME.getValue(color).indexOf(cell)
            return if (p >= 0) 51 + p else null
        }

        fun cellOf(color: PlayerColor, rel: Int): Cell? = when (rel) {
            in 0..50 -> RING[(color.startIndex + rel) % 52]
            in 51..55 -> HOME.getValue(color)[rel - 51]
            else -> null
        }
    }
}
