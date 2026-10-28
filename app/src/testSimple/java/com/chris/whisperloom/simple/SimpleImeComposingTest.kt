package com.chris.whisperloom.simple

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.simple.realtime.RealtimeLink
import com.chris.whisperloom.simple.realtime.RealtimeSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAudioRecord
import java.time.Duration

/**
 * Die Tastatur am echten Dienst mit Fake-Editor und Fake-Verbindung: Composing ueber den Lebenszyklus
 * (onUpdateSelection, onFinishInput), TYPE_NULL, Passwortfeld, TalkBack-Regel, Fehler ohne Verbindung.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SimpleImeComposingTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    private class FakeEditor : Editor {
        val composing = mutableListOf<String>()
        val commits = mutableListOf<String>()
        var finished = 0
        var deleted = 0
        override fun setComposing(text: String): Boolean { composing += text; return true }
        override fun commit(text: String): Boolean { commits += text; return true }
        override fun finishComposing() { finished++ }
        override fun deleteBefore(chars: Int) { deleted += chars }
        override fun textBeforeCursor(chars: Int): CharSequence = ""
    }

    private class FakeLink(val listener: RealtimeSession.Listener) : RealtimeLink {
        var started = false
        var finished = false
        var cancelled = false
        val audio = mutableListOf<ByteArray>()
        override fun start(): Boolean { started = true; return true }
        override fun sendAudio(pcm: ByteArray) { synchronized(audio) { audio += pcm } }
        override fun finish() { finished = true }
        override fun cancel() { cancelled = true }
    }

    private val editor = FakeEditor()
    private var link: FakeLink? = null
    private lateinit var service: SimpleInputMethodService
    private lateinit var view: View

    @Before fun aufbau() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        // Ohne Doppeltreffer-Sperre, ohne Toene: die Tests tippen schneller als ein Mensch.
        app.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit()
            .putInt(SimpleProfile.KEY_LOCK_MS, 0).putBoolean(SimpleProfile.KEY_TONES, false).commit()
        // Das Schatten-Mikrofon liefert nichts (0 Samples) — hier geht es um Text, nicht um Audio.
        ShadowAudioRecord.setSourceProvider { object : ShadowAudioRecord.AudioRecordSource {} }
        service = Robolectric.buildService(SimpleInputMethodService::class.java).create().get()
        service.editorFactory = { editor }
        service.linkFactory = { l -> FakeLink(l).also { link = it } }
        view = service.onCreateInputView()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun info(inputType: Int) = EditorInfo().apply { this.inputType = inputType }

    private fun record() = view.findViewById<Button>(R.id.simple_record)
    private fun status() = view.findViewById<TextView>(R.id.simple_status).text.toString()
    private fun preview() = view.findViewById<TextView>(R.id.simple_preview)

    /** Feld oeffnen, Aufnehmen tippen, Verbindung "steht", Mikrofon offen. */
    private fun aufnahme(inputType: Int = InputType.TYPE_CLASS_TEXT): FakeLink {
        service.onStartInput(info(inputType), false)
        service.onStartInputView(info(inputType), false)
        record().performClick()
        idle()
        val l = link!!
        // start() laeuft auf dem IO-Thread des Dienstes.
        val until = System.currentTimeMillis() + 5_000
        while (!l.started && System.currentTimeMillis() < until) Thread.sleep(10)
        assertTrue("Verbindung muss gestartet werden", l.started)
        assertEquals(SimpleKeyboardState.Phase.CONNECTING, service.phase)
        l.listener.onOpen()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertEquals(SimpleKeyboardState.Phase.RECORDING, service.phase)
        return l
    }

    @Test fun partialGehtAlsComposingInsFeldUndCommittedAlsFesterText() {
        val l = aufnahme()
        l.listener.onPartial("Hallo")
        idle()
        assertEquals(listOf("Hallo"), editor.composing)
        assertEquals("im Feld: keine Vorschauzeile", View.GONE, preview().visibility)
        l.listener.onCommitted("Hallo Anna.")
        idle()
        assertEquals(listOf("Hallo Anna. "), editor.commits)
        // Fertig: Verbindung schliesst, Text ist drin.
        record().performClick()
        idle()
        assertTrue(l.finished)
        l.listener.onClosed(finished = true, pending = ByteArray(0))
        idle()
        assertEquals(SimpleKeyboardState.Phase.INSERTED, service.phase)
        assertEquals(app.getString(R.string.simple_btn_inserted), record().text.toString())
    }

    @Test fun cursorSprungSchreibtFestOnUpdateSelection() {
        val l = aufnahme()
        l.listener.onPartial("Hallo Anna")
        idle()
        // Composing-Bereich 5..15, Cursor springt davor (2): festschreiben.
        service.onUpdateSelection(15, 15, 2, 2, 5, 15)
        assertEquals(1, editor.finished)
        l.listener.onCommitted("Hallo Anna, bis morgen.")
        idle()
        assertEquals(listOf(", bis morgen. "), editor.commits)
    }

    @Test fun feldwechselWaehrendDerAufnahmeSchreibtFestUndBeendet() {
        val l = aufnahme()
        l.listener.onPartial("Hallo")
        idle()
        service.onFinishInput()
        assertEquals("offener Text festgeschrieben", 1, editor.finished)
        assertTrue("Verbindung beendet", l.cancelled)
        assertEquals(SimpleKeyboardState.Phase.READY, service.phase)
    }

    @Test fun typeNullBekommtNurFertigeSaetze() {
        val l = aufnahme(InputType.TYPE_NULL)
        l.listener.onPartial("Hallo")
        idle()
        assertTrue("kein Composing bei TYPE_NULL", editor.composing.isEmpty())
        assertEquals("Hallo", preview().text.toString())
        assertEquals(View.VISIBLE, preview().visibility)
        l.listener.onCommitted("Hallo.")
        idle()
        assertEquals(listOf("Hallo. "), editor.commits)
    }

    @Test fun passwortfeldSperrtDenKnopf() {
        service.onStartInput(info(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD), false)
        service.onStartInputView(info(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD), false)
        assertFalse(record().isEnabled)
        assertEquals(app.getString(R.string.simple_err_password), status())
        assertTrue(SimpleInputMethodService.isPassword(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
        assertFalse(SimpleInputMethodService.isPassword(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        // Normales Feld danach: wieder frei.
        service.onStartInput(info(InputType.TYPE_CLASS_TEXT), false)
        assertTrue(record().isEnabled)
    }

    @Test fun talkBackRegelVorlaeufigerTextNurInDerVorschauzeile() {
        val am = app.getSystemService(AccessibilityManager::class.java)
        shadowOf(am).setTouchExplorationEnabled(true)
        val l = aufnahme()
        l.listener.onPartial("Hallo Anna")
        idle()
        assertTrue("TalkBack: kein Composing im Feld", editor.composing.isEmpty())
        assertEquals("Hallo Anna", preview().text.toString())
        l.listener.onCommitted("Hallo Anna.")
        idle()
        assertEquals("je Abschnitt genau ein commitText", listOf("Hallo Anna. "), editor.commits)
        assertEquals("", preview().text.toString())
    }

    @Test fun loeschenWaehrendDerAufnahmeVerwirftDanachEntferntEsDasDiktat() {
        val l = aufnahme()
        l.listener.onPartial("Hallo")
        idle()
        view.findViewById<Button>(R.id.simple_delete).performClick()
        assertEquals(listOf("Hallo", ""), editor.composing)
        assertTrue(l.cancelled)
        assertEquals(SimpleKeyboardState.Phase.READY, service.phase)

        val l2 = aufnahme()
        l2.listener.onCommitted("Bis morgen.")
        record().performClick()
        l2.listener.onClosed(finished = true, pending = ByteArray(0))
        idle()
        assertEquals(SimpleKeyboardState.Phase.INSERTED, service.phase)
        // Bestaetigung: erster Tipp fragt, zweiter loescht "Bis morgen. " (12 Zeichen).
        val delete = view.findViewById<Button>(R.id.simple_delete)
        delete.performClick()
        assertEquals(app.getString(R.string.simple_btn_delete_confirm), delete.text.toString())
        delete.performClick()
        assertEquals(12, editor.deleted)
    }

    @Test fun ohneTokenDienstGeradeKeineVerbindung() {
        // Etappe 1 ohne Fake: die echte Sitzung hat kein Token.
        service.linkFactory = null
        service.onStartInput(info(InputType.TYPE_CLASS_TEXT), false)
        service.onStartInputView(info(InputType.TYPE_CLASS_TEXT), false)
        record().performClick()
        // Der IO-Thread meldet den Fehler, main verarbeitet ihn.
        val until = System.currentTimeMillis() + 5_000
        while (service.phase != SimpleKeyboardState.Phase.ERROR && System.currentTimeMillis() < until) {
            Thread.sleep(20)
            idle()
        }
        assertEquals(SimpleKeyboardState.Phase.ERROR, service.phase)
        assertEquals(app.getString(R.string.simple_err_no_connection), status())
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE, view.findViewById<View>(R.id.simple_status).accessibilityLiveRegion)
        assertNotNull(record().stateDescription)
        assertEquals(app.getString(R.string.simple_state_error), record().stateDescription.toString())
    }
}
