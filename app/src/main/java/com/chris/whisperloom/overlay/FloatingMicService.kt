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
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.core.view.ViewCompat
import com.chris.whisperloom.AppNav
import com.chris.whisperloom.AudioRecorder
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.RefineSkip
import com.chris.whisperloom.TranscriptionEngine
import com.chris.whisperloom.a11y.FieldFocus
import com.chris.whisperloom.a11y.InsertResult
import com.chris.whisperloom.a11y.TextInserterAccessibilityService
import com.chris.whisperloom.api.ApiNotConfiguredException
import com.chris.whisperloom.api.isRetryable
import com.chris.whisperloom.history.History
import com.chris.whisperloom.history.HistorySource
import com.chris.whisperloom.ime.DictationSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot

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
            // Der Aufnahme-Thread ist an einem Fehler gestorben: senden, was schon aufgenommen ist,
            // statt ins geschlossene Mikrofon weiterzuzaehlen.
            if (!recorder.isRecording) {
                stopRec()
                return
            }
            val elapsed = elapsedMs()
            // Grenzen der IME (UX-Spec §6.22): online ab 10 min ein Hinweis, bei 12 min senden.
            when (DictationSession.length(elapsed, lengthLimited)) {
                DictationSession.Length.MAX -> {
                    toast(getString(R.string.float_too_long_sent))
                    stopRec()
                    return
                }
                DictationSession.Length.LONG -> if (!longWarned) {
                    longWarned = true
                    renderer?.haptic(BubbleMotion.Haptic.REJECT)
                    toast(getString(R.string.float_too_long_soon))
                }
                DictationSession.Length.OK -> Unit
            }
            renderer?.updateTimer(elapsed)
            main.postDelayed(this, BubbleUi.DOT_PERIOD_MS - elapsed % BubbleUi.DOT_PERIOD_MS)
        }
    }

    /**
     * Die laufende Aufnahme geht an die Online-Erkennung und hat damit eine Hoechstdauer. Gemessen
     * wird die Zeit seit offenem Mikrofon — der Knopf kennt keine Pause, sie ist also die Tonlaenge.
     */
    private var lengthLimited = false

    /** Der Hinweis "noch 2 Minuten" kam schon. */
    private var longWarned = false

    /** Touch-Geste am Knopf: Tipp, Halten zum Sprechen, Modus-Kreuz, Ziehen ([BubbleGesture]). */
    private val gesture = BubbleGesture()

    /** Druckpunkt der laufenden Geste in Bildschirm-Pixeln; Mitte des Modus-Kreuzes. */
    private var downX = 0f
    private var downY = 0f

    /**
     * Halte-Zeit abgelaufen (nur in IDLE): Modus-Kreuz oeffnen und mit "Halten zum Sprechen" die
     * Aufnahme starten. Ohne Mikrofonrecht oder Einrichtung nur das Kreuz — der Hinweis kommt beim
     * Loslassen ohne Ziel (wie ein Tipp), nicht mitten in der Geste (docs/design/bubble-textfeld.md §1).
     */
    private val holdStart = Runnable {
        if (!gesture.holdElapsed(state == BubbleState.IDLE)) return@Runnable
        if (prefs.holdToTalk && canRecord()) {
            startRec()
            if (state == BubbleState.RECORDING) gesture.recordingStarted()
        }
        openCross()
    }

    private val clearCopiedHint = Runnable {
        if (state == BubbleState.IDLE && copiedHint) {
            copiedHint = false
            render()
            updateAlpha()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        cancelTarget = CancelTarget(this, wm)
        crossWindow = ModeCrossWindow(this, wm)
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
        instance = this
        _running.value = true
    }

    /**
     * Immer START_NOT_STICKY: Nach einem Prozess-Kill startet das System den Dienst nicht von selbst
     * neu. Ein Neustart ohne Nutzeraktion waere ein Mikrofon-Foreground-Service aus dem Hintergrund —
     * das System verweigert dort den Mikrofonzugriff (ab Android 14 wirft startForeground), und
     * Google Play wertet Dauer-Neustarts als Pruefrisiko (CTR-205).
     * Der Knopf startet wieder ueber App, Assistent oder Widget.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
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
            title = WINDOW_TITLE
        }
        // Touch-Ziel ist der 68-dp-Kreis; der Klick-Listener bedient TalkBacks Doppeltipp.
        r.bubble.setOnTouchListener(dragTapListener())
        r.bubble.setOnClickListener { onTap() }
        recorder.onAmplitude = { amp -> r.level = amp }
        // Erst am naechsten Textfeld mit offener Tastatur sichtbar, nicht sofort auf dem Home-Screen.
        v.visibility = View.GONE
        wm.addView(v, lp)
        bubbleView = v
        applyState(BubbleState.IDLE)
        refreshActions()
        onFocus(TextInserterAccessibilityService.currentFocus())
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

        var startX = 0
        var startY = 0

        // Kein performClick(): es spielte den System-Klickton ab — genau zum Aufnahmestart, also
        // mit im Diktat. TalkBack bedient den Knopf ueber den OnClickListener (addBubble).
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, e: MotionEvent): Boolean {
            val root = bubbleView ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y
                    crossMoved = false
                    touching = true
                    updateAlpha()
                    gesture.down()
                    main.postDelayed(holdStart, holdTimeout())
                }
                MotionEvent.ACTION_MOVE -> {
                    // Nach der Halte-Zeit waehlt Bewegung nur ein Ziel im Kreuz, sie verschiebt nichts.
                    if (gesture.phase == BubbleGesture.Phase.HELD) {
                        aimCross(e.rawX, e.rawY, touchSlop.toFloat())
                        return true
                    }
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()
                    if ((abs(dx) > touchSlop || abs(dy) > touchSlop) && gesture.beyondSlop()) {
                        main.removeCallbacks(holdStart)
                        when {
                            canDiscard() -> cancelTarget.show(CancelTarget.Kind.DISCARD)
                            state == BubbleState.IDLE -> cancelTarget.show(CancelTarget.Kind.HIDE)
                        }
                    }
                    if (!gesture.dragging) return true // unter der Schwelle: noch nicht verschieben
                    val p = clampToScreen(startX + dx, startY + dy, root.width, root.height)
                    lp.x = p.x
                    lp.y = p.y
                    runCatching { wm.updateViewLayout(root, lp) }
                    updateCancelHit()
                }
                MotionEvent.ACTION_UP -> {
                    main.removeCallbacks(holdStart)
                    touching = false
                    crossWindow.hide()
                    when (gesture.up()) {
                        // Halten ohne Aufnahme und nach Bewegung im Kreuz: nur schliessen (umentschieden).
                        BubbleGesture.Release.TAP -> if (!crossMoved) onTap()
                        // Kurz nach dem Start losgelassen = langsamer Tipp: weiter aufnehmen statt
                        // einen fast leeren Clip zu senden, den die Erkennung mit Erfundenem fuellt.
                        BubbleGesture.Release.SEND -> releaseHeld(e)
                        BubbleGesture.Release.DROP -> drop()
                        BubbleGesture.Release.PICK ->
                            (gesture.armed as? ModeCross.CrossTarget.Mode)?.let { pickMode(it.mode) }
                        BubbleGesture.Release.DISCARD -> if (state == BubbleState.RECORDING) {
                            renderer?.haptic(BubbleMotion.Haptic.REJECT)
                            discard()
                        }
                        BubbleGesture.Release.NONE -> Unit
                    }
                    endTouch()
                }
                MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(holdStart)
                    touching = false
                    val dragged = gesture.dragging
                    val onDiscard = gesture.armed == ModeCross.CrossTarget.Discard
                    val send = gesture.cancel()
                    cancelTarget.hide()
                    crossWindow.hide()
                    if (dragged) savePosition()
                    // Ein abgefangener Touch soll kein gehaltenes Diktat fressen (wie die IME) — ausser der
                    // Finger lag schon auf dem ✕; ohne Sprache geht nichts raus.
                    if (send && onDiscard) discard() else if (send) stopRec(requireSpeech = true)
                    endTouch()
                }
            }
            return true
        }
    }

    /** Finger weg: Deckkraft zurueck, und was waehrend der Geste aufgeschoben war, jetzt verstecken. */
    private fun endTouch() {
        updateAlpha()
        updateVisibility()
    }

    /** Halte-Zeit des Systems: folgt der Einstellung "Beruehrungs- und Haltedauer". */
    private fun holdTimeout() = ViewConfiguration.getLongPressTimeout().toLong()

    /** Loslassen nach einem Zug (vor der Halte-Zeit begonnen, also nie mit gehaltener Aufnahme). */
    private fun drop() {
        if (cancelTarget.isHit && cancelTarget.kind == CancelTarget.Kind.HIDE && state == BubbleState.IDLE) {
            renderer?.haptic(BubbleMotion.Haptic.CONFIRM)
            cancelTarget.hide()
            restorePosition() // kommt beim naechsten Feld an der gemerkten Stelle zurueck
            hideUntilNextField()
        } else if (cancelTarget.isHit && canDiscard()) {
            // canDiscard: das Ziel blieb stehen, waehrend die Aufnahme schon in SENDING ging
            // (12-min-Grenze, totes Mikrofon) — dann nichts verwerfen, die Uebertragung laeuft.
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

    // --- Modus-Kreuz (docs/design/bubble-textfeld.md §2/§3) --------------------

    private lateinit var crossWindow: ModeCrossWindow

    /** Modi im Kreuz und in den TalkBack-Aktionen: "Prompt" nur, wenn eingeschaltet. */
    private fun crossModes() = RefineMode.settings(prefs.promptLevelEnabled)

    private fun canRecord() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            TranscriptionEngine.isConfigured(this)

    /** Der Finger hat im offenen Kreuz den Touch-Slop verlassen: Loslassen ist dann kein langsamer Tipp mehr. */
    private var crossMoved = false

    /** Kreuz um den Druckpunkt, kaum sichtbar, solange der Finger auf dem Knopf liegt. */
    private fun openCross() {
        crossMoved = false
        val layout = ModeCross.layout(downX, downY, safeArea(), crossModes(), resources.displayMetrics.density)
        crossWindow.show(layout, prefs.refineMode)
    }

    /** Bildschirm ohne Status-, Navigationsleiste und Kamera-Aussparung, in Bildschirm-Pixeln wie rawX/rawY. */
    private fun safeArea(): ModeCross.Area {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            val b = metrics.bounds
            return ModeCross.Area(b.left + insets.left, b.top + insets.top, b.right - insets.right, b.bottom - insets.bottom)
        }
        val dm = resources.displayMetrics
        return ModeCross.Area(0, (STATUS_BAR_FALLBACK_DP * dm.density).toInt(), dm.widthPixels, dm.heightPixels)
    }

    /** Finger bewegt sich im Halten: Deckkraft nach Fingerweg, Ziel waehlen, beim Einrasten Haptik. */
    private fun aimCross(x: Float, y: Float, slopPx: Float) {
        val layout = crossWindow.layout ?: return
        val density = resources.displayMetrics.density
        val travel = hypot(x - downX, y - downY)
        if (travel > slopPx) crossMoved = true
        val target = ModeCross.pick(x, y, downX, downY, layout, gesture.armed, density)
        if (target != gesture.armed && target != null) renderer?.haptic(BubbleMotion.Haptic.CONTEXT_CLICK)
        gesture.aim(target)
        crossWindow.update(ModeCross.alpha(travel, slopPx, density), target)
    }

    /**
     * Loslassen im Halten ohne Ziel: Hat der Finger das Kreuz nie verlassen und kam das Loslassen frueh,
     * war es ein langsamer Tipp (Aufnahme laeuft weiter); sonst senden, ohne Sprache nichts.
     */
    private fun releaseHeld(e: MotionEvent) {
        if (!crossMoved && BubbleGesture.isSlowTap(e.downTime, e.eventTime, holdTimeout())) return
        stopRec(requireSpeech = true)
    }

    /** Modus dauerhaft setzen; laeuft die gehaltene Aufnahme, mit ihm senden — ohne Sprache nur der Wechsel. */
    private fun pickMode(mode: RefineMode) {
        setMode(mode)
        if (state == BubbleState.RECORDING) stopRec(requireSpeech = true, haptic = false)
    }

    private fun setMode(mode: RefineMode) {
        prefs.refineMode = mode
        renderer?.haptic(BubbleMotion.Haptic.CONFIRM)
        toast(getString(R.string.float_mode_set, getString(ModeCrossWindow.labelRes(mode))))
        refreshActions()
    }

    // --- TalkBack (N11): je Modus eine Aktion, dazu Ausblenden bzw. Verwerfen ---

    private val actionIds = mutableListOf<Int>()

    private fun refreshActions() {
        val view = renderer?.bubble ?: return
        // Der aktive Modus gehoert zur Ansage am Knopf, nicht erst ins Aktionsmenue.
        ViewCompat.setStateDescription(
            view, getString(R.string.float_mode_set, getString(ModeCrossWindow.labelRes(prefs.refineMode))),
        )
        actionIds.forEach { ViewCompat.removeAccessibilityAction(view, it) }
        actionIds.clear()
        for (action in BubbleActions.of(state, crossModes(), prefs.refineMode)) {
            val label = when (action) {
                is BubbleAction.SetMode -> getString(
                    if (action.active) R.string.cd_bubble_action_mode_active else R.string.cd_bubble_action_mode,
                    getString(ModeCrossWindow.labelRes(action.mode)),
                )
                BubbleAction.Hide -> getString(R.string.cd_hide)
                BubbleAction.Discard -> getString(R.string.cd_bubble_action_discard)
            }
            actionIds += ViewCompat.addAccessibilityAction(view, label) { _, _ ->
                when (action) {
                    is BubbleAction.SetMode -> setMode(action.mode)
                    BubbleAction.Hide -> if (state == BubbleState.IDLE) {
                        restorePosition()
                        hideUntilNextField()
                    }
                    BubbleAction.Discard -> if (canDiscard()) discard()
                }
                true
            }
        }
    }

    // --- Sichtbar nur am Textfeld, Ausblenden bis zum naechsten Feld (UX-Spec §6.22) ---

    /** Entscheidet aus den Meldungen der Bedienungshilfe, ob der Knopf zu sehen ist. */
    private val visibility = BubbleVisibility()

    /** Finger liegt auf dem Knopf: voll deckend, und waehrend der Geste wird nicht versteckt. */
    private var touching = false

    /** Aufnahme oder Senden: der Knopf bleibt sichtbar, auch ohne Feld oder Tastatur. */
    private fun busy() = state == BubbleState.RECORDING || state == BubbleState.SENDING

    /**
     * Nimmt nur den View weg; der Dienst laeuft weiter — aus dem Hintergrund duerfte er unter
     * Android 15 nicht wieder starten. Keine Frist, kein "Einblenden": zurueck kommt er am naechsten Feld.
     */
    private fun hideUntilNextField() {
        visibility.hide()
        updateVisibility()
    }

    private fun onFocus(focus: FieldFocus?) {
        visibility.onFocus(focus)
        updateVisibility()
    }

    /** Zeigt oder versteckt den View nach [visibility]; nie mitten in einer Geste. */
    private fun updateVisibility() {
        val v = bubbleView ?: return
        val show = visibility.visible(busy())
        if (show == (v.visibility == View.VISIBLE) || (!show && touching)) return
        if (show) {
            restorePosition() // Display kann sich gedreht haben: neu in den Bildschirm begrenzen
            refreshActions() // der Modus kann sich in der App geaendert haben
        }
        v.visibility = if (show) View.VISIBLE else View.GONE
    }

    /** Ruhe halb deckend, Beruehrung/Aufnahme/Senden voll ([BubbleVisibility.alpha]). */
    private fun updateAlpha() {
        val v = bubbleView ?: return
        val target = BubbleVisibility.alpha(state, touching, copiedHint)
        v.animate().cancel()
        // Versteckt oder nicht angehaengt laeuft keine Animation: direkt setzen.
        if (BubbleAnimators.reduceMotion(this) || v.visibility != View.VISIBLE || !v.isAttachedToWindow) {
            v.alpha = target
        } else {
            v.animate().alpha(target).setDuration(BubbleMotion.ALPHA_FADE_MS).start()
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
        updateAlpha()
        updateVisibility() // Ende von Aufnahme/Senden bei geschlossener Tastatur: jetzt weg
        if (previous != next) {
            renderer?.announce()
            refreshActions()
        }
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
        // Vor dem Mikrofon: die Vibration soll nicht als Ton in der Aufnahme landen.
        renderer?.haptic(BubbleMotion.Haptic.CONFIRM)
        if (recorder.start()) {
            recordingStartedAt = SystemClock.elapsedRealtime()
            pendingSamples = null
            lengthLimited = prefs.engine == Engine.ONLINE
            longWarned = false
            applyState(BubbleState.RECORDING)
            TranscriptionEngine.warmUp(this) // lokales Textmodell parallel zur Aufnahme laden
        } else {
            toast(getString(R.string.kb_error))
        }
    }

    /**
     * Aufnahme beenden und senden. [requireSpeech] (Moduswahl im Kreuz): ohne Sprachpegel nichts senden,
     * keine Anfrage an die Erkennung — dann war es nur ein Moduswechsel (N7) oder ein stilles Halten.
     * [haptic] false, wenn der Aufrufer schon eine Haptik gegeben hat (eine je Loslassen).
     */
    private fun stopRec(requireSpeech: Boolean = false, haptic: Boolean = true) {
        if (state != BubbleState.RECORDING) return
        applyState(BubbleState.SENDING)
        if (haptic) renderer?.haptic(BubbleMotion.Haptic.CONTEXT_CLICK)
        runIo {
            val samples = recorder.stop()
            // Sehr kurze Aufnahmen (< 0,3 s) verwerfen — meist versehentliche Taps.
            if (samples.size < AudioRecorder.SAMPLE_RATE * 3 / 10 || (requireSpeech && !ModeCross.hasSpeech(samples))) {
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
            val password = targetIsPassword()
            if (!password) History.record(applicationContext, HistorySource.BUBBLE, dictation)
            pendingSamples = null
            main.post {
                // Passwortfeld: weder einfuegen noch in die Zwischenablage (UX-Spec §6.22). Die
                // Bedienungshilfe prueft beim Einfuegen selbst noch einmal (Fokus kann wechseln).
                val result = when {
                    out.isBlank() -> null
                    password -> InsertResult.BLOCKED
                    else -> TextInserterAccessibilityService.tryInsert(out)
                }
                if (result == InsertResult.BLOCKED) {
                    applyState(BubbleState.IDLE)
                    renderer?.haptic(BubbleMotion.Haptic.REJECT)
                    toast(getString(R.string.float_password_blocked))
                    return@post
                }
                val copied = result == InsertResult.FAILED
                if (copied) fallbackClipboard(out)
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
        if (instance === this) instance = null
        _running.value = false
        main.removeCallbacks(tick)
        main.removeCallbacks(clearCopiedHint)
        main.removeCallbacks(holdStart)
        cancelTarget.hide()
        crossWindow.hide()
        renderer?.release()
        renderer = null
        runCatching { bubbleView?.let { wm.removeView(it) } }
        bubbleView = null
        if (recorder.hasSession) recorder.cancel()
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FloatingMic"

        /** Fenstertitel des Knopfs in `dumpsys window` (Emulator-Pruefung tools/bubble_emulator_check.py). */
        const val WINDOW_TITLE = "WhisperLoomBubble"

        /** Assistenten-Schritt "Mikrofon erlauben" (UX-Spec §2.2). */
        private const val SETUP_STEP_MIC = 3

        /** Statusleiste vor Android 11 (ohne WindowMetrics): Modus-Kreuz bleibt darunter. */
        private const val STATUS_BAR_FALLBACK_DP = 24
        const val ACTION_STOP = "com.chris.whisperloom.STOP_FLOAT"

        /**
         * Naht fuer Tests: ist das Zielfeld ein Passwortfeld? Robolectric hat kein aktives Fenster
         * fuer die Bedienungshilfe. Ohne Bedienungshilfe ist das Ziel unbekannt (false).
         */
        @VisibleForTesting
        internal var targetIsPassword: () -> Boolean = TextInserterAccessibilityService::focusedIsPassword

        private val _running = MutableStateFlow(false)

        /**
         * Ob der schwebende Knopf laeuft — beobachtbar, damit die App auch dann umschaltet, wenn
         * die Benachrichtigung den Dienst beendet, waehrend sie offen ist (CTR-197).
         */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        /** Momentaufnahme von [running] (SystemStatus.read). */
        val isRunning: Boolean get() = _running.value

        /** Laufender Dienst fuer Meldungen der Bedienungshilfe (gleicher Prozess). */
        @Volatile private var instance: FloatingMicService? = null

        /**
         * Die Bedienungshilfe meldet Fokus und Tastatur (nur Metadaten, kein Inhalt); null = sie ist
         * aus. Ohne Meldung erscheint der Knopf nicht.
         */
        fun focusChanged(focus: FieldFocus?) {
            val service = instance ?: return
            service.main.post { service.onFocus(focus) }
        }

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
