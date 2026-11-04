package com.aigamingagent.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import com.aigamingagent.MainActivity
import com.aigamingagent.action.AccessibilityTapper
import com.aigamingagent.action.ActionController
import com.aigamingagent.action.ActionKind
import com.aigamingagent.core.AgentHub
import com.aigamingagent.core.AgentSnapshot
import com.aigamingagent.core.AgentSettings
import com.aigamingagent.core.LogLevel
import com.aigamingagent.vision.VisionEngine
import com.aigamingagent.vision.VisionOverlay

/**
 * Screen Capture Engine (Module A).
 * Owns MediaProjection. Android 14 rules respected: foreground service of type mediaProjection is
 * started BEFORE getMediaProjection, and the projection callback is registered BEFORE
 * createVirtualDisplay. A projection token is single-use, so every start needs a fresh consent.
 */
class ScreenCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var active = false

    private val gate = FrameGate(3)
    private var vision = VisionEngine()
    private var lastNotes = ""
    private var controller = ActionController(AccessibilityTapper())
    private var frameToScreen = 1.0
    private var lastKind: ActionKind? = null
    private lateinit var settings: AgentSettings

    private var received = 0L
    private var analyzed = 0L
    private var fpsWindowStart = 0L
    private var fpsWindowCount = 0
    private var errorsLogged = 0
    private var cachedThermalFps = -1
    private var lastThermalCheck = 0L

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            AgentHub.log(LogLevel.WARN, "توقفت مشاركة الشاشة (من المستخدم أو النظام)")
            shutdown("توقفت مشاركة الشاشة")
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        settings = AgentSettings(applicationContext)
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_EMERGENCY_STOP -> {
                AgentHub.log(LogLevel.WARN, "إيقاف طوارئ")
                shutdown("تم الإيقاف الطارئ")
                stopSelf()
            }
            ACTION_STOP -> {
                shutdown("تم إيقاف التقاط الشاشة")
                stopSelf()
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        // Must be called promptly after startForegroundService(), and before getMediaProjection().
        startForegroundCompat()
        if (active) return

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultData == null) {
            fail("بيانات إذن التقاط الشاشة غير متاحة")
            return
        }

        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.areNotificationsEnabled()) {
                AgentHub.log(LogLevel.WARN, "الإشعارات معطلة: لن يظهر إشعار التشغيل الدائم. فعّلها من إعدادات النظام.")
            }

            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val p = manager.getMediaProjection(resultCode, resultData)
                ?: throw IllegalStateException("getMediaProjection أعاد null")
            projection = p
            p.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

            val (screenW, screenH) = realScreenSize()
            val scale = settings.scalePercent / 100f
            val width = (((screenW * scale).toInt()) / 2 * 2).coerceAtLeast(2)
            val height = (((screenH * scale).toInt()) / 2 * 2).coerceAtLeast(2)
            val density = resources.displayMetrics.densityDpi

            val thread = HandlerThread("AIGameFrameWorker", Process.THREAD_PRIORITY_BACKGROUND)
            thread.start()
            worker = thread
            val handler = Handler(thread.looper)
            workerHandler = handler

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader
            reader.setOnImageAvailableListener({ r -> onFrame(r) }, handler)

            virtualDisplay = p.createVirtualDisplay(
                "AIGamingAgentDisplay", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, handler
            )

            vision = VisionEngine(); lastNotes = ""
            controller = ActionController(AccessibilityTapper()); lastKind = null
            frameToScreen = screenW.toDouble() / width
            if (settings.liveTaps) AgentHub.log(LogLevel.WARN, "التنفيذ الفعلي للمسات مفعّل")
            received = 0; analyzed = 0; fpsWindowStart = 0; fpsWindowCount = 0; errorsLogged = 0
            active = true
            AgentHub.update {
                it.copy(
                    aiStatus = "التقاط فقط (لا قرارات في هذه المرحلة)",
                    captureStatus = "يعمل — ${width}×${height}",
                    frameWidth = width, frameHeight = height
                )
            }
            AgentHub.log(LogLevel.INFO, "بدأ الالتقاط ${width}×${height} (شاشة ${screenW}×${screenH}) تحليل ${settings.analysisFps} إطار/ثانية")
            AgentHub.log(LogLevel.INFO, "إن اخترت مشاركة تطبيق واحد في نافذة النظام فاختر Yalla Ludo، وإلا قد تتجمد المعاينة.")
            updateNotification("التقاط الشاشة يعمل")
        } catch (e: Exception) {
            fail("فشل تشغيل الالتقاط: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun onFrame(reader: ImageReader) {
        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
        try {
            received++
            val now = SystemClock.elapsedRealtime()
            gate.setFps(effectiveFps(now))
            if (!gate.shouldProcess(now)) return

            val bitmap = imageToBitmap(image)
            analyzed++
            runVision(bitmap)

            if (fpsWindowStart == 0L) fpsWindowStart = now
            fpsWindowCount++
            val elapsed = now - fpsWindowStart
            if (elapsed >= 1000) {
                val fps = fpsWindowCount * 1000f / elapsed
                fpsWindowStart = now; fpsWindowCount = 0
                AgentHub.update {
                    it.copy(framesReceived = received, framesAnalyzed = analyzed, measuredFps = fps)
                }
            }
        } catch (e: Exception) {
            if (errorsLogged < 3) {
                errorsLogged++
                AgentHub.log(LogLevel.ERROR, "خطأ في معالجة إطار: ${e.message ?: e.javaClass.simpleName}")
            }
        } finally {
            image.close()
        }
    }

    /** Phase 2/3: reads the board. It never acts: dice reading and touch execution are not implemented yet. */
    private fun runVision(bitmap: Bitmap) {
        val result = vision.analyze(VisionOverlay.toPixelImage(bitmap))
        AgentHub.frame = VisionOverlay.draw(bitmap, result)
        val status = controller.onFrame(
            result, AgentHub.manualRoll, settings.myColor, frameToScreen,
            settings.liveTaps, SystemClock.elapsedRealtime()
        )
        if (status.kind == ActionKind.VERIFIED) AgentHub.manualRoll = null
        AgentHub.update {
            it.copy(
                visionStatus = result.summary(),
                decisionStatus = status.text,
                moves = controller.moves,
                lastDecision = status.decision?.let { d -> describe(d) } ?: it.lastDecision,
                confidence = if (result.board != null) result.confidence.toFloat() else null
            )
        }
        if (status.kind != lastKind) {
            lastKind = status.kind
            if (status.kind == ActionKind.FAILED || status.kind == ActionKind.HALTED)
                AgentHub.log(LogLevel.ERROR, status.text)
            else if (status.kind == ActionKind.EXECUTING || status.kind == ActionKind.VERIFIED)
                AgentHub.log(LogLevel.INFO, status.text)
        }
        val notes = result.notes.joinToString(" • ")
        if (notes != lastNotes) {
            lastNotes = notes
            if (notes.isNotEmpty()) AgentHub.log(LogLevel.WARN, notes)
            else AgentHub.log(LogLevel.INFO, "حالة اللوحة مفهومة: ${result.summary()}")
        }
    }

    private fun describe(d: com.aigamingagent.ludo.ScoredMove): String {
        val m = d.move
        val from = if (m.exitsBase) "القاعدة" else m.from.toString()
        return "حجر ${m.pieceIndex + 1}: $from ← ${m.to} (${d.reasons.joinToString("، ")})"
    }

    /** Copies an RGBA_8888 frame into a Bitmap, removing the row padding the ImageReader may add. */
    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowPadding = plane.rowStride - pixelStride * image.width
        val padded = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888
        )
        padded.copyPixelsFromBuffer(plane.buffer)
        if (rowPadding == 0) return padded
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        padded.recycle()
        return cropped
    }

    /** Drops to 1 fps when the device reports severe thermal state. Checked at most every 5 s. */
    private fun effectiveFps(now: Long): Int {
        val wanted = settings.analysisFps
        if (Build.VERSION.SDK_INT < 29) return wanted
        if (now - lastThermalCheck > 5000) {
            lastThermalCheck = now
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val hot = pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
            val newFps = if (hot) 1 else -1
            if (newFps != cachedThermalFps) {
                cachedThermalFps = newFps
                if (hot) AgentHub.log(LogLevel.WARN, "حرارة الجهاز مرتفعة: خُفّض التحليل إلى إطار/ثانية")
            }
        }
        return if (cachedThermalFps == 1) 1 else wanted
    }

    @Suppress("DEPRECATION")
    private fun realScreenSize(): Pair<Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            return b.width() to b.height()
        }
        val m = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(m)
        return m.widthPixels to m.heightPixels
    }

    private fun fail(message: String) {
        AgentHub.log(LogLevel.ERROR, message)
        shutdown(message)
        stopSelf()
    }

    /** Idempotent. Unregisters the callback first so projection.stop() cannot re-enter. */
    private fun shutdown(reason: String) {
        val p = projection
        val hadSession = active || p != null
        active = false
        projection = null
        runCatching { p?.unregisterCallback(projectionCallback) }
        runCatching { imageReader?.setOnImageAvailableListener(null, null) }
        runCatching { virtualDisplay?.release() }
        runCatching { imageReader?.close() }
        runCatching { p?.stop() }
        runCatching { worker?.quitSafely() }
        virtualDisplay = null; imageReader = null; worker = null; workerHandler = null
        AgentHub.frame = null
        AgentHub.update { AgentSnapshot() }
        if (hadSession) AgentHub.log(LogLevel.INFO, reason)
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
    }

    private fun startForegroundCompat() {
        val channel = NotificationChannel(CHANNEL_ID, "AI Gaming Agent", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        val notification = buildNotification("التقاط الشاشة قيد التجهيز")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(message: String): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val stop = PendingIntent.getService(
            this, 11, Intent(this, ScreenCaptureService::class.java).setAction(ACTION_EMERGENCY_STOP), flags
        )
        val open = PendingIntent.getActivity(this, 12, Intent(this, MainActivity::class.java), flags)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Gaming Agent")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause), "إيقاف طارئ", stop
                ).build()
            )
            .build()
    }

    private fun updateNotification(message: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(message))
    }

    override fun onDestroy() {
        shutdown("انتهت الخدمة")
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.aigamingagent.action.START_CAPTURE"
        const val ACTION_STOP = "com.aigamingagent.action.STOP_CAPTURE"
        const val ACTION_EMERGENCY_STOP = "com.aigamingagent.action.EMERGENCY_STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "ai_capture_channel"
        private const val NOTIFICATION_ID = 7001
    }
}
