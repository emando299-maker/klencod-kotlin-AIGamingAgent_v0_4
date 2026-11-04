package com.aigamingagent.vision

import com.aigamingagent.ludo.PlayerColor
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class Hit(val cell: Cell, val score: Double)

/**
 * Finds pieces without hard-coding their look (skins differ per player):
 *  1. a base slot is "filled" when its texture is high (empty slots are flat discs);
 *  2. the first filled slot of a colour is cropped and kept as that colour's template;
 *  3. every track/home cell is matched against the template (normalised cross-correlation, 3 scales).
 * Thresholds were validated on three real screenshots (exact cell match, margin 0.84 vs 0.69).
 */
class PieceDetector {
    private class Patch(val side: Int, val pixels: IntArray)
    private class Scaled(val side: Int, val values: DoubleArray, val norm: Double)

    private val patches = mutableMapOf<PlayerColor, Patch>()
    private val scaledCache = mutableMapOf<Pair<PlayerColor, Int>, Scaled>()

    val learned: Set<PlayerColor> get() = patches.keys

    fun reset() {
        patches.clear()
        scaledCache.clear()
    }

    fun slotFilled(gray: IntArray, w: Int, h: Int, geo: BoardGeometry, color: PlayerColor, index: Int): Boolean {
        val (cx, cy) = geo.slotCenter(color, index)
        val half = (0.35 * geo.cell).roundToInt()
        val x = cx.roundToInt()
        val y = cy.roundToInt()
        if (x - half < 0 || y - half < 0 || x + half > w || y + half > h) return false
        var sum = 0.0
        var sq = 0.0
        val n = (2 * half) * (2 * half)
        for (yy in y - half until y + half) for (xx in x - half until x + half) {
            val v = gray[yy * w + xx].toDouble()
            sum += v; sq += v * v
        }
        val mean = sum / n
        val std = sqrt(max(0.0, sq / n - mean * mean))
        return std > SLOT_STD
    }

    /** Learns [color]'s template from its first filled base slot. Returns true if learned now. */
    fun learnFromBase(gray: IntArray, w: Int, h: Int, geo: BoardGeometry, color: PlayerColor): Boolean {
        if (color in patches) return false
        val index = (0 until 4).firstOrNull { slotFilled(gray, w, h, geo, color, it) } ?: return false
        val (cx, cy) = geo.slotCenter(color, index)
        val half = (0.47 * geo.cell).roundToInt()
        val x = cx.roundToInt()
        val y = cy.roundToInt()
        if (x - half < 0 || y - half < 0 || x + half > w || y + half > h) return false
        val side = 2 * half
        val px = IntArray(side * side)
        for (j in 0 until side) for (i in 0 until side) px[j * side + i] = gray[(y - half + j) * w + (x - half + i)]
        patches[color] = Patch(side, px)
        return true
    }

    fun detect(gray: IntArray, w: Int, h: Int, cls: ByteArray, geo: BoardGeometry, color: PlayerColor): List<Hit> {
        val patch = patches[color] ?: return emptyList()
        val ownClass = ColorClasses.of(color)
        val hits = mutableListOf<Hit>()
        val cells = BoardGeometry.RING + BoardGeometry.HOME.getValue(color)
        val hw = (0.35 * geo.cell).roundToInt()
        val pad = (0.146 * geo.cell).roundToInt()
        val sizes = SCALES.map { (it * geo.cell).roundToInt() }.distinct()
        for (cell in cells) {
            // Cheap pre-filter: a piece of this colour must show some of its colour in the cell centre.
            val ccx = geo.centerX(cell.col).toInt()
            val ccy = geo.centerY(cell.row).toInt()
            if (ccx - hw < 0 || ccy - hw < 0 || ccx + hw > w || ccy + hw > h) continue
            var own = 0
            for (yy in ccy - hw until ccy + hw) for (xx in ccx - hw until ccx + hw) {
                if (cls[yy * w + xx].toInt() == ownClass) own++
            }
            if (own.toDouble() / ((2 * hw) * (2 * hw)) < MIN_OWN_COLOR) continue

            val rx = (geo.x0 + cell.col * geo.cell).toInt() - pad
            val ry = (geo.y0 + cell.row * geo.cell).toInt() - pad
            val rw = geo.cell.toInt() + 2 * pad
            if (rx < 0 || ry < 0 || rx + rw > w || ry + rw > h) continue
            var best = -1.0
            for (s in sizes) {
                if (s > rw) continue
                val t = scaled(color, patch, s)
                best = max(best, bestNcc(gray, w, rx, ry, rw, t))
            }
            if (best >= MATCH_THRESHOLD) hits.add(Hit(cell, best))
        }
        return hits
    }

    private fun scaled(color: PlayerColor, patch: Patch, side: Int): Scaled =
        scaledCache.getOrPut(color to side) {
            val v = resizeArea(patch.pixels, patch.side, side)
            val mean = v.average()
            var sq = 0.0
            for (i in v.indices) { v[i] -= mean; sq += v[i] * v[i] }
            Scaled(side, v, sqrt(sq))
        }

    /** Max of zero-mean normalised cross-correlation over every offset of the template in the region. */
    private fun bestNcc(gray: IntArray, w: Int, rx: Int, ry: Int, rw: Int, t: Scaled): Double {
        val s = t.side
        val n = (s * s).toDouble()
        var best = -1.0
        for (oy in 0..rw - s) {
            for (ox in 0..rw - s) {
                var sum = 0.0
                var sq = 0.0
                var dot = 0.0
                for (j in 0 until s) {
                    val base = (ry + oy + j) * w + rx + ox
                    val tb = j * s
                    for (i in 0 until s) {
                        val v = gray[base + i].toDouble()
                        sum += v; sq += v * v
                        dot += v * t.values[tb + i]
                    }
                }
                val variance = sq - sum * sum / n
                if (variance <= 1e-6 || t.norm <= 1e-9) continue
                val score = dot / (sqrt(variance) * t.norm)
                if (score > best) best = score
            }
        }
        return best
    }

    /** Fractional box-filter resize (same idea as an area resize). */
    private fun resizeArea(src: IntArray, sSide: Int, dSide: Int): DoubleArray {
        val out = DoubleArray(dSide * dSide)
        val ratio = sSide.toDouble() / dSide
        for (dy in 0 until dSide) {
            val y0 = dy * ratio
            val y1 = (dy + 1) * ratio
            for (dx in 0 until dSide) {
                val x0 = dx * ratio
                val x1 = (dx + 1) * ratio
                var acc = 0.0
                var wsum = 0.0
                for (sy in floor(y0).toInt() until min(sSide, ceil(y1).toInt())) {
                    val wy = min(sy + 1.0, y1) - max(sy.toDouble(), y0)
                    for (sx in floor(x0).toInt() until min(sSide, ceil(x1).toInt())) {
                        val wx = min(sx + 1.0, x1) - max(sx.toDouble(), x0)
                        val wgt = wx * wy
                        acc += src[sy * sSide + sx] * wgt
                        wsum += wgt
                    }
                }
                out[dy * dSide + dx] = acc / wsum
            }
        }
        return out
    }

    companion object {
        const val SLOT_STD = 28.0
        const val MATCH_THRESHOLD = 0.76
        const val MIN_OWN_COLOR = 0.04
        val SCALES = listOf(0.64, 0.70, 0.76)
    }
}
