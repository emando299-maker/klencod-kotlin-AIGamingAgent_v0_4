package com.aigamingagent.core

import android.graphics.Bitmap

enum class LogLevel { INFO, WARN, ERROR }

data class LogLine(val timeMs: Long, val level: LogLevel, val text: String)

/** Snapshot of everything the UI shows. Immutable; replaced atomically by AgentHub.update. */
data class AgentSnapshot(
    val aiStatus: String = "متوقف",
    val captureStatus: String = "متوقف",
    val visionStatus: String = "غير مفعّل (المرحلة 2)",
    val decisionStatus: String = "غير مفعّل (المرحلة 4)",
    val moves: Int = 0,
    val lastDecision: String = "—",
    /** null = no recognition has run yet. Never faked. */
    val confidence: Float? = null,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val framesReceived: Long = 0,
    val framesAnalyzed: Long = 0,
    val measuredFps: Float = 0f
)

/** Process-wide state shared between the capture service and the UI. */
object AgentHub {
    @Volatile
    var snapshot: AgentSnapshot = AgentSnapshot()
        private set

    /** Latest captured frame (replaced, never recycled while visible). */
    @Volatile
    var frame: Bitmap? = null

    /** Dice value typed by the user for testing (dice reading from the screen is not implemented yet). */
    @Volatile
    var manualRoll: Int? = null

    private val lock = Any()
    private val logs = mutableListOf<LogLine>()
    private const val MAX_LOGS = 300

    fun update(block: (AgentSnapshot) -> AgentSnapshot) {
        synchronized(lock) { snapshot = block(snapshot) }
    }

    fun log(level: LogLevel, text: String) {
        synchronized(lock) {
            logs.add(LogLine(System.currentTimeMillis(), level, text))
            if (logs.size > MAX_LOGS) logs.removeAt(0)
        }
    }

    fun recentLogs(count: Int): List<LogLine> = synchronized(lock) { logs.takeLast(count) }
}
