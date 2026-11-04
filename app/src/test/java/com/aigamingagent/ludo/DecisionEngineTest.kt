package com.aigamingagent.ludo

import com.aigamingagent.ludo.PlayerColor.GREEN
import com.aigamingagent.ludo.PlayerColor.RED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecisionEngineTest {
    private val engine = DecisionEngine()
    private val allBase = listOf(-1, -1, -1, -1)
    private fun state(red: List<Int>, green: List<Int> = allBase) =
        LudoState(mapOf(RED to red, GREEN to green))

    @Test fun noLegalMoveReturnsNull() {
        assertNull(engine.choose(LudoState.initial(listOf(RED, GREEN)), RED, 3))
    }

    @Test fun prefersExitingBaseOverSmallProgress() {
        val best = engine.choose(state(listOf(-1, 20, -1, -1)), RED, 6)!!
        assertEquals(-1, best.move.from)
    }

    @Test fun prefersCapture() {
        val s = state(listOf(5, 30, -1, -1), listOf(46, -1, -1, -1))
        assertEquals(0, engine.choose(s, RED, 2)!!.move.pieceIndex)
    }

    @Test fun prefersFinishing() {
        val s = state(listOf(53, 20, -1, -1))
        assertEquals(0, engine.choose(s, RED, 3)!!.move.pieceIndex)
    }

    @Test fun escapesThreatToSafeSquare() {
        // RED piece 0 (abs 10) is threatened by GREEN (abs 6); rolling 3 reaches safe abs 13.
        val s = state(listOf(10, 30, -1, -1), listOf(45, -1, -1, -1))
        assertEquals(0, engine.choose(s, RED, 3)!!.move.pieceIndex)
    }

    @Test fun choiceIsDeterministic() {
        val s = state(listOf(-1, -1, -1, -1))
        val a = engine.choose(s, RED, 6)!!.move
        val b = engine.choose(s, RED, 6)!!.move
        assertEquals(a, b)
    }
}
