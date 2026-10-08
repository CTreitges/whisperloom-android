package com.chris.whisperloom.overlay

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import com.chris.whisperloom.a11y.TextInserterAccessibilityService
import com.chris.whisperloom.history.History
import com.chris.whisperloom.history.HistorySource
import com.chris.whisperloom.history.Processing
import com.chris.whisperloom.llm.OfflineRefineFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/**
 * Der schwebende Knopf schreibt jedes Diktat VOR dem Einfuegen in den Verlauf — nie, wenn das
 * Fokusfeld der Bedienungshilfe ein Passwortfeld ist. Ohne Bedienungshilfe landet der Text in der
 * Zwischenablage (Ziel unbekannt) und kommt in den Verlauf.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingMicHistoryTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val fixture = OfflineRefineFixture(app)
    private val originalTarget = FloatingMicService.targetIsPassword
    private lateinit var controller: ServiceController<FloatingMicService>
    private lateinit var bubble: View

    @Before fun aufbau() {
        fixture.setUp()
        History.clear(app)
        ShadowSettings.setCanDrawOverlays(true)
        controller = Robolectric.buildService(FloatingMicService::class.java).create()
        val wm = controller.get().getSystemService(Context.WINDOW_SERVICE)
        bubble = Shadow.extract<ShadowWindowManagerImpl>(wm).views.single().findViewById(R.id.bubble)
    }

    @After fun abbau() {
        FloatingMicService.targetIsPassword = originalTarget
        fixture.tearDown()
        controller.destroy()
        History.clear(app)
    }

    private fun clip(): String? =
        app.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()

    /** Tippen (Aufnahme), Tippen (senden) — bis der Text in der Zwischenablage liegt. */
    private fun diktieren() {
        bubble.performClick()
        fixture.awaitRecorded()
        bubble.performClick()
        fixture.waitFor("Text nicht eingefuegt") { clip() != null }
    }

    @Test fun diktatLandetImVerlauf() {
        diktieren()

        val entry = History.list(app).single()
        assertEquals(HistorySource.BUBBLE, entry.source)
        assertEquals("also ähm hallo welt", entry.raw)
        assertEquals("Lokal verbessert.", entry.versions.getValue(Processing.POLISH_PLAIN).text)
    }

    @Test fun ausDemPasswortfeldNicht() {
        FloatingMicService.targetIsPassword = { true }
        diktieren()
        assertEquals("eingefuegt wird trotzdem", "Lokal verbessert.", clip())
        assertEquals(0, History.count(app))
    }

    @Test fun verlaufAusSchreibtNichts() {
        History.setEnabled(app, false)
        diktieren()
        assertEquals(0, History.count(app))
    }

    @Test fun passwortfeldLautKnotenOderEingabetyp() {
        fun knoten(password: Boolean, inputType: Int) = AccessibilityNodeInfo().apply {
            isPassword = password
            this.inputType = inputType
        }
        val text = InputType.TYPE_CLASS_TEXT
        assertTrue(TextInserterAccessibilityService.isPassword(knoten(true, text)))
        assertTrue(TextInserterAccessibilityService.isPassword(knoten(false, text or InputType.TYPE_TEXT_VARIATION_PASSWORD)))
        assertFalse(TextInserterAccessibilityService.isPassword(knoten(false, text)))
        assertFalse(TextInserterAccessibilityService.isPassword(knoten(false, text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)))
    }

    @Test fun ohneBedienungshilfeIstDasZielUnbekannt() {
        assertFalse(TextInserterAccessibilityService.focusedIsPassword())
    }
}
