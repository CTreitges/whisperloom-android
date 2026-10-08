package com.chris.whisperloom.ime

import android.app.Application
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import com.chris.whisperloom.history.History
import com.chris.whisperloom.history.HistorySource
import com.chris.whisperloom.history.HistoryVersion
import com.chris.whisperloom.history.Processing
import com.chris.whisperloom.llm.OfflineRefineFixture
import com.chris.whisperloom.whisper.TextModelCatalog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Die Tastatur schreibt jedes Diktat VOR dem Einfuegen in den Verlauf (Plan §6.5) — nie aus
 * Passwort- und Inkognito-Feldern. Am echten Dienst mit Offline-Diktat und lokalem Fake-Textmodell
 * ([OfflineRefineFixture]: "also ähm hallo welt" → "Lokal verbessert.").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImeHistoryTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val fixture = OfflineRefineFixture(app)

    private lateinit var service: WhisperLoomInputMethodService
    private lateinit var root: View
    private lateinit var mic: ImageButton
    private val committed = mutableListOf<CharSequence>()

    private val statusText: String get() = root.findViewById<TextView>(R.id.status).text.toString()

    @Before fun aufbau() {
        fixture.setUp()
        History.clear(app)
        service = Robolectric.buildService(WhisperLoomInputMethodService::class.java).create().get()
        root = service.onCreateInputView()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(660, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        mic = root.findViewById(R.id.mic)
        feld(InputType.TYPE_CLASS_TEXT)
    }

    @After fun abbau() {
        fixture.tearDown()
        service.onFinishInputView(true)
        service.onDestroy()
        History.clear(app)
    }

    /** Ein Eingabefeld, das mitschreibt (wie ImeRefineTest), mit diesem Eingabetyp. */
    private fun feld(inputType: Int, imeOptions: Int = 0) {
        val field = object : BaseInputConnection(root, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                committed += text
                return true
            }

            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = ""
        }
        verbinden(field)
        editorInfo(EditorInfo().also {
            it.inputType = inputType
            it.imeOptions = imeOptions
        })
    }

    private fun verbinden(field: BaseInputConnection?) {
        InputMethodService::class.java.getDeclaredField("mStartedInputConnection").apply {
            isAccessible = true
            set(service, field)
        }
    }

    /** Das Feld, das [InputMethodService.getCurrentInputEditorInfo] meldet. */
    private fun editorInfo(info: EditorInfo) {
        InputMethodService::class.java.getDeclaredField("mInputEditorInfo").apply {
            isAccessible = true
            set(service, info)
        }
    }

    private fun touch(action: Int, dx: Float = 0f) {
        val ev = MotionEvent.obtain(0L, 0L, action, mic.width / 2f + dx, mic.height / 2f, 0)
        mic.dispatchTouchEvent(ev)
        ev.recycle()
    }

    /** Halten, sprechen, loslassen — und warten, bis der Text eingefuegt ist. */
    private fun diktieren() {
        touch(MotionEvent.ACTION_DOWN)
        fixture.awaitRecorded()
        touch(MotionEvent.ACTION_UP)
        fixture.waitFor("Text nicht eingefuegt") { committed.isNotEmpty() }
    }

    @Test fun diktatInsNormaleFeldLandetImVerlauf() {
        diktieren()

        assertEquals(listOf<CharSequence>("Lokal verbessert. "), committed)
        val entry = History.list(app).single()
        assertEquals(HistorySource.KEYBOARD, entry.source)
        assertEquals("also ähm hallo welt", entry.raw)
        assertEquals("de", entry.language)
        assertTrue("Dauer der Aufnahme", entry.durationMs > 0)
        assertEquals(Processing.POLISH_PLAIN, entry.processing)
        val version = entry.versions.getValue(Processing.POLISH_PLAIN)
        assertEquals(HistoryVersion("Lokal verbessert.", version.createdAt, TextModelCatalog.GEMMA4_E2B.label), version)
    }

    @Test fun diktatInsPasswortfeldNicht() {
        feld(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        diktieren()
        assertEquals("eingefuegt wird trotzdem", listOf<CharSequence>("Lokal verbessert. "), committed)
        assertEquals(0, History.count(app))
    }

    @Test fun inkognitoFeldNicht() {
        feld(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        diktieren()
        assertEquals(0, History.count(app))
    }

    @Test fun verlaufAusSchreibtNichts() {
        History.setEnabled(app, false)
        diktieren()
        assertEquals(0, History.count(app))
    }

    /** Waehrend der Erkennung wechselt das Feld: es zaehlt das Feld, in dem diktiert wurde. */
    @Test fun feldwechselWaehrendDerErkennungZaehltDasStartfeld() {
        feld(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        touch(MotionEvent.ACTION_DOWN)
        val model = fixture.holdWarmedModel()
        fixture.awaitRecorded()
        touch(MotionEvent.ACTION_UP)
        assertTrue("Rechnung laeuft", model.started.await(5, TimeUnit.SECONDS))

        feld(InputType.TYPE_CLASS_TEXT)
        model.proceed()
        fixture.waitFor("Text nicht eingefuegt") { committed.isNotEmpty() }

        assertEquals(0, History.count(app))
    }

    /** Pause, dann in einem Passwortfeld gesendet: der Text landet dort — also nicht im Verlauf. */
    @Test fun ausDerPauseInsPasswortfeldGesendetNicht() {
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_MOVE, 400f)
        touch(MotionEvent.ACTION_UP, 400f) // festgestellt
        fixture.awaitRecorded()
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_UP) // Pause
        assertTrue(statusText, statusText.startsWith("Pausiert"))

        feld(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        root.findViewById<View>(R.id.gesture_lock).performClick() // Senden
        fixture.waitFor("Text nicht eingefuegt") { committed.isNotEmpty() }

        assertEquals(0, History.count(app))
    }

    /**
     * N2: Ist das Feld beim Einfuegen weg (App gewechselt, Tastatur zu), verwarf die Tastatur den
     * fertigen, womoeglich bezahlten Text still. Jetzt liegt er im Verlauf, und die Statuszeile
     * sagt es — auch beim naechsten Oeffnen, bis zum naechsten Diktat.
     */
    @Test fun textBleibtImVerlaufWennDasFeldWegIst() {
        verbinden(null)
        touch(MotionEvent.ACTION_DOWN)
        fixture.awaitRecorded()
        touch(MotionEvent.ACTION_UP)
        val hinweis = app.getString(R.string.kb_only_in_history)
        fixture.waitFor("kein Hinweis auf den Verlauf") { statusText == hinweis }

        assertEquals("Lokal verbessert.", History.list(app).single().versions.getValue(Processing.POLISH_PLAIN).text)
        service.onStartInputView(EditorInfo(), false)
        assertEquals("auch im naechsten Feld", hinweis, statusText)

        // Das naechste Diktat (hier zu kurz, das Mikrofon ist leer) loest den Hinweis ab.
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_UP)
        fixture.waitFor("Hinweis bleibt stehen") { statusText == app.getString(R.string.kb_hint_hold) }
    }
}
