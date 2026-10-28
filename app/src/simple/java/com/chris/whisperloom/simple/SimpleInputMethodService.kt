package com.chris.whisperloom.simple

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.text.InputType
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.chris.whisperloom.AudioRecorder
import com.chris.whisperloom.PresetSeed
import com.chris.whisperloom.R
import com.chris.whisperloom.simple.realtime.ElevenLabsRealtime
import com.chris.whisperloom.simple.realtime.NoTokenSource
import com.chris.whisperloom.simple.realtime.RealtimeLink
import com.chris.whisperloom.simple.realtime.RealtimeOptions
import com.chris.whisperloom.simple.realtime.RealtimeSession
import com.chris.whisperloom.simple.realtime.TokenCache
import okhttp3.OkHttpClient
import java.util.concurrent.Executors

/**
 * Ein-Knopf-Tastatur (Konzept §4, §6): Tippen = Aufnehmen, Tippen = Fertig, daneben Loeschen. Jeder Knopf ist ein
 * normaler Knopf mit genau einer Klick-Aktion (TalkBack-Doppeltipp, Schalterzugang, Voice Access), nicht als
 * Tastaturtaste markiert; der Zustand steht in der stateDescription. Kein Halten, kein Wischen, keine Einstellungen.
 *
 * Ablauf: Tipp → Verbinden (Token + WebSocket) → Start-Ton → Mikrofon an, 100-ms-Stuecke zum Anbieter →
 * vorlaeufiger Text per Composing (oder nur in der Vorschauzeile), fester Text per commitText → Tipp "Fertig" →
 * `commit: true`, letzten festen Text einfuegen, Verbindung zu → "Eingefuegt" → Bereit.
 *
 * Context7: Android Developer Reference — InputMethodService (onStartInput/onFinishInput/onUpdateSelection),
 * InputConnection (setComposingText/commitText/finishComposingText), View.setStateDescription,
 * setAccessibilityPaneTitle.
 */
class SimpleInputMethodService : InputMethodService() {

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "loom-simple-io") }
    private val main = Handler(Looper.getMainLooper())
    private val recorder = AudioRecorder()

    /** Etappe 2 bringt den Token-Dienst; bis dahin endet jeder Start mit "Gerade keine Verbindung". */
    private val tokens = TokenCache(NoTokenSource)
    private val client: OkHttpClient by lazy { OkHttpClient() }

    private var profile: SimpleProfile? = null
    private var state = SimpleKeyboardState(DEFAULT_LOCK_MS)
    private var link: RealtimeLink? = null
    private var writer: ComposingWriter? = null

    /** Fehlertext fuer die Statuszeile, solange der Zustand ERROR ist. */
    private var errorText: Int? = null

    /** Audio seit dem letzten festen Text nach einem Abbruch — fuer den Batch-Rueckfall (Etappe 2). */
    private var pendingAudio: ByteArray? = null

    private var passwordField = false
    private var composingSupported = true

    /** Letzte Textaenderung bzw. Aufnahmestart — fuer "Fertig per Stille" und den Sicherheits-Stopp. */
    private var lastTextAt = 0L
    private var committedChars = 0

    private var root: View? = null
    private var recordButton: Button? = null
    private var deleteButton: Button? = null
    private var switchButton: Button? = null
    private var preview: TextView? = null
    private var status: TextView? = null

    /** Tests: Editor statt der echten InputConnection. */
    internal var editorFactory: (() -> Editor?)? = null

    /** Tests: Fake-Verbindung statt OkHttp. */
    internal var linkFactory: ((RealtimeSession.Listener) -> RealtimeLink)? = null

    internal val phase: SimpleKeyboardState.Phase get() = state.phase

    private val silenceTick = object : Runnable {
        override fun run() {
            val p = profile ?: return
            if (state.phase != SimpleKeyboardState.Phase.RECORDING) return
            val quiet = System.currentTimeMillis() - lastTextAt
            when {
                p.safetyStopMin > 0 && quiet >= p.safetyStopMin * 60_000L -> {
                    finishRecording()
                    showStatus(R.string.simple_hint_silence_stop)
                }
                p.doneBySilenceSec > 0 && committedChars + (writer?.composing?.length ?: 0) > 0 &&
                    quiet >= p.doneBySilenceSec * 1_000L -> finishRecording()
                else -> main.postDelayed(this, TICK_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        loadProfile()
    }

    /** Profil erst nach dem Entsperren (geschuetzter Speicher, directBootAware) — dann auch die Vorbelegung nachholen. */
    private fun loadProfile() {
        if (profile != null || !isUnlocked()) return
        try {
            PresetSeed.applyFromAssets(this)
        } catch (e: Exception) {
            Log.e(TAG, "Vorbelegung nicht uebernommen", e)
        }
        val p = SimpleProfile.load(this)
        profile = p
        state = SimpleKeyboardState(p.lockMs)
    }

    private fun isUnlocked(): Boolean = getSystemService(UserManager::class.java)?.isUserUnlocked ?: true

    // inflate ohne Elternteil: das System haengt die Tastatur selbst ein (siehe Haupt-App).
    @SuppressLint("InflateParams")
    override fun onCreateInputView(): View {
        val v = layoutInflater.inflate(R.layout.simple_keyboard, null)
        root = v
        ViewCompat.setAccessibilityPaneTitle(v, getString(R.string.simple_pane_title))
        preview = v.findViewById(R.id.simple_preview)
        status = v.findViewById(R.id.simple_status)
        recordButton = v.findViewById<Button>(R.id.simple_record).also { it.setOnClickListener { onRecordTap() } }
        deleteButton = v.findViewById<Button>(R.id.simple_delete).also { it.setOnClickListener { onDeleteTap() } }
        switchButton = v.findViewById<Button>(R.id.simple_switch).also { it.setOnClickListener { switchKeyboard() } }
        if (profile?.buttonHuge == true) {
            v.findViewById<View>(R.id.simple_row).layoutParams.height = (HUGE_ROW_DP * resources.displayMetrics.density).toInt()
        }
        render()
        return v
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        val type = info?.inputType ?: InputType.TYPE_NULL
        passwordField = isPassword(type)
        composingSupported = type != InputType.TYPE_NULL
        render()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        loadProfile()
        if (profile != null) io.execute { tokens.prefetch() }
        render()
    }

    /** Feld geht zu (Konzept §4.4): offenen Text festschreiben, Aufnahme beenden. */
    override fun onFinishInput() {
        if (isActive()) {
            writer?.fieldFinished()
            abortRecording()
        }
        super.onFinishInput()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        if (isActive() && !finishingInput) {
            // Nur die Tastatur geht zu, das Feld bleibt: wie ein Feldwechsel behandeln, nichts verlieren.
            writer?.fieldFinished()
            abortRecording()
        }
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        writer?.cursorMoved(newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    private fun isActive(): Boolean = when (state.phase) {
        SimpleKeyboardState.Phase.CONNECTING, SimpleKeyboardState.Phase.RECORDING -> true
        else -> false
    }

    // --- Knoepfe --------------------------------------------------------------

    private fun onRecordTap() {
        val p = profile
        if (p == null) {
            showStatus(R.string.simple_err_locked)
            return
        }
        when (state.tapRecord()) {
            SimpleKeyboardState.RecordAction.NONE -> Unit
            SimpleKeyboardState.RecordAction.START -> startRecording(p)
            SimpleKeyboardState.RecordAction.FINISH -> finishRecording()
        }
    }

    private fun onDeleteTap() {
        val p = profile ?: return
        when (state.tapDelete(p.deleteConfirm)) {
            SimpleKeyboardState.DeleteAction.NONE -> Unit
            SimpleKeyboardState.DeleteAction.ASK_CONFIRM -> main.postDelayed(::render, SimpleKeyboardState.CONFIRM_MS)
            SimpleKeyboardState.DeleteAction.CANCEL_CONNECT -> abortRecording()
            SimpleKeyboardState.DeleteAction.DISCARD_RECORDING -> {
                writer?.discard()
                abortRecording()
            }
            SimpleKeyboardState.DeleteAction.DISCARD_ERROR -> {
                pendingAudio = null
                errorText = null
            }
            SimpleKeyboardState.DeleteAction.REMOVE_LAST -> {
                editor()?.deleteBefore(state.lastInserted)
                state.lastInserted = 0
            }
        }
        render()
    }

    /** Tastatur wechseln — der Rueckweg zum Tippen (Konzept I1), nicht waehrend einer Aufnahme. */
    private fun switchKeyboard() {
        if (isActive()) return
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
    }

    // --- Aufnahme -------------------------------------------------------------

    private fun startRecording(p: SimpleProfile) {
        errorText = null
        if (passwordField) {
            fail(R.string.simple_err_password)
            return
        }
        if (!hasMicPermission()) {
            fail(R.string.simple_err_mic_permission)
            return
        }
        val editor = editorFactory?.invoke() ?: currentInputConnection?.let { InputConnectionEditor(it) }
        if (editor == null) {
            fail(R.string.simple_err_password)
            return
        }
        val inField = ComposingWriter.inField(p.preview, composingSupported, talkBackActive())
        writer = ComposingWriter(editor, inField)
        committedChars = 0
        lastTextAt = System.currentTimeMillis()
        haptic(1)
        render()
        val options = RealtimeOptions(
            vadSilenceSeconds = p.commit.vadSeconds,
            keyterms = p.keyterms,
            noVerbatim = p.noVerbatim,
        )
        val l = linkFactory?.invoke(sessionListener)
            ?: RealtimeSession(client, ElevenLabsRealtime, tokens, options, sessionListener)
        link = l
        io.execute { l.start() }
    }

    /** Fertig-Tipp: Mikrofon aus, letzten Abschnitt festschreiben lassen; "Eingefuegt" kommt mit onClosed. */
    private fun finishRecording() {
        state.finish()
        main.removeCallbacks(silenceTick)
        recorder.onChunk = null
        recorder.stop()
        val l = link
        if (l == null) {
            inserted()
        } else {
            l.finish()
        }
        render()
    }

    /** Abbruch ohne Einfuegen (Loeschen, Feld zu, Tastatur zu): Mikrofon frei, Verbindung zu, zurueck zu Bereit. */
    private fun abortRecording() {
        main.removeCallbacks(silenceTick)
        recorder.onChunk = null
        recorder.cancel()
        link?.cancel()
        link = null
        writer = null
        if (isActive()) state.ready()
        render()
    }

    private fun inserted() {
        val n = writer?.inserted ?: 0
        link = null
        if (n == 0) {
            fail(R.string.simple_err_nothing)
            return
        }
        state.inserted(n)
        haptic(1, long = true)
        tone(ToneGenerator.TONE_PROP_ACK)
        render()
        main.postDelayed({ if (state.phase == SimpleKeyboardState.Phase.INSERTED) { state.ready(); render() } }, recommendedTimeout(INSERTED_MS))
    }

    private fun fail(text: Int) {
        state.failed()
        errorText = text
        link = null
        haptic(3)
        tone(ToneGenerator.TONE_PROP_NACK)
        render()
    }

    private val sessionListener = object : RealtimeSession.Listener {
        override fun onOpen() = ui {
            val l = link ?: return@ui
            if (state.phase != SimpleKeyboardState.Phase.CONNECTING) {
                l.cancel()
                return@ui
            }
            // Start-Ton endet, bevor das Mikrofon oeffnet (Konzept §4.3) — sonst landet er in der Aufnahme.
            tone(ToneGenerator.TONE_PROP_BEEP)
            main.postDelayed({ openMic(l) }, if (profile?.tones == true) START_TONE_MS else 0L)
        }

        override fun onPartial(text: String) = ui {
            if (state.phase != SimpleKeyboardState.Phase.RECORDING) return@ui
            lastTextAt = System.currentTimeMillis()
            preview?.text = writer?.partial(text).orEmpty()
            render()
        }

        override fun onCommitted(text: String) = ui {
            lastTextAt = System.currentTimeMillis()
            writer?.committed(text)
            committedChars += text.length
            preview?.text = ""
            render()
        }

        override fun onError(code: String) = ui {
            errorText = when (code) {
                "quota_exceeded" -> R.string.simple_err_quota
                RealtimeSession.ERROR_NO_TOKEN -> R.string.simple_err_no_connection
                else -> R.string.simple_err_dropped
            }
        }

        override fun onClosed(finished: Boolean, pending: ByteArray) = ui {
            when (state.phase) {
                SimpleKeyboardState.Phase.INSERTING -> inserted()
                SimpleKeyboardState.Phase.CONNECTING, SimpleKeyboardState.Phase.RECORDING -> {
                    // Abbruch: fester Text bleibt im Feld, vorlaeufiger in der Vorschau, Rest im Puffer (§6.4).
                    main.removeCallbacks(silenceTick)
                    recorder.onChunk = null
                    recorder.cancel()
                    pendingAudio = pending.takeIf { it.isNotEmpty() }
                    fail(errorText ?: if (state.phase == SimpleKeyboardState.Phase.CONNECTING) R.string.simple_err_no_connection else R.string.simple_err_dropped)
                }
                else -> Unit
            }
        }
    }

    /** Rueckrufe der Verbindung kommen auf OkHttp-Threads — alles Weitere auf main. */
    private fun ui(block: () -> Unit) {
        main.post(block)
    }

    private fun openMic(l: RealtimeLink) {
        if (state.phase != SimpleKeyboardState.Phase.CONNECTING) return
        recorder.onChunk = { l.sendAudio(it) }
        if (!recorder.start()) {
            recorder.onChunk = null
            l.cancel()
            fail(R.string.simple_err_mic_busy)
            return
        }
        state.connected()
        lastTextAt = System.currentTimeMillis()
        haptic(2)
        main.postDelayed(silenceTick, TICK_MS)
        render()
    }

    // --- Darstellung -----------------------------------------------------------

    private fun render() {
        val b = recordButton ?: return
        val (text, color, stateText) = when (state.phase) {
            SimpleKeyboardState.Phase.READY -> Triple(R.string.simple_btn_record, R.color.simple_ready, R.string.simple_state_ready)
            SimpleKeyboardState.Phase.CONNECTING -> Triple(R.string.simple_btn_wait, R.color.simple_wait, R.string.simple_state_connecting)
            SimpleKeyboardState.Phase.RECORDING -> Triple(R.string.simple_btn_done, R.color.simple_recording, R.string.simple_state_recording)
            SimpleKeyboardState.Phase.INSERTING -> Triple(R.string.simple_btn_wait, R.color.simple_wait, R.string.simple_state_inserting)
            SimpleKeyboardState.Phase.INSERTED -> Triple(R.string.simple_btn_inserted, R.color.simple_inserted, R.string.simple_state_inserted)
            SimpleKeyboardState.Phase.ERROR -> Triple(R.string.simple_btn_record, R.color.simple_error, R.string.simple_state_error)
        }
        b.setText(text)
        b.backgroundTintList = ColorStateList.valueOf(getColor(color))
        ViewCompat.setStateDescription(b, getString(stateText))
        b.isEnabled = !passwordField
        deleteButton?.apply {
            setText(if (state.confirmPending) R.string.simple_btn_delete_confirm else R.string.simple_btn_delete)
            isEnabled = state.phase != SimpleKeyboardState.Phase.INSERTING
        }
        switchButton?.isEnabled = !isActive()
        preview?.let { it.visibility = if (it.text.isNullOrEmpty()) View.GONE else View.VISIBLE }
        status?.let { s ->
            val error = state.phase == SimpleKeyboardState.Phase.ERROR
            s.accessibilityLiveRegion = if (error) View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE else View.ACCESSIBILITY_LIVE_REGION_POLITE
            when {
                passwordField -> s.setText(R.string.simple_err_password)
                error && errorText != null -> s.setText(errorText!!)
                else -> if (!error) s.text = ""
            }
        }
    }

    private fun showStatus(text: Int) {
        status?.setText(text)
    }

    private fun editor(): Editor? = editorFactory?.invoke() ?: currentInputConnection?.let { InputConnectionEditor(it) }

    private fun hasMicPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun talkBackActive(): Boolean =
        getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true

    /** Quittungen laenger stehen lassen, wenn die Bedienungshilfen es wuenschen (Konzept §4.2). */
    private fun recommendedTimeout(ms: Int): Long {
        if (Build.VERSION.SDK_INT < 29) return ms.toLong()
        val am = getSystemService(AccessibilityManager::class.java) ?: return ms.toLong()
        return am.getRecommendedTimeoutMillis(
            ms, AccessibilityManager.FLAG_CONTENT_ICONS or AccessibilityManager.FLAG_CONTENT_TEXT,
        ).toLong()
    }

    /** Haptik-Muster (Konzept §4.3): Start 1x, gesperrt 2x, eingefuegt lang, Fehler 3x kurz. */
    private fun haptic(times: Int, long: Boolean = false) {
        if (profile?.haptics == false) return
        val b = recordButton ?: return
        val constant = when {
            long -> HapticFeedbackConstants.LONG_PRESS
            Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.CONFIRM
            else -> HapticFeedbackConstants.KEYBOARD_TAP
        }
        for (i in 0 until times) main.postDelayed({ b.performHapticFeedback(constant) }, i * HAPTIC_GAP_MS)
    }

    private fun tone(kind: Int) {
        if (profile?.tones != true) return
        try {
            val gen = ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
            gen.startTone(kind, TONE_MS)
            main.postDelayed({ gen.release() }, TONE_MS + 50L)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Kein Ton", e)
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        recorder.onChunk = null
        recorder.cancel()
        link?.cancel()
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SimpleIME"
        private const val DEFAULT_LOCK_MS = 500L
        private const val TICK_MS = 1_000L
        private const val INSERTED_MS = 2_000
        private const val START_TONE_MS = 150L
        private const val TONE_MS = 100
        private const val TONE_VOLUME = 70
        private const val HAPTIC_GAP_MS = 120L
        private const val HUGE_ROW_DP = 180

        /** Passwortfelder (Konzept §4.4): Knopf gesperrt, "Hier bitte tippen." */
        fun isPassword(inputType: Int): Boolean {
            val cls = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            return when (cls) {
                InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
                else -> false
            }
        }
    }
}
