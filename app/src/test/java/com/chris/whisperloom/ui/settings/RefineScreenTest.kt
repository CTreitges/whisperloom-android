package com.chris.whisperloom.ui.settings

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Engine
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.RefineWay
import com.chris.whisperloom.PolishCleanup
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Seite "Textverbesserung" (3.9.0): beide Stufen-Gruppen auf einer Seite und unabhaengig voneinander,
 * die Verweise auf Woerterbuch & Regeln und auf die Regel bei Offline-Erkennung.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class RefineScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs

    /** Offline geht, ein Textmodell passt (16 GB). */
    private val status = SystemStatus(installedModels = setOf("small"), totalRamBytes = 16L shl 30)

    @Before fun setUp() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
        prefs.engine = Engine.ONLINE
        prefs.apiKey = "sk-test"
    }

    private fun page(s: SystemStatus = status): NavState {
        val env = AppEnv(PrefsState(prefs), s) { s }
        val nav = NavState(listOf(Screen.Home, Screen.SettingsHub, Screen.Refine))
        compose.setContent {
            WhisperLoomTheme { CompositionLocalProvider(LocalAppEnv provides env) { RefineScreen(nav) } }
        }
        compose.waitForIdle()
        return nav
    }

    private fun diktat(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(DICTATION_REFINE_TAG)))
    private fun sprachnachrichten(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(SHARE_REFINE_TAG)))

    private fun click(node: SemanticsNodeInteraction) {
        node.performClick()
        compose.waitForIdle()
    }

    // --- Zwei Gruppen ----------------------------------------------------------------------

    @Test fun diktatStehtUeberDenSprachnachrichten() {
        page()
        val reihenfolge = listOf("Beim Diktieren", "Automatische Absätze", "Bei geteilten Sprachnachrichten", "Bei Offline-Erkennung")
        val oben = reihenfolge.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals("Von oben nach unten: $reihenfolge", oben.sorted(), oben)
    }

    @Test fun dieStufeDesDiktatsLaesstDieSprachnachrichtenInRuhe() {
        page()
        click(diktat("Verschönern"))
        assertEquals(RefineMode.BEAUTIFY, Prefs(ctx).refineMode)
        assertEquals(RefineMode.OFF, Prefs(ctx).shareRefineMode)
        click(sprachnachrichten("Zusammenfassen"))
        assertEquals(RefineMode.SUMMARIZE, Prefs(ctx).shareRefineMode)
        assertEquals(RefineMode.BEAUTIFY, Prefs(ctx).refineMode)
    }

    @Test fun lesbarerGlaettenDerSprachnachrichtenWirktNurDort() {
        prefs.refineMode = RefineMode.POLISH
        prefs.shareRefineMode = RefineMode.POLISH
        page()
        click(sprachnachrichten("Lesbarer glätten"))
        val p = Prefs(ctx)
        assertEquals(PolishCleanup.READABLE, p.polishCleanupFor(RefineWay.SHARE))
        assertEquals(PolishCleanup.PLAIN, p.polishCleanupFor(RefineWay.DICTATION))
        assertEquals(RefineMode.READABLE, p.refinementFor(RefineWay.SHARE).mode)
        assertEquals(RefineMode.POLISH, p.refinementFor(RefineWay.DICTATION).mode)
    }

    @Test fun lesbarerGlaettenDerSprachnachrichtenNenntIhreStufeGlaetten() {
        prefs.refineMode = RefineMode.POLISH // die Diktat-Stufe zaehlt hier nicht
        prefs.shareRefineMode = RefineMode.SUMMARIZE
        page()
        sprachnachrichten("Lesbarer glätten").assertIsEnabled()
        sprachnachrichten("Wirkt mit der Stufe „Glätten“.").assertExists()
        diktat("Wirkt mit der Stufe „Glätten“.").assertDoesNotExist()
        click(sprachnachrichten("Glätten"))
        assertEquals(RefineMode.POLISH, Prefs(ctx).shareRefineMode)
        sprachnachrichten("Wirkt mit der Stufe „Glätten“.").assertDoesNotExist()
    }

    /** Nach dem Update auf 3.8.6 (v5 uebernimmt den alten Wert) stand der Schalter bei anderer Stufe an und war gesperrt. */
    @Test fun lesbarerGlaettenDerSprachnachrichtenLaesstSichOhneGlaettenAbschalten() {
        prefs.shareRefineMode = RefineMode.SUMMARIZE
        prefs.setPolishCleanupFor(RefineWay.SHARE, PolishCleanup.READABLE)
        page()
        sprachnachrichten("Wirkt mit der Stufe „Glätten“.").assertExists()
        click(sprachnachrichten("Lesbarer glätten"))
        assertEquals(PolishCleanup.PLAIN, Prefs(ctx).polishCleanupFor(RefineWay.SHARE))
    }

    @Test fun lesbarerGlaettenDesDiktatsLaesstSichOhneGlaettenAbschalten() {
        prefs.refineMode = RefineMode.BEAUTIFY
        prefs.setPolishCleanupFor(RefineWay.DICTATION, PolishCleanup.READABLE)
        page()
        click(diktat("Lesbarer glätten"))
        assertEquals(PolishCleanup.PLAIN, Prefs(ctx).polishCleanupFor(RefineWay.DICTATION))
    }

    @Test fun lesbarerGlaettenDesDiktatsLaesstDieSprachnachrichtenInRuhe() {
        prefs.refineMode = RefineMode.POLISH
        prefs.shareRefineMode = RefineMode.POLISH
        prefs.setPolishCleanupFor(RefineWay.SHARE, PolishCleanup.READABLE)
        page()
        click(diktat("Lesbarer glätten"))
        assertEquals(PolishCleanup.READABLE, Prefs(ctx).polishCleanupFor(RefineWay.DICTATION))
        assertEquals(PolishCleanup.READABLE, Prefs(ctx).polishCleanupFor(RefineWay.SHARE))
        click(diktat("Lesbarer glätten"))
        assertEquals(PolishCleanup.PLAIN, Prefs(ctx).polishCleanupFor(RefineWay.DICTATION))
        assertEquals(PolishCleanup.READABLE, Prefs(ctx).polishCleanupFor(RefineWay.SHARE))
    }

    // --- Verweise ---------------------------------------------------------------------------

    @Test fun unterDemDiktatGehtEsZuWoerterbuchUndRegeln() {
        prefs.removeFillers = true
        prefs.autoCapitalize = false
        val nav = page()
        diktat("Noch keine Begriffe · Füllwörter").assertExists()
        click(diktat("Wörterbuch & Regeln"))
        assertEquals(Screen.Dictionary, nav.current)
    }

    @Test fun beiOfflineErkennungNenntDieRegelUndFuehrtZuDenOfflineModellen() {
        val nav = page()
        compose.onNodeWithText("Lokales Textmodell · Textmodell fehlt").assertExists()
        click(compose.onNodeWithText("Bei Offline-Erkennung"))
        assertEquals(Screen.Models, nav.current)
    }

    @Test fun beiOfflineErkennungMitGeladenemTextmodell() {
        prefs.localLlmModel = "gemma4_e2b"
        page(status.copy(installedTextModels = setOf("gemma4_e2b")))
        compose.onNodeWithText("Lokales Textmodell · Gemma 4 E2B").assertExists()
    }

    @Test fun beiOfflineErkennungUeberspringen() {
        prefs.offlineRefine = OfflineRefineRule.SKIP
        page()
        compose.onNodeWithText("Überspringen").assertExists()
    }

    @Test fun ohneOfflineErkennungAufDemGeraetFehltDieZeile() {
        page(SystemStatus(offlineSupported = false))
        compose.onNodeWithText("Bei Offline-Erkennung").assertDoesNotExist()
        compose.onNode(hasTestTag(SHARE_REFINE_TAG)).assertExists()
    }
}
