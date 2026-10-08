package com.chris.whisperloom.ui.history

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.chris.whisperloom.Refinement
import com.chris.whisperloom.history.History
import com.chris.whisperloom.history.HistorySource
import com.chris.whisperloom.ui.nav.Screen
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verlauf-Liste (Plan §6.2): Tagesgruppen, Zeile, Oeffnen, Loeschen mit Rueckgaengig, leere Zustaende, ⋮. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class HistoryListScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val ui = HistoryUi(compose)

    @Before fun setUp() = ui.setUp()

    @After fun tearDown() = ui.tearDown()

    private fun liste() = ui.show(Screen.Home, Screen.History)

    @Test fun gruppiertNachHeuteGesternUndDatum() {
        val alt = LocalDate.now().minusDays(9)
        ui.record(raw = "heute diktiert", at = ui.at(0, "09:15"))
        ui.record(raw = "gestern diktiert", at = ui.at(1, "18:02"), source = HistorySource.BUBBLE)
        ui.record(raw = "vor neun Tagen diktiert", at = ui.at(9, "07:00"))
        liste()

        ui.waitFor("heute diktiert")
        val reihenfolge = listOf(
            "HEUTE", "heute diktiert", "GESTERN", "gestern diktiert",
            HistoryText.date(alt, LocalDate.now()).uppercase(), "vor neun Tagen diktiert",
        )
        val oben = reihenfolge.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals("Von oben nach unten: $reihenfolge", oben.sorted(), oben)
        compose.onNodeWithText("09:15 · Tastatur · 0:41").assertIsDisplayed()
        compose.onNodeWithText("18:02 · Knopf · 0:41").assertIsDisplayed()
    }

    @Test fun zeileZeigtDieStufeUndOhneKiBeiFehlerUndBeiAusKeinEtikett() {
        ui.record(raw = "zusammengefasst", at = ui.at(0, "10:00"))
        ui.record(raw = "gescheitert", text = "Gescheitert.", model = null, skipped = "Kein Netz für den Online-Zugang", at = ui.at(0, "11:00"))
        ui.record(raw = "ohne stufe", text = "Ohne Stufe", refinement = Refinement.OFF, model = null, at = ui.at(0, "12:00"))
        liste()

        ui.waitFor("zusammengefasst")
        compose.onNodeWithText("Zusammenfassen").assertIsDisplayed()
        compose.onNodeWithText("ohne KI").assertIsDisplayed()
        // Die Zeile zeigt den Ursprung, nie die Fassung.
        compose.onNodeWithText("Gescheitert.").assertDoesNotExist()
        assertEquals(1, compose.onAllNodes(hasText("Zusammenfassen")).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodes(hasText("ohne KI")).fetchSemanticsNodes().size)
    }

    @Test fun tippenOeffnetDenEintrag() {
        val entry = ui.record(raw = "bitte öffnen")
        val nav = liste()

        ui.waitFor("bitte öffnen").performClick()
        compose.waitForIdle()

        assertEquals(Screen.HistoryDetail(entry.id), nav.current)
    }

    @Test fun wischenLoeschtUndRueckgaengigHoltDenEintragZurueck() {
        val entry = ui.record(raw = "weg damit")
        liste()

        ui.waitFor("weg damit").performTouchInput { swipeLeft() }
        ui.waitFor("Eintrag gelöscht")
        ui.waitUntil { History.count(ui.ctx) == 0 }
        compose.onNodeWithText("Noch keine Diktate").assertIsDisplayed()

        compose.onNodeWithText("Rückgängig").performClick()
        ui.waitFor("weg damit")
        assertEquals(entry, History.get(ui.ctx, entry.id))
    }

    @Test fun nachRechtsWischenLoeschtNicht() {
        ui.record(raw = "bleibt da")
        liste()

        ui.waitFor("bleibt da").performTouchInput { swipeRight() }
        compose.waitForIdle()

        assertEquals(1, History.count(ui.ctx))
        compose.onNodeWithText("Eintrag gelöscht").assertDoesNotExist()
    }

    @Test fun talkBackAktionLoeschenStattWischen() {
        ui.record(raw = "per TalkBack")
        liste()

        val zeile = ui.waitFor("per TalkBack")
        val aktion = zeile.fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        assertEquals("Löschen", aktion.label)
        compose.runOnIdle { aktion.action() }

        ui.waitFor("Eintrag gelöscht")
        ui.waitUntil { History.count(ui.ctx) == 0 }
    }

    @Test fun leerSagtNochKeineDiktate() {
        liste()
        ui.waitFor("Noch keine Diktate")
        compose.onNodeWithText("Einschalten").assertDoesNotExist()
    }

    @Test fun ausgeschaltetBietetEinschaltenAn() {
        History.setEnabled(ui.ctx, false)
        liste()

        compose.onNodeWithText("Verlauf ist aus").assertIsDisplayed()
        compose.onNodeWithText("Einschalten").performClick()

        ui.waitFor("Noch keine Diktate")
        assertTrue(ui.prefs.historyEnabled)
    }

    @Test fun menueOeffnetDieEinstellungen() {
        val nav = liste()

        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Verlauf-Einstellungen").performClick()
        compose.waitForIdle()

        assertEquals(Screen.HistorySettings, nav.current)
    }

    @Test fun alleLoeschenFragtVorher() {
        ui.record(raw = "eins", at = ui.at(0, "08:00"))
        ui.record(raw = "zwei", at = ui.at(0, "09:00"))
        liste()
        ui.waitFor("zwei")

        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Alle löschen").performClick()
        compose.onNodeWithText("Alle Einträge löschen?").assertIsDisplayed()
        compose.onNodeWithText("Abbrechen").performClick()
        compose.waitForIdle()
        assertEquals(2, History.count(ui.ctx))

        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Alle löschen").performClick()
        compose.onNodeWithText("Alle löschen").performClick() // die Bestaetigung im Dialog
        ui.waitFor("Noch keine Diktate")
        assertEquals(0, History.count(ui.ctx))
    }

    @Test fun alleLoeschenIstOhneEintraegeGesperrt() {
        liste()
        ui.waitFor("Noch keine Diktate")
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Alle löschen").assertIsNotEnabled()
    }
}
