package com.aigamingagent.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.aigamingagent.core.AgentHub
import com.aigamingagent.core.LogLevel

/** Dispatches taps. Enabled only by the user in Android Accessibility settings; it reads no screen content. */
class AgentAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AgentHub.log(LogLevel.INFO, "خدمة إمكانية الوصول متصلة")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* intentionally unused */ }

    override fun onInterrupt() { /* nothing to interrupt */ }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        AgentHub.log(LogLevel.WARN, "خدمة إمكانية الوصول انفصلت")
        return super.onUnbind(intent)
    }

    fun tapAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile
        var instance: AgentAccessibilityService? = null
    }
}

class AccessibilityTapper : Tapper {
    override fun tap(x: Float, y: Float): Boolean = AgentAccessibilityService.instance?.tapAt(x, y) ?: false
}
