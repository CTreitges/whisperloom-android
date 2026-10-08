package com.chris.whisperloom.overlay

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import com.chris.whisperloom.AppNav
import com.chris.whisperloom.AudioRecorder
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineSkip
import com.chris.whisperloom.TranscriptionEngine
import com.chris.whisperloom.a11y.TextInserterAccessibilityService
import com.chris.whisperloom.api.ApiNotConfiguredException
import com.chris.whisperloom.api.isRetryable
import com.chris.whisperloom.history.History
import com.chris.whisperloom.history.HistorySource
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Schwebender Mikro-Knopf (Overlay ueber allen Apps, UX-Spec §5.1/§5.2). Tippen startet und
 * beendet das Diktat, der erkannte Text wird per Bedienungshilfe ins fokussierte Feld
 * eingefuegt — Gboard bleibt dabei die aktive Tastatur.
 *
 * Der Knopf zeigt vier Zustaende ([BubbleState]), weil die Transkription ueber das Netz
 * laeuft und spuerbar dauert: bereit, nimmt auf (mit Timer), sendet, fehlgeschlagen.
 * Ein fehlgeschlagenes Diktat bleibt gepuffert und kann per Tippen erneut gesendet
 * werden; Ziehen auf das Abbrechen-Ziel am unteren Rand verwirft es. Laeuft die
 * Textverbesserung, fuegt ein Tipp den erkannten Text sofort ohne KI ein ([RefineSkip]).
 *
 * Optik: [BubbleVisuals] beschreibt den Zustand, [BubbleRenderer] zeichnet ihn,
 * [CancelTarget] ist das Abbrechen-Ziel, [BubbleNotification] die Foreground-Notification.
 *
 * Foreground-Service (Typ microphone), damit der Knopf dauerhaft sichtbar bleibt.
 */
class FloatingMicService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private val recorder = AudioRecorder()
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "loom-float-io") }
    private val main = Handler(Looper.getMainLooper())

    private var bubbleView: View? = null
    private var renderer: BubbleRenderer? = null
    private lateinit var cancelTarget: CancelTarget
    private lateinit var lp: WindowManager.LayoutParams

    @Volatile private var state = BubbleState.IDLE

    /** IDLE-Hinweis "Kopiert — einfuegen" (2 s nach dem Clipboard-Fallback). */
    private var copiedHint = false

    /** Audio des letzten fehlgeschlagenen Versuchs — Grundlage fuer den Wiederholen-Tipp. */
    private var pendingSamples: FloatArray? = null

    /** Ausweg der laufenden Uebertragung; null = keine. */
    @Volatile private var refineSkip: RefineSkip? = null

    /** SENDING in der Textverbesserung: Tippen fuegt den Text ohne KI ein. */
    private var refining = false

    private var recordingStartedAt = 0L

    /** Timer + Blink-Punkt im 500-ms-Takt, auf die Sekundengrenze ausgerichtet. */
    private val tick = object : Runnable {
        override fun run() {
            if (state != BubbleState.RECORDING) return
            val elapsed = elapsedMs()
            renderer?.updateTimer(elapsed)
            main.postDelayed(this, BubbleUi.DOT_PERIOD_MS - elapsed % BubbleUi.DOT_PERIOD_MS)
        }
    }

    private val clearCopiedHint = Runnable {
        if (state == BubbleState.IDLE && copiedHint) {
            copiedHint = false
            render()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        cancelTarget = CancelTarget(this, wm)
        try {
            startAsForeground()
        } catch (e: Exception) {
            Log.e(TAG, "Foreground-Start fehlgeschlagen", e)
            stopSelf()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast(getString(R.string.float_no_overlay))
            stopSelf()
            return
        }
        addBubble()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    // --- Foreground-Notification ---------------------------------------------

    // MICROPHONE (API 30) wird als Zahl eingebaut; Android 10 nimmt sie an, weil sie dem
    // foregroundServiceType="microphone" aus dem Manifest entspricht.
    @SuppressLint("InlinedApi")
    private fun startAsForeground() {
        BubbleNotification.ensureChannel(this)
        val notif = BubbleNotification.build(this, state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(BubbleNotification.ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(BubbleNotification.ID, notif)
        }
    }

    /** Akzentfarbe der Notification folgt der Aufnahme (loom_recording waehrend RECORDING). */
    private fun refreshNotification() {
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(BubbleNotification.ID, BubbleNotification.build(this, state))
        }
    }

    // --- Bubble --------------------------------------------------------------

    @SuppressLint("InflateParams") // Overlay-Fenster ohne Elternteil; die LayoutParams setzt wm.addView.
    private fun addBubble() {
        val v = LayoutInflater.from(this).inflate(R.layout.floating_mic, null)
        val r = BubbleRenderer(v) { BubbleAnimators.reduceMotion(this) }
        renderer = r
        // Gemerkte Position wiederherstellen; die Bubble-Groesse steht vor dem Layout
        // noch nicht fest, deshalb hier mit 0 clampen (haelt sie im Bildschirm) und
        // beim ersten Ziehen exakt nachziehen.
        val start = clampToScreen(prefs.floatX, prefs.floatY, 0, 0)
        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = start.x
            y = start.y
        }
        // Touch-Ziel ist der 68-dp-Kreis; der Klick-Listener bedient TalkBacks Doppeltipp.
        r.bubble.setOnTouchListener(dragTapListener())
        r.bubble.setOnClickListener { onTap() }
        recorder.onAmplitude = { amp -> r.level = amp }
        wm.addView(v, lp)
        bubbleView = v
        applyState(BubbleState.IDLE)
    }

    private fun clampToScreen(x: Int, y: Int, width: Int, height: Int): BubblePos {
        val dm = resources.displayMetrics
        return BubblePosition.clamp(x, y, width, height, dm.widthPixels, dm.heightPixels)
    }

    private fun savePosition() {
        prefs.floatX = lp.x
        prefs.floatY = lp.y
    }

    // --- Abbrechen-Ziel ------------------------------------------------------

    /** Nur zeigen, wenn es auch etwas zu verwerfen gibt. */
    private fun canDiscard() = state == BubbleState.RECORDING || state == BubbleState.ERROR

    private fun isOverCancelTarget(): Boolean {
        val cancel = cancelTarget.circleRect() ?: return false
        val frame = renderer?.frameRect() ?: return false
        val radius = (BubblePosition.CANCEL_HIT_RADIUS_DP * resources.displayMetrics.density).toInt()
        return BubblePosition.isOverCancel(
            lp.x + frame.left, lp.y + frame.top, frame.width(), frame.height(),
            cancel.left, cancel.top, cancel.width(), cancel.height(),
            radius,
        )
    }

    /** Magnet-Optik + Haptik CONFIRM beim Eintritt in den Treffer-Radius. */
    private fun updateCancelHit() {
        if (!cancelTarget.isShown) return
        val over = isOverCancelTarget()
        if (over == cancelTarget.isHit) return
        cancelTarget.setHit(over)
        if (over) renderer?.haptic(BubbleMotion.Haptic.CONFIRM)
    }

    private fun dragTapListener() = object : View.OnTouchListener {
        // Systemweite Schwelle statt fester Pixelzahl: 12 px sind auf einem dichten
        // Display nur ~3 dp — dann galt schon ein leichtes Zittern beim Tippen als
        // Ziehen (Diktat startete nicht), waehrend eine bewusste kleine Korrektur der
        // Position umgekehrt als Tippen durchging und die Aufnahme startete.
        private val touchSlop = ViewConfiguration.get(this@FloatingMicService).scaledTouchSlop

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        // Kein performClick(): es spielte den System-Klickton ab — genau zum Aufnahmestart, also
        // mit im Diktat. TalkBack bedient den Knopf ueber den OnClickListener (addBubble).
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, e: MotionEvent): Boolean {
            val root = bubbleView ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        if (!moved && canDiscard()) cancelTarget.show()
                        moved = true
                    }
                    if (!moved) return true // unter der Schwelle: noch nicht verschieben
                    val p = clampToScreen(startX + dx, startY + dy, root.width, root.height)
                    lp.x = p.x
                    lp.y = p.y
                    runCatching { wm.updateViewLayout(root, lp) }
                    updateCancelHit()
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        onTap()
                    } else if (cancelTarget.isHit) {
                        renderer?.haptic(BubbleMotion.Haptic.REJECT)
                        cancelTarget.hide()
                        // Der Knopf soll nach dem Verwerfen nicht ueber dem Ziel liegen
                        // bleiben — zurueck an die gemerkte Position.
                        restorePosition()
                        discard()
                    } else {
                        cancelTarget.hide()
                        savePosition()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    cancelTarget.hide()
                    if (moved) savePosition()
                }
            }
            return true
        }
    }

    private fun restorePosition() {
        val p = clampToScreen(prefs.floatX, prefs.floatY, 0, 0)
        lp.x = p.x
        lp.y = p.y
        runCatching { wm.updateViewLayout(bubbleView, lp) }
    }

    private fun onTap() = when (state) {
        BubbleState.IDLE -> startRec()
        BubbleState.RECORDING -> stopRec()
        BubbleState.SENDING -> skipRefine() // laeuft schon; nur in der Textverbesserung gibt es den Ausweg
        BubbleState.ERROR -> retry()
    }

    // --- Zustands-Anzeige ----------------------------------------------------

    private fun elapsedMs() = SystemClock.elapsedRealtime() - recordingStartedAt

    private fun render() {
        val visual = BubbleVisuals.visualFor(state, copiedHint, BubbleAnimators.reduceMotion(this), refining)
        renderer?.render(visual, elapsedMs())
    }

    /**
     * Zustand setzen und zeichnen. [copied] zeigt in IDLE fuer 2 s "Kopiert — einfuegen";
     * TalkBack bekommt nur echte Wechsel angesagt.
     */
    private fun applyState(next: BubbleState, copied: Boolean = false) {
        val previous = state
        state = next
        if (next != BubbleState.SENDING) refining = false
        copiedHint = copied
        main.removeCallbacks(tick)
        main.removeCallbacks(clearCopiedHint)
        render()
        if (previous != next) renderer?.announce()
        if (next == BubbleState.RECORDING) main.post(tick)
        if (copied) main.postDelayed(clearCopiedHint, BubbleMotion.COPIED_HINT_MS)
        if ((previous == BubbleState.RECORDING) != (next == BubbleState.RECORDING)) refreshNotification()
    }

    // --- Aufnahme + Transkription -------------------------------------------

    private fun startRec() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast(getString(R.string.kb_need_permission))
            startActivity(AppNav.setup(this, SETUP_STEP_MIC))
            return
        }
        if (!TranscriptionEngine.isConfigured(this)) {
            toast(getString(R.string.float_not_configured))
            startActivity(AppNav.setup(this))
            return
        }
        if (recorder.start()) {
            recordingStartedAt = SystemClock.elapsedRealtime()
            pendingSamples = null
            applyState(BubbleState.RECORDING)
            renderer?.haptic(BubbleMotion.Haptic.CONFIRM)
            TranscriptionEngine.warmUp(this) // lokales Textmodell parallel zur Aufnahme laden
        } else {
            toast(getString(R.string.kb_error))
        }
    }

    private fun stopRec() {
        if (state != BubbleState.RECORDING) return
        applyState(BubbleState.SENDING)
        renderer?.haptic(BubbleMotion.Haptic.CONTEXT_CLICK)
        runIo {
            val samples = recorder.stop()
            // Sehr kurze Aufnahmen (< 0,3 s) verwerfen — meist versehentliche Taps.
            if (samples.size < AudioRecorder.SAMPLE_RATE * 3 / 10) {
                main.post { applyState(BubbleState.IDLE) }
                return@runIo
            }
            send(samples)
        }
    }

    private fun retry() {
        val samples = pendingSamples
        if (samples == null) {
            applyState(BubbleState.IDLE)
            return
        }
        applyState(BubbleState.SENDING)
        renderer?.haptic(BubbleMotion.Haptic.CONTEXT_CLICK)
        runIo { send(samples) }
    }

    /**
     * Hintergrundarbeit, die den Knopf garantiert wieder aus SENDING holt: ein Throwable, das
     * kein Exception ist (OutOfMemoryError bei sehr langen Diktaten), versickert sonst im Future
     * von submit(), und der Knopf reagiert bis zum Neustart nicht mehr.
     */
    private fun runIo(block: () -> Unit) {
        io.execute {
            try {
                block()
            } catch (e: Throwable) {
                Log.e(TAG, "Diktat abgebrochen", e)
                pendingSamples = null
                main.post {
                    toast(getString(R.string.kb_error))
                    applyState(BubbleState.IDLE)
                }
            }
        }
    }

    /** Verwirft eine laufende Aufnahme oder das gepufferte Audio. */
    private fun discard() {
        if (state == BubbleState.RECORDING) recorder.cancel()
        pendingSamples = null
        applyState(BubbleState.IDLE)
        toast(getString(R.string.float_discarded))
    }

    /** Tipp waehrend der Textverbesserung: den erkannten Text sofort ohne KI einfuegen. */
    private fun skipRefine() {
        if (!refining) return
        refineSkip?.skip()
        renderer?.haptic(BubbleMotion.Haptic.CONFIRM)
    }

    /** Main-Thread: die Uebertragung ist in der Textverbesserung angekommen — Label und Ansage nennen den Ausweg. */
    private fun showRefining(skip: RefineSkip) {
        if (refineSkip !== skip || state != BubbleState.SENDING) return
        refining = true
        render()
        renderer?.announce()
    }

    /** Laeuft auf dem io-Thread. */
    private fun send(samples: FloatArray) {
        val skip = RefineSkip()
        refineSkip = skip
        try {
            val dictation = TranscriptionEngine.transcribe(
                applicationContext,
                samples,
                skip = skip,
                onRefineStart = { main.post { showRefining(skip) } },
            )
            val text = dictation.text
            val out = if (prefs.trailingSpace && text.isNotEmpty()) "$text " else text
            // Vor dem Einfuegen, nie aus einem Passwortfeld (Ziel = Fokusfeld der Bedienungshilfe).
            if (!targetIsPassword()) History.record(applicationContext, HistorySource.BUBBLE, dictation)
            pendingSamples = null
            main.post {
                var copied = false
                if (out.isNotBlank() && !TextInserterAccessibilityService.tryInsert(out)) {
                    fallbackClipboard(out)
                    copied = true
                }
                applyState(BubbleState.IDLE, copied = copied)
                renderer?.flashSuccess()
                dictation.result.skipped?.let { toast(getString(R.string.refine_skipped, it)) }
                    ?: dictation.result.note?.let { toast(it) }
            }
        } catch (e: ApiNotConfiguredException) {
            pendingSamples = null
            main.post {
                toast(getString(R.string.float_not_configured))
                applyState(BubbleState.IDLE)
                startActivity(AppNav.setup(this))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transkription fehlgeschlagen", e)
            // Nur puffern, wenn ein zweiter Versuch ueberhaupt Sinn hat — sonst
            // haengt der Knopf dauerhaft im Fehlerzustand.
            val retryable = e.isRetryable()
            pendingSamples = if (retryable) samples else null
            main.post {
                toast(e.message ?: getString(R.string.kb_error))
                applyState(if (retryable) BubbleState.ERROR else BubbleState.IDLE)
                renderer?.shake()
                renderer?.haptic(BubbleMotion.Haptic.REJECT)
            }
        } finally {
            refineSkip = null
        }
    }

    private fun fallbackClipboard(text: String) {
        runCatching {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("whisperloom", text.trim()))
        }
        toast(getString(R.string.float_clipboard_fallback))
    }

    private fun toast(msg: String) = main.post {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        isRunning = false
        main.removeCallbacks(tick)
        main.removeCallbacks(clearCopiedHint)
        cancelTarget.hide()
        renderer?.release()
        renderer = null
        runCatching { bubbleView?.let { wm.removeView(it) } }
        bubbleView = null
        if (recorder.isRecording) recorder.cancel()
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FloatingMic"

        /** Assistenten-Schritt "Mikrofon erlauben" (UX-Spec §2.2). */
        private const val SETUP_STEP_MIC = 3
        const val ACTION_STOP = "com.chris.whisperloom.STOP_FLOAT"

        /**
         * Naht fuer Tests: ist das Zielfeld ein Passwortfeld? Robolectric hat kein aktives Fenster
         * fuer die Bedienungshilfe. Ohne Bedienungshilfe ist das Ziel unbekannt (false).
         */
        @VisibleForTesting
        internal var targetIsPassword: () -> Boolean = TextInserterAccessibilityService::focusedIsPassword

        /** Ob der schwebende Knopf aktuell laeuft (fuer die Setup-Statusanzeige). */
        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            val i = Intent(context, FloatingMicService::class.java)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, FloatingMicService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
