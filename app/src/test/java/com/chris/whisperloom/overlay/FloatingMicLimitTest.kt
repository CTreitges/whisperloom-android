package com.chris.whisperloom.overlay

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.ime.DictationSession
import com.chris.whisperloom.llm.OfflineRefineFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
import org.robolectric.shadows.ShadowToast
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.time.Duration

/**
 * Hoechstdauer am schwebenden Knopf (UX-Spec §6.22): bei Online-Erkennung ab 10 min ein Hinweis,
 * bei 12 min sendet der Knopf von selbst — wie die IME, damit kein Diktat an der Grenze des
 * Anbieters (413) verloren geht. Offline gibt es keine Grenze.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingMicLimitTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val fixture = OfflineRefineFixture(app)
    private var controller: ServiceController<FloatingMicService>? = null
    private lateinit var bubble: View

    @Before fun aufbau() {
        fixture.setUp()
        ShadowSettings.setCanDrawOverlays(true)
    }

    @After fun abbau() {
        controller?.destroy()
        fixture.tearDown()
    }

    /** Konfigurierter Online-Zugang; die Erkennung bleibt der Fake der Fixture, ohne KI-Stufe. */
    private fun online() = Prefs(app).apply {
        engine = Engine.ONLINE
        sttProviderId = "custom"
        apiBaseUrl = "http://127.0.0.1:1/v1"
        refineMode = RefineMode.OFF
    }

    private fun starten() {
        val c = Robolectric.buildService(FloatingMicService::class.java).create()
        controller = c
        val wm = c.get().getSystemService(Context.WINDOW_SERVICE)
        bubble = Shadow.extract<ShadowWindowManagerImpl>(wm).views.single().findViewById(R.id.bubble)
        bubble.performClick()
        fixture.awaitRecorded()
    }

    private fun warten(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun zustand() = bubble.stateDescription?.toString()

    private fun clip(): String? =
        app.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()

    @Test fun onlineNach10MinutenHinweisNach12Gesendet() {
        online()
        starten()
        warten(DictationSession.LONG_MS - 1_000)
        assertNotEquals(app.getString(R.string.float_too_long_soon), ShadowToast.getTextOfLatestToast())

        warten(2_000)
        assertEquals(app.getString(R.string.float_too_long_soon), ShadowToast.getTextOfLatestToast())
        assertEquals(app.getString(R.string.cd_bubble_recording), zustand())

        warten(DictationSession.MAX_MS - DictationSession.LONG_MS)
        assertNotEquals("bei 12 min nicht mehr in der Aufnahme", app.getString(R.string.cd_bubble_recording), zustand())
        fixture.waitFor("Diktat an der Grenze nicht gesendet") { clip() != null }
        assertEquals("ohne KI, nur die Regeln ohne KI", "Also hallo welt", clip())
    }

    @Test fun hinweisNurEinmal() {
        online()
        starten()
        warten(DictationSession.LONG_MS + 1_000)
        val toasts = ShadowToast.shownToastCount()
        warten(60_000)
        assertEquals(toasts, ShadowToast.shownToastCount())
    }

    @Test fun offlineKeineGrenze() {
        starten()
        warten(DictationSession.MAX_MS + 60_000)
        assertEquals(app.getString(R.string.cd_bubble_recording), zustand())
        assertNotEquals(app.getString(R.string.float_too_long_soon), ShadowToast.getTextOfLatestToast())
    }
}
