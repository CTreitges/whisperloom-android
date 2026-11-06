package com.chris.whisperloom.ui.settings

import android.app.Application
import android.content.Context
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.ime.WhisperLoomInputMethodService
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stufe der Textverbesserung zwischen Diktat-Tastatur und Einstellungen synchron (gemeldet
 * 04.11.2026 auf zwei Handys): beide laufen gleichzeitig, jede mit eigener [Prefs]-Instanz —
 * die Seite "Textverbesserung" am echten [PrefsState], die Tastatur am echten Dienst. Eine Wahl
 * auf der einen Seite muss die andere sofort zeigen, ohne dass sie neu aufgebaut wird.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class StageSyncTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var service: WhisperLoomInputMethodService
    private lateinit var root: View
    private lateinit var settings: PrefsState

    @Before fun aufbau() {
        app.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        Prefs(app).apply {
            engine = Engine.ONLINE
            sttProviderId = "custom"
            apiBaseUrl = "http://127.0.0.1:1/v1"
            llmModel = "qwen3:8b"
            promptLevelEnabled = true
            refineMode = RefineMode.POLISH
        }
        // Einstellungen: eigene Prefs-Instanz wie in MainActivity.
        settings = PrefsState(Prefs(app))
        val env = AppEnv(settings, SystemStatus()) { SystemStatus() }
        val nav = NavState(listOf(Screen.Home, Screen.SettingsHub, Screen.Refine))
        compose.setContent {
            WhisperLoomTheme { CompositionLocalProvider(LocalAppEnv provides env) { RefineScreen(nav) } }
        }
        compose.waitForIdle()

        // Tastatur: der echte Dienst, Leiste offen.
        service = Robolectric.buildService(WhisperLoomInputMethodService::class.java).create().get()
        root = service.onCreateInputView()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(660, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        root.findViewById<View>(R.id.key_refine).performClick()
        assertTrue("Leiste zeigt die gespeicherte Stufe", key(R.id.refine_polish).isSelected)
    }

    @After fun abbau() {
        service.onFinishInputView(true)
        service.onDestroy()
        settings.dispose()
    }

    private fun key(id: Int): View = root.findViewById(id)
    private fun zone(stufe: String) = compose.onNode(hasContentDescription("$stufe für Diktat verwenden"))

    @Test fun eineWahlInDerTastaturZeigenDieEinstellungenSofort() {
        key(R.id.refine_prompt).performClick()
        compose.waitForIdle()

        assertEquals(RefineMode.PROMPT, Prefs(app).refineMode)
        zone("Prompt").assertIsSelected()
        zone("Glätten").assertIsNotSelected()
    }

    @Test fun eineWahlInDenEinstellungenZeigtDieOffeneTastaturSofort() {
        zone("Zusammenfassen").performClick()
        compose.waitForIdle()

        assertEquals(RefineMode.SUMMARIZE, Prefs(app).refineMode)
        // Ohne Feldwechsel: die Leiste steht offen, waehrend in der App gewaehlt wird.
        assertTrue("Leiste zeigt die alte Stufe", key(R.id.refine_summarize).isSelected)
        assertFalse(key(R.id.refine_polish).isSelected)
    }

    @Test fun derPromptSchalterDerAppWirktAufDieOffeneLeiste() {
        assertEquals(View.VISIBLE, key(R.id.refine_prompt).visibility)
        settings.promptLevelEnabled = false
        compose.waitForIdle()
        assertEquals("Ausgeschaltete Stufe noch in der Leiste", View.GONE, key(R.id.refine_prompt).visibility)
    }

    @Test fun hinUndHerBleibenBeideGleich() {
        key(R.id.refine_beautify).performClick()
        compose.waitForIdle()
        zone("Verschönern").assertIsSelected()

        zone("Aus").performClick()
        compose.waitForIdle()
        assertTrue(key(R.id.refine_off).isSelected)

        key(R.id.refine_polish).performClick()
        compose.waitForIdle()
        zone("Glätten").assertIsSelected()
        zone("Aus").assertIsNotSelected()
    }
}
