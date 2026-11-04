package com.aigamingagent.vision

import com.aigamingagent.ludo.LudoState
import com.aigamingagent.ludo.PlayerColor

data class DetectedPiece(val color: PlayerColor, val cell: Cell, val rel: Int?, val score: Double)

/**
 * [board] is expressed in WORKING-image pixels; multiply by [scale] to get source-frame pixels.
 * [confidence] is a heuristic (board check x weakest piece match), NOT a calibrated probability.
 */
data class VisionResult(
    val board: BoardGeometry?,
    val boardConfidence: Double,
    val baseCounts: Map<PlayerColor, Int>,
    val pieces: List<DetectedPiece>,
    val activeColors: Set<PlayerColor>,
    val state: LudoState?,
    val confidence: Double,
    val notes: List<String>,
    val scale: Int,
    /** Filled base-slot indices per colour (used to know where to tap a piece still in base). */
    val filledSlots: Map<PlayerColor, List<Int>> = emptyMap()
) {
    val understood: Boolean get() = state != null

    fun summary(): String {
        if (board == null) return "لم تُكتشف لوحة لودو"
        if (activeColors.isEmpty()) return "اللوحة ظاهرة، لا قوالب أحجار بعد (ابدأ والأحجار في القواعد)"
        val parts = activeColors.sortedBy { it.ordinal }.map { c ->
            val onBoard = pieces.count { it.color == c }
            "${arabic(c)}: ${baseCounts[c] ?: 0} في القاعدة + $onBoard على اللوحة"
        }
        return parts.joinToString(" | ")
    }

    private fun arabic(c: PlayerColor) = when (c) {
        PlayerColor.RED -> "أحمر"; PlayerColor.GREEN -> "أخضر"
        PlayerColor.YELLOW -> "أصفر"; PlayerColor.BLUE -> "أزرق"
    }
}

/** Stateful: remembers board geometry and learned piece templates for the session. */
class VisionEngine {
    private var geometry: BoardGeometry? = null
    private val detector = PieceDetector()

    fun resetCalibration() {
        geometry = null
        detector.reset()
    }

    fun analyze(source: PixelImage): VisionResult {
        val (img, factor) = source.downscaleToWidth(WORKING_WIDTH)
        val cls = ColorClasses.map(img)
        val notes = mutableListOf<String>()

        var geo = geometry
        var boardConf = if (geo != null) BoardDetector.verify(geo, img, cls) else 0.0
        if (geo == null || boardConf < MIN_BOARD_CONF) {
            geo = BoardDetector.detect(img, cls)
            geometry = geo
            boardConf = if (geo != null) BoardDetector.verify(geo, img, cls) else 0.0
        }
        if (geo == null || boardConf < MIN_BOARD_CONF) {
            notes.add("لوحة لودو غير واضحة في الإطار")
            return VisionResult(null, boardConf, emptyMap(), emptyList(), emptySet(), null, 0.0, notes, factor)
        }

        val gray = img.toGray()
        val w = img.width
        val h = img.height

        val baseCounts = mutableMapOf<PlayerColor, Int>()
        for (color in PlayerColor.values()) {
            detector.learnFromBase(gray, w, h, geo, color)
            baseCounts[color] = (0 until 4).count { detector.slotFilled(gray, w, h, geo, color, it) }
        }
        val active = detector.learned.toSet()
        val filledSlots = PlayerColor.values().associateWith { c ->
            (0 until 4).filter { detector.slotFilled(gray, w, h, geo, c, it) }
        }

        val pieces = mutableListOf<DetectedPiece>()
        val positions = mutableMapOf<PlayerColor, List<Int>>()
        var consistent = true
        for (color in active.sortedBy { it.ordinal }) {
            val rels = MutableList(baseCounts.getValue(color)) { -1 }
            for (hit in detector.detect(gray, w, h, cls, geo, color)) {
                val rel = BoardGeometry.relOf(color, hit.cell)
                pieces.add(DetectedPiece(color, hit.cell, rel, hit.score))
                if (rel == null) {
                    notes.add("حجر $color في خانة غير ممكنة (${hit.cell.row},${hit.cell.col})")
                    consistent = false
                } else rels.add(rel)
            }
            if (rels.size != 4) {
                notes.add("عدد أحجار $color المكتشف ${rels.size} من 4 (حجر مخفي أو متراكم أو منتهٍ؟)")
                consistent = false
            }
            positions[color] = rels.sorted()
        }
        if (active.isEmpty()) notes.add("لا قوالب أحجار: ابدأ التشغيل والأحجار في القواعد")
        else if (active.size < 2) {
            notes.add("لاعب واحد فقط معروف الشكل: لا حالة مؤكدة (يلزم لاعبان على الأقل)")
            consistent = false
        }

        val state = if (consistent && positions.isNotEmpty()) LudoState(positions) else null
        val minScore = pieces.minOfOrNull { it.score } ?: 1.0
        val confidence = if (state == null) 0.0 else boardConf * minScore.coerceIn(0.0, 1.0)
        return VisionResult(geo, boardConf, baseCounts, pieces, active, state, confidence, notes, factor, filledSlots)
    }

    companion object {
        const val WORKING_WIDTH = 540
        const val MIN_BOARD_CONF = 0.75
    }
}
