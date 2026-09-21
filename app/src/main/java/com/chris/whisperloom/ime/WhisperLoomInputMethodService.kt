package com.chris.whisperloom.ime

import android.Manifest
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import com.chris.whisperloom.Formats
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.TranscriptionEngine
import com.chris.whisperloom.api.ApiNotConfiguredException
import com.chris.whisperloom.api.isRetryable
import com.chris.whisperloom.overlay.BubbleAnimators
import com.chris.whisperloom.overlay.BubbleMotion
import com.chris.whisperloom.overlay.BubbleState
import com.chris.whisperloom.overlay.BubbleVisuals
import com.chris.whisperloom.overlay.MicIcon
import com.chris.whisperloom.overlay.MicRings
import java.util.concurrent.Executors

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
 * Scheitert die Anfrage (kein Netz, Server-Aussetzer), bleibt das Audio gepuffert und
 * die Wiederholen-Taste erscheint — sonst waere ein langes Diktat verloren.
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

    private var statusView: TextView? = null
    private var levelBand: LevelBandView? = null
    private var micZone: View? = null
    private var micButton: ImageButton? = null
    private var rings: MicRings? = null
    private var retryKey: View? = null
    private var gestureTargets: GestureTargets? = null

    /** Aktueller Stand der Wisch-Geste; nur waehrend eines liegenden Fingers aussagekraeftig. */
    private var gesturePhase = DictationGesture.Phase.RECORDING

    /** Aufnahme laeuft ohne liegenden Finger weiter (nach rechts gewischt oder per Klick gestartet). */
    private var locked = false

    private var recordingStartedAt = 0L
    private var downX = 0f
    private var downY = 0f

    // Gesten-Schwellen in Pixeln; berechnet, sobald die View steht (braucht die Dichte).
    private var armPx = 0f
    private var hysteresisPx = 0f
    private var verticalPx = 0f

    /** Haelt die Dauer in der Statuszeile aktuell, solange die Aufnahme festgestellt ist. */
    private val lockedTicker = object : Runnable {
        override fun run() {
            if (!locked) return
            val elapsed = elapsedMs()
            showLockedStatus(elapsed)
            updateMicDescription()
            main.postDelayed(this, TICK_MS - elapsed % TICK_MS)
        }
    }

    /** Statuszeile: Farbe und Tipp-Ziel je Art (UX-Spec §5.3). */
    private enum class Status {
        HINT, LISTENING, LOCK_ARMED, CANCEL_ARMED, LOCKED, DISCARDED,
        TRANSCRIBING, ERROR, NEED_PERMISSION, NOT_CONFIGURED,
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

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

        micButton?.setOnTouchListener { _, ev -> onMicTouch(ev) }
        // Zusaetzlich zum Touch-Listener, nicht statt seiner: onTouch liefert true, also ruft
        // das System performClick() nicht von selbst — dieser Listener feuert praktisch nur
        // ueber den Bedienungshilfen-Pfad (TalkBack loest ACTION_CLICK aus). Genau dort war die
        // Taste bisher unbedienbar, weil Gedrueckthalten mit TalkBack nicht ankommt.
        micButton?.setOnClickListener { onMicClick() }
        discardTarget.setOnClickListener { discardDictation() }
        // Im festgestellten Zustand traegt das rechte Ziel das Senden-Symbol (siehe GestureTargets).
        lockTarget.setOnClickListener { stopDictation() }

        root.findViewById<View>(R.id.key_globe).setOnClickListener { showImePicker() }
        root.findViewById<View>(R.id.key_comma).setOnClickListener { commitRaw(", ") }
        root.findViewById<View>(R.id.key_period).setOnClickListener { commitRaw(". ") }
        root.findViewById<View>(R.id.key_space).setOnClickListener { commitRaw(" ") }
        root.findViewById<View>(R.id.key_backspace).setOnClickListener { backspace() }
        root.findViewById<View>(R.id.key_enter).setOnClickListener { performEnter() }
        root.findViewById<View>(R.id.key_settings).setOnClickListener { startActivity(AppNav.settings(this)) }
        retryKey?.setOnClickListener { retry() }

        // Nach dem Setzen der Listener, nicht davor: setOnClickListener macht eine View
        // wieder bedienbar. Im Ruhezustand sollen die Ziele weder anklickbar noch im
        // Bedienungshilfen-Baum sein.
        gestureTargets?.hide()
        applyState(BubbleState.IDLE, animate = false)
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
        armPx = maxOf(DictationGesture.ARM_DISTANCE_DP * density, slop)
        hysteresisPx = DictationGesture.RELEASE_HYSTERESIS_DP * density
        verticalPx = maxOf(DictationGesture.VERTICAL_TOLERANCE_DP * density, slop)
    }

    /** Ab fontScale 1,3 werden die Tasten 56 statt 48 dp hoch (UX-Spec §5.3). */
    private fun applyKeyHeight(root: View) {
        val dp = ImeMetrics.keyHeightDp(resources.configuration.fontScale)
        if (dp == ImeMetrics.KEY_HEIGHT_DP) return
        val density = resources.displayMetrics.density
        val row = root.findViewById<ViewGroup>(R.id.key_row)
        row.layoutParams = row.layoutParams.apply { height = ((dp + KEY_ROW_EXTRA_DP) * density).toInt() }
        for (i in 0 until row.childCount) {
            val key = row.getChildAt(i)
            key.layoutParams = key.layoutParams.apply { height = (dp * density).toInt() }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Ein alter Fehlerzustand gilt fuer das neue Feld nicht mehr; eine laufende
        // Uebertragung bleibt sichtbar.
        if (state == BubbleState.ERROR) {
            pendingSamples = null
            applyState(BubbleState.IDLE)
        }
        if (state != BubbleState.SENDING) showIdleStatus()
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
     */
    override fun onFinishInputView(finishingInput: Boolean) {
        if (locked && recorder.isRecording) {
            if (finishingInput) discardDictation() else stopDictation()
        } else if (recorder.isRecording) {
            recorder.cancel()
        }
        endLockedMode()
        if (state == BubbleState.RECORDING) applyState(BubbleState.IDLE)
        levelBand?.stop()
        super.onFinishInputView(finishingInput)
    }

    // --- Geste --------------------------------------------------------------

    /**
     * Push-to-talk plus Wisch-Geste. Gemessen wird relativ zum Druckpunkt, damit die Auswertung
     * nicht davon abhaengt, wo auf der 88-dp-Taste der Finger aufsetzt.
     */
    private fun onMicTouch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Im festgestellten Zustand entscheidet erst das Loslassen (= Tipp zum Senden).
                if (locked) return true
                downX = ev.x
                downY = ev.y
                gesturePhase = DictationGesture.Phase.RECORDING
                startDictation()
            }

            MotionEvent.ACTION_MOVE -> {
                if (locked || state != BubbleState.RECORDING) return true
                updateGesture(ev.x - downX, ev.y - downY)
            }

            // ACTION_CANCEL wie ACTION_UP: ein abgefangener Touch soll kein Diktat fressen.
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (locked) {
                    stopDictation()
                    return true
                }
                if (state != BubbleState.RECORDING) {
                    gestureTargets?.hide()
                    return true
                }
                when (DictationGesture.release(gesturePhase)) {
                    DictationGesture.Release.SEND -> stopDictation()
                    DictationGesture.Release.LOCK -> lockDictation()
                    DictationGesture.Release.DISCARD -> discardDictation()
                }
            }

            else -> return false
        }
        return true
    }

    /**
     * Der Bedienungshilfen-Pfad (siehe [onCreateInputView]): ein Klick schaltet um, statt zu
     * halten. Ein so gestartetes Diktat geht gleich in den festgestellten Zustand — ohne
     * liegenden Finger gaebe es sonst nichts, was die Aufnahme beendet.
     */
    private fun onMicClick() {
        when {
            locked || state == BubbleState.RECORDING -> stopDictation()
            state == BubbleState.SENDING -> Unit
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
        if (!recorder.isRecording) return
        locked = true
        gesturePhase = DictationGesture.Phase.RECORDING
        gestureTargets?.showLocked()
        haptic(BubbleMotion.Haptic.CONFIRM)
        main.removeCallbacks(lockedTicker)
        lockedTicker.run()
    }

    /** Aufnahme wegwerfen: nichts wird transkribiert, nichts eingefuegt. */
    private fun discardDictation() {
        if (!recorder.isRecording) return
        recorder.cancel()
        endLockedMode()
        pendingSamples = null
        applyState(BubbleState.IDLE)
        showStatus(Status.DISCARDED)
        haptic(BubbleMotion.Haptic.REJECT)
        // Die Meldung ist eine Quittung, kein Zustand — danach wieder der Ruhe-Hinweis.
        main.postDelayed({ if (state == BubbleState.IDLE && !locked) showIdleStatus() }, DISCARD_HINT_MS)
    }

    /** Zurueck aus dem festgestellten Zustand: Ticker aus, Ziele weg. */
    private fun endLockedMode() {
        locked = false
        gesturePhase = DictationGesture.Phase.RECORDING
        main.removeCallbacks(lockedTicker)
        gestureTargets?.hide()
    }

    private fun elapsedMs() = SystemClock.elapsedRealtime() - recordingStartedAt

    // --- Diktat -------------------------------------------------------------

    private fun startDictation() {
        if (state == BubbleState.SENDING) return // vorheriges Diktat wird noch uebertragen
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
        if (recorder.isRecording) return
        if (recorder.start()) {
            pendingSamples = null
            recordingStartedAt = SystemClock.elapsedRealtime()
            applyState(BubbleState.RECORDING)
            showStatus(Status.LISTENING)
            haptic(BubbleMotion.Haptic.CONFIRM)
        } else {
            showStatus(Status.ERROR)
        }
    }

    private fun stopDictation() {
        if (!recorder.isRecording) return
        endLockedMode()
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

    /** Laeuft auf dem io-Thread. */
    private fun send(samples: FloatArray) {
        try {
            var refineSkipped: String? = null
            val text = TranscriptionEngine.transcribe(applicationContext, samples) { refineSkipped = it }
            pendingSamples = null
            main.post {
                commitDictation(text)
                applyState(BubbleState.IDLE)
                rings?.flashSuccess()
                showIdleStatus()
                // Text ist eingefuegt, nur die Veredelung fiel aus — Hinweis statt Fehlerzustand.
                refineSkipped?.let { showStatus(Status.ERROR, getString(R.string.refine_skipped, it)) }
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
        }
    }

    // --- Anzeige ------------------------------------------------------------

    /** Mikro-Taste (Fuellung, Icon, Ringe), Wiederholen-Taste und Pegelband auf [next] setzen. */
    private fun applyState(next: BubbleState, animate: Boolean = true) {
        state = next
        val visual = BubbleVisuals.visualFor(next, reduceMotion = reduceMotion())
        micButton?.let {
            it.background.level = ImeMetrics.micFillLevel(visual)
            MicIcon.apply(it, visual, animate = animate && !reduceMotion())
        }
        rings?.show(visual.ring)
        retryKey?.visibility = if (next == BubbleState.ERROR) View.VISIBLE else View.GONE
        if (next == BubbleState.RECORDING) levelBand?.start() else levelBand?.stop()
        updateMicDescription()
    }

    /**
     * Beschreibung der Mikro-Taste je Zustand. Mit TalkBack ist sie das einzige Bedienelement
     * der Aufnahme, deshalb muss sie sagen, was ein Antippen jetzt gerade tut.
     */
    private fun updateMicDescription() {
        val mic = micButton ?: return
        mic.contentDescription = when {
            locked -> getString(R.string.cd_mic_locked, Formats.duration(elapsedMs()))
            state == BubbleState.RECORDING -> getString(R.string.cd_mic_recording, Formats.duration(elapsedMs()))
            state == BubbleState.SENDING -> getString(R.string.cd_mic_sending)
            state == BubbleState.ERROR -> getString(R.string.cd_mic_error)
            else -> getString(R.string.cd_mic)
        }
    }

    private fun showLockedStatus(elapsedMs: Long) =
        showStatus(Status.LOCKED, getString(R.string.kb_locked, Formats.duration(elapsedMs)))

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
                Status.DISCARDED -> R.string.kb_discarded
                Status.TRANSCRIBING -> R.string.kb_transcribing
                Status.ERROR -> R.string.kb_error
                Status.NEED_PERMISSION -> R.string.kb_need_permission
                Status.NOT_CONFIGURED -> R.string.kb_not_configured
            },
        )
        v.setTextColor(
            getColor(
                when (kind) {
                    Status.HINT, Status.TRANSCRIBING, Status.DISCARDED -> R.color.loom_onSurfaceVariant
                    Status.LISTENING, Status.LOCKED -> R.color.loom_recordingText
                    Status.LOCK_ARMED -> R.color.loom_primary
                    Status.CANCEL_ARMED, Status.ERROR -> R.color.loom_error
                    Status.NEED_PERMISSION, Status.NOT_CONFIGURED -> R.color.loom_warning
                },
            ),
        )
        // Warnzeilen fuehren per Tipp in den Assistenten (Mikrofon = Schritt 3).
        when (kind) {
            Status.NEED_PERMISSION -> v.setOnClickListener { startActivity(AppNav.setup(this, SETUP_STEP_MIC)) }
            Status.NOT_CONFIGURED -> v.setOnClickListener { startActivity(AppNav.setup(this)) }
            else -> v.setOnClickListener(null)
        }
        v.isClickable = kind == Status.NEED_PERMISSION || kind == Status.NOT_CONFIGURED
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

    private fun showImePicker() {
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
    }

    override fun onDestroy() {
        main.removeCallbacks(lockedTicker)
        if (recorder.isRecording) recorder.cancel()
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

        /** Takt der Dauer-Anzeige im festgestellten Zustand. */
        private const val TICK_MS = 1000L

        /** Wie lange "Aufnahme verworfen" stehen bleibt, bevor der Ruhe-Hinweis zurueckkehrt. */
        private const val DISCARD_HINT_MS = 2000L
    }
}
