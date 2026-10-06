package com.chris.whisperloom.ime

import android.app.Application
import android.inputmethodservice.InputMethodService
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.widget.ImageButton
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import com.chris.whisperloom.llm.LocalTextEngine
import com.chris.whisperloom.llm.OfflineRefineFixture
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
 * Der Ausweg in der Tastatur am echten Dienst: Offline-Diktat, die lokale Textverbesserung haengt
 * (Fake-Modell angehalten) — die Statuszeile sagt es, ein Tipp fuegt den erkannten Text sofort ohne
 * KI ein und bricht die Rechnung ab. Dazu: der Aufnahmestart waermt das Textmodell vor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImeRefineTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val fixture = OfflineRefineFixture(app)

    private lateinit var service: WhisperLoomInputMethodService
    private lateinit var root: View
    private lateinit var mic: ImageButton
    private val committed = mutableListOf<CharSequence>()

    private val status: TextView get() = root.findViewById(R.id.status)
    private val statusText: String get() = status.text.toString()

    @Before fun aufbau() {
        fixture.setUp()
        service = Robolectric.buildService(WhisperLoomInputMethodService::class.java).create().get()
        root = service.onCreateInputView()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(660, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        mic = root.findViewById(R.id.mic)
        // Ein Eingabefeld, das mitschreibt. Ohne gebundenes Feld ist currentInputConnection null und
        // der Dienst fuegt still nichts ein — dann bewiese der Test das Einfuegen nicht.
        val field = object : BaseInputConnection(root, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                committed += text
                return true
            }

            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = ""
        }
        InputMethodService::class.java.getDeclaredField("mStartedInputConnection").apply {
            isAccessible = true
            set(service, field)
        }
    }

    @After fun abbau() {
        fixture.tearDown()
        service.onFinishInputView(true)
        service.onDestroy()
    }

    private fun touch(action: Int) {
        val ev = MotionEvent.obtain(0L, 0L, action, mic.width / 2f, mic.height / 2f, 0)
        mic.dispatchTouchEvent(ev)
        ev.recycle()
    }

    /** Halten, aufnehmen, loslassen — bis die Uebertragung in der Textverbesserung steht. */
    private fun diktierenBisZurVerbesserung() {
        touch(MotionEvent.ACTION_DOWN)
        val model = fixture.holdWarmedModel()
        fixture.awaitRecorded()
        touch(MotionEvent.ACTION_UP)
        fixture.waitFor("Statuszeile zeigt die Textverbesserung nicht") {
            statusText == app.getString(R.string.kb_refining)
        }
        assertTrue("Rechnung laeuft", model.started.await(5, TimeUnit.SECONDS))
    }

    @Test fun aufnahmestartWaermtDasTextmodellVor() {
        touch(MotionEvent.ACTION_DOWN)
        fixture.holdWarmedModel() // wartet, bis geladen — waehrend die Aufnahme noch laeuft
        assertTrue(LocalTextEngine.isLoaded)
        assertEquals("noch keine Rechnung", 0, fixture.made.single().calls.size)
    }

    @Test fun tippAufDieStatuszeileFuegtDenTextOhneKiEin() {
        diktierenBisZurVerbesserung()
        assertTrue("Statuszeile ist das Tipp-Ziel", status.isClickable)
        assertEquals(app.getString(R.string.cd_mic_refining), mic.contentDescription)
        assertEquals("noch nichts eingefuegt", emptyList<CharSequence>(), committed)

        status.performClick()
        fixture.waitFor("Text ohne KI nicht eingefuegt") { committed.isNotEmpty() }

        assertEquals(listOf<CharSequence>("Also hallo welt "), committed)
        assertEquals("Ruhe-Hinweis statt Fehler — so gewollt", app.getString(R.string.kb_hint_hold), statusText)
        assertEquals(app.getString(R.string.cd_mic), mic.contentDescription)
        fixture.waitFor("lokale Rechnung nicht abgebrochen") { fixture.made.single().cancels == 1 }
        assertEquals("cancelProcess, nie close mitten in der Rechnung", 0, fixture.made.single().closes)
    }

    @Test fun tippAufDieMikroTasteIstDerselbeAusweg() {
        diktierenBisZurVerbesserung()
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_UP)
        fixture.waitFor("Text ohne KI nicht eingefuegt") { committed.isNotEmpty() }
        assertEquals(listOf<CharSequence>("Also hallo welt "), committed)
    }

    @Test fun ohneTippKommtDerVerbesserteText() {
        diktierenBisZurVerbesserung()
        fixture.made.single().proceed()
        fixture.waitFor("verbesserter Text nicht eingefuegt") { committed.isNotEmpty() }
        assertEquals(listOf<CharSequence>("Lokal verbessert. "), committed)
    }
}
