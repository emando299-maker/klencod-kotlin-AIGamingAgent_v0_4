package com.aigamingagent.ludo

/**
 * Player colours in clockwise board order. startIndex is the player's start square on the shared
 * 52-square track. NOTE: the real Yalla Ludo colour layout must be mapped by the vision layer
 * (calibration); this engine only assumes a standard clockwise 4-player board.
 */
enum class PlayerColor(val startIndex: Int) { RED(0), GREEN(13), YELLOW(26), BLUE(39) }

/**
 * Piece position is RELATIVE to its owner:
 *  -1 = in base, 0..50 = on the shared track (0 = own start square),
 *  51..55 = own home column (cannot be captured), 56 = finished.
 */
object Track {
    const val BASE = -1
    const val LAST_TRACK = 50
    const val HOME_COLUMN_START = 51
    const val FINISHED = 56
    const val SIZE = 52
    const val PIECES = 4
}

/** Rule switches. Defaults follow standard Ludo; Yalla Ludo variants must be verified in-game. */
data class LudoConfig(
    val exitRoll: Int = 6,
    val extraTurnOnSix: Boolean = true,
    val extraTurnOnCapture: Boolean = true,
    val extraTurnOnFinish: Boolean = true,
    /** true: a roll that overshoots the finish is illegal. */
    val exactFinish: Boolean = true,
    /** Absolute track squares where pieces cannot be captured (start squares + star squares). */
    val safeSquares: Set<Int> = setOf(0, 8, 13, 21, 26, 34, 39, 47)
)

data class LudoState(val pieces: Map<PlayerColor, List<Int>>) {
    init {
        pieces.values.forEach { list ->
            require(list.size == Track.PIECES) { "كل لاعب يملك 4 أحجار" }
            require(list.all { it in Track.BASE..Track.FINISHED }) { "موضع حجر غير صالح" }
        }
    }

    fun positions(color: PlayerColor): List<Int> =
        pieces[color] ?: throw IllegalArgumentException("لاعب غير موجود: $color")

    fun withPiece(color: PlayerColor, index: Int, position: Int): LudoState {
        val updated = positions(color).toMutableList().also { it[index] = position }
        return LudoState(pieces + (color to updated))
    }

    fun hasWon(color: PlayerColor): Boolean = positions(color).all { it == Track.FINISHED }

    companion object {
        fun initial(colors: List<PlayerColor>) =
            LudoState(colors.associateWith { List(Track.PIECES) { Track.BASE } })
    }
}

/** Absolute track square of a relative position, or null if in base / home column / finished. */
fun absSquare(color: PlayerColor, rel: Int): Int? =
    if (rel in 0..Track.LAST_TRACK) (color.startIndex + rel) % Track.SIZE else null

data class Move(
    val color: PlayerColor,
    val pieceIndex: Int,
    val from: Int,
    val to: Int,
    val captured: List<Pair<PlayerColor, Int>>,
    val extraTurn: Boolean
) {
    val exitsBase: Boolean get() = from == Track.BASE
    val finishes: Boolean get() = to == Track.FINISHED
}
