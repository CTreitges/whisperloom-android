package com.chris.whisperloom.ime

import android.Manifest
import android.app.Application
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Formats
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAudioRecord
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pause in der Diktat-Tastatur (Plan §1) am echten Dienst: Im festgestellten Zustand wird die
 * Mikro-Taste Pause bzw. Weiter, gesendet wird ueber ➤ oder die TalkBack-Aktion.
 *
 * Das Mikrofon liefert nur, was ein Test mit [sprechen] bereitstellt — die Aufnahmezeit kommt
 * aus den Samples, so steht sie fest. Ohne Deckel lieferte das Schatten-Mikrofon so schnell,
 * wie die CPU kann. Main-Looper angehalten wie in [ImeGestureTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImePauseTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val mikro = Mikro()

    private lateinit var service: WhisperLoomInputMethodService
    private lateinit var root: View
    private lateinit var mic: ImageButton

    private val status: TextView get() = root.findViewById(R.id.status)
    private val statusText: String get() = status.text.toString()
    private val discard: ImageButton get() = root.findViewById(R.id.gesture_discard)
    private val send: ImageButton get() = root.findViewById(R.id.gesture_lock)
    private val globe: View get() = root.findViewById(R.id.key_globe)
    private val band: LevelBandView get() = root.findViewById(R.id.level)

    @Before fun aufbau() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        // Toter, aber konfigurierter Online-Zugang wie in ImeGestureTest.
        Prefs(app).apply {
            engine = Engine.ONLINE
            sttProviderId = "custom"
            apiBaseUrl = "http://127.0.0.1:1/v1"
            llmModel = "qwen3:8b"
        }
        ShadowAudioRecord.setSource(mikro)
        service = Robolectric.buildService(WhisperLoomInputMethodService::class.java).create().get()
        aufbauen()
    }

    @After fun abbau() {
        service.onFinishInputView(true)
        service.onDestroy()
        ShadowAudioRecord.clearSource()
    }

    /** Eingabe-View (neu) aufbauen und messen — wie nach Drehen oder Dunkelmodus. */
    private fun aufbauen() {
        root = service.onCreateInputView()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(660, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        mic = root.findViewById(R.id.mic)
    }

    /** Liefert nur bereitgestellten Ton, sonst "gerade nichts da" (0). */
    private class Mikro : ShadowAudioRecord.AudioRecordSource {
        private var rest = 0L
        private var leer = CountDownLatch(0)

        @Synchronized
        override fun readInShortArray(data: ShortArray, offset: Int, size: Int, blocking: Boolean): Int {
            if (rest <= 0) {
                // Erst beim Lesen NACH dem letzten Stueck: dann ist alles geschrieben.
                leer.countDown()
                return 0
            }
            val n = minOf(size.toLong(), rest).toInt()
            for (i in 0 until n) data[offset + i] = if (i % 2 == 0) 4000 else -4000
            rest -= n
            return n
        }

        /** [ms] Ton bereitstellen und warten, bis die laufende Aufnahme ihn geschrieben hat. */
        fun sprechen(ms: Long) {
            val fertig = synchronized(this) {
                rest += ms * 16 // 16 kHz
                CountDownLatch(1).also { leer = it }
            }
            assertTrue("Aufnahme hat den Ton nicht gelesen", fertig.await(10, TimeUnit.SECONDS))
        }
    }

    // --- Hilfen ---------------------------------------------------------------

    private fun event(action: Int, dx: Float = 0f) {
        val ev = MotionEvent.obtain(0L, 0L, action, mic.width / 2f + dx, mic.height / 2f, 0)
        mic.dispatchTouchEvent(ev)
        ev.recycle()
    }

    private fun down() = event(MotionEvent.ACTION_DOWN)
    private fun up() = event(MotionEvent.ACTION_UP)
    private fun tippen() {
        down()
        up()
    }

    /** Wisch nach rechts und loslassen (wie ImeGestureTest). */
    private fun feststellen() {
        down()
        event(MotionEvent.ACTION_MOVE, 400f)
        event(MotionEvent.ACTION_UP, 400f)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun warten(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun zeit(ms: Long) = Formats.duration(ms)
    private fun laeuft(ms: Long) = app.getString(R.string.kb_locked, zeit(ms))
    private fun pausiert(ms: Long) = app.getString(R.string.kb_paused, zeit(ms))

    /** Benutzerdefinierte Bedienungshilfen-Aktion der Mikro-Taste mit [label]; null = keine. */
    private fun aktion(label: Int) = mic.createAccessibilityNodeInfo().actionList
        .firstOrNull { it.label?.toString() == app.getString(label) }

    private fun aktionAusfuehren(label: Int) {
        val a = aktion(label) ?: return fail("Aktion fehlt: ${app.getString(label)}")
        assertTrue(mic.performAccessibilityAction(a.id, null))
    }

    // --- Pause und Weiter -----------------------------------------------------

    @Test fun tippAufDieFestgestellteTastePausiert() {
        feststellen()
        mikro.sprechen(2_000)
        tippen()
        assertEquals(pausiert(2_000), statusText)
        assertFalse("Pegelband muss in der Pause stehen", band.isActive)
        assertEquals(app.getString(R.string.cd_mic_paused, zeit(2_000)), mic.contentDescription)
        // Kein Ticker mehr: die Zeile darf wieder angesagt werden.
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, status.accessibilityLiveRegion)
        assertTrue("Senden bleibt bedienbar", send.isClickable)
        assertTrue("Verwerfen bleibt bedienbar", discard.isClickable)
        assertEquals("Zauberstab verborgen", View.GONE, root.findViewById<View>(R.id.key_refine).visibility)
        assertEquals(R.drawable.ic_mic, shadowOf(mic.drawable).createdFromResId)
        assertEquals("Ruhiger Ring statt Puls", View.VISIBLE, root.findViewById<View>(R.id.mic_ring).visibility)
        assertNotEquals(View.VISIBLE, root.findViewById<View>(R.id.mic_pulse).visibility)
    }

    @Test fun festgestelltZeigtDasPauseSymbol() {
        feststellen()
        assertEquals(R.drawable.ic_pause, shadowOf(mic.drawable).createdFromResId)
        assertTrue(band.isActive)
    }

    @Test fun schonDasAufsetzenSetztFortUndDanachBleibtEsFestgestellt() {
        feststellen()
        mikro.sprechen(1_000)
        tippen()
        down()
        // Wer aus Gewohnheit haelt und spricht, verliert sonst die ersten Woerter.
        assertEquals(laeuft(1_000), statusText)
        assertTrue(band.isActive)
        up()
        assertEquals("Das Loslassen darf nicht gleich wieder pausieren", laeuft(1_000), statusText)
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_NONE, status.accessibilityLiveRegion)
        assertTrue(mic.contentDescription.contains("festgestellt"))
    }

    @Test fun dieUhrStehtInDerPauseUndZaehltDanachWeiter() {
        feststellen()
        mikro.sprechen(2_000)
        tippen()
        warten(5_000)
        assertEquals(pausiert(2_000), statusText)
        tippen()
        mikro.sprechen(1_000)
        warten(1_000)
        assertEquals("Ueber die Pause summiert", laeuft(3_000), statusText)
    }

    @Test fun sendenAusDerPauseUebertraegtDasGanzeDiktat() {
        feststellen()
        mikro.sprechen(1_000)
        tippen()
        send.performClick()
        assertEquals(app.getString(R.string.kb_transcribing), statusText)
        // Kaeme aus der Pause kein Audio an, ginge es als "zu kurz" still zurueck in die Ruhe.
        // Mit Audio scheitert es am toten Zugang — und das Audio bleibt fuer die Wiederholung.
        val bis = System.currentTimeMillis() + 10_000
        while (statusText == app.getString(R.string.kb_transcribing)) {
            if (System.currentTimeMillis() > bis) fail("Uebertragung endet nicht")
            Thread.sleep(10)
            idle()
        }
        assertNotEquals(app.getString(R.string.kb_hint_hold), statusText)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.key_retry).visibility)
    }

    @Test fun verwerfenAusDerPause() {
        feststellen()
        tippen()
        discard.performClick()
        assertEquals(app.getString(R.string.kb_discarded), statusText)
        assertEquals(View.GONE, send.visibility)
        assertEquals(app.getString(R.string.cd_mic), mic.contentDescription)
    }

    // --- Lebenszyklus ---------------------------------------------------------

    @Test fun neuaufbauInDerPauseBleibtPausiert() {
        feststellen()
        mikro.sprechen(1_000)
        tippen()
        aufbauen()
        assertEquals(pausiert(1_000), statusText)
        assertEquals(View.VISIBLE, send.visibility)
        assertTrue(send.isClickable)
        assertTrue(discard.isClickable)
        assertFalse(band.isActive)
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, status.accessibilityLiveRegion)
        assertEquals(app.getString(R.string.cd_mic_paused, zeit(1_000)), mic.contentDescription)
        assertEquals(R.drawable.ic_mic, shadowOf(mic.drawable).createdFromResId)
        tippen()
        assertEquals(laeuft(1_000), statusText)
    }

    @Test fun tastaturZuInDerPauseBehaeltDasDiktat() {
        feststellen()
        mikro.sprechen(1_000)
        tippen()
        service.onFinishInputView(false)
        service.onStartInputView(EditorInfo(), true)
        assertEquals(pausiert(1_000), statusText)
        assertTrue(send.isClickable)
        tippen()
        assertEquals("Weiter nach dem Wiederoeffnen", laeuft(1_000), statusText)
    }

    @Test fun feldwechselInDerPauseBehaeltDasDiktat() {
        feststellen()
        mikro.sprechen(1_000)
        tippen()
        service.onFinishInputView(true)
        service.onStartInputView(EditorInfo(), false)
        assertEquals(pausiert(1_000), statusText)
        send.performClick()
        assertEquals("Senden fuegt ins jetzt sichtbare Feld ein", app.getString(R.string.kb_transcribing), statusText)
    }

    @Test fun globusIstGesperrtSolangeEinDiktatOffenIst() {
        assertEquals(app.getString(R.string.cd_kb_switch), globe.contentDescription)
        feststellen()
        assertEquals(app.getString(R.string.cd_kb_switch_blocked), globe.contentDescription)
        globe.performClick()
        assertEquals(app.getString(R.string.kb_finish_first), statusText)

        tippen()
        globe.performClick()
        assertEquals(app.getString(R.string.kb_finish_first), statusText)
        // Die Quittung haelt nicht ewig: danach wieder der Pausen-Status.
        warten(3_000)
        assertEquals(pausiert(0), statusText)

        discard.performClick()
        assertEquals(app.getString(R.string.cd_kb_switch), globe.contentDescription)
        globe.performClick()
        assertEquals("Ohne Diktat waehlt der Globus wieder", app.getString(R.string.kb_discarded), statusText)
    }

    // --- Bedienungshilfen -----------------------------------------------------

    @Test fun talkBackStartPauseWeiterSenden() {
        mic.performClick()
        assertEquals(laeuft(0), statusText)
        mic.performClick()
        assertEquals(pausiert(0), statusText)
        mic.performClick()
        assertEquals(laeuft(0), statusText)
        aktionAusfuehren(R.string.cd_kb_send)
        assertEquals(app.getString(R.string.kb_transcribing), statusText)
        assertNull("Nach dem Senden keine Senden-Aktion mehr", aktion(R.string.cd_kb_send))
        idle()
    }

    @Test fun talkBackVerwerfenAusDerPause() {
        mic.performClick()
        mic.performClick()
        aktionAusfuehren(R.string.cd_kb_discard)
        assertEquals(app.getString(R.string.kb_discarded), statusText)
        assertNull(aktion(R.string.cd_kb_discard))
    }

    @Test fun ohneDiktatHatDieMikroTasteKeineAktionen() {
        assertNull(aktion(R.string.cd_kb_send))
        assertNull(aktion(R.string.cd_kb_discard))
        down()
        assertNull("Beim Halten liegt der Finger auf der Taste", aktion(R.string.cd_kb_send))
        up()
    }

    // --- Laengen-Schutz --------------------------------------------------------

    @Test fun abZehnMinutenHinweisBeiZwoelfAutomatischPausiert() {
        feststellen()
        mikro.sprechen(DictationSession.LONG_MS + 30_000)
        warten(1_000)
        assertEquals(
            app.getString(R.string.kb_long, zeit(DictationSession.LONG_MS + 30_000), zeit(DictationSession.MAX_MS)),
            statusText,
        )

        mikro.sprechen(DictationSession.MAX_MS - DictationSession.LONG_MS)
        warten(1_000)
        assertEquals(app.getString(R.string.kb_capped), statusText)
        assertFalse("Mikrofon muss frei sein", band.isActive)
        assertEquals(app.getString(R.string.cd_mic_capped), mic.contentDescription)

        // Weiter ist gesperrt — per Finger und per TalkBack.
        tippen()
        assertEquals(app.getString(R.string.kb_capped), statusText)
        mic.performClick()
        assertEquals(app.getString(R.string.kb_capped), statusText)
        assertFalse(band.isActive)
        assertTrue("Senden bleibt", send.isClickable)
        discard.performClick()
        assertEquals(app.getString(R.string.kb_discarded), statusText)
    }

    /**
     * Die Hoechstlaenge faellt zwischen zwei Takte, und der naechste Takt kommt erst mit dem Neuaufbau
     * (Drehen): der deckelt dann. Die gedeckelte Pause muss ihre Live-Region behalten — sonst sagt
     * TalkBack spaetere Statuswechsel nicht mehr an.
     */
    @Test fun gedeckeltBeimNeuaufbauBehaeltDieLiveRegion() {
        feststellen()
        mikro.sprechen(DictationSession.MAX_MS + 500)
        aufbauen()
        assertEquals(app.getString(R.string.kb_capped), statusText)
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, status.accessibilityLiveRegion)
        assertFalse(band.isActive)
    }

    // --- Erkennung in der Pause umgestellt (Zahnrad) -------------------------------------------

    /** Offline ueber die Hoechstlaenge, pausiert, dann auf Online: [feststellen] laeuft online an. */
    private fun offlineZuLangPausiertDannOnline() {
        feststellen()
        Prefs(app).engine = Engine.OFFLINE
        mikro.sprechen(DictationSession.MAX_MS + 60_000)
        tippen()
        assertEquals(pausiert(DictationSession.MAX_MS + 60_000), statusText)
        Prefs(app).engine = Engine.ONLINE
    }

    @Test fun zuLangFuerOnlineSendetNichtUndBleibtPausiert() {
        offlineZuLangPausiertDannOnline()

        send.performClick()

        // Der Anbieter lehnte die Datei mit 413 ab, nicht wiederholbar — das Audio waere weg.
        assertEquals(app.getString(R.string.kb_too_long), statusText)
        assertTrue("Senden bleibt", send.isClickable)
        assertTrue("Verwerfen bleibt", discard.isClickable)
        aufbauen()
        assertEquals("auch nach dem Wiederoeffnen", app.getString(R.string.kb_too_long), statusText)
    }

    @Test fun zuLangFuerOnlineSetztNichtFort() {
        offlineZuLangPausiertDannOnline()

        tippen()

        assertEquals(app.getString(R.string.kb_too_long), statusText)
        assertFalse("Mikrofon bleibt zu", band.isActive)
        // Zurueck auf Offline: weiter geht es wieder.
        Prefs(app).engine = Engine.OFFLINE
        tippen()
        assertEquals(laeuft(DictationSession.MAX_MS + 60_000), statusText)
    }

    @Test fun gedeckeltUndDannOfflineGehtEsWeiter() {
        feststellen()
        mikro.sprechen(DictationSession.MAX_MS)
        warten(1_000)
        assertEquals(app.getString(R.string.kb_capped), statusText)

        Prefs(app).engine = Engine.OFFLINE
        tippen()

        assertEquals(laeuft(DictationSession.MAX_MS), statusText)
        assertTrue(band.isActive)
    }

    @Test fun ohneZugangInDerPauseBleibtDasDiktatOffen() {
        feststellen()
        mikro.sprechen(1_000)
        tippen()
        Prefs(app).apiBaseUrl = ""

        send.performClick()

        assertEquals(app.getString(R.string.kb_not_configured), statusText)
        assertTrue("Diktat bleibt offen", send.isClickable)
        // Zugang wieder da: Senden geht (scheitert hier am toten Server, das Audio bleibt fuer die Wiederholung).
        Prefs(app).apiBaseUrl = "http://127.0.0.1:1/v1"
        send.performClick()
        assertEquals(app.getString(R.string.kb_transcribing), statusText)
        idle()
    }

    /** Ohne Pause (gehalten): der Zugang fehlt beim Senden — das Audio bleibt fuer "Erneut senden". */
    @Test fun ohneZugangBeimSendenBleibtDasAudio() {
        down()
        mikro.sprechen(1_000)
        Prefs(app).apiBaseUrl = ""
        up()

        val bis = System.currentTimeMillis() + 10_000
        while (statusText == app.getString(R.string.kb_transcribing)) {
            if (System.currentTimeMillis() > bis) fail("Uebertragung endet nicht")
            Thread.sleep(10)
            idle()
        }
        assertEquals(app.getString(R.string.kb_not_configured), statusText)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.key_retry).visibility)
    }

    @Test fun offlineGibtEsKeineHoechstlaenge() {
        feststellen()
        // Die Regel liest die Erkennung bei jedem Takt; offline rechnet whisper.cpp ohne Obergrenze.
        Prefs(app).engine = Engine.OFFLINE
        mikro.sprechen(DictationSession.MAX_MS + 1_000)
        warten(1_000)
        assertEquals(laeuft(DictationSession.MAX_MS + 1_000), statusText)
        assertTrue(band.isActive)
    }
}
