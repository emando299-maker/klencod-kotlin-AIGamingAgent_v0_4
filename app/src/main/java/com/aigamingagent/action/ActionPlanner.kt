package com.aigamingagent.action

import com.aigamingagent.ludo.Move
import com.aigamingagent.vision.BoardGeometry
import com.aigamingagent.vision.VisionResult

/** Turns a chosen [Move] into screen tap points. Pure maths; no Android. */
object ActionPlanner {
    /**
     * @param frameToScreen screen pixels per captured-frame pixel (real screen width / frame width).
     * Returns an empty list when the target cannot be located (caller must treat that as a failure).
     */
    fun plan(result: VisionResult, move: Move, frameToScreen: Double): List<Pair<Float, Float>> {
        val geo = result.board ?: return emptyList()
        val k = result.scale * frameToScreen
        val (wx, wy) = if (move.exitsBase) {
            val slot = result.filledSlots[move.color]?.firstOrNull() ?: return emptyList()
            geo.slotCenter(move.color, slot)
        } else {
            val cell = BoardGeometry.cellOf(move.color, move.from) ?: return emptyList()
            geo.centerX(cell.col) to geo.centerY(cell.row)
        }
        return listOf((wx * k).toFloat() to (wy * k).toFloat())
    }
}
