package com.aigamingagent.vision

import com.aigamingagent.ludo.PlayerColor
import kotlin.math.max
import kotlin.math.min

/** Classifies saturated board colours. Thresholds were validated on real Yalla Ludo screenshots. */
object ColorClasses {
    const val NONE = 0
    const val RED = 1
    const val YELLOW = 2
    const val GREEN = 3
    const val BLUE = 4

    fun of(color: PlayerColor): Int = when (color) {
        PlayerColor.RED -> RED
        PlayerColor.YELLOW -> YELLOW
        PlayerColor.GREEN -> GREEN
        PlayerColor.BLUE -> BLUE
    }

    fun classify(p: Int): Int {
        val r = (p shr 16) and 255
        val g = (p shr 8) and 255
        val b = p and 255
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        if (mx == 0) return NONE
        val d = (mx - mn).toDouble()
        val s = d / mx
        if (s <= 0.59) return NONE
        val v = mx / 255.0
        var h = when (mx) {
            r -> 60.0 * (((g - b) / d) % 6.0)
            g -> 60.0 * ((b - r) / d + 2.0)
            else -> 60.0 * ((r - g) / d + 4.0)
        }
        if (h < 0) h += 360.0
        return when {
            v > 0.47 && (h < 16 || h > 344) -> RED
            v > 0.59 && h in 36.0..64.0 -> YELLOW
            v > 0.47 && h in 100.0..160.0 -> GREEN
            v > 0.47 && h in 200.0..250.0 -> BLUE
            else -> NONE
        }
    }

    fun map(img: PixelImage): ByteArray = ByteArray(img.argb.size) { classify(img.argb[it]).toByte() }
}
