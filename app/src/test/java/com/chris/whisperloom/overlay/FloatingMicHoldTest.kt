package com.chris.whisperloom.overlay

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.llm.OfflineRefineFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
 * Halten zum Sprechen am echten, vom Dienst verdrahteten Knopf (UX-Spec §6.21): Touch-Ereignisse
 * statt performClick, die Halte-Zeit ueber den Main-Looper.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingMicHoldTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val fixture = OfflineRefineFixture(app)
    private var controller: ServiceController<FloatingMicService>? = null
    private lateinit var bubble: View
    private val holdMs = ViewConfiguration.getLongPressTimeout().toLong()

    @Before fun aufbau() {
        fixture.setUp()
        ShadowSettings.setCanDrawOverlays(true)
    }

    @After fun abbau() {
        controller?.destroy()
        fixture.tearDown()
    }

    private fun starten() {
        val c = Robolectric.buildService(FloatingMicService::class.java).create()
        controller = c
        val wm = c.get().getSystemService(Context.WINDOW_SERVICE)
        bubble = Shadow.extract<ShadowWindowManagerImpl>(wm).views.single().findViewById(R.id.bubble)
    }

    private fun touch(action: Int, x: Float = 10f, y: Float = 10f) {
        val now = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(now, now, action, x, y, 0)
        bubble.dispatchTouchEvent(e)
        e.recycle()
    }

    private fun warten(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun zustand() = bubble.stateDescription?.toString()

    private fun clip(): String? =
        app.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()

    @Test fun haltenStartetDieAufnahmeUndLoslassenSendet() {
        starten()
        touch(MotionEvent.ACTION_DOWN)
        warten(holdMs + 50)
        assertEquals(app.getString(R.string.cd_bubble_recording), zustand())
        fixture.awaitRecorded()

        touch(MotionEvent.ACTION_UP)
        fixture.waitFor("Gehaltenes Diktat nicht gesendet") { clip() != null }
        assertEquals("Lokal verbessert.", clip())
    }

    @Test fun abgefangenerTouchImHaltenSendet() {
        starten()
        touch(MotionEvent.ACTION_DOWN)
        warten(holdMs + 50)
        fixture.awaitRecorded()
        touch(MotionEvent.ACTION_CANCEL)
        fixture.waitFor("Diktat beim ACTION_CANCEL verloren") { clip() != null }
    }

    @Test fun kurzerTippStartetZweiterSendet() {
        starten()
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_UP)
        warten(0)
        assertEquals(app.getString(R.string.cd_bubble_recording), zustand())
        fixture.awaitRecorded()
        warten(holdMs + 50) // die Halte-Zeit des ersten Drucks laeuft ins Leere
        assertEquals(app.getString(R.string.cd_bubble_recording), zustand())

        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_UP)
        fixture.waitFor("Zweiter Tipp hat nicht gesendet") { clip() != null }
    }

    @Test fun bewegungVorDerHaltezeitVerschiebtNur() {
        starten()
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_MOVE, x = 200f, y = 200f)
        warten(holdMs + 50)
        assertEquals(app.getString(R.string.cd_bubble_idle), zustand())
        touch(MotionEvent.ACTION_UP, x = 200f, y = 200f)
        warten(0)
        assertEquals(app.getString(R.string.cd_bubble_idle), zustand())
        assertNull(clip())
    }

    @Test fun schalterAusHaltenStartetNichts() {
        Prefs(app).holdToTalk = false
        starten()
        touch(MotionEvent.ACTION_DOWN)
        warten(holdMs * 2)
        assertEquals(app.getString(R.string.cd_bubble_idle), zustand())
    }

    @Test fun haltenIstAbWerkAn() {
        assertEquals(true, Prefs(app).holdToTalk)
    }
}
