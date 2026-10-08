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
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chris.whisperloom.AppNav
import com.chris.whisperloom.overlay.FloatingMicService
import com.chris.whisperloom.ui.history.HistoryDetailScreen
import com.chris.whisperloom.ui.history.HistoryEditScreen
import com.chris.whisperloom.ui.history.HistoryListScreen
import com.chris.whisperloom.ui.history.HistorySettingsScreen
import com.chris.whisperloom.ui.home.HomeScreen
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.RouteRequest
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SetupFacts
import com.chris.whisperloom.ui.nav.SetupRouter
import com.chris.whisperloom.ui.nav.Start
import com.chris.whisperloom.ui.nav.WidgetTab
import com.chris.whisperloom.ui.nav.rememberNavState
import com.chris.whisperloom.ui.patchnotes.PatchnotesScreen
import com.chris.whisperloom.ui.settings.AdvancedScreen
import com.chris.whisperloom.ui.settings.ButtonKeyboardScreen
import com.chris.whisperloom.ui.settings.DictionaryScreen
import com.chris.whisperloom.ui.settings.HelpScreen
import com.chris.whisperloom.ui.settings.LlmAccessScreen
import com.chris.whisperloom.ui.settings.ModelsScreen
import com.chris.whisperloom.ui.settings.RecognitionScreen
import com.chris.whisperloom.ui.settings.RefineScreen
import com.chris.whisperloom.ui.settings.SettingsHubScreen
import com.chris.whisperloom.ui.settings.StageScreen
import com.chris.whisperloom.ui.settings.WidgetsScreen
import com.chris.whisperloom.ui.setup.SetupScreen
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.tutorial.TutorialKind
import com.chris.whisperloom.ui.tutorial.TutorialScreen
import com.chris.whisperloom.whisper.DownloadState
import com.chris.whisperloom.whisper.ModelDownloads

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

        // Ein fertiger Download aendert den Systemstatus (installierte Modelle) — auch wenn er auf einem
        // anderen Screen fertig wird als dem, der ihn gestartet hat (Home-Banner, Pflichtkarte, Hub).
        val downloads by ModelDownloads.states.collectAsStateWithLifecycle()
        LaunchedEffect(downloads) {
            val status = env.status
            val unseen = downloads.any { (id, state) ->
                state is DownloadState.Done && id !in status.installedModels && id !in status.installedTextModels
            }
            if (unseen) env.refreshStatus()
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
                Screen.Refine -> RefineScreen(nav)
                is Screen.Stage -> StageScreen(screen.stage, screen.way, nav)
                Screen.Dictionary -> DictionaryScreen(nav)
                Screen.Recognition -> RecognitionScreen(nav)
                Screen.LlmAccess -> LlmAccessScreen(nav)
                Screen.ButtonKeyboard -> ButtonKeyboardScreen(nav)
                Screen.Models -> ModelsScreen(nav)
                Screen.Advanced -> AdvancedScreen(nav)
                is Screen.Widgets -> WidgetsScreen(nav, tab = screen.tab, edit = screen.edit)
                is Screen.Help -> HelpScreen(section = (nav.current as? Screen.Help)?.section ?: screen.section, nav = nav)
                Screen.Patchnotes -> PatchnotesScreen(nav)
                Screen.History -> HistoryListScreen(nav)
                // Ein Chip-Wechsel ersetzt den Eintrag ohne Screen-Wechsel: die sichtbare Fassung aus nav lesen.
                is Screen.HistoryDetail -> HistoryDetailScreen((nav.current as? Screen.HistoryDetail)?.takeIf { it.id == screen.id } ?: screen, nav)
                is Screen.HistoryEdit -> HistoryEditScreen(screen.id, screen.processing, nav)
                Screen.HistorySettings -> HistorySettingsScreen(nav)
                is Screen.Tutorial -> TutorialScreen(
                    startPage = screen.startPage,
                    kind = screen.kind,
                    onFinish = {
                        // W9 "Knopf starten & los": der Knopf startet erst jetzt, nicht ueber dem Tutorial.
                        if (screen.startBubbleAfter) FloatingMicService.start(ctx)
                        finishTutorial(nav, screen.kind)
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

/**
 * Tutorial beendet ("Los geht's", "Ueberspringen", Zurueck auf Seite 1): das Einsteiger-Heft fuehrt
 * nach Home (Auto-Start, W9). Pro Widgets fuehrt dorthin zurueck, wo es geoeffnet wurde
 * (Widgets-Tab, Hilfe, Erweitert) — nur ohne vorigen Screen nach Home.
 */
fun finishTutorial(nav: NavState, kind: TutorialKind) {
    if (kind == TutorialKind.PRO_WIDGETS && nav.canPop) nav.pop() else nav.replaceAll(Screen.Home)
}

/**
 * Deep-Link anwenden: `home` (Notification), `settings` (IME-Zahnrad), `models` (IME, Textmodell fehlt),
 * `llm-access` (IME, KI-Zugang fehlt), `refine` (Sprachnachrichten-Fenster), `setup[+step]` (IME/Overlay/Share),
 * `advanced` und `widgets[+profile]` (Widget-Tipp, der nicht aufnehmen kann; Aufnahme-Notification).
 */
fun applyRoute(nav: NavState, route: RouteRequest, facts: SetupFacts) {
    when (route.route) {
        AppNav.ROUTE_HOME -> nav.replaceAll(Screen.Home)
        AppNav.ROUTE_SETTINGS -> nav.replaceAll(startScreen(facts), Screen.SettingsHub)
        // Tastatur: offline ohne Textmodell.
        AppNav.ROUTE_MODELS -> nav.replaceAll(startScreen(facts), Screen.SettingsHub, Screen.Models)
        // Tastatur: Zugang fehlt fuer die KI-Stufen.
        AppNav.ROUTE_LLM_ACCESS -> nav.replaceAll(startScreen(facts), Screen.SettingsHub, Screen.LlmAccess)
        // Sprachnachrichten-Fenster: "aenderbar unter Einstellungen › Textverbesserung".
        AppNav.ROUTE_REFINE -> nav.replaceAll(startScreen(facts), Screen.SettingsHub, Screen.Refine)
        // Pro Widgets aus. "agent" kommt noch aus Intents von 3.7.0.
        AppNav.ROUTE_ADVANCED, AppNav.ROUTE_AGENT -> nav.replaceAll(startScreen(facts), Screen.SettingsHub, Screen.Advanced)
        // Kein Mikrofon oder Aufnahme-Notification; mit Profil: dessen Server fehlt, der Editor oeffnet sich.
        AppNav.ROUTE_WIDGETS -> nav.replaceAll(
            startScreen(facts), Screen.SettingsHub, Screen.Widgets(WidgetTab.PRO, route.profileId),
        )
        AppNav.ROUTE_SETUP -> {
            val setup = Screen.Setup(route.step ?: SetupRouter.firstOpenStep(facts))
            // Aus Home geoeffnet: Zurueck fuehrt nach Home; sonst ist der Assistent der einzige Screen.
            if (SetupRouter.isSetUp(facts)) nav.replaceAll(Screen.Home, setup) else nav.replaceAll(setup)
        }
    }
}
