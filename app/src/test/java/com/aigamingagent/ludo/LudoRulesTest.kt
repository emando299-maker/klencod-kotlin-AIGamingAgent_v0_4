package com.aigamingagent.ludo

import com.aigamingagent.ludo.PlayerColor.BLUE
import com.aigamingagent.ludo.PlayerColor.GREEN
import com.aigamingagent.ludo.PlayerColor.RED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoRulesTest {
    private val rules = LudoRules()
    private val allBase = listOf(-1, -1, -1, -1)
    private fun state(red: List<Int>, green: List<Int> = allBase) =
        LudoState(mapOf(RED to red, GREEN to green))

    @Test fun nothingMovesFromBaseWithoutSix() {
        assertTrue(rules.legalMoves(LudoState.initial(listOf(RED, GREEN)), RED, 3).isEmpty())
    }

    @Test fun sixExitsEveryBasePiece() {
        val moves = rules.legalMoves(LudoState.initial(listOf(RED, GREEN)), RED, 6)
        assertEquals(4, moves.size)
        assertTrue(moves.all { it.exitsBase && it.to == 0 && it.extraTurn })
    }

    @Test fun overshootingFinishIsIllegal() {
        assertTrue(rules.legalMoves(state(listOf(54, -1, -1, -1)), RED, 3).isEmpty())
    }

    @Test fun exactFinishWorksAndGivesExtraTurn() {
        val m = rules.legalMoves(state(listOf(54, -1, -1, -1)), RED, 2).single()
        assertTrue(m.finishes)
        assertTrue(m.extraTurn)
    }

    @Test fun captureOnUnsafeSquare() {
        // GREEN rel 46 -> abs (13+46)%52 = 7. RED rel 5 + 2 = abs 7.
        val s = state(listOf(5, -1, -1, -1), listOf(46, -1, -1, -1))
        val m = rules.legalMoves(s, RED, 2).single()
        assertEquals(listOf(GREEN to 0), m.captured)
        assertTrue(m.extraTurn)
        assertEquals(-1, rules.apply(s, m).positions(GREEN)[0])
        assertEquals(7, rules.apply(s, m).positions(RED)[0])
    }

    @Test fun noCaptureOnSafeSquare() {
        // abs 8 is a safe (star) square. GREEN rel 47 -> abs 8.
        val s = state(listOf(6, -1, -1, -1), listOf(47, -1, -1, -1))
        val m = rules.legalMoves(s, RED, 2).single()
        assertTrue(m.captured.isEmpty())
    }

    @Test fun relativeToAbsoluteMapping() {
        assertEquals(0, absSquare(BLUE, 13))
        assertEquals(null, absSquare(RED, 51))
        assertEquals(null, absSquare(RED, Track.BASE))
    }

    @Test fun threatFromBehindWithinSixSquares() {
        // RED rel 10 = abs 10. GREEN rel 45 = abs 6; GREEN needs 4 to reach abs 10.
        val s = state(listOf(10, -1, -1, -1), listOf(45, -1, -1, -1))
        assertEquals(1.0 / 6.0, rules.threat(s, RED, 10), 1e-9)
    }

    @Test fun noThreatWhenOpponentLeavesTrackFirst() {
        // GREEN rel 50 turns into its home column, cannot land on abs 12.
        val s = state(listOf(12, -1, -1, -1), listOf(50, -1, -1, -1))
        assertEquals(0.0, rules.threat(s, RED, 12), 1e-9)
    }

    @Test fun safeSquareIsNeverThreatened() {
        val s = state(listOf(8, -1, -1, -1), listOf(47, -1, -1, -1))
        assertEquals(0.0, rules.threat(s, RED, 8), 1e-9)
    }

    @Test fun winDetection() {
        assertTrue(state(listOf(56, 56, 56, 56)).hasWon(RED))
        assertFalse(state(listOf(56, 56, 56, 55)).hasWon(RED))
    }
}
