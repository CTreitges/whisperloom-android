package com.chris.whisperloom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.chris.whisperloom.AppNav
import com.chris.whisperloom.overlay.FloatingMicService
import com.chris.whisperloom.ui.home.HomeScreen
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.RouteRequest
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SetupFacts
import com.chris.whisperloom.ui.nav.SetupRouter
import com.chris.whisperloom.ui.nav.Start
import com.chris.whisperloom.ui.nav.rememberNavState
import com.chris.whisperloom.ui.settings.AgentScreen
import com.chris.whisperloom.ui.settings.ButtonKeyboardScreen
import com.chris.whisperloom.ui.settings.HelpScreen
import com.chris.whisperloom.ui.settings.ModelsScreen
import com.chris.whisperloom.ui.settings.RecognitionScreen
import com.chris.whisperloom.ui.settings.SettingsHubScreen
import com.chris.whisperloom.ui.settings.TextSettingsScreen
import com.chris.whisperloom.ui.setup.SetupScreen
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.tutorial.TutorialScreen

/**
 * Wurzel der Compose-UI: Router (§1.2), Back-Stack und Screen-Wechsel (Fade-through, §5.4).
 * [route] = offener Deep-Link; nach dem Navigieren wird [onRouteConsumed] gerufen.
 */
@Composable
fun WhisperLoomApp(env: AppEnv, route: RouteRequest? = null, onRouteConsumed: () -> Unit = {}) {
    CompositionLocalProvider(LocalAppEnv provides env) {
        val ctx = LocalContext.current
        val nav = rememberNavState { startStack(SetupFacts.from(env.prefs, env.status), env.prefs.tutorialSeen) }

        LaunchedEffect(route) {
            if (route != null) {
                applyRoute(nav, route, SetupFacts.from(env.prefs, env.status))
                onRouteConsumed()
            }
        }

        // Der Assistent hat einen eigenen BackHandler (Schritt zurueck), der tiefer in der
        // Composition liegt und deshalb Vorrang hat.
        BackHandler(enabled = nav.canPop) { nav.pop() }

        AnimatedContent(
            targetState = nav.current,
            contentKey = { it.key },
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220)))
                    .togetherWith(fadeOut(tween(90)))
            },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.Home -> HomeScreen(nav)
                // Schrittwechsel aendern den Zustand ohne Screen-Wechsel: aktuellen Schritt aus nav lesen.
                is Screen.Setup -> SetupScreen(step = (nav.current as? Screen.Setup)?.step ?: screen.step, nav = nav)
                Screen.SettingsHub -> SettingsHubScreen(nav)
                Screen.Recognition -> RecognitionScreen(nav)
                Screen.TextSettings -> TextSettingsScreen(nav)
                Screen.ButtonKeyboard -> ButtonKeyboardScreen(nav)
                Screen.Models -> ModelsScreen(nav)
                Screen.Agent -> AgentScreen(nav)
                is Screen.Help -> HelpScreen(section = (nav.current as? Screen.Help)?.section ?: screen.section, nav = nav)
                is Screen.Tutorial -> TutorialScreen(
                    startPage = screen.startPage,
                    kind = screen.kind,
                    onFinish = {
                        // W9 "Knopf starten & los": der Knopf startet erst jetzt, nicht ueber dem Tutorial.
                        if (screen.startBubbleAfter) FloatingMicService.start(ctx)
                        nav.replaceAll(Screen.Home)
                    },
                )
            }
        }
    }
}

/** Erster Screen nach dem Start (Router §1.2). */
fun startScreen(facts: SetupFacts): Screen = when (val start = SetupRouter.start(facts)) {
    Start.Home -> Screen.Home
    Start.Welcome -> Screen.Setup(Screen.Setup.WELCOME)
    is Start.Step -> Screen.Setup(start.step)
}

/**
 * Back-Stack beim Start: Bestandsnutzer, die bereits eingerichtet sind, sehen das Tutorial einmal
 * (Home darunter, Beenden fuehrt dorthin) — nie, solange die Einrichtung offen ist (dort zeigt W9 es).
 */
fun startStack(facts: SetupFacts, tutorialSeen: Boolean): List<Screen> {
    val start = startScreen(facts)
    return if (start == Screen.Home && !tutorialSeen) listOf(Screen.Home, Screen.Tutorial()) else listOf(start)
}

/** Deep-Link anwenden: `home` (Notification), `settings` (IME-Zahnrad), `setup[+step]` (IME/Overlay/Share). */
fun applyRoute(nav: NavState, route: RouteRequest, facts: SetupFacts) {
    when (route.route) {
        AppNav.ROUTE_HOME -> nav.replaceAll(Screen.Home)
        AppNav.ROUTE_SETTINGS -> nav.replaceAll(startScreen(facts), Screen.SettingsHub)
        // Widget-Tipp, solange nichts eingerichtet oder das Mikrofon nicht erlaubt ist.
        AppNav.ROUTE_AGENT -> nav.replaceAll(startScreen(facts), Screen.SettingsHub, Screen.Agent)
        AppNav.ROUTE_SETUP -> {
            val setup = Screen.Setup(route.step ?: SetupRouter.firstOpenStep(facts))
            // Aus Home geoeffnet: Zurueck fuehrt nach Home; sonst ist der Assistent der einzige Screen.
            if (SetupRouter.isSetUp(facts)) nav.replaceAll(Screen.Home, setup) else nav.replaceAll(setup)
        }
    }
}
