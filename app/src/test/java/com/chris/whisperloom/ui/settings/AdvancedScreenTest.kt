package com.chris.whisperloom.ui.settings

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.ProFeature
import com.chris.whisperloom.R
import com.chris.whisperloom.agent.VoiceTaskState
import com.chris.whisperloom.agent.VoiceTaskStore
import com.chris.whisperloom.agent.VoiceTaskWidget
import com.chris.whisperloom.agent.VoiceTaskWork
import com.chris.whisperloom.agent.WidgetProfile
import com.chris.whisperloom.agent.WidgetProfileStore
import com.chris.whisperloom.ui.components.hasIllustration
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.nav.WidgetTab
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import com.chris.whisperloom.ui.tutorial.TutorialKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * E7 — "Erweitert": je Pro-Funktion ein Schalter, mit Pro Widgets die Wege ins Widget-Menue und
 * zur Anleitung. Server, Mikrofon und offener Auftrag stehen seit 3.7.1 im Tab "Pro Widgets" —
 * nur mit ausgeschalteten Pro Widgets laesst sich ein wartender Auftrag hier verwerfen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class AdvancedScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var nav: NavState

    @Before fun setUp() {
        ctx.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(WidgetProfileStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(VoiceTaskStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        VoiceTaskStore(ctx).clear()
    }

    private fun show(micGranted: Boolean = true) {
        nav = NavState(listOf(Screen.SettingsHub, Screen.Advanced))
        val status = SystemStatus(micGranted = micGranted)
        val env = AppEnv(PrefsState(Prefs(ctx)), status) { status }
        compose.setContent {
            WhisperLoomTheme {
                CompositionLocalProvider(LocalAppEnv provides env) { AdvancedScreen(nav) }
            }
        }
        compose.waitForIdle()
    }

    private fun click(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    private fun offenerAuftrag() = VoiceTaskStore(ctx).apply {
        begin(FloatArray(800) { 0.3f }, 4000, "2026-09-21T20:00:00Z", WidgetProfile.DEFAULT_ID)
        state = VoiceTaskState.ERROR
    }

    @Test fun derScreenHeisstErweitertUndErklaertSichKurz() {
        show()
        compose.onNodeWithText("Erweitert").assertExists()
        compose.onNodeWithText("Pro-Funktionen für Entwickler und Bastler. Für das normale Diktieren brauchst du hier nichts.")
            .assertIsDisplayed()
        compose.onNode(hasIllustration(R.drawable.ill_pro_features, ctx.getString(R.string.img_pro_features))).assertExists()
    }

    @Test fun jedeProFunktionHatIhrenSchalterUndIstZuerstAus() {
        show()
        compose.onNodeWithText("Pro-Funktionen").assertExists()
        ProFeature.entries.forEach {
            compose.onNodeWithText(ctx.getString(it.title)).assertIsDisplayed().assertIsOff()
            compose.onNodeWithText(ctx.getString(it.sub)).assertExists()
            assertFalse(it.name, Prefs(ctx).isEnabled(it))
        }
        compose.onNodeWithText("Pro Widgets").assertExists()
        compose.onNodeWithText("Stufe „Prompt“ anbieten").assertExists()
    }

    @Test fun derSchalterProWidgetsSchreibtSofortDurch() {
        show()
        click("Pro Widgets")
        assertTrue("Kein Speichern-Knopf — die Zuweisung schreibt direkt", Prefs(ctx).proWidgetsEnabled)
        assertFalse("Nur die eine Funktion", Prefs(ctx).promptLevelEnabled)
    }

    @Test fun derSchalterPromptSchreibtSofortDurch() {
        show()
        click("Stufe „Prompt“ anbieten")
        assertTrue(Prefs(ctx).promptLevelEnabled)
        assertFalse(Prefs(ctx).proWidgetsEnabled)
    }

    @Test fun dieWegeZuDenProWidgetsErscheinenErstMitDemSchalter() {
        show()
        compose.onNodeWithText("Pro Widgets verwalten").assertDoesNotExist()
        compose.onNodeWithText("Anleitung Pro Widgets").assertDoesNotExist()

        click("Pro Widgets")

        compose.onNodeWithText("Pro Widgets verwalten").assertIsDisplayed()
        compose.onNodeWithText("Anleitung Pro Widgets").assertIsDisplayed()
    }

    @Test fun verwaltenFuehrtInDenTabProWidgets() {
        Prefs(ctx).proWidgetsEnabled = true
        show()
        click("Pro Widgets verwalten")
        assertEquals(Screen.Widgets(WidgetTab.PRO), nav.current)
    }

    @Test fun dieAnleitungFuehrtInsTutorialProWidgets() {
        Prefs(ctx).proWidgetsEnabled = true
        show()
        click("Anleitung Pro Widgets")
        assertEquals(Screen.Tutorial(kind = TutorialKind.PRO_WIDGETS), nav.current)
    }

    @Test fun serverMikrofonUndOffenerAuftragStehenNichtMehrHier() {
        // Jedes Widget hat seinen eigenen Server — ein globales Feld waere eine zweite, falsche Wahrheit.
        // Mikrofon und offener Auftrag gehoeren zu den Widgets und stehen im Tab "Pro Widgets".
        Prefs(ctx).proWidgetsEnabled = true
        VoiceTaskStore(ctx).apply {
            begin(FloatArray(800) { 0.3f }, 4000, "2026-09-21T20:00:00Z", WidgetProfile.DEFAULT_ID)
            state = VoiceTaskState.WORKING
        }
        show(micGranted = false)
        listOf(
            "Server-Adresse", "Token", "Verbindung prüfen",
            "Für Pro Widgets fehlt die Mikrofon-Berechtigung.", "Offenen Auftrag verwerfen",
        ).forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }

    @Test fun mitProWidgetsAusLaesstSichEinOffenerAuftragHierVerwerfen() {
        // Ohne Pro Widgets fehlt der Tab "Pro Widgets" — bis 3.7.0 ging das Verwerfen immer.
        offenerAuftrag()
        var abgebrochen = 0
        val echtesCancel = VoiceTaskWork.cancelImpl
        VoiceTaskWork.cancelImpl = { abgebrochen++ }
        try {
            show()
            compose.onNodeWithText("Offener Auftrag").assertIsDisplayed()
            click("Offenen Auftrag verwerfen")
        } finally {
            VoiceTaskWork.cancelImpl = echtesCancel
        }
        assertEquals(1, abgebrochen)
        assertFalse("Der Auftrag muss wirklich weg sein", VoiceTaskStore(ctx).hasWork)
        compose.onNodeWithText("Offenen Auftrag verwerfen").assertDoesNotExist()
    }

    @Test fun ohneOffenenAuftragGibtEsHierNichtsZuVerwerfen() {
        show()
        compose.onNodeWithText("Offener Auftrag").assertDoesNotExist()
        compose.onNodeWithText("Offenen Auftrag verwerfen").assertDoesNotExist()
    }

    @Test fun mitProWidgetsAnStehtDerOffeneAuftragWiederNurImTab() {
        offenerAuftrag()
        show()
        compose.onNodeWithText("Offenen Auftrag verwerfen").assertExists()

        click("Pro Widgets")

        compose.onNodeWithText("Offenen Auftrag verwerfen").assertDoesNotExist()
        assertTrue("Einschalten verwirft nichts", VoiceTaskStore(ctx).hasWork)
    }

    @Test fun einAuftragAusDerZwischenzeitErscheintBeimAusschaltenDerProWidgets() {
        // "Erweitert" bleibt offen (ein Deep-Link mit gleichem Screen haelt die Composition), der
        // Auftrag entsteht im Hintergrund — beim Ausschalten muss die Karte trotzdem erscheinen.
        Prefs(ctx).proWidgetsEnabled = true
        show()
        offenerAuftrag()

        click("Pro Widgets")

        compose.onNodeWithText("Offener Auftrag").assertIsDisplayed()
        compose.onNodeWithText("Offenen Auftrag verwerfen").assertExists()
    }

    @Test fun einAuftragAusDerZwischenzeitErscheintNachDerRueckkehrInDieApp() {
        show()
        compose.onNodeWithText("Offener Auftrag").assertDoesNotExist()
        offenerAuftrag()

        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        compose.onNodeWithText("Offener Auftrag").assertIsDisplayed()
        compose.onNodeWithText("Offenen Auftrag verwerfen").assertExists()
    }

    @Test fun umschaltenZeichnetDieWidgetsNeu() {
        shadowOf(ctx as Application).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        val manager = AppWidgetManager.getInstance(ctx)
        val id = shadowOf(manager).createWidget(VoiceTaskWidget::class.java, R.layout.widget_task)
        fun zeile() = shadowOf(manager).getViewFor(id).findViewById<TextView>(R.id.widget_status).text.toString()
        show()
        assertEquals(ctx.getString(R.string.widget_off), zeile())

        click("Pro Widgets")

        assertEquals("Das Standardprofil hat noch keinen Server", ctx.getString(R.string.widget_no_server), zeile())
    }
}
