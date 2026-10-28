package com.chris.whisperloom.overlay

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import com.chris.whisperloom.llm.OfflineRefineFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
 * Ausblenden statt Beenden (UX-Spec §6.22): der Knopf verschwindet fuer 15 min, der Dienst laeuft
 * weiter, die Notification bietet "Einblenden". Ausgeloest per Bedienungshilfe-Aktion (das Ziehen
 * aufs Ziel prueft der Gerätetest); das Ziel selbst wird am Zug in IDLE geprueft.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingMicSnoozeTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val fixture = OfflineRefineFixture(app)
    private var controller: ServiceController<FloatingMicService>? = null
    private lateinit var shadowWm: ShadowWindowManagerImpl
    private lateinit var root: View
    private lateinit var bubble: View

    private val ausblenden get() = app.getString(R.string.cd_bubble_snooze)

    @Before fun aufbau() {
        fixture.setUp()
        ShadowSettings.setCanDrawOverlays(true)
        val c = Robolectric.buildService(FloatingMicService::class.java).create()
        controller = c
        shadowWm = Shadow.extract(c.get().getSystemService(Context.WINDOW_SERVICE))
        root = shadowWm.views.single()
        bubble = root.findViewById(R.id.bubble)
    }

    @After fun abbau() {
        controller?.destroy()
        fixture.tearDown()
    }

    private fun warten(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun aktion(label: String) =
        bubble.createAccessibilityNodeInfo().actionList.firstOrNull { it.label?.toString() == label }

    private fun ausblendenPerAktion() {
        val a = aktion(ausblenden) ?: return fail("Aktion „$ausblenden“ fehlt in IDLE")
        assertTrue(bubble.performAccessibilityAction(a.id, null))
        warten(0)
    }

    private fun notification(): Notification =
        shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(BubbleNotification.ID)

    private fun aktionen() = notification().actions.orEmpty().map { it.title.toString() }

    @Test fun ausblendenVersteckt15MinutenUndKommtZurueck() {
        ausblendenPerAktion()
        assertEquals(View.GONE, root.visibility)
        assertTrue("Dienst laeuft weiter", FloatingMicService.isRunning)
        assertEquals(listOf(app.getString(R.string.float_snooze_end), app.getString(R.string.float_stop)), aktionen())

        warten(FloatingMicService.SNOOZE_MS - 60_000)
        assertEquals(View.GONE, root.visibility)
        warten(61_000)
        assertEquals(View.VISIBLE, root.visibility)
        assertEquals(listOf(app.getString(R.string.float_stop)), aktionen())
    }

    @Test fun einblendenAusDerNotification() {
        ausblendenPerAktion()
        controller!!.withIntent(Intent(app, FloatingMicService::class.java).setAction(FloatingMicService.ACTION_SNOOZE_END))
            .startCommand(0, 1)
        warten(0)
        assertEquals(View.VISIBLE, root.visibility)
        assertTrue(FloatingMicService.isRunning)
    }

    /** Ohne Notification-Recht fehlt "Einblenden": ein erneuter Start aus der App blendet ein. */
    @Test fun erneuterStartBlendetEin() {
        ausblendenPerAktion()
        controller!!.withIntent(Intent(app, FloatingMicService::class.java)).startCommand(0, 2)
        warten(0)
        assertEquals(View.VISIBLE, root.visibility)
    }

    /** Auch im Tiefschlaf zaehlt die Zeit: die Frist haengt an elapsedRealtime, nicht an der Uptime. */
    @Test fun nachDemSchlafSofortZurueck() {
        ausblendenPerAktion()
        val start = SystemClock.elapsedRealtime()
        warten(FloatingMicService.SNOOZE_MS + 1_000)
        assertTrue(SystemClock.elapsedRealtime() - start > FloatingMicService.SNOOZE_MS)
        assertEquals(View.VISIBLE, root.visibility)
    }

    @Test fun ausblendenNurInRuhe() {
        assertNotNull(aktion(ausblenden))
        bubble.performClick()
        fixture.awaitRecorded()
        assertNull("nicht waehrend der Aufnahme", aktion(ausblenden))
    }

    private fun ziehen() {
        val now = SystemClock.uptimeMillis()
        bubble.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, 10f, 0))
        bubble.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, 200f, 300f, 0))
    }

    private fun zielLabel(): String? = shadowWm.views
        .mapNotNull { it.findViewById<TextView>(R.id.cancel_label) }
        .singleOrNull()?.text?.toString()

    @Test fun zielInRuheIstAusblenden() {
        ziehen()
        assertEquals(app.getString(R.string.float_snooze_label), zielLabel())
    }

    @Test fun zielInDerAufnahmeBleibtVerwerfen() {
        bubble.performClick()
        fixture.awaitRecorded()
        ziehen()
        assertEquals(app.getString(R.string.float_cancel_label), zielLabel())
    }
}
