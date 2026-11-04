package com.aigamingagent.ludo

data class Weights(
    val exit: Double = 30.0,
    val progress: Double = 1.0,
    val capture: Double = 50.0,
    val capturedProgress: Double = 0.5,
    val finish: Double = 60.0,
    val safeSquare: Double = 8.0,
    val homeColumn: Double = 12.0,
    val danger: Double = 40.0,
    val escape: Double = 25.0
)

data class ScoredMove(val move: Move, val score: Double, val reasons: List<String>)

/** Heuristic move ranking. Deterministic: same input always gives the same choice. */
class DecisionEngine(
    private val rules: LudoRules = LudoRules(),
    private val w: Weights = Weights()
) {
    /** Best move, or null when there is no legal move. */
    fun choose(state: LudoState, color: PlayerColor, roll: Int): ScoredMove? =
        rank(state, color, roll).firstOrNull()

    fun rank(state: LudoState, color: PlayerColor, roll: Int): List<ScoredMove> =
        rules.legalMoves(state, color, roll)
            .map { score(state, it) }
            .sortedWith(compareByDescending<ScoredMove> { it.score }.thenByDescending { it.move.from }
                .thenBy { it.move.pieceIndex })

    private fun score(state: LudoState, m: Move): ScoredMove {
        var s = 0.0
        val reasons = mutableListOf<String>()

        if (m.exitsBase) { s += w.exit; reasons.add("إخراج حجر من القاعدة") }
        else s += (m.to - m.from) * w.progress

        for ((c, j) in m.captured) {
            val lost = state.positions(c)[j]
            s += w.capture + w.capturedProgress * lost
            reasons.add("أكل حجر $c")
        }
        if (m.finishes) { s += w.finish; reasons.add("وصول للنهاية") }
        if (m.to in Track.HOME_COLUMN_START until Track.FINISHED && m.from < Track.HOME_COLUMN_START) {
            s += w.homeColumn; reasons.add("دخول ممر الأمان")
        }

        val fromAbs = absSquare(m.color, m.from)
        val toAbs = absSquare(m.color, m.to)
        val safe = rules.config.safeSquares
        if (toAbs != null && toAbs in safe && (fromAbs == null || fromAbs !in safe)) {
            s += w.safeSquare; reasons.add("الوصول إلى مربع آمن")
        }

        val after = rules.apply(state, m)
        val threatAfter = rules.threat(after, m.color, m.to)
        if (threatAfter > 0) {
            s -= w.danger * threatAfter * (1.0 + m.to / Track.FINISHED.toDouble())
            reasons.add("خطر بعد الحركة")
        }
        if (!m.exitsBase) {
            val threatBefore = rules.threat(state, m.color, m.from)
            val gained = threatBefore - threatAfter
            if (gained > 0) { s += w.escape * gained; reasons.add("الهروب من خطر") }
        }
        if (reasons.isEmpty()) reasons.add("تقدّم")
        return ScoredMove(m, s, reasons)
    }
}
