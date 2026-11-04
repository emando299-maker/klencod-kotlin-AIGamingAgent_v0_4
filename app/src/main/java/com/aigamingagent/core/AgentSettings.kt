package com.aigamingagent.core

import android.content.Context
import com.aigamingagent.ludo.PlayerColor

class AgentSettings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("agent_settings", Context.MODE_PRIVATE)

    /** Frames per second that are converted and analysed (1..10). */
    var analysisFps: Int
        get() = prefs.getInt(KEY_FPS, 3).coerceIn(1, 10)
        set(v) { prefs.edit().putInt(KEY_FPS, v.coerceIn(1, 10)).apply() }

    /** Capture resolution as a percentage of the real screen (25..100). Applied on next start. */
    var scalePercent: Int
        get() = prefs.getInt(KEY_SCALE, 50).coerceIn(25, 100)
        set(v) { prefs.edit().putInt(KEY_SCALE, v.coerceIn(25, 100)).apply() }

    /** false = decisions are only displayed (dry run). Must be switched on deliberately. */
    var liveTaps: Boolean
        get() = prefs.getBoolean(KEY_LIVE, false)
        set(v) { prefs.edit().putBoolean(KEY_LIVE, v).apply() }

    /** Which colour is the user's. Default YELLOW (bottom-left in the supplied screenshots). */
    var myColor: PlayerColor
        get() = runCatching { PlayerColor.valueOf(prefs.getString(KEY_COLOR, "YELLOW")!!) }.getOrDefault(PlayerColor.YELLOW)
        set(v) { prefs.edit().putString(KEY_COLOR, v.name).apply() }

    private companion object {
        const val KEY_LIVE = "live_taps"
        const val KEY_COLOR = "my_color"
        const val KEY_FPS = "analysis_fps"
        const val KEY_SCALE = "scale_percent"
    }
}
