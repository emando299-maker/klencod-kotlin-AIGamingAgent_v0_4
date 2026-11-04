package com.aigamingagent.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameGateTest {
    @Test fun firstFrameAlwaysPasses() {
        assertTrue(FrameGate(1).shouldProcess(0))
    }

    @Test fun frameInsideIntervalIsDropped() {
        val gate = FrameGate(2) // 500 ms
        assertTrue(gate.shouldProcess(1000))
        assertFalse(gate.shouldProcess(1200))
        assertFalse(gate.shouldProcess(1499))
    }

    @Test fun frameAfterIntervalPasses() {
        val gate = FrameGate(2)
        assertTrue(gate.shouldProcess(1000))
        assertTrue(gate.shouldProcess(1500))
    }

    @Test fun changingFpsChangesInterval() {
        val gate = FrameGate(1) // 1000 ms
        assertTrue(gate.shouldProcess(0))
        gate.setFps(10) // 100 ms
        assertTrue(gate.shouldProcess(100))
    }

    @Test fun fpsIsClamped() {
        val gate = FrameGate(0) // clamped to 1 fps
        assertTrue(gate.shouldProcess(0))
        assertFalse(gate.shouldProcess(500))
    }
}
