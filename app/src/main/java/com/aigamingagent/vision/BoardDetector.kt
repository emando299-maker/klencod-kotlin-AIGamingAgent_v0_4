package com.aigamingagent.vision

import kotlin.math.max

/**
 * Locates the board from the centre "pinwheel": red above, blue left, green right, yellow below.
 * Cell size is derived from the screen width (the board fills the width in portrait).
 * If the game is shown differently, calibration must override [CELL_FRACTION].
 */
object BoardDetector {
    const val CELL_FRACTION = 1030.0 / 1080.0 / 15.0
    private const val MIN_HITS = 20

    fun detect(img: PixelImage, cls: ByteArray): BoardGeometry? {
        val w = img.width
        val h = img.height
        val c = CELL_FRACTION * w
        val r = max(2, (0.09 * c).toInt())
        val stepX = max(1, (0.09 * c).toInt())
        val stepY = max(1, (0.06 * c).toInt())
        var sx = 0.0
        var sy = 0.0
        var n = 0
        var cx = (w / 2 - 0.5 * c).toInt()
        val cxEnd = (w / 2 + 0.5 * c).toInt()
        while (cx <= cxEnd) {
            var cy = (0.2 * h).toInt()
            val cyEnd = (0.8 * h).toInt()
            while (cy < cyEnd) {
                if (frac(cls, w, h, cx.toDouble(), cy - c, r, ColorClasses.RED) > 0.6 &&
                    frac(cls, w, h, cx - c, cy.toDouble(), r, ColorClasses.BLUE) > 0.6 &&
                    frac(cls, w, h, cx + c, cy.toDouble(), r, ColorClasses.GREEN) > 0.6 &&
                    frac(cls, w, h, cx.toDouble(), cy + c, r, ColorClasses.YELLOW) > 0.6
                ) {
                    sx += cx; sy += cy; n++
                }
                cy += stepY
            }
            cx += stepX
        }
        if (n < MIN_HITS) return null
        return BoardGeometry(sx / n - 7.5 * c, sy / n - 7.5 * c, c)
    }

    /**
     * Sanity check: the middle cell of each home column must show its colour. Returns 0, .25, .5, .75 or 1.
     * A piece standing on a column cell can fail one sample, hence 3 of 4 is accepted by callers.
     */
    fun verify(geo: BoardGeometry, img: PixelImage, cls: ByteArray): Double {
        val r = max(2, (0.25 * geo.cell).toInt())
        val samples = listOf(
            Triple(3, 7, ColorClasses.RED), Triple(7, 11, ColorClasses.GREEN),
            Triple(11, 7, ColorClasses.YELLOW), Triple(7, 3, ColorClasses.BLUE)
        )
        var ok = 0
        for ((row, col, k) in samples) {
            if (frac(cls, img.width, img.height, geo.centerX(col), geo.centerY(row), r, k) >= 0.4) ok++
        }
        return ok / 4.0
    }

    fun frac(cls: ByteArray, w: Int, h: Int, x: Double, y: Double, r: Int, k: Int): Double {
        val xi = x.toInt()
        val yi = y.toInt()
        if (xi - r < 0 || yi - r < 0 || xi + r >= w || yi + r >= h) return 0.0
        var count = 0
        for (yy in yi - r..yi + r) {
            val base = yy * w
            for (xx in xi - r..xi + r) if (cls[base + xx].toInt() == k) count++
        }
        val side = 2 * r + 1
        return count.toDouble() / (side * side)
    }
}
