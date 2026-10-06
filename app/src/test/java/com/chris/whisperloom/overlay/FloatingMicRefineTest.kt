package com.chris.whisperloom.overlay

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import com.chris.whisperloom.llm.LocalTextEngine
import com.chris.whisperloom.llm.OfflineRefineFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.util.concurrent.TimeUnit

/**
 * Der Ausweg am schwebenden Knopf, am echten Dienst: Offline-Diktat, die lokale Textverbesserung
 * haengt (Fake-Modell angehalten) — das Label nennt den Ausweg, ein Tipp fuegt den erkannten Text
 * sofort ohne KI ein. Ohne Bedienungshilfe landet er in der Zwischenablage; dort wird er geprueft.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingMicRefineTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val fixture = OfflineRefineFixture(app)
    private lateinit var controller: ServiceController<FloatingMicService>
    private lateinit var bubble: View
    private lateinit var label: TextView

    @Before fun aufbau() {
        fixture.setUp()
        ShadowSettings.setCanDrawOverlays(true)
        controller = Robolectric.buildService(FloatingMicService::class.java).create()
        val wm = controller.get().getSystemService(Context.WINDOW_SERVICE)
        val root = Shadow.extract<ShadowWindowManagerImpl>(wm).views.single()
        bubble = root.findViewById(R.id.bubble)
        label = root.findViewById(R.id.bubble_label)
    }

    @After fun abbau() {
        fixture.tearDown()
        controller.destroy()
    }

    private fun clip(): String? =
        app.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()

    /** Tippen (Aufnahme), Tippen (senden) — bis die Uebertragung in der Textverbesserung steht. */
    private fun diktierenBisZurVerbesserung() {
        bubble.performClick()
        val model = fixture.holdWarmedModel() // der Aufnahmestart hat vorgewaermt
        assertTrue(LocalTextEngine.isLoaded)
        fixture.awaitRecorded()
        bubble.performClick()
        fixture.waitFor("Label zeigt die Textverbesserung nicht") {
            label.text.toString() == app.getString(R.string.float_refining)
        }
        assertTrue("Rechnung laeuft", model.started.await(5, TimeUnit.SECONDS))
    }

    @Test fun tippInDerVerbesserungFuegtDenTextOhneKiEin() {
        diktierenBisZurVerbesserung()
        assertEquals(app.getString(R.string.cd_bubble_refining), bubble.contentDescription)
        assertNull("noch nichts eingefuegt", clip())

        bubble.performClick()
        fixture.waitFor("Text ohne KI nicht eingefuegt") { clip() != null }

        assertEquals("Also hallo welt", clip())
        assertEquals(app.getString(R.string.cd_bubble_idle), bubble.contentDescription)
        fixture.waitFor("lokale Rechnung nicht abgebrochen") { fixture.made.single().cancels == 1 }
        assertEquals("cancelProcess, nie close mitten in der Rechnung", 0, fixture.made.single().closes)
    }

    @Test fun ohneTippKommtDerVerbesserteText() {
        diktierenBisZurVerbesserung()
        fixture.made.single().proceed()
        fixture.waitFor("verbesserter Text nicht eingefuegt") { clip() != null }
        assertEquals("Lokal verbessert.", clip())
    }
}
