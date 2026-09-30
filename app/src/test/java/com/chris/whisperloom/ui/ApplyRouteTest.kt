package com.chris.whisperloom.ui

import com.chris.whisperloom.AppNav
import com.chris.whisperloom.Engine
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.RouteRequest
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SetupFacts
import com.chris.whisperloom.ui.nav.WidgetTab
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Deep-Links in den Back-Stack: wohin ein Widget-Tipp oder die Aufnahme-Notification fuehrt. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApplyRouteTest {

    private val eingerichtet = SetupFacts(
        engine = Engine.ONLINE, sttComplete = true, urlValid = true,
        micGranted = true, overlayGranted = true, welcomeSeen = true,
    )

    private fun stapel(route: RouteRequest, facts: SetupFacts = eingerichtet): List<Screen> {
        val nav = NavState(listOf(Screen.Home, Screen.Models))
        applyRoute(nav, route, facts)
        return nav.snapshot()
    }

    @Test fun erweitertLiegtUeberDenEinstellungen() {
        assertEquals(listOf(Screen.Home, Screen.SettingsHub, Screen.Advanced), stapel(RouteRequest(AppNav.ROUTE_ADVANCED)))
    }

    @Test fun dieAlteRouteAgentFuehrtNachErweitert() {
        // Intents von 3.7.0 (z. B. eine noch offene Notification) tragen "agent".
        assertEquals(listOf(Screen.Home, Screen.SettingsHub, Screen.Advanced), stapel(RouteRequest(AppNav.ROUTE_AGENT)))
    }

    @Test fun widgetsOeffnetDenTabProWidgets() {
        assertEquals(
            listOf(Screen.Home, Screen.SettingsHub, Screen.Widgets(WidgetTab.PRO)),
            stapel(RouteRequest(AppNav.ROUTE_WIDGETS)),
        )
    }

    @Test fun mitProfilOeffnetSichDessenEditor() {
        assertEquals(
            listOf(Screen.Home, Screen.SettingsHub, Screen.Widgets(WidgetTab.PRO, "p1")),
            stapel(RouteRequest(AppNav.ROUTE_WIDGETS, profileId = "p1")),
        )
    }

    @Test fun untenLiegtWieImmerDerStartScreen() {
        // Noch nicht eingerichtet: Zurueck fuehrt in den Assistenten, nicht nach Home.
        assertEquals(
            listOf(Screen.Setup(Screen.Setup.WELCOME), Screen.SettingsHub, Screen.Widgets(WidgetTab.PRO)),
            stapel(RouteRequest(AppNav.ROUTE_WIDGETS), SetupFacts()),
        )
    }
}
