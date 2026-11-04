package com.aigamingagent.ui

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.content.Intent
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.aigamingagent.core.AgentSettings
import com.aigamingagent.action.AgentAccessibilityService
import com.aigamingagent.ludo.PlayerColor

class SettingsActivity : Activity() {
    private var statusView: TextView? = null

    override fun onResume() {
        super.onResume()
        statusView?.text = if (AgentAccessibilityService.instance != null) "خدمة إمكانية الوصول: مفعّلة"
        else "خدمة إمكانية الوصول: غير مفعّلة (على أندرويد 13+ قد تحتاج السماح بالإعدادات المقيّدة من معلومات التطبيق)"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = AgentSettings(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@SettingsActivity, 20), Ui.dp(this@SettingsActivity, 28),
                Ui.dp(this@SettingsActivity, 20), Ui.dp(this@SettingsActivity, 24))
        }
        root.addView(TextView(this).apply {
            text = "الإعدادات"; textSize = 22f; setTextColor(Ui.TEXT)
            gravity = Gravity.CENTER; setPadding(0, 0, 0, Ui.dp(this@SettingsActivity, 18))
        }, Ui.matchWrap())

        // Analysis speed
        val speed = Ui.panel(this)
        speed.addView(Ui.title(this, "سرعة التحليل"))
        val fpsLabel = TextView(this).apply { setTextColor(Ui.TEXT); textSize = 14f }
        val fpsBar = SeekBar(this).apply { max = 9; progress = settings.analysisFps - 1 }
        fun showFps(v: Int) { fpsLabel.text = "$v إطار/ثانية" }
        showFps(settings.analysisFps)
        fpsBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                settings.analysisFps = p + 1; showFps(p + 1)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        speed.addView(fpsLabel, Ui.matchWrap())
        speed.addView(fpsBar, Ui.matchWrap())
        speed.addView(note("قيمة أقل = حرارة وبطارية أقل. تُطبَّق فورًا. تنخفض تلقائيًا إلى 1 عند ارتفاع حرارة الجهاز بشدة."), Ui.matchWrap())
        root.addView(speed, Ui.matchWrap())

        // Capture resolution
        val res = Ui.panel(this)
        res.addView(Ui.title(this, "دقة الالتقاط (استهلاك المعالج والذاكرة)"))
        val scaleLabel = TextView(this).apply { setTextColor(Ui.TEXT); textSize = 14f }
        val scaleBar = SeekBar(this).apply { max = 15; progress = (settings.scalePercent - 25) / 5 }
        fun showScale(v: Int) { scaleLabel.text = "$v% من دقة الشاشة" }
        showScale(settings.scalePercent)
        scaleBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = 25 + p * 5
                settings.scalePercent = v; showScale(v)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        res.addView(scaleLabel, Ui.matchWrap())
        res.addView(scaleBar, Ui.matchWrap())
        res.addView(note("تُطبَّق عند بدء جلسة التقاط جديدة. القيمة الموصى بها 50%: عليها اختُبرت قراءة اللوحة. دقة أقل قد تُضعف التعرف على الأحجار."), Ui.matchWrap())
        root.addView(res, Ui.matchWrap().apply { topMargin = Ui.dp(this@SettingsActivity, 12) })

        // Execution
        val exec = Ui.panel(this)
        exec.addView(Ui.title(this, "التنفيذ"))
        val live = Switch(this).apply {
            text = "تنفيذ اللمسات فعليًا"; setTextColor(Ui.TEXT); isChecked = settings.liveTaps
            setOnCheckedChangeListener { _, on -> settings.liveTaps = on }
        }
        exec.addView(live, Ui.matchWrap())
        exec.addView(note("مغلق = يعرض القرار فقط. لن تُرسل أي لمسة قبل تفعيل خدمة إمكانية الوصول وهذا المفتاح معًا."), Ui.matchWrap())
        val colorBtn = Ui.button(this, "", Ui.BG, Ui.CYAN)
        colorBtn.background = Ui.rounded(this, Ui.BG, Ui.STROKE, 10)
        fun showColor() { colorBtn.text = "لوني: ${settings.myColor.name}  (اضغط للتغيير)" }
        showColor()
        colorBtn.setOnClickListener {
            val all = PlayerColor.values()
            settings.myColor = all[(settings.myColor.ordinal + 1) % all.size]; showColor()
        }
        exec.addView(colorBtn, Ui.matchWrap().apply { topMargin = Ui.dp(this@SettingsActivity, 10) })
        val acc = Ui.button(this, "فتح إعدادات إمكانية الوصول", Ui.BG, Ui.CYAN)
        acc.background = Ui.rounded(this, Ui.BG, Ui.STROKE, 10)
        acc.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        exec.addView(acc, Ui.matchWrap().apply { topMargin = Ui.dp(this@SettingsActivity, 8) })
        val accStatus = TextView(this).apply { textSize = 12f; setTextColor(Ui.MUTED) }
        exec.addView(accStatus, Ui.matchWrap())
        statusView = accStatus
        root.addView(exec, Ui.matchWrap().apply { topMargin = Ui.dp(this@SettingsActivity, 12) })

        val back = Ui.button(this, "رجوع", Ui.CYAN, Ui.BG)
        back.setOnClickListener { finish() }
        root.addView(back, Ui.matchWrap().apply { topMargin = Ui.dp(this@SettingsActivity, 20) })

        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(root) })
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text; textSize = 12f; setTextColor(Ui.MUTED)
        setPadding(0, Ui.dp(this@SettingsActivity, 8), 0, 0)
    }
}
