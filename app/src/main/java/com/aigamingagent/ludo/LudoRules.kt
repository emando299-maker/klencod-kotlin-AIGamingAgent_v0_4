package com.aigamingagent.ludo

/** Pure game rules. No Android, no vision dependencies. */
class LudoRules(val config: LudoConfig = LudoConfig()) {

    fun legalMoves(state: LudoState, color: PlayerColor, roll: Int): List<Move> {
        require(roll in 1..6) { "قيمة النرد يجب أن تكون بين 1 و6" }
        val positions = state.positions(color)
        return positions.indices.mapNotNull { moveFor(state, color, it, roll) }
    }

    private fun moveFor(state: LudoState, color: PlayerColor, index: Int, roll: Int): Move? {
        val from = state.positions(color)[index]
        if (from == Track.FINISHED) return null
        val to: Int = if (from == Track.BASE) {
            if (roll != config.exitRoll) return null
            0
        } else {
            val target = from + roll
            when {
                target <= Track.FINISHED -> target
                config.exactFinish -> return null
                else -> Track.FINISHED
            }
        }
        val captured = capturesAt(state, color, to)
        val extra = (roll == 6 && config.extraTurnOnSix) ||
            (captured.isNotEmpty() && config.extraTurnOnCapture) ||
            (to == Track.FINISHED && config.extraTurnOnFinish)
        return Move(color, index, from, to, captured, extra)
    }

    private fun capturesAt(state: LudoState, color: PlayerColor, rel: Int): List<Pair<PlayerColor, Int>> {
        val abs = absSquare(color, rel) ?: return emptyList()
        if (abs in config.safeSquares) return emptyList()
        val result = mutableListOf<Pair<PlayerColor, Int>>()
        for ((other, list) in state.pieces) {
            if (other == color) continue
            list.forEachIndexed { j, pos -> if (absSquare(other, pos) == abs) result.add(other to j) }
        }
        return result
    }

    fun apply(state: LudoState, move: Move): LudoState {
        require(state.positions(move.color)[move.pieceIndex] == move.from) { "الحركة لا تطابق الحالة" }
        var next = state.withPiece(move.color, move.pieceIndex, move.to)
        for ((c, j) in move.captured) next = next.withPiece(c, j, Track.BASE)
        return next
    }

    /**
     * Probability-like threat to a piece of [color] at relative position [rel]: each opponent piece
     * that can land exactly on that square with one die roll adds 1/6. Safe squares, base, home
     * column and finished are never threatened.
     */
    fun threat(state: LudoState, color: PlayerColor, rel: Int): Double {
        val abs = absSquare(color, rel) ?: return 0.0
        if (abs in config.safeSquares) return 0.0
        var total = 0.0
        for ((other, list) in state.pieces) {
            if (other == color) continue
            val targetRel = (abs - other.startIndex + Track.SIZE) % Track.SIZE
            if (targetRel !in 1..Track.LAST_TRACK) continue
            for (pos in list) {
                if (pos in 0..Track.LAST_TRACK && targetRel > pos && targetRel - pos <= 6) total += 1.0 / 6.0
            }
        }
        return total
    }
}
