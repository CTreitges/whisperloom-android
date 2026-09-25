package com.chris.whisperloom.ime

import android.Manifest
import android.app.Application
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Die Wisch-Geste am echten Dienst: Touch-Ereignisse gehen durch denselben Listener wie auf
 * dem Geraet, und die Aufnahme laeuft wirklich (Robolectric laesst AudioRecord zu, es liefert
 * nur Stille).
 *
 * Zwei Dinge, ohne die diese Tests sich selbst bestaetigen wuerden:
 * - Die inflatete View ist ungemessen 0 x 0 — ohne [layout] ist jede Pruefung auf
 *   Koordinaten *innerhalb* der Taste wertlos.
 * - Der Main-Looper ist angehalten. Was der io-Thread zurueckpostet, kommt erst bei
 *   `idle()` an; bis dahin ist der Zustand direkt nach der Geste stabil pruefbar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImeGestureTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    private lateinit var service: WhisperLoomInputMethodService
    private lateinit var root: View
    private lateinit var mic: ImageButton

    private val status: TextView get() = root.findViewById(R.id.status)
    private val discard: ImageButton get() = root.findViewById(R.id.gesture_discard)
    private val lock: ImageButton get() = root.findViewById(R.id.gesture_lock)
    private val statusText: String get() = status.text.toString()

    /** Weit ueber jeder Schwelle: 56 dp Einrasten, 64 dp senkrechte Toleranz. */
    private val weit = 400f

    @Before fun aufbau() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        // Ein erreichbarer, aber toter Zugang: konfiguriert genug zum Aufnehmen, und ein
        // versehentlicher Netz-Aufruf laeuft sofort ins Leere statt nach draussen.
        Prefs(app).apply {
            engine = Engine.ONLINE
            sttProviderId = "custom"
            apiBaseUrl = "http://127.0.0.1:1/v1"
            // Ohne Textmodell gilt die KI-Stufe nicht als bereit (Review 3.5.0).
            llmModel = "qwen3:8b"
        }
        service = Robolectric.buildService(WhisperLoomInputMethodService::class.java).create().get()
        root = service.onCreateInputView()
        layout()
        mic = root.findViewById(R.id.mic)
    }

    @After fun abbau() {
        // Ohne das laesst jeder Test, der mit laufendem Mikrofon endet, einen Aufnahme- und
        // einen io-Thread zurueck.
        service.onFinishInputView(true)
        service.onDestroy()
    }

    private fun layout() {
        val w = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val h = View.MeasureSpec.makeMeasureSpec(660, View.MeasureSpec.EXACTLY)
        root.measure(w, h)
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    // --- Touch-Hilfen --------------------------------------------------------

    private fun event(action: Int, x: Float, y: Float) =
        MotionEvent.obtain(0L, 0L, action, x, y, 0).also { mic.dispatchTouchEvent(it); it.recycle() }

    private fun mitte() = Pair(mic.width / 2f, mic.height / 2f)

    private fun down() = mitte().let { event(MotionEvent.ACTION_DOWN, it.first, it.second) }
    private fun move(dx: Float, dy: Float = 0f) =
        mitte().let { event(MotionEvent.ACTION_MOVE, it.first + dx, it.second + dy) }

    private fun up(dx: Float = 0f, dy: Float = 0f) =
        mitte().let { event(MotionEvent.ACTION_UP, it.first + dx, it.second + dy) }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /**
     * Ereignis mit mehreren Zeigern. `MotionEvent.obtain(..., x, y, ...)` erzeugt immer nur
     * EINEN Zeiger mit der ID 0 — damit laesst sich ueber Multitouch nichts aussagen.
     */
    private fun pointers(action: Int, vararg finger: Triple<Int, Float, Float>) {
        val props = finger.map { (id, _, _) ->
            MotionEvent.PointerProperties().apply {
                this.id = id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }.toTypedArray()
        val coords = finger.map { (_, x, y) ->
            MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
            }
        }.toTypedArray()
        val ev = MotionEvent.obtain(
            0L, 0L, action, finger.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0,
        )
        mic.dispatchTouchEvent(ev)
        ev.recycle()
    }

    /** Aktion mit Zeiger-Index, z. B. ACTION_POINTER_UP fuer den zweiten Finger. */
    private fun mitIndex(action: Int, index: Int) =
        action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    /** Finger [id] an der Mikro-Mitte, um [dx]/[dy] verschoben. */
    private fun finger(id: Int, dx: Float = 0f, dy: Float = 0f) =
        mitte().let { Triple(id, it.first + dx, it.second + dy) }

    // --- Halten und loslassen -----------------------------------------------

    @Test fun haltenStartetDieAufnahme() {
        down()
        assertEquals(app.getString(R.string.kb_listening), statusText)
        assertEquals("Ziele gehoeren nicht zum blossen Halten", View.GONE, lock.visibility)
    }

    @Test fun zitternBlendetDieZieleNochNichtEin() {
        down()
        // Unter dem System-Slop: ein liegender Finger zittert, das ist kein Wischen.
        move(dx = 1f, dy = 1f)
        assertEquals(View.GONE, discard.visibility)
        assertEquals(View.GONE, lock.visibility)
        assertEquals(app.getString(R.string.kb_listening), statusText)
    }

    @Test fun loslassenOhneZiehenSendet() {
        down()
        up()
        assertEquals(app.getString(R.string.kb_transcribing), statusText)
    }

    // --- Ziehen --------------------------------------------------------------

    @Test fun ziehenBlendetBeideZieleEinOhneEinzurasten() {
        down()
        move(dx = 20f)
        assertEquals(View.VISIBLE, discard.visibility)
        assertEquals(View.VISIBLE, lock.visibility)
        assertFalse(discard.isActivated)
        assertFalse(lock.isActivated)
        assertEquals(app.getString(R.string.kb_listening), statusText)
    }

    @Test fun nachRechtsZiehenRastetDasFeststellenEin() {
        down()
        move(dx = weit)
        assertTrue(lock.isActivated)
        assertFalse(discard.isActivated)
        assertEquals(app.getString(R.string.kb_lock_armed), statusText)
    }

    @Test fun nachLinksZiehenRastetDasVerwerfenEin() {
        down()
        move(dx = -weit)
        assertTrue(discard.isActivated)
        assertFalse(lock.isActivated)
        assertEquals(app.getString(R.string.kb_cancel_armed), statusText)
    }

    @Test fun steilWeggezogenRastetNichtsEin() {
        down()
        move(dx = weit, dy = weit)
        assertFalse(lock.isActivated)
        assertEquals(app.getString(R.string.kb_listening), statusText)
    }

    // --- Feststellen ---------------------------------------------------------

    private fun feststellen() {
        down()
        move(dx = weit)
        up(dx = weit)
    }

    @Test fun loslassenNachRechtsStelltFest() {
        feststellen()
        // kb_locked = "Aufnahme %1$s — senden oder verwerfen"; startsWith("Aufnahme") wuerde
        // auch auf "Aufnahme verworfen" passen.
        assertTrue("Statuszeile zeigt nicht den festgestellten Zustand: $statusText",
            statusText.endsWith("senden oder verwerfen"))
        assertEquals(View.VISIBLE, lock.visibility)
        assertTrue("Senden muss bedienbar sein", lock.isClickable)
        assertEquals(app.getString(R.string.cd_kb_send), lock.contentDescription)
        assertTrue("Verwerfen muss bedienbar sein", discard.isClickable)
    }

    @Test fun imFestgestelltenZustandSchweigtDieLiveRegion() {
        feststellen()
        // Der Ticker schreibt jede Sekunde — als Live-Region waere das eine Ansage pro Sekunde.
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_NONE, status.accessibilityLiveRegion)
    }

    @Test fun sendenTasteBeendetDasFestgestellteDiktat() {
        feststellen()
        lock.performClick()
        assertEquals(app.getString(R.string.kb_transcribing), statusText)
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, status.accessibilityLiveRegion)
    }

    @Test fun verwerfenTasteWirftDasDiktatWeg() {
        feststellen()
        discard.performClick()
        assertEquals(app.getString(R.string.kb_discarded), statusText)
        assertEquals(View.GONE, lock.visibility)
        assertEquals(app.getString(R.string.cd_kb_lock), lock.contentDescription)
    }

    @Test fun nachLinksZiehenUndLoslassenVerwirftSofort() {
        down()
        move(dx = -weit)
        up(dx = -weit)
        assertEquals(app.getString(R.string.kb_discarded), statusText)
        assertEquals(View.GONE, discard.visibility)
    }

    // --- Regressionen aus der Review ----------------------------------------

    @Test fun festgestelltUndNebenDerTasteLosgelassenSendetNicht() {
        feststellen()
        val vorher = statusText
        // Der Finger wandert von der Mikro-Taste zur sichtbaren Verwerfen-Taste und laesst
        // dort los. Die Mikro-Taste haelt den Touch-Strom und bekommt das UP trotzdem —
        // sie darf das Diktat deshalb nicht abschicken.
        down()
        up(dx = -(mic.width.toFloat() + 10f))
        assertEquals("Loslassen neben der Taste darf nicht senden", vorher, statusText)
        assertNotEquals(app.getString(R.string.kb_transcribing), statusText)
    }

    @Test fun festgestelltUndAufDerTasteLosgelassenSendet() {
        feststellen()
        down()
        up()
        assertEquals(app.getString(R.string.kb_transcribing), statusText)
    }

    @Test fun konfigurationswechselBehaeltDenFestgestelltenZustand() {
        feststellen()
        // Drehen oder Dunkelmodus: das Framework baut den Eingabe-View neu auf, ruft aber
        // kein onFinishInputView. Ohne Rekonstruktion zeigte die neue Tastatur Ruhe, waehrend
        // das Mikrofon weiterlief — und kein Weg fuehrte mehr zum Verwerfen.
        root = service.onCreateInputView()
        layout()
        mic = root.findViewById(R.id.mic)
        assertEquals("Senden muss nach dem Neuaufbau da sein", View.VISIBLE, lock.visibility)
        assertTrue(lock.isClickable)
        assertEquals(app.getString(R.string.cd_kb_send), lock.contentDescription)
        assertEquals(View.VISIBLE, discard.visibility)
        assertTrue(statusText.endsWith("senden oder verwerfen"))
        discard.performClick()
        assertEquals(app.getString(R.string.kb_discarded), statusText)
    }

    @Test fun dieGesteFolgtDemFuehrendenFingerNichtDemErstenZeiger() {
        down()
        move(dx = weit)
        // Zweiter Finger links auf der Taste. Sie faengt ihn ein, obwohl er sie nicht
        // beruehrt: findet der Touch-Dispatch kein passendes Kind, haengt er ihn ans
        // bestehende Touch-Ziel.
        pointers(mitIndex(MotionEvent.ACTION_POINTER_DOWN, 1), finger(0, weit), finger(1, -weit))
        assertTrue("Einrastung darf nicht verloren gehen", lock.isActivated)

        // Bewegung, bei der der fuehrende Finger NICHT an Index 0 steht. Wer blind getX(0)
        // nimmt, liest hier den zweiten Finger und kippt auf Verwerfen.
        pointers(MotionEvent.ACTION_MOVE, finger(1, -weit), finger(0, weit))
        assertTrue("Geste ist dem falschen Finger gefolgt", lock.isActivated)
        assertFalse(discard.isActivated)
    }

    @Test fun nachAbhebenDesFuehrendenFingersLoestDerZweiteNichtsAus() {
        // Der zweite Finger liegt AUF der Taste — sonst faengt ihn schon die Bounds-Pruefung
        // ab und der Test bewiese nichts.
        val zweiter = 4f
        down()
        move(dx = weit)
        pointers(mitIndex(MotionEvent.ACTION_POINTER_DOWN, 1), finger(0, weit), finger(1, zweiter))
        // Der fuehrende Finger hebt ab: das stellt fest.
        pointers(mitIndex(MotionEvent.ACTION_POINTER_UP, 0), finger(0, weit), finger(1, zweiter))
        assertTrue("Abheben des fuehrenden Fingers muss feststellen", lock.isClickable)

        // Jetzt hebt der zweite ab. Er hat die Geste nie gefuehrt — sonst folgte auf das
        // Feststellen sofort das Senden, in einem einzigen Zug.
        pointers(MotionEvent.ACTION_UP, finger(1, zweiter))
        assertNotEquals(
            "Abheben des zweiten Fingers darf nicht senden",
            app.getString(R.string.kb_transcribing),
            statusText,
        )
        assertTrue(statusText.endsWith("senden oder verwerfen"))
    }

    // --- Tastatur schliessen -------------------------------------------------

    @Test fun tastaturZuAberFeldBleibtUebertraegtNoch() {
        feststellen()
        service.onFinishInputView(false)
        assertEquals(app.getString(R.string.kb_transcribing), statusText)
    }

    @Test fun eingabeBeendetVerwirftDieFestgestellteAufnahme() {
        feststellen()
        service.onFinishInputView(true)
        assertEquals(app.getString(R.string.kb_discarded), statusText)
    }

    // --- Schnellzugriff Textverbesserung -------------------------------------

    private val refineRow: View get() = root.findViewById(R.id.refine_row)
    private fun refineKey(id: Int): View = root.findViewById(id)

    @Test fun dieSchnellzugriffTasteKlapptAufUndZu() {
        assertEquals("Im Ruhezustand eingeklappt", View.GONE, refineRow.visibility)
        root.findViewById<View>(R.id.key_refine).performClick()
        assertEquals(View.VISIBLE, refineRow.visibility)
        root.findViewById<View>(R.id.key_refine).performClick()
        assertEquals(View.GONE, refineRow.visibility)
    }

    @Test fun eineStufeWaehlenSchreibtSieSofortInDiePrefs() {
        Prefs(app).refineMode = RefineMode.OFF
        root.findViewById<View>(R.id.key_refine).performClick()
        refineKey(R.id.refine_beautify).performClick()
        // TranscriptionEngine liest die Prefs bei jedem Diktat frisch — die Wahl wirkt sofort.
        assertEquals(RefineMode.BEAUTIFY, Prefs(app).refineMode)
        assertTrue(refineKey(R.id.refine_beautify).isSelected)
        // Quittung in der Statuszeile — mit dem Namen der Stufe, nicht nur "gespeichert".
        assertEquals(
            app.getString(R.string.kb_refine_set, app.getString(R.string.level_beautify)),
            statusText,
        )
    }

    @Test fun jedeStufeQuittiertMitIhremEigenenNamen() {
        root.findViewById<View>(R.id.key_refine).performClick()
        for ((id, label) in listOf(
            R.id.refine_off to R.string.level_off,
            R.id.refine_polish to R.string.level_smooth,
            R.id.refine_beautify to R.string.level_beautify,
            R.id.refine_summarize to R.string.level_summarize,
        )) {
            refineKey(id).performClick()
            assertEquals(app.getString(R.string.kb_refine_set, app.getString(label)), statusText)
        }
    }

    @Test fun dieOffeneLeisteZeigtDieGespeicherteStufe() {
        Prefs(app).refineMode = RefineMode.SUMMARIZE
        root.findViewById<View>(R.id.key_refine).performClick()
        assertTrue(refineKey(R.id.refine_summarize).isSelected)
        assertFalse(refineKey(R.id.refine_off).isSelected)
    }

    @Test fun ohneKiZugangErklaertDieStatuszeileWarumEsAbgeblendetIst() {
        Prefs(app).apiBaseUrl = ""
        root.findViewById<View>(R.id.key_refine).performClick()
        assertEquals(app.getString(R.string.kb_refine_needs_llm), statusText)
        assertFalse(refineKey(R.id.refine_polish).isEnabled)
        assertTrue("Aus bleibt waehlbar", refineKey(R.id.refine_off).isEnabled)
        // Der Hinweis muss irgendwohin fuehren, sonst ist er eine Sackgasse.
        assertTrue("Hinweis nicht antippbar", status.isClickable)
        status.performClick()
        assertNotNull("Tipp muss in die Einstellungen fuehren", shadowOf(app).nextStartedActivity)
    }

    @Test fun derZauberstabVerschwindetWaehrendDerAufnahme() {
        val toggle = root.findViewById<View>(R.id.key_refine)
        assertEquals(View.VISIBLE, toggle.visibility)
        down()
        // Er sitzt in der Mikro-Zone, wo jetzt die Wisch-Ziele erscheinen.
        assertEquals(View.GONE, toggle.visibility)
        up()
        assertEquals("Waehrend der Uebertragung ebenfalls weg", View.GONE, toggle.visibility)
    }

    @Test fun eineAufnahmeKlapptDieOffeneLeisteZu() {
        root.findViewById<View>(R.id.key_refine).performClick()
        assertEquals(View.VISIBLE, refineRow.visibility)
        down()
        // Sonst stuende sie offen, waehrend ihr Ausloeser verschwindet.
        assertEquals(View.GONE, refineRow.visibility)
    }

    @Test fun einFeldwechselFuehrtDieOffeneLeisteNach() {
        Prefs(app).refineMode = RefineMode.OFF
        root.findViewById<View>(R.id.key_refine).performClick()
        assertTrue(refineKey(R.id.refine_off).isSelected)

        // Der Eingabe-View wird wiederverwendet; inzwischen hat jemand die Stufe geaendert.
        Prefs(app).refineMode = RefineMode.SUMMARIZE
        service.onStartInputView(null, false)

        assertTrue("Leiste zeigt die alte Stufe", refineKey(R.id.refine_summarize).isSelected)
        assertFalse(refineKey(R.id.refine_off).isSelected)
    }

    @Test fun mitKiZugangKommtKeinHinweis() {
        root.findViewById<View>(R.id.key_refine).performClick()
        assertEquals(app.getString(R.string.kb_hint_hold), statusText)
        assertTrue(refineKey(R.id.refine_polish).isEnabled)
    }

    @Test fun diePromptTasteGibtEsNurMitSchalter() {
        root.findViewById<View>(R.id.key_refine).performClick()
        assertEquals("Ohne Schalter unsichtbar", View.GONE, refineKey(R.id.refine_prompt).visibility)
        root.findViewById<View>(R.id.key_refine).performClick() // zu

        Prefs(app).promptLevelEnabled = true
        root.findViewById<View>(R.id.key_refine).performClick() // auf, liest die Prefs frisch
        assertEquals(View.VISIBLE, refineKey(R.id.refine_prompt).visibility)
        refineKey(R.id.refine_prompt).performClick()
        assertEquals(RefineMode.PROMPT, Prefs(app).refineMode)
        assertEquals(app.getString(R.string.kb_refine_set, app.getString(R.string.level_prompt)), statusText)
    }

    @Test fun einFeldwechselFuehrtDenPromptSchalterNach() {
        Prefs(app).promptLevelEnabled = true
        root.findViewById<View>(R.id.key_refine).performClick()
        assertEquals(View.VISIBLE, refineKey(R.id.refine_prompt).visibility)

        Prefs(app).promptLevelEnabled = false
        service.onStartInputView(null, false)

        assertEquals("Ausgeschaltete Stufe noch in der Leiste", View.GONE, refineKey(R.id.refine_prompt).visibility)
    }

    // --- Bedienungshilfen ----------------------------------------------------

    @Test fun klickStartetDieAufnahmeGleichFestgestellt() {
        // Der Weg mit TalkBack: Gedrueckthalten kommt dort nicht an, also muss ein Antippen
        // eine Aufnahme starten, die von selbst weiterlaeuft.
        mic.performClick()
        assertTrue("Klick muss festgestellt starten", statusText.endsWith("senden oder verwerfen"))
        assertTrue(lock.isClickable)
        assertEquals(app.getString(R.string.cd_kb_send), lock.contentDescription)
        assertTrue(mic.contentDescription.contains("festgestellt"))
    }

    @Test fun zweiterKlickSendet() {
        mic.performClick()
        mic.performClick()
        assertEquals(app.getString(R.string.kb_transcribing), statusText)
        idle()
    }
}
