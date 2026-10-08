package com.chris.whisperloom.ime

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.TextView
import com.chris.whisperloom.AppNav
import com.chris.whisperloom.AudioRecorder
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Formats
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineDecision
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.RefineSkip
import com.chris.whisperloom.SetupState
import com.chris.whisperloom.TranscriptionEngine
import com.chris.whisperloom.api.ApiNotConfiguredException
import com.chris.whisperloom.api.isRetryable
import com.chris.whisperloom.llm.LocalTextEngine
import com.chris.whisperloom.overlay.BubbleAnimators
import com.chris.whisperloom.overlay.BubbleMotion
import com.chris.whisperloom.overlay.BubbleState
import com.chris.whisperloom.overlay.MicIcon
import com.chris.whisperloom.overlay.MicRings
import com.chris.whisperloom.whisper.OfflineSupport
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Diktier-Tastatur (UX-Spec §5.3): grosser Push-to-talk-Mikro-Knopf mit denselben vier
 * Zustaenden wie der schwebende Knopf ([BubbleState]), Pegelband und ein paar Basis-Tasten.
 * Halten = aufnehmen, loslassen = transkribieren und Text ins aktive Feld schreiben.
 *
 * Wer nicht dauerhaft halten will, wischt beim Aufnehmen nach rechts: die Aufnahme bleibt
 * dann stehen ("festgestellt") und wird ueber die beiden eingeblendeten Tasten gesendet oder
 * verworfen. Nach links wischen verwirft sofort. Die Auswertung selbst steht Android-frei in
 * [DictationGesture].
 *
 * Festgestellt wird die Mikro-Taste zur Pause- bzw. Weiter-Taste ([DictationSession]): In der
 * Pause ist das Mikrofon frei, das Aufgenommene bleibt im Speicher und uebersteht das Schliessen
 * der Tastatur. Gesendet wird dann nur noch ueber die Senden-Taste (mit TalkBack auch ueber eine
 * Aktion der Mikro-Taste, siehe [SessionKeys]).
 *
 * Scheitert die Anfrage (kein Netz, Server-Aussetzer), bleibt das Audio gepuffert und
 * die Wiederholen-Taste erscheint — sonst waere ein langes Diktat verloren. Laeuft die
 * Textverbesserung, fuegt ein Tipp (Mikro-Taste oder Statuszeile) den erkannten Text sofort
 * ohne KI ein ([RefineSkip]).
 */
class WhisperLoomInputMethodService : InputMethodService() {

    private lateinit var prefs: Prefs
    private val recorder = AudioRecorder()

    // Alle Netz-/IO-Arbeiten seriell auf einem Hintergrund-Thread; UI ueber main.
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "loom-ime-io") }
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var state = BubbleState.IDLE

    /** Audio des letzten fehlgeschlagenen Versuchs. */
    private var pendingSamples: FloatArray? = null

    /** Ausweg der laufenden Uebertragung; null = keine. */
    @Volatile private var refineSkip: RefineSkip? = null

    /** SENDING in der Textverbesserung: ein Tipp fuegt den Text ohne KI ein. */
    private var refining = false

    private var statusView: TextView? = null
    private var levelBand: LevelBandView? = null
    private var micZone: View? = null
    private var micButton: ImageButton? = null
    private var rings: MicRings? = null
    private var retryKey: View? = null
    private var gestureTargets: GestureTargets? = null
    private var refineBar: RefineBar? = null
    private var refineKey: View? = null
    private var sessionKeys: SessionKeys? = null

    /** Aktueller Stand der Wisch-Geste; nur waehrend eines liegenden Fingers aussagekraeftig. */
    private var gesturePhase = DictationGesture.Phase.RECORDING

    /** Gehalten, festgestellt oder pausiert; gehoert dem Dienst und uebersteht den Neuaufbau. */
    private val session = DictationSession()

    /** Dieses Aufsetzen hat die Pause beendet — sein Loslassen pausiert nicht gleich wieder. */
    private var resumedByTouch = false

    private var downX = 0f
    private var downY = 0f

    /**
     * Der Finger, der die Geste fuehrt. Ohne das waere immer Zeiger 0 gemeint — und dessen
     * Index wandert, sobald ein anderer Finger abhebt. Die Mikro-Taste faengt naemlich auch
     * Finger ein, die sie gar nicht beruehren: findet der Touch-Dispatch kein anderes Kind,
     * haengt er sie an das bestehende Touch-Ziel.
     */
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    /** Ob der Finger die Systemschwelle ueberschritten hat; haelt bis zum Loslassen. */
    private var dragged = false

    // Gesten-Schwellen in Pixeln; berechnet, sobald die View steht (braucht die Dichte).
    private var armPx = 0f
    private var hysteresisPx = 0f
    private var verticalPx = 0f
    private var slopPx = 0f

    /**
     * Haelt die Dauer in der Statuszeile aktuell, solange die festgestellte Aufnahme laeuft, und
     * pausiert an der Hoechstlaenge. In der Pause steht er — die Uhr zaehlt Aufgenommenes.
     */
    private val lockedTicker = object : Runnable {
        override fun run() {
            if (!session.isLocked) return
            if (session.update(recorder.recordedMs, lengthLimited()) == DictationSession.Length.MAX) {
                capDictation()
                return
            }
            showSessionStatus()
            updateMicDescription()
            main.postDelayed(this, TICK_MS - session.recordedMs % TICK_MS)
        }
    }

    /** Statuszeile: Farbe und Tipp-Ziel je Art (UX-Spec §5.3). */
    private enum class Status {
        HINT, LISTENING, LOCK_ARMED, CANCEL_ARMED, LOCKED, PAUSED, WARNING, DISCARDED,
        TRANSCRIBING, REFINING, ERROR, NEED_PERMISSION, NOT_CONFIGURED, NEEDS_LLM, NEEDS_LOCAL,
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    // performClick() aus onTouch heraus zu rufen (was Lint verlangt) wuerde hier doppelt
    // feuern: der Touch-Pfad bedient die Aufnahme bereits, und der OnClickListener ist
    // ausdruecklich nur fuer Bedienungshilfen da (siehe unten).
    // inflate ohne Elternteil: das System haengt die Tastatur selbst ein und setzt dabei eigene
    // LayoutParams — ein Elternteil gibt es vor setInputView nicht.
    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard_view, null)
        statusView = root.findViewById(R.id.status)
        levelBand = root.findViewById(R.id.level)
        micZone = root.findViewById(R.id.mic_zone)
        micButton = root.findViewById(R.id.mic)
        retryKey = root.findViewById(R.id.key_retry)
        val discardTarget = root.findViewById<ImageButton>(R.id.gesture_discard)
        val lockTarget = root.findViewById<ImageButton>(R.id.gesture_lock)
        gestureTargets = GestureTargets(discardTarget, lockTarget, ::reduceMotion)
        refineBar = RefineBar(root.findViewById(R.id.refine_row), ::reduceMotion)
        measureGesture()
        rings = MicRings(
            pulse = root.findViewById(R.id.mic_pulse),
            ring = root.findViewById(R.id.mic_ring),
            arc = root.findViewById(R.id.mic_progress),
            reduceMotion = ::reduceMotion,
        )
        applyKeyHeight(root)

        recorder.onAmplitude = { amp ->
            levelBand?.setLevel(amp)
            rings?.level = amp
        }

        micButton?.setOnTouchListener { view, ev -> onMicTouch(view, ev) }
        // Zusaetzlich zum Touch-Listener, nicht statt seiner: onTouch liefert true, also ruft
        // das System performClick() nicht von selbst — dieser Listener feuert praktisch nur
        // ueber den Bedienungshilfen-Pfad (TalkBack loest ACTION_CLICK aus). Genau dort war die
        // Taste bisher unbedienbar, weil Gedrueckthalten mit TalkBack nicht ankommt.
        micButton?.setOnClickListener { onMicClick() }
        discardTarget.setOnClickListener { discardDictation() }
        // Im festgestellten Zustand traegt das rechte Ziel das Senden-Symbol (siehe GestureTargets).
        lockTarget.setOnClickListener { stopDictation() }

        val globe = root.findViewById<View>(R.id.key_globe)
        globe.setOnClickListener { switchKeyboard() }
        sessionKeys = SessionKeys(root.findViewById(R.id.mic), globe, ::stopDictation, ::discardDictation)
        root.findViewById<View>(R.id.key_comma).setOnClickListener { commitRaw(", ") }
        root.findViewById<View>(R.id.key_period).setOnClickListener { commitRaw(". ") }
        root.findViewById<View>(R.id.key_space).setOnClickListener { commitRaw(" ") }
        root.findViewById<View>(R.id.key_backspace).setOnClickListener { backspace() }
        root.findViewById<View>(R.id.key_enter).setOnClickListener { performEnter() }
        root.findViewById<View>(R.id.key_settings).setOnClickListener { startActivity(AppNav.settings(this)) }
        refineKey = root.findViewById<View>(R.id.key_refine).also {
            it.setOnClickListener { toggleRefineBar() }
        }
        retryKey?.setOnClickListener { retry() }
        refineBar?.bind(::pickRefineMode)

        // Nach dem Setzen der Listener, nicht davor: setOnClickListener macht eine View
        // wieder bedienbar. Im Ruhezustand sollen die Ziele weder anklickbar noch im
        // Bedienungshilfen-Baum sein.
        //
        // Und: aus dem Dienst-Zustand rekonstruieren statt blind auf Ruhe setzen. Bei einem
        // Konfigurationswechsel (Drehen, Dunkelmodus, Schriftgroesse) baut das Framework den
        // Eingabe-View neu auf, ruft dabei aber KEIN onFinishInputView — Aufnahme, session und
        // state gehoeren dem Dienst und ueberleben. Ohne das zeigte die neue Tastatur Ruhe,
        // waehrend das Mikrofon weiterlief (oder ein pausiertes Diktat wartete) und kein Weg
        // mehr zum Verwerfen fuehrte.
        if (session.withoutFinger && recorder.hasSession) enterLockedUi() else gestureTargets?.hide()
        applyState(state, animate = false)
        restoreStatus()
        return root
    }

    /**
     * Gesten-Schwellen in Pixel. Der System-Slop ist die Untergrenze: eine reine dp-Schwelle
     * koennte auf sehr dichten Displays unter dem liegen, was das System ueberhaupt als
     * Bewegung zaehlt — dann waere die Geste nicht zuverlaessig ausloesbar.
     */
    private fun measureGesture() {
        val density = resources.displayMetrics.density
        val slop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        slopPx = slop
        armPx = maxOf(DictationGesture.ARM_DISTANCE_DP * density, slop)
        hysteresisPx = DictationGesture.RELEASE_HYSTERESIS_DP * density
        verticalPx = maxOf(DictationGesture.VERTICAL_TOLERANCE_DP * density, slop)
    }

    /** Ab fontScale 1,3 werden die Tasten 56 statt 48 dp hoch (UX-Spec §5.3). */
    private fun applyKeyHeight(root: View) {
        val dp = ImeMetrics.keyHeightDp(resources.configuration.fontScale)
        if (dp == ImeMetrics.KEY_HEIGHT_DP) return
        val density = resources.displayMetrics.density
        // Beide Reihen, sonst bleibt der Schnellzugriff bei grosser Schrift zu flach.
        for (id in intArrayOf(R.id.key_row, R.id.refine_row)) {
            val row = root.findViewById<ViewGroup>(id)
            row.layoutParams = row.layoutParams.apply { height = ((dp + KEY_ROW_EXTRA_DP) * density).toInt() }
            for (i in 0 until row.childCount) {
                val key = row.getChildAt(i)
                key.layoutParams = key.layoutParams.apply { height = (dp * density).toInt() }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Ein alter Fehlerzustand gilt fuer das NEUE Feld nicht mehr. Bei [restarting] ist es
        // aber dasselbe Feld — das Framework baut nur neu auf (Drehen, Dunkelmodus). Dann den
        // Puffer behalten, sonst verliert eine Drehung das Audio eines fehlgeschlagenen
        // Diktats, obwohl die Wiederholen-Taste danebensteht.
        if (state == BubbleState.ERROR && !restarting) {
            pendingSamples = null
            applyState(BubbleState.IDLE)
        }
        // Der Eingabe-View wird ueber Feld- und App-Wechsel hinweg wiederverwendet. Eine
        // offen stehende Leiste zeigte sonst die Stufe und den KI-Zugang von vorhin —
        // beides kann sich inzwischen geaendert haben.
        if (refineBar?.isShown == true) refineBar?.show(prefs.refineMode, refineBlocked() == null, prefs.promptLevelEnabled)
        restoreStatus()
    }

    /**
     * Bewusste Wahl fuer die festgestellte Aufnahme: [finishingInput] `false` heisst "nur die
     * Tastatur geht zu, das Feld bleibt" — dann wird fertig transkribiert und der Text noch
     * eingefuegt (kommt die [android.view.inputmethod.InputConnection] doch nicht mehr zurueck,
     * faengt commitDictation das ab). Bei `true` ist die Eingabe ganz beendet, der Text haette
     * nirgends hin — also verwerfen statt ins Leere zu senden.
     *
     * Ohne Feststellen bleibt es beim bisherigen harten Abbruch: dort liegt der Finger noch,
     * die Aufnahme war nie eigenstaendig.
     *
     * Eine Pause uebersteht beides (Plan §1.2): Das Audio liegt im Speicher, das Mikrofon ist
     * frei, und beim naechsten Oeffnen steht wieder "Pausiert bei …" da — nur so hilft die Pause
     * beim Nachschlagen in einer anderen App. Senden fuegt dann ins gerade sichtbare Feld ein.
     */
    override fun onFinishInputView(finishingInput: Boolean) {
        if (!session.isPaused) {
            when {
                session.isLocked -> if (finishingInput) discardDictation() else stopDictation()
                recorder.isRecording -> recorder.cancel()
            }
            endSession()
            if (state == BubbleState.RECORDING) applyState(BubbleState.IDLE)
            levelBand?.stop()
        }
        super.onFinishInputView(finishingInput)
    }

    // --- Geste --------------------------------------------------------------

    /**
     * Push-to-talk plus Wisch-Geste. Gemessen wird relativ zum Druckpunkt, damit die Auswertung
     * nicht davon abhaengt, wo auf der 88-dp-Taste der Finger aufsetzt — und immer am
     * fuehrenden Finger, damit ein zweiter Finger die Geste nicht kapert.
     */
    private fun onMicTouch(view: View, ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = ev.getPointerId(ev.actionIndex)
                downX = ev.x
                downY = ev.y
                dragged = false
                gesturePhase = DictationGesture.Phase.RECORDING
                resumedByTouch = false
                when {
                    // Schon das Aufsetzen setzt fort: wer aus Gewohnheit haelt und spricht,
                    // verliert sonst die ersten Woerter. Danach laeuft es festgestellt weiter.
                    session.isPaused -> resumedByTouch = resumeDictation()
                    // Festgestellt entscheidet erst das Loslassen (= Tipp zum Pausieren).
                    !session.isLocked -> startDictation()
                }
            }

            // Weitere Finger gehoeren nicht zur Geste. Trotzdem konsumieren: sonst bekommt
            // sie ein anderes Ziel und die Taste verliert womoeglich den Touch-Strom.
            MotionEvent.ACTION_POINTER_DOWN -> Unit

            MotionEvent.ACTION_MOVE -> {
                if (session.withoutFinger || state != BubbleState.RECORDING) return true
                val index = ev.findPointerIndex(activePointerId)
                if (index < 0) return true
                val dx = ev.getX(index) - downX
                val dy = ev.getY(index) - downY
                // Ein liegender Finger zittert; ohne diese Schwelle poppen die Ziele bei
                // jedem Diktat auf. Sie haelt, sobald sie einmal ueberschritten wurde —
                // dasselbe Muster wie beim schwebenden Knopf (FloatingMicService).
                if (!dragged) {
                    if (abs(dx) <= slopPx && abs(dy) <= slopPx) return true
                    dragged = true
                }
                updateGesture(dx, dy)
            }

            // Hebt der fuehrende Finger ab, ist die Geste vorbei — auch wenn noch andere liegen.
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                // Nur der fuehrende Finger loest aus. Ohne diese Pruefung beendet der letzte
                // verbliebene Finger die Geste ein zweites Mal: erst stellt das Abheben des
                // fuehrenden fest, dann schickt sein Abheben das Diktat gleich hinterher.
                if (ev.getPointerId(ev.actionIndex) != activePointerId) return true
                activePointerId = MotionEvent.INVALID_POINTER_ID
                releaseGesture(view, ev.getX(ev.actionIndex), ev.getY(ev.actionIndex))
            }

            // Abgefangener Touch (Dialog, Fenster-Wechsel): ohne Feststellen wie bisher
            // senden, damit kein Diktat verloren geht. Mit Feststellen ist nichts zu retten —
            // die Aufnahme laeuft (oder pausiert) weiter und beide Tasten sind bedienbar.
            MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                resumedByTouch = false
                if (!session.withoutFinger) releaseGesture(view, ev.x, ev.y)
            }

            else -> return false
        }
        return true
    }

    /** [x]/[y] sind die Koordinaten des Loslassens, relativ zur Mikro-Taste. */
    private fun releaseGesture(view: View, x: Float, y: Float) {
        if (session.withoutFinger) {
            // Das Aufsetzen hat schon fortgesetzt; dieses Loslassen gehoert noch dazu.
            if (resumedByTouch) {
                resumedByTouch = false
                return
            }
            // Nur pausieren, wenn wirklich auf der Taste losgelassen wurde. Die Mikro-Taste haelt
            // den Touch-Strom, bekommt das Loslassen also auch weit ausserhalb ihrer Grenzen —
            // wer den Finger zur Verwerfen-Taste zieht, meint nicht die Pause.
            // View.onTouchEvent pruefte das selbst; dieser Listener ersetzt es.
            if (x >= 0f && y >= 0f && x < view.width && y < view.height) pauseDictation()
            return
        }
        if (state != BubbleState.RECORDING) {
            gestureTargets?.hide()
            return
        }
        when (DictationGesture.release(gesturePhase)) {
            DictationGesture.Release.SEND -> stopDictation()
            DictationGesture.Release.LOCK -> lockDictation()
            DictationGesture.Release.DISCARD -> discardDictation()
        }
    }

    /**
     * Der Bedienungshilfen-Pfad (siehe [onCreateInputView]): ein Klick schaltet um, statt zu
     * halten. Ein so gestartetes Diktat geht gleich in den festgestellten Zustand — ohne
     * liegenden Finger gaebe es sonst nichts, was die Aufnahme beendet. Danach: Tipp = Pause,
     * Tipp = Weiter; gesendet und verworfen wird ueber die Aktionen der Taste ([SessionKeys]).
     */
    private fun onMicClick() {
        when {
            session.isLocked -> pauseDictation()
            session.isPaused -> resumeDictation()
            state == BubbleState.RECORDING -> stopDictation()
            state == BubbleState.SENDING -> skipRefine()
            state == BubbleState.ERROR -> retry()
            else -> {
                startDictation()
                if (recorder.isRecording) lockDictation()
            }
        }
    }

    /** Ziele einblenden und das getroffene hervorheben, waehrend der Finger zieht. */
    private fun updateGesture(dx: Float, dy: Float) {
        val next = DictationGesture.phase(dx, dy, armPx, hysteresisPx, verticalPx, gesturePhase)
        val wasShown = gestureTargets?.isShown == true
        gestureTargets?.showDragging(next)
        if (next == gesturePhase && wasShown) return
        gesturePhase = next
        if (next != DictationGesture.Phase.RECORDING) haptic(BubbleMotion.Haptic.CONFIRM)
        showStatus(
            when (next) {
                DictationGesture.Phase.LOCK_ARMED -> Status.LOCK_ARMED
                DictationGesture.Phase.CANCEL_ARMED -> Status.CANCEL_ARMED
                DictationGesture.Phase.RECORDING -> Status.LISTENING
            },
        )
    }

    /** Aufnahme laeuft ohne Finger weiter; aus den Anzeigen werden Verwerfen und Senden. */
    private fun lockDictation() {
        if (!recorder.isRecording || !session.lock()) return
        gesturePhase = DictationGesture.Phase.RECORDING
        haptic(BubbleMotion.Haptic.CONFIRM)
        applyState(state) // Pause-Symbol, Aktionen, Globus
        enterLockedUi()
    }

    /** Festgestellt → Pause: Mikrofon frei, das Aufgenommene bleibt (Plan §1.3). */
    private fun pauseDictation() {
        if (session.pause()) enterPause(BubbleMotion.Haptic.CONFIRM)
    }

    /**
     * Hoechstlaenge erreicht (nur Online-Erkennung): pausieren und Weiter sperren. Darueber lehnt
     * der Anbieter die Datei ab, und dieser Fehler ist nicht wiederholbar — das Audio waere weg.
     */
    private fun capDictation() {
        if (session.cap()) enterPause(BubbleMotion.Haptic.REJECT)
    }

    private fun enterPause(feedback: BubbleMotion.Haptic) {
        // Wartet kurz auf den Aufnahme-Thread (ein Lesevorgang) — im Stil von discardDictation.
        recorder.pause()
        session.update(recorder.recordedMs, lengthLimited())
        haptic(feedback)
        applyState(state)
        enterLockedUi()
    }

    /** Aus der Pause weiter; false = gesperrt (Hoechstlaenge) oder das Mikrofon kam nicht. */
    private fun resumeDictation(): Boolean {
        if (!session.isPaused) return false
        if (session.capped || !recorder.resume()) {
            haptic(BubbleMotion.Haptic.REJECT)
            showSessionStatus()
            return false
        }
        session.resume()
        haptic(BubbleMotion.Haptic.CONFIRM)
        applyState(state)
        enterLockedUi()
        return true
    }

    /**
     * Anzeige des festgestellten bzw. pausierten Zustands aufbauen — beim Feststellen, bei Pause
     * und Weiter und beim Neuaufbau der Tastatur.
     *
     * Waehrend der Aufnahme verliert die Statuszeile ihre Live-Region: der Ticker schreibt sie im
     * Sekundentakt neu, und TalkBack liest jede Aenderung einer Live-Region vor. Das waere eine
     * Ansage pro Sekunde, die dem Nutzer ins eigene Diktat redet und seine Warteschlange nie leer
     * laufen laesst. Der erste Lauf des Tickers passiert noch davor, das Feststellen selbst wird
     * also angesagt; danach ist Ruhe. In der Pause schreibt kein Ticker — dann darf die Zeile
     * wieder ansagen ("Pausiert bei 0:42"). [endSession] stellt die Live-Region zurueck.
     */
    private fun enterLockedUi() {
        gestureTargets?.showLocked()
        main.removeCallbacks(lockedTicker)
        if (session.isPaused) {
            statusView?.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            showSessionStatus()
        } else {
            lockedTicker.run()
            statusView?.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
        }
    }

    /** Aufnahme wegwerfen — auch aus der Pause: nichts wird transkribiert, nichts eingefuegt. */
    private fun discardDictation() {
        if (!recorder.hasSession) return
        endSession()
        pendingSamples = null
        applyState(BubbleState.IDLE)
        showStatus(Status.DISCARDED)
        haptic(BubbleMotion.Haptic.REJECT)
        // Bewusst zuletzt und bewusst synchron: cancel() wartet auf den Aufnahme-Thread
        // (join bis 1 s), und die Haptik soll nicht darauf warten. Auf den io-Thread gehoert
        // es trotzdem nicht — cancel() setzt `recording` als erstes zurueck, ein
        // zwischenzeitliches start() wuerde dann auf einen noch laufenden Thread treffen.
        recorder.cancel()
        // Die Meldung ist eine Quittung, kein Zustand — danach wieder der Ruhe-Hinweis.
        main.postDelayed({ if (state == BubbleState.IDLE && !session.isOpen) showIdleStatus() }, DISCARD_HINT_MS)
    }

    /** Diktat zu Ende (gesendet oder verworfen): Ticker aus, Ziele weg, Live-Region zurueck. */
    private fun endSession() {
        session.end()
        resumedByTouch = false
        gesturePhase = DictationGesture.Phase.RECORDING
        main.removeCallbacks(lockedTicker)
        gestureTargets?.hide()
        statusView?.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    /** Nur die Online-Erkennung hat eine Obergrenze; whisper.cpp rechnet beliebig lange. */
    private fun lengthLimited() = prefs.engine == Engine.ONLINE

    // --- Schnellzugriff Textverbesserung ------------------------------------

    private fun toggleRefineBar() {
        val bar = refineBar ?: return
        if (bar.isShown) {
            closeRefineBar()
            return
        }
        val blocked = refineBlocked()
        bar.show(prefs.refineMode, blocked == null, prefs.promptLevelEnabled)
        refineKey?.isSelected = true
        // Ohne Zugang bzw. Textmodell sind die KI-Stufen abgeblendet — das braucht eine Erklaerung,
        // sonst sieht es nach einem Fehler aus.
        if (blocked != null && state == BubbleState.IDLE) showStatus(blocked)
    }

    private fun closeRefineBar() {
        val bar = refineBar ?: return
        if (!bar.isShown) return
        bar.hide()
        refineKey?.isSelected = false
        if (state == BubbleState.IDLE) showIdleStatus()
    }

    private fun pickRefineMode(mode: RefineMode) {
        prefs.refineMode = mode
        refineBar?.select(mode)
        haptic(BubbleMotion.Haptic.CONTEXT_CLICK)
        // Die naechste Transkription liest die Prefs frisch — die Wahl wirkt sofort.
        if (state == BubbleState.IDLE) {
            showStatus(Status.HINT, getString(R.string.kb_refine_set, refineLabel(mode)))
            main.postDelayed({ if (state == BubbleState.IDLE) showIdleStatus() }, DISCARD_HINT_MS)
        }
    }

    private fun refineLabel(mode: RefineMode) = getString(
        when (mode) {
            RefineMode.OFF -> R.string.level_off
            RefineMode.POLISH, RefineMode.READABLE -> R.string.level_smooth
            RefineMode.BEAUTIFY -> R.string.level_beautify
            RefineMode.SUMMARIZE -> R.string.level_summarize
            RefineMode.PROMPT -> R.string.level_prompt
        },
    )

    /**
     * Warum eine KI-Stufe gerade nichts ausrichten kann ([RefineDecision.stagesReady]); null = sie
     * kann. Sonst kaeme nur der Rohtext zurueck (plus Hinweis) — ein Fehlgriff in der Leiste
     * zerstoert also nichts, aber ins Leere fuehren soll sie trotzdem nicht. Offline mit einer
     * Regel, die lokal rechnen will: das Textmodell fehlt; sonst fehlt ein (eigener) Zugang.
     */
    private fun refineBlocked(): Status? {
        val offline = prefs.engine == Engine.OFFLINE
        val localReady = offline && LocalTextEngine.isReady(this, prefs.localLlmModel)
        val onlineReady = SetupState.llmReady(prefs.llmAccess())
        // Passt kein Textmodell ins Geraet, gilt "Ueberspringen": dann fehlt der Zugang, nicht das Modell.
        val rule = prefs.offlineRefine.effective(OfflineSupport.textModelFits(this))
        return when {
            RefineDecision.stagesReady(prefs.engine, rule, onlineReady, localReady) -> null
            offline && rule != OfflineRefineRule.SKIP -> Status.NEEDS_LOCAL
            else -> Status.NEEDS_LLM
        }
    }

    // --- Diktat -------------------------------------------------------------

    private fun startDictation() {
        if (state == BubbleState.SENDING) {
            // Vorheriges Diktat wird noch uebertragen. In der Textverbesserung ist der Tipp der Ausweg.
            skipRefine()
            return
        }
        if (!hasMicPermission()) {
            showStatus(Status.NEED_PERMISSION)
            startActivity(AppNav.setup(this, SETUP_STEP_MIC))
            return
        }
        if (!TranscriptionEngine.isConfigured(this)) {
            showStatus(Status.NOT_CONFIGURED)
            startActivity(AppNav.setup(this))
            return
        }
        // Auch nicht ueber einem pausierten Diktat: start() leert den Puffer.
        if (recorder.hasSession) return
        if (recorder.start()) {
            session.hold()
            pendingSamples = null
            // Sonst bliebe die Leiste offen, waehrend ihr Ausloeser verschwindet.
            closeRefineBar()
            applyState(BubbleState.RECORDING)
            showStatus(Status.LISTENING)
            haptic(BubbleMotion.Haptic.CONFIRM)
            TranscriptionEngine.warmUp(this) // lokales Textmodell parallel zur Aufnahme laden
        } else {
            showStatus(Status.ERROR)
        }
    }

    /** Senden — auch aus der Pause. */
    private fun stopDictation() {
        if (!recorder.hasSession) return
        endSession()
        // Sofortiges UI-Feedback auf dem Main-Thread ...
        applyState(BubbleState.SENDING)
        showStatus(Status.TRANSCRIBING)
        haptic(BubbleMotion.Haptic.CONTEXT_CLICK)
        runIo {
            // ... aber stop() (join + PCM->Float) und die Anfrage bewusst auf dem
            // io-Thread, NIE auf dem UI-Thread (sonst Freeze/ANR beim Loslassen).
            val samples = recorder.stop()
            // Sehr kurze Aufnahmen (< 0,3 s) verwerfen — meist versehentliche Taps.
            if (samples.size < AudioRecorder.SAMPLE_RATE * 3 / 10) {
                main.post {
                    applyState(BubbleState.IDLE)
                    showIdleStatus()
                }
                return@runIo
            }
            send(samples)
        }
    }

    /**
     * Hintergrundarbeit, die die Taste garantiert wieder aus SENDING holt: ein Throwable, das
     * kein Exception ist (OutOfMemoryError bei sehr langen Diktaten), versickert sonst im Future
     * von submit(), und die Tastatur diktiert bis zum Prozessende nicht mehr.
     */
    private fun runIo(block: () -> Unit) {
        io.execute {
            try {
                block()
            } catch (e: Throwable) {
                Log.e(TAG, "Diktat abgebrochen", e)
                pendingSamples = null
                main.post {
                    applyState(BubbleState.IDLE)
                    showStatus(Status.ERROR)
                }
            }
        }
    }

    private fun retry() {
        val samples = pendingSamples ?: return
        if (state == BubbleState.SENDING) return
        applyState(BubbleState.SENDING)
        showStatus(Status.TRANSCRIBING)
        haptic(BubbleMotion.Haptic.CONTEXT_CLICK)
        runIo { send(samples) }
    }

    /** Tipp waehrend der Textverbesserung: den erkannten Text sofort ohne KI einfuegen. */
    private fun skipRefine() {
        if (!refining) return
        refineSkip?.skip()
        haptic(BubbleMotion.Haptic.CONFIRM)
    }

    /** Main-Thread: die Uebertragung ist in der Textverbesserung angekommen — Statuszeile nennt den Ausweg. */
    private fun showRefining(skip: RefineSkip) {
        if (refineSkip !== skip || state != BubbleState.SENDING) return
        refining = true
        showStatus(Status.REFINING)
        updateMicDescription()
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
            pendingSamples = null
            main.post {
                commitDictation(dictation.text)
                applyState(BubbleState.IDLE)
                rings?.flashSuccess()
                showIdleStatus()
                // Text ist eingefuegt, nur die Veredelung fiel aus — Hinweis statt Fehlerzustand.
                dictation.result.skipped?.let { showStatus(Status.ERROR, getString(R.string.refine_skipped, it)) }
                    ?: dictation.result.note?.let { showStatus(Status.HINT, it) }
            }
        } catch (e: ApiNotConfiguredException) {
            pendingSamples = null
            main.post {
                applyState(BubbleState.IDLE)
                showStatus(Status.NOT_CONFIGURED)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transkription fehlgeschlagen", e)
            val retryable = e.isRetryable()
            pendingSamples = if (retryable) samples else null
            main.post {
                applyState(if (retryable) BubbleState.ERROR else BubbleState.IDLE)
                showStatus(Status.ERROR, e.message ?: getString(R.string.kb_error))
                if (!reduceMotion()) micZone?.let { BubbleAnimators.shake(it).start() }
                haptic(BubbleMotion.Haptic.REJECT)
            }
        } finally {
            refineSkip = null
        }
    }

    // --- Anzeige ------------------------------------------------------------

    /** Mikro-Taste (Fuellung, Icon, Ringe), Wiederholen-Taste und Pegelband auf [next] setzen. */
    private fun applyState(next: BubbleState, animate: Boolean = true) {
        state = next
        if (next != BubbleState.SENDING) refining = false
        val visual = ImeMetrics.micVisual(next, paused = session.isPaused, reduceMotion = reduceMotion())
        micButton?.let {
            it.background.level = ImeMetrics.micFillLevel(visual)
            MicIcon.apply(it, visual, animate = animate && !reduceMotion())
            // Festgestellt pausiert ein Tipp. Das Symbol kennt nur die Tastatur — der Knopf teilt
            // BubbleVisual und hat keine Pause.
            if (next == BubbleState.RECORDING && session.isLocked) it.setImageResource(R.drawable.ic_pause)
        }
        rings?.show(visual.ring)
        retryKey?.visibility = if (next == BubbleState.ERROR) View.VISIBLE else View.GONE
        // In der Pause steht das Band: das Mikrofon ist frei.
        if (next == BubbleState.RECORDING && !session.isPaused) levelBand?.start() else levelBand?.stop()
        // Der Zauberstab sitzt in der Mikro-Zone, wo bei einer laufenden Aufnahme die
        // Wisch-Ziele erscheinen — waehrend des Diktierens hat er dort nichts verloren.
        refineKey?.visibility = if (next == BubbleState.RECORDING || next == BubbleState.SENDING) {
            View.GONE
        } else {
            View.VISIBLE
        }
        sessionKeys?.show(open = session.isOpen, withoutFinger = session.withoutFinger)
        updateMicDescription()
    }

    /**
     * Beschreibung der Mikro-Taste je Zustand. Mit TalkBack ist sie das einzige Bedienelement
     * der Aufnahme, deshalb muss sie sagen, was ein Antippen jetzt gerade tut.
     */
    private fun updateMicDescription() {
        val mic = micButton ?: return
        val time = Formats.duration(session.recordedMs)
        mic.contentDescription = when {
            session.capped -> getString(R.string.cd_mic_capped)
            session.isPaused -> getString(R.string.cd_mic_paused, time)
            session.isLocked -> getString(R.string.cd_mic_locked, time)
            state == BubbleState.RECORDING -> getString(R.string.cd_mic_recording, time)
            state == BubbleState.SENDING && refining -> getString(R.string.cd_mic_refining)
            state == BubbleState.SENDING -> getString(R.string.cd_mic_sending)
            state == BubbleState.ERROR -> getString(R.string.cd_mic_error)
            else -> getString(R.string.cd_mic)
        }
    }

    /** Statuszeile eines Diktats ohne liegenden Finger: laeuft, pausiert oder am Laengen-Deckel. */
    private fun showSessionStatus() {
        val time = Formats.duration(session.recordedMs)
        when {
            session.capped -> showStatus(Status.WARNING, getString(R.string.kb_capped))
            session.isPaused -> showStatus(Status.PAUSED, getString(R.string.kb_paused, time))
            session.length == DictationSession.Length.LONG -> showStatus(
                Status.WARNING,
                getString(R.string.kb_long, time, Formats.duration(DictationSession.MAX_MS)),
            )
            else -> showStatus(Status.LOCKED, getString(R.string.kb_locked, time))
        }
    }

    /**
     * Statuszeile zum aktuellen Zustand des Dienstes. Gebraucht nach dem Neuaufbau der
     * Tastatur: die neue Zeile kommt mit dem Ruhe-Hinweis aus dem Layout, waehrend Mikrofon
     * oder Uebertragung weiterlaufen.
     */
    private fun restoreStatus() = when {
        session.withoutFinger -> showSessionStatus()
        state == BubbleState.SENDING -> showStatus(if (refining) Status.REFINING else Status.TRANSCRIBING)
        state == BubbleState.RECORDING -> showStatus(Status.LISTENING)
        else -> showIdleStatus()
    }

    /** Ruhe-Statuszeile: Hinweis oder Warnung (fehlende Berechtigung / kein Zugang). */
    private fun showIdleStatus() = showStatus(
        when {
            !hasMicPermission() -> Status.NEED_PERMISSION
            !TranscriptionEngine.isConfigured(this) -> Status.NOT_CONFIGURED
            else -> Status.HINT
        },
    )

    private fun showStatus(kind: Status, text: CharSequence? = null) {
        val v = statusView ?: return
        v.text = text ?: getString(
            when (kind) {
                Status.HINT -> R.string.kb_hint_hold
                Status.LISTENING -> R.string.kb_listening
                Status.LOCK_ARMED -> R.string.kb_lock_armed
                Status.CANCEL_ARMED -> R.string.kb_cancel_armed
                Status.LOCKED -> R.string.kb_locked
                Status.PAUSED -> R.string.kb_paused
                Status.WARNING -> R.string.kb_finish_first
                Status.DISCARDED -> R.string.kb_discarded
                Status.TRANSCRIBING -> R.string.kb_transcribing
                Status.REFINING -> R.string.kb_refining
                Status.ERROR -> R.string.kb_error
                Status.NEED_PERMISSION -> R.string.kb_need_permission
                Status.NOT_CONFIGURED -> R.string.kb_not_configured
                Status.NEEDS_LLM -> R.string.kb_refine_needs_llm
                Status.NEEDS_LOCAL -> R.string.kb_refine_needs_local
            },
        )
        v.setTextColor(
            getColor(
                when (kind) {
                    Status.HINT, Status.TRANSCRIBING, Status.DISCARDED, Status.PAUSED -> R.color.loom_onSurfaceVariant
                    Status.LISTENING, Status.LOCKED -> R.color.loom_recordingText
                    Status.LOCK_ARMED, Status.REFINING -> R.color.loom_primary
                    Status.CANCEL_ARMED, Status.ERROR -> R.color.loom_error
                    Status.NEED_PERMISSION, Status.NOT_CONFIGURED, Status.NEEDS_LLM, Status.NEEDS_LOCAL,
                    Status.WARNING -> R.color.loom_warning
                },
            ),
        )
        // Warnzeilen fuehren per Tipp in den Assistenten (Mikrofon = Schritt 3).
        when (kind) {
            Status.NEED_PERMISSION -> v.setOnClickListener { startActivity(AppNav.setup(this, SETUP_STEP_MIC)) }
            Status.NOT_CONFIGURED -> v.setOnClickListener { startActivity(AppNav.setup(this)) }
            // Der KI-Zugang wird unter Einstellungen › KI-Zugang eingerichtet, nicht im Assistenten.
            Status.NEEDS_LLM -> v.setOnClickListener { startActivity(AppNav.llmAccess(this)) }
            // Das Textmodell laedt man unter Offline-Modelle.
            Status.NEEDS_LOCAL -> v.setOnClickListener { startActivity(AppNav.models(this)) }
            // Ausweg waehrend der Textverbesserung: Text sofort ohne KI einfuegen.
            Status.REFINING -> v.setOnClickListener { skipRefine() }
            else -> v.setOnClickListener(null)
        }
        v.isClickable = kind == Status.NEED_PERMISSION || kind == Status.NOT_CONFIGURED ||
            kind == Status.NEEDS_LLM || kind == Status.NEEDS_LOCAL || kind == Status.REFINING
    }

    private fun reduceMotion() = BubbleAnimators.reduceMotion(this)

    private fun haptic(kind: BubbleMotion.Haptic) {
        micButton?.performHapticFeedback(BubbleMotion.hapticConstant(kind, Build.VERSION.SDK_INT))
    }

    // --- Text einfuegen -----------------------------------------------------

    private fun commitDictation(text: String) {
        val ic = currentInputConnection ?: return
        if (text.isEmpty()) return
        var out = text
        // Fuehrendes Leerzeichen, wenn direkt an ein Wort angefuegt wird.
        val before = ic.getTextBeforeCursor(1, 0)
        if (!before.isNullOrEmpty()) {
            val prev = before[0]
            if (!prev.isWhitespace() && out.isNotEmpty() && out[0].isLetterOrDigit()) {
                out = " $out"
            }
        }
        if (prefs.trailingSpace && !out.endsWith(" ")) out += " "
        ic.commitText(out, 1)
    }

    private fun commitRaw(s: String) {
        currentInputConnection?.commitText(s, 1)
    }

    private fun backspace() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun performEnter() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnterAction =
            ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (action != EditorInfo.IME_ACTION_NONE && !noEnterAction) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    // --- Helfer -------------------------------------------------------------

    private fun hasMicPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Globus: Tastatur wechseln — nicht, solange ein Diktat offen ist. Der Wechsel beendet den
     * Dienst und wuerfe auch ein pausiertes Diktat still weg (siehe [SessionKeys]).
     */
    private fun switchKeyboard() {
        if (!session.isOpen) {
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
            return
        }
        haptic(BubbleMotion.Haptic.REJECT)
        showStatus(Status.WARNING)
        // Laufend schreibt der Ticker die Zeile ohnehin neu; in der Pause kehrt sie hier zurueck.
        main.postDelayed({ if (session.isPaused) showSessionStatus() }, DISCARD_HINT_MS)
    }

    override fun onDestroy() {
        main.removeCallbacks(lockedTicker)
        // Auch eine Pause: der Dienst geht, und mit ihm das Audio im Speicher.
        if (recorder.hasSession) recorder.cancel()
        rings?.release()
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WhisperLoomIME"

        /** Assistenten-Schritt "Mikrofon erlauben" (UX-Spec §2.2). */
        private const val SETUP_STEP_MIC = 3

        /** Tastenreihe ist 4 dp hoeher als die Tasten (52/48 bzw. 60/56). */
        private const val KEY_ROW_EXTRA_DP = 4

        /** Takt der Dauer-Anzeige und der Laengen-Pruefung im festgestellten Zustand. */
        private const val TICK_MS = 1000L

        /** Wie lange eine Quittung ("Aufnahme verworfen") stehen bleibt, bevor der Zustand zurueckkehrt. */
        private const val DISCARD_HINT_MS = 2000L
    }
}
