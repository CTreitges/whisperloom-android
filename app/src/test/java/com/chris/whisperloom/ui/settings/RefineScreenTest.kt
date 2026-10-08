package com.chris.whisperloom.ui.settings

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isToggleable
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
import com.chris.whisperloom.SummarizeForm
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
 * je Stufe zwei Ziele (Text oeffnet die Stufen-Seite, Zone waehlt), auch bei "Aus", die Unterzeile
 * mit den Abweichungen, die Verweise auf Woerterbuch & Regeln und auf die Regel bei Offline-Erkennung.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class RefineScreenTest {

    private companion object {
        const val GLAETTEN = "Zeichensetzung und Groß-/Kleinschreibung. Inhalt unverändert."
        const val VERSCHOENERN = "Formuliert flüssiger und klarer, behält Inhalt, Ton und deine Wörter."
        const val ZUSAMMENFASSEN = "Kürzt auf das Wesentliche. Namen, Zahlen und Termine bleiben genau."
    }

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

    /** Text-Bereich einer Stufe (oeffnet ihre Seite). */
    private fun diktat(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(DICTATION_REFINE_TAG)))
    private fun sprachnachrichten(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(SHARE_REFINE_TAG)))

    /** Auswahl-Zone einer Stufe. */
    private fun zoneDiktat(stufe: String) = compose.onNode(hasContentDescription("$stufe für Diktat verwenden"))
    private fun zoneSprachnachrichten(stufe: String) = compose.onNode(hasContentDescription("$stufe für Sprachnachrichten verwenden"))

    private fun click(node: SemanticsNodeInteraction) {
        node.performClick()
        compose.waitForIdle()
    }

    // --- Zwei Gruppen ----------------------------------------------------------------------

    @Test fun diktatStehtUeberDenSprachnachrichten() {
        page()
        val reihenfolge = listOf("Beim Diktieren", "Wörterbuch & Regeln", "Bei geteilten Sprachnachrichten", "Bei Offline-Erkennung")
        val oben = reihenfolge.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals("Von oben nach unten: $reihenfolge", oben.sorted(), oben)
    }

    @Test fun dieStufeDesDiktatsLaesstDieSprachnachrichtenInRuhe() {
        page()
        click(zoneDiktat("Verschönern"))
        assertEquals(RefineMode.BEAUTIFY, Prefs(ctx).refineMode)
        assertEquals(RefineMode.OFF, Prefs(ctx).shareRefineMode)
        click(zoneSprachnachrichten("Zusammenfassen"))
        assertEquals(RefineMode.SUMMARIZE, Prefs(ctx).shareRefineMode)
        assertEquals(RefineMode.BEAUTIFY, Prefs(ctx).refineMode)
        zoneDiktat("Verschönern").assertIsSelected()
        zoneSprachnachrichten("Verschönern").assertIsNotSelected()
    }

    /** Die alten Schalter stehen jetzt auf den Stufen-Seiten. */
    @Test fun keineSchalterMehrAufDerSeite() {
        page()
        compose.onNode(isToggleable()).assertDoesNotExist()
        listOf("Lesbarer glätten", "Füllwörter intelligent entfernen", "Automatische Absätze").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
    }

    // --- Zwei Ziele je Stufe ------------------------------------------------------------------

    @Test fun derTextEinerStufeOeffnetIhreSeiteUndAendertNichts() {
        val nav = page()
        click(diktat("Glätten"))
        assertEquals(Screen.Stage(RefineMode.POLISH, RefineWay.DICTATION), nav.current)
        nav.pop()
        click(sprachnachrichten("Zusammenfassen"))
        assertEquals(Screen.Stage(RefineMode.SUMMARIZE, RefineWay.SHARE), nav.current)
        assertEquals(RefineMode.OFF, Prefs(ctx).refineMode)
        assertEquals(RefineMode.OFF, Prefs(ctx).shareRefineMode)
    }

    @Test fun promptHatSeineSeiteNurBeimDiktat() {
        prefs.promptLevelEnabled = true
        val nav = page()
        sprachnachrichten("Prompt").assertDoesNotExist()
        click(zoneDiktat("Prompt"))
        assertEquals(RefineMode.PROMPT, Prefs(ctx).refineMode)
        click(diktat("Prompt"))
        assertEquals(Screen.Stage(RefineMode.PROMPT, RefineWay.DICTATION), nav.current)
    }

    /** "Aus" wie jede Stufe: der Punkt waehlt, die Zeile oeffnet die Seite "Aus" — in beiden Gruppen. */
    @Test fun ausHatZweiZiele() {
        prefs.refineMode = RefineMode.POLISH
        prefs.shareRefineMode = RefineMode.SUMMARIZE
        val nav = page()
        diktat("Aus")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        click(diktat("Aus"))
        assertEquals(Screen.Stage(RefineMode.OFF, RefineWay.DICTATION), nav.current)
        nav.pop()
        click(sprachnachrichten("Aus"))
        assertEquals(Screen.Stage(RefineMode.OFF, RefineWay.SHARE), nav.current)
        nav.pop()
        assertEquals("die Zeile waehlt nicht", RefineMode.POLISH, Prefs(ctx).refineMode)
        assertEquals(RefineMode.SUMMARIZE, Prefs(ctx).shareRefineMode)

        click(zoneDiktat("Aus").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertIsNotSelected())
        assertEquals(RefineMode.OFF, Prefs(ctx).refineMode)
        assertEquals("kein Seitenwechsel", Screen.Refine, nav.current)
        zoneDiktat("Aus").assertIsSelected()
        zoneSprachnachrichten("Aus").assertIsNotSelected()
        click(zoneSprachnachrichten("Aus"))
        assertEquals(RefineMode.OFF, Prefs(ctx).shareRefineMode)
        zoneSprachnachrichten("Aus").assertIsSelected()
    }

    /** "x von y" fuer TalkBack: selectableGroup() zaehlte die Zonen nicht, also selbst gesetzt. */
    @Test fun listeninfoZaehltAusUndAlleStufen() {
        prefs.promptLevelEnabled = true
        page()
        val listen = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo)).fetchSemanticsNodes()
        assertEquals("Diktat mit Prompt, dann Sprachnachrichten", listOf(5, 4), listen.map { it.config[SemanticsProperties.CollectionInfo].rowCount })
        fun position(node: SemanticsNodeInteraction) = node.fetchSemanticsNode().config[SemanticsProperties.CollectionItemInfo].rowIndex
        assertEquals(0, position(zoneDiktat("Aus")))
        assertEquals(0, position(zoneSprachnachrichten("Aus")))
        assertEquals(1, position(zoneDiktat("Glätten")))
        assertEquals(4, position(zoneDiktat("Prompt")))
        assertEquals(3, position(zoneSprachnachrichten("Zusammenfassen")))
    }

    // --- Unterzeile: Kurzbeschreibung und Abweichungen ----------------------------------------

    /** Mit Abweichung entfaellt der Schlusspunkt der Kurzbeschreibung ("… genau · Fließtext"). */
    @Test fun unterzeileNenntWasVomStandardAbweicht() {
        prefs.setPolishCleanupFor(RefineWay.DICTATION, PolishCleanup.READABLE)
        prefs.setParagraphsFor(RefineMode.BEAUTIFY, false)
        prefs.setSummarizeFormFor(RefineWay.SHARE, SummarizeForm.PROSE)
        prefs.setPolishCleanupFor(RefineWay.SHARE, PolishCleanup.CLEAN)
        page()
        diktat("${GLAETTEN.dropLast(1)} · Lesbar").assertExists()
        diktat("${VERSCHOENERN.dropLast(1)} · Ohne Absätze").assertExists()
        diktat(ZUSAMMENFASSEN).assertExists()
        sprachnachrichten("${GLAETTEN.dropLast(1)} · Ohne Füllwörter").assertExists()
        // Sprachnachrichten sind immer gegliedert: kein "Ohne Absätze" bei ihnen.
        sprachnachrichten(VERSCHOENERN).assertExists()
        sprachnachrichten("${ZUSAMMENFASSEN.dropLast(1)} · Fließtext").assertExists()
    }

    @Test fun einEigenesModellStehtBeiBeidenWegen() {
        prefs.llmProviderId = "anthropic"
        prefs.llmKey = "sk-ant"
        prefs.setLlmModelFor(RefineMode.SUMMARIZE, "claude-opus-5-5")
        page()
        diktat("${ZUSAMMENFASSEN.dropLast(1)} · Claude Opus 5.5").assertExists()
        sprachnachrichten("${ZUSAMMENFASSEN.dropLast(1)} · Claude Opus 5.5").assertExists()
        // "Standard" ist keine Abweichung.
        diktat(GLAETTEN).assertExists()
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
