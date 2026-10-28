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
 * (onUpdateSelection, onFinishInput), TYPE_NULL, Passwortfeld, TalkBack-Regel, Fehler ohne Verbindung,
 * Abbruch ohne Textverlust, veraltete Sitzungen, Stille-Erkennung.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SimpleImeComposingTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    /** Fuehrt den Feldtext (fester Text + Composing am Ende). */
    private class FakeEditor : Editor {
        val fixedText = StringBuilder()
        var composingText = ""
        val composing = mutableListOf<String>()
        val commits = mutableListOf<String>()
        var finished = 0
        var deleted = 0
        val text: String get() = fixedText.toString() + composingText
        override fun setComposing(text: String): Boolean { composing += text; composingText = text; return true }
        override fun commit(text: String): Boolean { commits += text; composingText = ""; fixedText.append(text); return true }
        override fun finishComposing() { finished++; fixedText.append(composingText); composingText = "" }
        override fun deleteBefore(chars: Int) { deleted += chars; fixedText.setLength((fixedText.length - chars).coerceAtLeast(0)) }
        override fun textBeforeCursor(chars: Int): CharSequence = fixedText.takeLast(chars)
    }

    private class FakeLink(val listener: RealtimeSession.Listener) : RealtimeLink {
        @Volatile var started = false
        var finished = false
        var cancelled = false
        val audio = mutableListOf<ByteArray>()
        override fun start(): Boolean { started = true; return true }
        override fun sendAudio(pcm: ByteArray) { synchronized(audio) { audio += pcm } }
        override fun finish() { finished = true }
        override fun cancel() { cancelled = true }
    }

    private val editor = FakeEditor()
    private val links = mutableListOf<FakeLink>()
    private val link: FakeLink? get() = links.lastOrNull()
    private lateinit var service: SimpleInputMethodService
    private lateinit var view: View

    @Before fun aufbau() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        prefs(SimpleProfile.KEY_LOCK_MS to 0, SimpleProfile.KEY_TONES to false)
        // Das Schatten-Mikrofon liefert nichts (0 Samples) — hier geht es um Text, nicht um Audio.
        ShadowAudioRecord.setSourceProvider { object : ShadowAudioRecord.AudioRecordSource {} }
        dienst()
    }

    /** Ohne Doppeltreffer-Sperre, ohne Toene: die Tests tippen schneller als ein Mensch. */
    private fun prefs(vararg values: Pair<String, Any>) {
        val e = app.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit()
        for ((k, v) in values) when (v) {
            is Int -> e.putInt(k, v)
            is Boolean -> e.putBoolean(k, v)
            is String -> e.putString(k, v)
        }
        e.commit()
    }

    private fun dienst() {
        service = Robolectric.buildService(SimpleInputMethodService::class.java).create().get()
        service.editorFactory = { editor }
        service.linkFactory = { l -> FakeLink(l).also { links += it } }
        view = service.onCreateInputView()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun idleFor(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun info(inputType: Int) = EditorInfo().apply { this.inputType = inputType }

    private fun record() = view.findViewById<Button>(R.id.simple_record)
    private fun delete() = view.findViewById<Button>(R.id.simple_delete)
    private fun status() = view.findViewById<TextView>(R.id.simple_status).text.toString()
    private fun preview() = view.findViewById<TextView>(R.id.simple_preview)

    private fun feld(inputType: Int = InputType.TYPE_CLASS_TEXT) {
        service.onStartInput(info(inputType), false)
        service.onStartInputView(info(inputType), false)
    }

    private fun awaitStarted(l: FakeLink) {
        // start() laeuft auf dem IO-Thread des Dienstes.
        val until = System.currentTimeMillis() + 5_000
        while (!l.started && System.currentTimeMillis() < until) Thread.sleep(10)
        assertTrue("Verbindung muss gestartet werden", l.started)
    }

    /** Aufnehmen tippen, Verbindung "steht", Mikrofon offen. */
    private fun aufnahme(): FakeLink {
        record().performClick()
        idle()
        val l = link!!
        awaitStarted(l)
        assertEquals(SimpleKeyboardState.Phase.CONNECTING, service.phase)
        l.listener.onOpen()
        idleFor(300)
        assertEquals(SimpleKeyboardState.Phase.RECORDING, service.phase)
        return l
    }

    private fun fertig(l: FakeLink, finished: Boolean = true) {
        record().performClick()
        idle()
        assertTrue(l.finished)
        l.listener.onClosed(finished = finished, pending = ByteArray(0))
        idle()
    }

    @Test fun partialGehtAlsComposingInsFeldUndCommittedAlsFesterText() {
        feld()
        val l = aufnahme()
        l.listener.onPartial("Hallo")
        idle()
        assertEquals(listOf("Hallo"), editor.composing)
        assertEquals("im Feld: keine Vorschauzeile", View.GONE, preview().visibility)
        l.listener.onCommitted("Hallo Anna.")
        idle()
        assertEquals(listOf("Hallo Anna. "), editor.commits)
        fertig(l)
        assertEquals(SimpleKeyboardState.Phase.INSERTED, service.phase)
        assertEquals(app.getString(R.string.simple_btn_inserted), record().text.toString())
        assertEquals("Hallo Anna. ", editor.text)
    }

    @Test fun cursorSprungSchreibtFestOnUpdateSelection() {
        feld()
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
        feld()
        val l = aufnahme()
        l.listener.onPartial("Hallo")
        idle()
        service.onFinishInput()
        assertEquals("offener Text festgeschrieben", 1, editor.finished)
        assertEquals("Hallo", editor.text)
        assertTrue("Verbindung beendet", l.cancelled)
        assertEquals(SimpleKeyboardState.Phase.READY, service.phase)
    }

    @Test fun typeNullBekommtNurFertigeSaetze() {
        feld(InputType.TYPE_NULL)
        val l = aufnahme()
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
        feld(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
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
        feld()
        val l = aufnahme()
        l.listener.onPartial("Hallo Anna")
        idle()
        assertTrue("TalkBack: kein Composing im Feld", editor.composing.isEmpty())
        assertEquals("Hallo Anna", preview().text.toString())
        assertTrue("Vorschauzeile ohne contentDescription — TalkBack liest den Text selbst", preview().contentDescription == null)
        l.listener.onCommitted("Hallo Anna.")
        idle()
        assertEquals("je Abschnitt genau ein commitText", listOf("Hallo Anna. "), editor.commits)
        assertEquals("", preview().text.toString())
    }

    @Test fun vorschauAusZeigtKeinenVorlaeufigenText() {
        prefs(SimpleProfile.KEY_PREVIEW to "off")
        dienst()
        feld()
        val l = aufnahme()
        l.listener.onPartial("Hallo")
        idle()
        assertTrue(editor.composing.isEmpty())
        assertEquals(View.GONE, preview().visibility)
        l.listener.onCommitted("Hallo.")
        idle()
        assertEquals("Hallo. ", editor.text)
    }

    @Test fun loeschenWaehrendDerAufnahmeVerwirftDanachEntferntEsDasDiktat() {
        feld()
        val l = aufnahme()
        l.listener.onPartial("Hallo")
        idle()
        delete().performClick()
        assertEquals(listOf("Hallo", ""), editor.composing)
        assertEquals("", editor.text)
        assertTrue(l.cancelled)
        assertEquals(SimpleKeyboardState.Phase.READY, service.phase)

        val l2 = aufnahme()
        l2.listener.onCommitted("Bis morgen.")
        idle()
        fertig(l2)
        assertEquals(SimpleKeyboardState.Phase.INSERTED, service.phase)
        // Bestaetigung: erster Tipp fragt, zweiter loescht "Bis morgen. " (12 Zeichen).
        delete().performClick()
        assertEquals(app.getString(R.string.simple_btn_delete_confirm), delete().text.toString())
        delete().performClick()
        assertEquals(12, editor.deleted)
        assertEquals("", editor.text)
    }

    @Test fun loeschenTrifftNieFremdenText() {
        // Review K1: nach Feldwechsel oder wenn anderer Text vor dem Cursor steht, bleibt der fremde Text.
        prefs(SimpleProfile.KEY_DELETE_CONFIRM to false)
        dienst()
        feld()
        val l = aufnahme()
        l.listener.onCommitted("Meins.")
        idle()
        fertig(l)
        // Die Person tippt noch etwas dahinter: jetzt steht nicht mehr das Diktat vor dem Cursor.
        editor.fixedText.append("tipp")
        delete().performClick()
        assertEquals(0, editor.deleted)
        assertEquals("Meins. tipp", editor.text)

        // Neues Feld (andere App): nichts zu entfernen, obwohl eben diktiert wurde.
        val l2 = aufnahme()
        l2.listener.onCommitted("Nochmal.")
        idle()
        fertig(l2)
        service.onStartInput(info(InputType.TYPE_CLASS_TEXT), false)
        delete().performClick()
        assertEquals(0, editor.deleted)
    }

    @Test fun endeOhneFestenTextSchreibtDenOffenenTextFestUndVerliertNichts() {
        // Review K2: Fertig, aber die Verbindung bricht ab, bevor der feste Text kommt.
        feld()
        val l = aufnahme()
        l.listener.onCommitted("Erster Satz.")
        l.listener.onPartial("zweiter Satz")
        idle()
        record().performClick()
        idle()
        l.listener.onClosed(finished = false, pending = ByteArray(3_200))
        idle()
        assertEquals("Erster Satz. zweiter Satz", editor.text)
        assertEquals(SimpleKeyboardState.Phase.INSERTED, service.phase)
        assertEquals("Hinweis auf den Abbruch", app.getString(R.string.simple_err_dropped), status())
        // Die naechste Aufnahme haengt an, statt den alten Text zu ersetzen.
        val l2 = aufnahme()
        l2.listener.onPartial("dritter")
        idle()
        assertEquals("Erster Satz. zweiter Satz dritter", editor.text)
    }

    @Test fun abbruchWaehrendDerAufnahmeHaeltFestenUndOffenenText() {
        feld()
        val l = aufnahme()
        l.listener.onCommitted("Erster Satz.")
        l.listener.onPartial("zweiter")
        idle()
        l.listener.onClosed(finished = false, pending = ByteArray(3_200))
        idle()
        assertEquals(SimpleKeyboardState.Phase.ERROR, service.phase)
        assertEquals(app.getString(R.string.simple_err_dropped), status())
        assertEquals("Erster Satz. zweiter", editor.text)
        assertEquals("", preview().text.toString())
    }

    @Test fun veralteteSitzungStoertDieNeueNicht() {
        // Review K3: Aufnehmen → Loeschen (Abbruch waehrend des Token-Abrufs) → Aufnehmen; die erste Sitzung meldet
        // sich spaeter noch. Ihr onOpen darf das Mikrofon nicht fuer die zweite oeffnen, ihr onClosed nichts beenden.
        feld()
        record().performClick()
        idle()
        val alt = link!!
        awaitStarted(alt)
        delete().performClick()
        assertTrue(alt.cancelled)
        assertEquals(SimpleKeyboardState.Phase.READY, service.phase)
        record().performClick()
        idle()
        val neu = link!!
        awaitStarted(neu)
        assertEquals(SimpleKeyboardState.Phase.CONNECTING, service.phase)
        alt.listener.onOpen()
        idleFor(300)
        assertEquals("die alte Sitzung oeffnet kein Mikrofon", SimpleKeyboardState.Phase.CONNECTING, service.phase)
        alt.listener.onClosed(finished = false, pending = ByteArray(0))
        idle()
        assertEquals("die alte Sitzung beendet nichts", SimpleKeyboardState.Phase.CONNECTING, service.phase)
        neu.listener.onOpen()
        idleFor(300)
        assertEquals(SimpleKeyboardState.Phase.RECORDING, service.phase)
        neu.listener.onPartial("Hallo")
        idle()
        assertEquals("Hallo", editor.text)
        fertig(neu)
        assertEquals(SimpleKeyboardState.Phase.INSERTED, service.phase)
    }

    @Test fun fertigPerStilleNachDerLetztenTextaenderung() {
        // Review W1: wiederholte gleiche Partials (Pause) zaehlen nicht als Aenderung.
        prefs(SimpleProfile.KEY_DONE_BY_SILENCE_SEC to 3)
        dienst()
        feld()
        val l = aufnahme()
        l.listener.onPartial("Hallo Anna")
        idle()
        idleFor(2_000)
        assertFalse(l.finished)
        l.listener.onPartial("Hallo Anna")
        idleFor(2_500)
        assertTrue("3 s nach der letzten Aenderung: Fertig (Takt 1 s)", l.finished)
        assertEquals(SimpleKeyboardState.Phase.INSERTING, service.phase)
    }

    @Test fun sicherheitsStoppNachLangerStilleMitHinweis() {
        prefs(SimpleProfile.KEY_SAFETY_STOP_MIN to 1)
        dienst()
        feld()
        val l = aufnahme()
        idleFor(61_000)
        assertTrue(l.finished)
        l.listener.onClosed(finished = true, pending = ByteArray(0))
        idle()
        assertEquals("nichts erkannt → Fehlertext", SimpleKeyboardState.Phase.ERROR, service.phase)
    }

    @Test fun stilleErkennungAusLaesstDieAufnahmeLaufen() {
        feld()
        val l = aufnahme()
        idleFor(5 * 60_000)
        assertFalse(l.finished)
        assertEquals(SimpleKeyboardState.Phase.RECORDING, service.phase)
    }

    @Test fun ohneTokenDienstGeradeKeineVerbindung() {
        // Etappe 1 ohne Fake: die echte Sitzung hat kein Token.
        service.linkFactory = null
        feld()
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
