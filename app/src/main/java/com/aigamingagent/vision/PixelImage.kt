package com.aigamingagent.vision

import kotlin.math.max

/** Android-free image: ARGB ints, row-major. Lets the whole vision pipeline run in JVM unit tests. */
class PixelImage(val width: Int, val height: Int, val argb: IntArray) {
    init {
        require(argb.size == width * height) { "حجم المصفوفة لا يطابق الأبعاد" }
    }

    /** Box-average downscale by an integer factor so width <= maxWidth. Returns (image, factor). */
    fun downscaleToWidth(maxWidth: Int): Pair<PixelImage, Int> {
        val f = max(1, (width + maxWidth - 1) / maxWidth)
        if (f == 1) return this to 1
        val nw = width / f
        val nh = height / f
        val out = IntArray(nw * nh)
        val n = f * f
        for (y in 0 until nh) {
            for (x in 0 until nw) {
                var r = 0; var g = 0; var b = 0
                for (dy in 0 until f) {
                    val base = (y * f + dy) * width + x * f
                    for (dx in 0 until f) {
                        val p = argb[base + dx]
                        r += (p shr 16) and 255
                        g += (p shr 8) and 255
                        b += p and 255
                    }
                }
                out[y * nw + x] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
        return PixelImage(nw, nh, out) to f
    }

    fun toGray(): IntArray = IntArray(argb.size) { i ->
        val p = argb[i]
        (((p shr 16) and 255) * 299 + ((p shr 8) and 255) * 587 + (p and 255) * 114) / 1000
    }
}
