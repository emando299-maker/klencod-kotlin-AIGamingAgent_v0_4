package com.aigamingagent.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.aigamingagent.ludo.PlayerColor

/** Android-only helpers: Bitmap <-> PixelImage and a debug overlay so you can SEE what the AI sees. */
object VisionOverlay {
    fun toPixelImage(bitmap: Bitmap): PixelImage {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        return PixelImage(w, h, px)
    }

    fun draw(source: Bitmap, result: VisionResult): Bitmap {
        val geo = result.board ?: return source
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val k = result.scale.toDouble()
        val cell = geo.cell * k

        val grid = Paint().apply { color = Color.argb(150, 0, 255, 255); style = Paint.Style.STROKE; strokeWidth = 2f }
        canvas.drawRect(
            Rect((geo.x0 * k).toInt(), (geo.y0 * k).toInt(),
                ((geo.x0 + 15 * geo.cell) * k).toInt(), ((geo.y0 + 15 * geo.cell) * k).toInt()),
            grid
        )
        val ring = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 4f }
        val label = Paint().apply {
            color = Color.WHITE; textSize = (cell * 0.42).toFloat(); isFakeBoldText = true
            setShadowLayer(3f, 0f, 0f, Color.BLACK)
        }
        for (p in result.pieces) {
            ring.color = when (p.color) {
                PlayerColor.RED -> Color.RED; PlayerColor.YELLOW -> Color.YELLOW
                PlayerColor.GREEN -> Color.GREEN; PlayerColor.BLUE -> Color.CYAN
            }
            val cx = (geo.centerX(p.cell.col) * k).toFloat()
            val cy = (geo.centerY(p.cell.row) * k).toFloat()
            canvas.drawCircle(cx, cy, (cell * 0.5).toFloat(), ring)
            canvas.drawText(p.rel?.toString() ?: "?", cx - (cell * 0.2).toFloat(), cy + (cell * 0.15).toFloat(), label)
        }
        return out
    }
}
