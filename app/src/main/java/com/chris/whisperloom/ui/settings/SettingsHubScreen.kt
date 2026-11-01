package com.chris.whisperloom.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chris.whisperloom.BuildConfig
import com.chris.whisperloom.ProFeature
import com.chris.whisperloom.R
import com.chris.whisperloom.agent.Tier
import com.chris.whisperloom.history.History
import com.chris.whisperloom.ui.components.DetailScaffold
import com.chris.whisperloom.ui.components.HubRow
import com.chris.whisperloom.ui.components.SectionHeader
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.WidgetProfilesState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * E — Einstellungen-Hub (UX-Spec §2.3, Neuaufbau §6.20): drei Gruppen nach dem, was der Nutzer tut.
 * SPRACHE & TEXT (Spracherkennung, Textverbesserung, Woerterbuch & Regeln) · BEDIENUNG (Knopf & Tastatur,
 * Widgets nur mit Pro Widgets) · WEITERES (Verlauf, Pro-Funktionen, Anleitung & Hilfe, Ueber).
 * KI-Zugang und Offline-Textverbesserung sind Unterseiten der Textverbesserung, die Whisper-Modelle
 * stehen in der Spracherkennung — bis 3.9.1 waren beides eigene Hub-Zeilen (KI-Zugang, Offline-Modelle).
 * Unterzeile = aktueller Wert, Zeilenname = Seitentitel, kein Schalter auf Hub-Ebene.
 */
@Composable
fun SettingsHubScreen(nav: NavState) {
    val ctx = LocalContext.current
    val env = LocalAppEnv.current
    val prefs = env.prefs
    val status = env.status
    val snack = rememberSnack()
    var showAbout by rememberSaveable { mutableStateOf(false) }

    val button = stringResource(if (status.bubbleRunning) R.string.settings_val_bubble_on else R.string.settings_val_bubble_off) +
        " · " + stringResource(if (status.imeEnabled) R.string.settings_val_kb_on else R.string.settings_val_kb_off)
    // Nur die eingeschalteten Pro-Funktionen als Unterzeile (kein Schalter auf Hub-Ebene, Spec §2.3).
    val active = ProFeature.entries.filter { prefs.isEnabled(it) }.map { stringResource(it.hubLabel) }
    val advanced = if (active.isEmpty()) stringResource(R.string.settings_agent_off) else active.joinToString(" · ")
    // Widgets gibt es bislang nur als Pro Widgets: ohne sie keine Zeile statt eines Platzhalters
    // ("Normale Widgets folgen bald" bis 3.9.1).
    val proWidgets = prefs.proWidgetsEnabled
    val widgetState = remember { WidgetProfilesState(ctx) }
    LifecycleResumeEffect(widgetState) {
        widgetState.reload()
        onPauseOrDispose { }
    }
    val placed = widgetState.placed.size
    val proCount = widgetState.profiles(Tier.PRO).size
    val widgets = pluralStringResource(R.plurals.widgets_sub_profiles, proCount, proCount) + " · " +
        if (placed == 0) stringResource(R.string.widgets_sub_none_placed)
        else pluralStringResource(R.plurals.widgets_sub_placed, placed, placed)
    // Verlauf: Zustand und Fuellstand ("An · 12 von 50"); gezaehlt wird im Hintergrund (Dateizugriff).
    val historyChanges by History.changes.collectAsStateWithLifecycle()
    val historyCount by produceState<Int?>(null, historyChanges) { value = withContext(Dispatchers.IO) { History.count(ctx) } }
    val history = when {
        !prefs.historyEnabled -> stringResource(R.string.settings_history_off)
        else -> historyCount?.let { stringResource(R.string.settings_history_on, it, prefs.historySize) }
            ?: stringResource(R.string.settings_history_on_plain)
    }

    DetailScaffold(title = stringResource(R.string.settings_title), onBack = { nav.pop() }, snack = snack) { padding ->
        // Gruppen mit Ueberschrift; die letzte Zeile einer Gruppe ohne Trenner.
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionHeader(stringResource(R.string.settings_section_speech)) }
            item {
                HubRow(R.drawable.ic_graphic_eq, stringResource(R.string.settings_group_recognition), recognitionValue(prefs, status)) {
                    nav.push(Screen.Recognition)
                }
            }
            item { HubRow(R.drawable.ic_auto_fix_high, stringResource(R.string.settings_group_refine), refineValue(prefs)) { nav.push(Screen.Refine) } }
            item {
                HubRow(R.drawable.ic_checklist, stringResource(R.string.settings_group_dictionary), dictionaryValue(prefs), divider = false) {
                    nav.push(Screen.Dictionary)
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_controls)) }
            item {
                HubRow(R.drawable.ic_touch_app, stringResource(R.string.settings_group_button), button, divider = proWidgets) {
                    nav.push(Screen.ButtonKeyboard)
                }
            }
            if (proWidgets) {
                item { HubRow(R.drawable.ic_layers, stringResource(R.string.settings_group_widgets), widgets, divider = false) { nav.push(Screen.Widgets()) } }
            }

            item { SectionHeader(stringResource(R.string.settings_section_more)) }
            item { HubRow(R.drawable.ic_history, stringResource(R.string.settings_group_history), history) { nav.push(Screen.History) } }
            item { HubRow(R.drawable.ic_build, stringResource(R.string.settings_group_advanced), advanced) { nav.push(Screen.Advanced) } }
            item { HubRow(R.drawable.ic_help, stringResource(R.string.settings_group_help), stringResource(R.string.settings_help_sub)) { nav.push(Screen.Help(1)) } }
            item {
                HubRow(
                    R.drawable.ic_info, stringResource(R.string.settings_group_about),
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME), divider = false,
                ) { showAbout = true }
            }
        }
    }

    // Patchnotes aus dem Sheet: Sheet zu, dann der Screen — Zurueck fuehrt in den Hub ohne Sheet.
    if (showAbout) {
        AboutSheet(snack, onPatchnotes = {
            showAbout = false
            nav.push(Screen.Patchnotes)
        }) { showAbout = false }
    }
}
