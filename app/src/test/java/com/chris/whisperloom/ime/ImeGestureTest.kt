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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
        }
        service = Robolectric.buildService(WhisperLoomInputMethodService::class.java).create().get()
        root = service.onCreateInputView()
        layout()
        mic = root.findViewById(R.id.mic)
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
        assertTrue("Aufnahme muss weiterlaufen", statusText.startsWith("Aufnahme"))
        assertNotEquals(app.getString(R.string.kb_transcribing), statusText)
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
        assertTrue(statusText.startsWith("Aufnahme"))
        discard.performClick()
        assertEquals(app.getString(R.string.kb_discarded), statusText)
    }

    @Test fun einZweiterFingerKapertDieGesteNicht() {
        down()
        move(dx = weit)
        // Ein zweiter Finger kommt auf — die Mikro-Taste faengt ihn ein, weil sie das
        // Touch-Ziel haelt. Die Geste muss trotzdem dem ersten Finger folgen.
        event(MotionEvent.ACTION_POINTER_DOWN, 0f, 0f)
        assertTrue("Einrastung darf nicht verloren gehen", lock.isActivated)
        up(dx = weit)
        assertTrue("Feststellen muss trotz zweitem Finger greifen", lock.isClickable)
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

    // --- Bedienungshilfen ----------------------------------------------------

    @Test fun klickStartetDieAufnahmeGleichFestgestellt() {
        // Der Weg mit TalkBack: Gedrueckthalten kommt dort nicht an, also muss ein Antippen
        // eine Aufnahme starten, die von selbst weiterlaeuft.
        mic.performClick()
        assertTrue("Klick muss festgestellt starten", statusText.startsWith("Aufnahme"))
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
