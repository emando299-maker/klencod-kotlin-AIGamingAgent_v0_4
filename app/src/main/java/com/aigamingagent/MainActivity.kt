package com.aigamingagent

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.aigamingagent.capture.ScreenCaptureService
import com.aigamingagent.core.AgentHub
import com.aigamingagent.core.AgentSettings
import com.aigamingagent.core.LogLevel
import com.aigamingagent.ui.SettingsActivity
import com.aigamingagent.ui.Ui
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private lateinit var preview: ImageView
    private lateinit var aiValue: TextView
    private lateinit var captureValue: TextView
    private lateinit var visionValue: TextView
    private lateinit var decisionValue: TextView
    private lateinit var movesValue: TextView
    private lateinit var lastDecisionValue: TextView
    private lateinit var confidenceValue: TextView
    private lateinit var framesValue: TextView
    private lateinit var logView: TextView
    private lateinit var rollValue: TextView
    private var shownFrame: Bitmap? = null

    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildInterface()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        handler.post(ticker)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(ticker)
    }

    private fun buildInterface() {
        val c = this
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 16), Ui.dp(c, 24), Ui.dp(c, 16), Ui.dp(c, 24))
        }

        root.addView(TextView(c).apply {
            text = "AI GAMING AGENT"; textSize = 24f; setTextColor(Ui.TEXT)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; letterSpacing = 0.12f
        }, Ui.matchWrap())
        root.addView(TextView(c).apply {
            text = "Yalla Ludo • ذكاء محلي • بدون إنترنت"; textSize = 12f; setTextColor(Ui.CYAN)
            gravity = Gravity.CENTER; setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 16))
        }, Ui.matchWrap())

        // Controls
        val controls = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        val start = Ui.button(c, "START AI", Ui.CYAN, Ui.BG)
        start.setOnClickListener { requestScreenCapture() }
        val stop = Ui.button(c, "STOP AI", Ui.PANEL, Ui.TEXT)
        stop.background = Ui.rounded(c, Ui.PANEL, Ui.STROKE, 12)
        stop.setOnClickListener {
            startService(Intent(c, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP))
        }
        controls.addView(start, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(stop, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginStart = Ui.dp(c, 10) })
        root.addView(controls, Ui.matchWrap())

        val emergency = Ui.button(c, "⛔ إيقاف طوارئ", Ui.RED, Ui.TEXT)
        emergency.setOnClickListener {
            startService(Intent(c, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_EMERGENCY_STOP))
        }
        root.addView(emergency, Ui.matchWrap().apply { topMargin = Ui.dp(c, 10) })

        // Preview
        val previewPanel = Ui.panel(c)
        previewPanel.addView(Ui.title(c, "ما يراه الذكاء الاصطناعي"))
        preview = ImageView(c).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = Ui.rounded(c, Ui.BG, null, 8)
        }
        previewPanel.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 280)))
        root.addView(previewPanel, Ui.matchWrap().apply { topMargin = Ui.dp(c, 14) })

        // Status
        val status = Ui.panel(c)
        status.addView(Ui.title(c, "الحالة"))
        aiValue = Ui.row(c, status, "الذكاء الاصطناعي")
        captureValue = Ui.row(c, status, "Screen Capture")
        visionValue = Ui.row(c, status, "Vision Engine")
        decisionValue = Ui.row(c, status, "Decision Engine")
        movesValue = Ui.row(c, status, "عدد الحركات")
        lastDecisionValue = Ui.row(c, status, "آخر قرار")
        confidenceValue = Ui.row(c, status, "نسبة الثقة")
        framesValue = Ui.row(c, status, "الإطارات")
        root.addView(status, Ui.matchWrap().apply { topMargin = Ui.dp(c, 12) })

        // Manual dice (temporary: reading the dice from the screen is not implemented yet)
        val rollPanel = Ui.panel(c)
        rollPanel.addView(Ui.title(c, "نرد يدوي للاختبار"))
        rollValue = TextView(c).apply { textSize = 12f; setTextColor(Ui.MUTED); text = "لا رقم محدد" }
        rollPanel.addView(rollValue, Ui.matchWrap())
        val rollRow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        for (n in 1..6) {
            val b = Ui.button(c, n.toString(), Ui.BG, Ui.CYAN)
            b.background = Ui.rounded(c, Ui.BG, Ui.STROKE, 10)
            b.minWidth = 0; b.minimumWidth = 0
            b.setOnClickListener { AgentHub.manualRoll = n }
            rollRow.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginEnd = Ui.dp(c, 4) })
        }
        val clear = Ui.button(c, "مسح", Ui.BG, Ui.MUTED)
        clear.background = Ui.rounded(c, Ui.BG, Ui.STROKE, 10)
        clear.minWidth = 0; clear.minimumWidth = 0
        clear.setOnClickListener { AgentHub.manualRoll = null }
        rollRow.addView(clear, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f))
        rollPanel.addView(rollRow, Ui.matchWrap())
        root.addView(rollPanel, Ui.matchWrap().apply { topMargin = Ui.dp(c, 12) })

        // Log
        val logPanel = Ui.panel(c)
        logPanel.addView(Ui.title(c, "سجل العمليات والأخطاء"))
        logView = TextView(c).apply {
            textSize = 11f; setTextColor(Ui.MUTED); typeface = Typeface.MONOSPACE
            text = "لا توجد أحداث بعد."
        }
        logPanel.addView(logView, Ui.matchWrap())
        root.addView(logPanel, Ui.matchWrap().apply { topMargin = Ui.dp(c, 12) })

        val settings = Ui.button(c, "الإعدادات", Ui.PANEL, Ui.TEXT)
        settings.background = Ui.rounded(c, Ui.PANEL, Ui.STROKE, 12)
        settings.setOnClickListener { startActivity(Intent(c, SettingsActivity::class.java)) }
        root.addView(settings, Ui.matchWrap().apply { topMargin = Ui.dp(c, 12) })

        setContentView(ScrollView(c).apply { setBackgroundColor(Ui.BG); addView(root) })
    }

    private fun render() {
        val s = AgentHub.snapshot
        aiValue.text = s.aiStatus
        val mode = if (AgentSettings(this).liveTaps) "تنفيذ فعلي" else "تجربة فقط"
        rollValue.text = (AgentHub.manualRoll?.let { "النرد المحدد: $it" } ?: "لا رقم محدد") + " • الوضع: $mode"
        captureValue.text = s.captureStatus
        visionValue.text = s.visionStatus
        decisionValue.text = s.decisionStatus
        movesValue.text = s.moves.toString()
        lastDecisionValue.text = s.lastDecision
        confidenceValue.text = s.confidence?.let { "${(it * 100).toInt()}%" } ?: "—"
        framesValue.text = if (s.framesReceived == 0L) "—"
        else "${s.framesAnalyzed}/${s.framesReceived} • ${"%.1f".format(Locale.US, s.measuredFps)} fps"

        val frame = AgentHub.frame
        if (frame !== shownFrame) {
            preview.setImageBitmap(frame)
            shownFrame = frame
        }

        val lines = AgentHub.recentLogs(40)
        if (lines.isNotEmpty()) {
            logView.text = lines.joinToString("\n") {
                val tag = when (it.level) { LogLevel.INFO -> "I"; LogLevel.WARN -> "W"; LogLevel.ERROR -> "E" }
                "${timeFormat.format(Date(it.timeMs))} [$tag] ${it.text}"
            }
        }
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    @Deprecated("Kept to avoid AndroidX dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CAPTURE) return
        if (resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            }
            startForegroundService(intent)
        } else {
            AgentHub.log(LogLevel.WARN, "لم يُمنح إذن مشاركة الشاشة")
        }
    }

    private companion object {
        const val REQ_CAPTURE = 4101
        const val REQ_NOTIFICATIONS = 4102
    }
}