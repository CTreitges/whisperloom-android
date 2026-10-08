package com.chris.whisperloom.ui.history

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Dictation
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.Refined
import com.chris.whisperloom.Refinement
import com.chris.whisperloom.history.History
import com.chris.whisperloom.history.HistoryEntry
import com.chris.whisperloom.history.HistorySource
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Gemeinsames der Verlauf-UI-Tests: Eintraege ueber den echten Speicher anlegen, die Verlauf-Screens
 * mit einem kleinen Router wie in WhisperLoomApp zeigen und auf den Hintergrund warten (die Screens
 * laden auf Dispatchers.IO, das sieht waitForIdle nicht).
 */
internal class HistoryUi(private val compose: ComposeContentTestRule) {

    val ctx: Context = ApplicationProvider.getApplicationContext()
    lateinit var prefs: Prefs
    private val originalClock = History.clock

    fun setUp() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx).apply {
            welcomeSeen = true
            tutorialSeen = true
            engine = Engine.ONLINE
            apiKey = "sk-test"
        }
        History.clear(ctx)
    }

    fun tearDown() {
        History.clock = originalClock
        History.clear(ctx)
    }

    /** Zeitpunkt [time] am Tag [daysAgo] vor heute, in der Zone des Geraets. */
    fun at(daysAgo: Long, time: String): Long =
        LocalDate.now().minusDays(daysAgo).atTime(LocalTime.parse(time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Ein Diktat ueber [History.record] — wie Tastatur und Knopf. */
    fun record(
        raw: String = "ähm also ich wollte kurz sagen dass ich morgen später komme",
        text: String = "Komme morgen später.",
        refinement: Refinement = Refinement(RefineMode.SUMMARIZE),
        model: String? = "Claude Sonnet 5.5",
        skipped: String? = null,
        at: Long = at(0, "14:32"),
        source: HistorySource = HistorySource.KEYBOARD,
        durationMs: Long = 41_000,
    ): HistoryEntry {
        History.clock = { at }
        check(History.record(ctx, source, Dictation(raw, "de", durationMs, Refined(refinement, text, model, skipped))))
        History.clock = originalClock
        return History.list(ctx).first { it.createdAt == at }
    }

    fun env(status: SystemStatus = SystemStatus(micGranted = true, canDrawOverlays = true, a11yRunning = true)) =
        AppEnv(PrefsState(prefs), status) { status }

    /** Zeigt [stack] und routet die Verlauf-Screens wie WhisperLoomApp; andere Screens als Platzhalter. */
    fun show(vararg stack: Screen, env: AppEnv = env()): NavState {
        val nav = NavState(stack.toList())
        compose.setContent { Themed(env) { Router(nav) } }
        compose.waitForIdle()
        return nav
    }

    @Composable
    fun Themed(env: AppEnv, content: @Composable () -> Unit) {
        WhisperLoomTheme { CompositionLocalProvider(LocalAppEnv provides env) { content() } }
    }

    @Composable
    fun Router(nav: NavState) {
        when (val screen = nav.current) {
            Screen.History -> HistoryListScreen(nav)
            is Screen.HistoryDetail -> HistoryDetailScreen(screen, nav)
            is Screen.HistoryEdit -> HistoryEditScreen(screen.id, screen.processing, nav)
            Screen.HistorySettings -> HistorySettingsScreen(nav)
            else -> Text("Screen ${screen.key}")
        }
    }

    /** Wartet, bis [text] (ganz) zu sehen ist — Laden und Neu-Verarbeiten laufen im Hintergrund. */
    fun waitFor(text: String, substring: Boolean = false): SemanticsNodeInteraction {
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring)).fetchSemanticsNodes().isNotEmpty() }
        return compose.onNode(hasText(text, substring))
    }

    fun waitUntil(condition: () -> Boolean) {
        compose.waitUntil(5_000) { condition() }
    }
}
