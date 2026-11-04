package com.aigamingagent.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Small helpers for the programmatic dark UI (no AndroidX / XML dependencies). */
object Ui {
    val BG = Color.rgb(7, 11, 20)
    val PANEL = Color.rgb(17, 25, 41)
    val STROKE = Color.rgb(36, 52, 80)
    val CYAN = Color.rgb(34, 211, 238)
    val GREEN = Color.rgb(52, 211, 153)
    val RED = Color.rgb(239, 68, 68)
    val TEXT = Color.rgb(232, 238, 248)
    val MUTED = Color.rgb(148, 163, 184)

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun rounded(c: Context, fill: Int, stroke: Int? = null, radiusDp: Int = 14) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(c, radiusDp).toFloat()
            if (stroke != null) setStroke(dp(c, 1), stroke)
        }

    fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    fun panel(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(c, PANEL, STROKE)
        setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
    }

    fun title(c: Context, text: String) = TextView(c).apply {
        this.text = text; textSize = 13f; setTextColor(CYAN)
        typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(c, 8))
    }

    fun button(c: Context, label: String, fill: Int, textColor: Int) = Button(c).apply {
        text = label; textSize = 14f; setTextColor(textColor); isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(c, fill, null, 12)
        minHeight = dp(c, 52); minimumHeight = dp(c, 52)
    }

    /** Adds "label ........ value" row and returns the value TextView for later updates. */
    fun row(c: Context, parent: LinearLayout, label: String): TextView {
        val line = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(c, 5), 0, dp(c, 5))
        }
        line.addView(TextView(c).apply {
            text = label; textSize = 13f; setTextColor(MUTED)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val value = TextView(c).apply {
            text = "—"; textSize = 13f; setTextColor(TEXT)
            gravity = Gravity.END; typeface = Typeface.DEFAULT_BOLD
        }
        line.addView(value, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f))
        parent.addView(line, matchWrap())
        return value
    }
}
