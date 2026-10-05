package com.chris.whisperloom.ui.patchnotes

import android.content.res.AssetManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.chris.whisperloom.BuildConfig
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Patchnotes-Screen gegen die echten Assets (Robolectric, Semantik). Hohes Fenster, damit die LazyColumn
 * alle Gruppen komponiert. Inhalte, die sich mit jedem Release verschieben (die neueste Version im Hero),
 * prueft der Test ueber BuildConfig; feste Inhalte ueber eine Liste, die bei 3.8.1 beginnt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class PatchnotesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(load: (AssetManager) -> List<Release> = PatchnotesLoader::load): NavState {
        val nav = NavState(listOf(Screen.Home, Screen.Patchnotes))
        compose.setContent { WhisperLoomTheme { PatchnotesScreen(nav, load) } }
        // Laden laeuft auf Dispatchers.IO: warten, bis Inhalt oder Fehlerzustand da ist.
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription("Was ist neu in Version", substring = true)).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Patchnotes nicht verfügbar").fetchSemanticsNodes().isNotEmpty()
        }
        return nav
    }

    /** Echte Assets, aber 3.8.1 oben — der Hero-Inhalt bleibt so auch nach dem naechsten Release gleich. */
    private fun ab381() = show { assets -> PatchnotesLoader.load(assets).dropWhile { it.version != "3.8.1" } }

    private fun zustand(wert: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, wert)

    /** Die Liste ist lazy: weiter unten liegende Gruppen erst in den Sichtbereich holen. */
    private fun scrollZu(text: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        compose.waitForIdle()
    }

    private fun oben(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    @Test fun heroZeigtDieInstallierteVersion() {
        show()
        compose.onNodeWithContentDescription("Was ist neu in Version ${BuildConfig.VERSION_NAME}").assert(isHeading())
        compose.onNodeWithText("Installiert").assertExists()
        compose.onNodeWithText("Das Wichtigste").assertExists()
    }

    @Test fun heroZeigtDieVierHighlights() {
        ab381()
        compose.onNodeWithText("5. Oktober 2026").assertExists()
        listOf(
            "„Lesbarer glätten“ – Glätten repariert auf Wunsch auch holprige Sätze, deine Wörter und dein Ton bleiben",
            "Neue Anweisungen für Glätten, Verschönern und Zusammenfassen",
            "Diktierte Fragen und Bitten werden bearbeitet, nicht beantwortet",
            "Glätten setzt kein Fragezeichen mehr hinter Aussagen",
        ).forEach { compose.onNodeWithText(it).assertExists() }
        // Details erst nach dem Aufklappen.
        compose.onNodeWithContentDescription("Neu, 1 Punkt").assertDoesNotExist()
    }

    @Test fun heroKlapptAlleAenderungenAuf() {
        ab381()
        val toggle = compose.onNodeWithText("Alle 5 Änderungen im Detail")
        toggle.assert(zustand("zugeklappt"))
        toggle.performClick()
        compose.waitForIdle()
        listOf("Neu, 1 Punkt", "Geändert, 2 Punkte", "Behoben, 2 Punkte").forEach {
            compose.onNodeWithContentDescription(it).assertExists().assert(isHeading())
        }
        compose.onNodeWithText("Weniger anzeigen").assert(zustand("aufgeklappt"))
        compose.onNodeWithText("„Lesbarer glätten“").assertExists()
    }

    @Test fun zeile370KlapptAufUndWiederZu() {
        show()
        val zeile = compose.onNodeWithText("Version 3.7.0")
        zeile.assert(zustand("zugeklappt")).assert(isHeading())
        zeile.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ein gescheiterter Versuch ist jetzt sichtbar", substring = true).assertExists()
        zeile.assert(zustand("aufgeklappt"))
        zeile.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ein gescheiterter Versuch ist jetzt sichtbar", substring = true).assertDoesNotExist()
    }

    @Test fun version300ZeigtBekanntePunkteAberKeineTechnik() {
        show()
        scrollZu("Version 3.0.0")
        // Zugeklappt traegt die Zeile das Intro als Einzeiler ...
        compose.onAllNodesWithText("Komplett neue Oberfläche", substring = true).assertCountEquals(1)
        compose.onNodeWithText("Version 3.0.0").performClick()
        compose.waitForIdle()
        // ... aufgeklappt steht es nur noch einmal da, im Inhalt (vorher doppelt).
        compose.onAllNodesWithText("Komplett neue Oberfläche", substring = true).assertCountEquals(1)
        compose.onNodeWithContentDescription("Entfernt, 3 Punkte").assertExists()
        compose.onNodeWithContentDescription("Bekannte Punkte, 2 Punkte").assertExists()
        compose.onNodeWithContentDescription("Technik", substring = true).assertDoesNotExist()
        compose.onNodeWithText("AGP 9.4.0", substring = true).assertDoesNotExist()
    }

    @Test fun altversionenStehenUnterVorVersion3() {
        show()
        scrollZu("VOR VERSION 3")
        compose.onNodeWithText("Die erste App-Generation – manches gibt es so nicht mehr.").assertExists()
        val reihenfolge = listOf("Version 3.0.0", "VOR VERSION 3", "Version 2.1.0", "Version 2.0.0", "Version 1.0")
        val y = reihenfolge.map(::oben)
        assertEquals("Von oben nach unten: $reihenfolge", y.sorted(), y)
        assertEquals("Keine zwei auf einer Hoehe", y.size, y.toSet().size)
        // Teaser aus dem ersten Eintrag (2.0.0 hat weder Intro noch Highlights).
        compose.onNodeWithText("19. August 2026 · Breaking: nur noch API-Betrieb").assertExists()
    }

    @Test fun ueberschriftenFuerDieNavigationPerTalkBack() {
        show()
        compose.onNodeWithContentDescription("Was ist neu in Version ${BuildConfig.VERSION_NAME}").assert(isHeading())
        compose.onNodeWithText("Das Wichtigste").assert(isHeading())
        // Gruppenkoepfe: sichtbar in Grossbuchstaben, vorgelesen in normaler Schreibung.
        compose.onNodeWithContentDescription("Frühere Versionen").assert(isHeading())
        scrollZu("VOR VERSION 3")
        compose.onNodeWithContentDescription("Vor Version 3").assert(isHeading())
        assertTrue(oben("Version 3.0.0") < oben("VOR VERSION 3"))
    }

    @Test fun ohneAssetsHinweisUndWegNachGithub() {
        show { throw IOException("fehlt") }
        compose.onNodeWithText("Patchnotes nicht verfügbar").assertExists()
        compose.onNodeWithText("Auf GitHub ansehen").assertExists()
        compose.onNode(hasContentDescription("Was ist neu in Version", substring = true)).assertDoesNotExist()
    }

    @Test fun leereListeGiltAlsNichtVerfuegbar() {
        show { emptyList() }
        compose.onNodeWithText("Patchnotes nicht verfügbar").assertExists()
        compose.onNodeWithText("Auf GitHub ansehen").assertExists()
    }
}
