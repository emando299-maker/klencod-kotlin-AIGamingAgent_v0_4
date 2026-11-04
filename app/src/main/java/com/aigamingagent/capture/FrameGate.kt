package com.aigamingagent.capture

/** Pure-Kotlin frame throttle so the screen (up to 60 fps) is analysed at a chosen, lower rate. */
class FrameGate(fps: Int) {
    private var minIntervalMs: Long = intervalFor(fps)
    private var started = false
    private var lastAcceptedMs = 0L

    fun setFps(fps: Int) { minIntervalMs = intervalFor(fps) }

    fun shouldProcess(nowMs: Long): Boolean {
        if (!started || nowMs - lastAcceptedMs >= minIntervalMs) {
            started = true
            lastAcceptedMs = nowMs
            return true
        }
        return false
    }

    private fun intervalFor(fps: Int): Long = 1000L / fps.coerceIn(1, 30)
}
