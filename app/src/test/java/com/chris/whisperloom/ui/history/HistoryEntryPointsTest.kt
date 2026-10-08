package com.chris.whisperloom.ui.history

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.chris.whisperloom.history.History
import com.chris.whisperloom.ui.home.HomeScreen
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.settings.SettingsHubScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Einstiege in den Verlauf (Plan §6.2): Hub-Zeile "Verlauf" mit Zustand und das Symbol in der Home-Titelleiste. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class HistoryEntryPointsTest {

    @get:Rule
    val compose = createComposeRule()

    private val ui = HistoryUi(compose)

    @Before fun setUp() = ui.setUp()

    @After fun tearDown() = ui.tearDown()

    @Test fun hubZeileZeigtDenZustandUndOeffnetDieListe() {
        repeat(3) { ui.record(raw = "Diktat $it", at = ui.at(0, "09:0$it")) }
        val nav = NavState(listOf(Screen.Home, Screen.SettingsHub))
        compose.setContent { ui.Themed(ui.env()) { SettingsHubScreen(nav) } }

        ui.waitFor("An · 3 von 50").performClick()
        compose.waitForIdle()

        assertEquals(Screen.History, nav.current)
    }

    @Test fun hubZeileSagtAusWennDerVerlaufAusIst() {
        History.setEnabled(ui.ctx, false)
        val nav = NavState(listOf(Screen.Home, Screen.SettingsHub))
        compose.setContent { ui.Themed(ui.env()) { SettingsHubScreen(nav) } }
        compose.waitForIdle()
        compose.onNodeWithText("Verlauf").assertExists()
        assertEquals(2, compose.onAllNodes(hasText("Aus")).fetchSemanticsNodes().size) // Verlauf + Erweitert
    }

    @Test fun symbolInHomeOeffnetDieListe() {
        val nav = NavState(listOf(Screen.Home))
        compose.setContent { ui.Themed(ui.env()) { HomeScreen(nav) } }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Verlauf").performClick()
        compose.waitForIdle()

        assertEquals(Screen.History, nav.current)
    }
}
