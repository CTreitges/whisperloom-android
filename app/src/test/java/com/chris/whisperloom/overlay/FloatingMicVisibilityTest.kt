package com.chris.whisperloom.overlay

import android.app.Application
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import com.chris.whisperloom.a11y.FieldFocus
import com.chris.whisperloom.a11y.FieldKey
import com.chris.whisperloom.llm.OfflineRefineFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.time.Duration

/**
 * Der Dienst setzt [BubbleVisibility] um (UX-Spec §6.22): Knopf nur am Feld mit offener Tastatur,
 * waehrend der Aufnahme auch ohne, in Ruhe halb deckend und bei Beruehrung voll.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingMicVisibilityTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val fixture = OfflineRefineFixture(app)
    private var controller: ServiceController<FloatingMicService>? = null
    private lateinit var root: View
    private lateinit var bubble: View

    private val feld = FieldKey(windowId = 7, nodeId = 101)

    @Before fun aufbau() {
        fixture.setUp()
        ShadowSettings.setCanDrawOverlays(true)
        val c = Robolectric.buildService(FloatingMicService::class.java).create()
        controller = c
        val shadowWm: ShadowWindowManagerImpl = Shadow.extract(c.get().getSystemService(Context.WINDOW_SERVICE))
        root = shadowWm.views.single()
        bubble = root.findViewById(R.id.bubble)
    }

    @After fun abbau() {
        controller?.destroy()
        fixture.tearDown()
    }

    private fun warten(ms: Long = 0) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun melden(focus: FieldFocus?) {
        FloatingMicService.focusChanged(focus)
        warten()
    }

    private fun tastaturOffen() = melden(FieldFocus(feld, password = false, keyboardOpen = true))
    private fun tastaturZu() = melden(FieldFocus(feld, password = false, keyboardOpen = false))

    private fun touch(action: Int) {
        val now = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(now, now, action, 10f, 10f, 0)
        bubble.dispatchTouchEvent(e)
        e.recycle()
    }

    @Test fun n1_nachDemStartUnsichtbar() {
        assertEquals("ohne Meldung der Bedienungshilfe kein Knopf", View.GONE, root.visibility)
        assertTrue(FloatingMicService.isRunning)
    }

    @Test fun feldMitTastaturZeigt_tastaturZuVersteckt() {
        tastaturOffen()
        assertEquals(View.VISIBLE, root.visibility)
        tastaturZu()
        assertEquals(View.GONE, root.visibility)
    }

    @Test fun passwortfeldUndBedienungshilfeAusVerstecken() {
        melden(FieldFocus(feld, password = true, keyboardOpen = true))
        assertEquals(View.GONE, root.visibility)
        tastaturOffen()
        melden(null)
        assertEquals(View.GONE, root.visibility)
    }

    @Test fun n4_aufnahmeBleibtSichtbarBisZumEnde() {
        tastaturOffen()
        bubble.performClick()
        fixture.awaitRecorded()
        tastaturZu()
        assertEquals("Aufnahme laeuft: bleibt", View.VISIBLE, root.visibility)
        melden(null)
        assertEquals("auch ohne Bedienungshilfe: Mikrofon bleibt sichtbar", View.VISIBLE, root.visibility)
        bubble.performClick() // senden; der Fake-Clip ist kurz -> zurueck in Ruhe
        fixture.waitFor("Knopf wieder in Ruhe") {
            warten()
            root.visibility == View.GONE
        }
    }

    @Test fun ruheHalbBeruehrungVoll() {
        tastaturOffen()
        warten(500)
        assertEquals(BubbleVisibility.IDLE_ALPHA, root.alpha, 0.01f)
        touch(MotionEvent.ACTION_DOWN)
        warten(BubbleMotion.ALPHA_FADE_MS + 50) // unter der Haltezeit, sonst startet die Aufnahme
        assertEquals(1f, root.alpha, 0.01f)
        touch(MotionEvent.ACTION_CANCEL)
        warten(500)
        assertEquals(BubbleVisibility.IDLE_ALPHA, root.alpha, 0.01f)
    }

    @Test fun aufnahmeVollDeckend() {
        tastaturOffen()
        bubble.performClick()
        fixture.awaitRecorded()
        warten(500)
        assertEquals(1f, root.alpha, 0.01f)
    }

    @Test fun tastaturZuWaehrendDerBeruehrungVersteckt_erstNachDemLoslassen() {
        tastaturOffen()
        touch(MotionEvent.ACTION_DOWN)
        tastaturZu()
        assertEquals("mitten in der Geste nicht wegnehmen", View.VISIBLE, root.visibility)
        touch(MotionEvent.ACTION_CANCEL)
        warten()
        assertEquals(View.GONE, root.visibility)
    }
}
