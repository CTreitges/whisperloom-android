package com.chris.whisperloom.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.chris.whisperloom.BuildConfig
import com.chris.whisperloom.Engine
import com.chris.whisperloom.ProFeature
import com.chris.whisperloom.R
import com.chris.whisperloom.agent.Tier
import com.chris.whisperloom.ui.components.DetailScaffold
import com.chris.whisperloom.ui.components.HubRow
import com.chris.whisperloom.ui.components.SectionHeader
import com.chris.whisperloom.ui.components.fileSize
import com.chris.whisperloom.ui.components.modelLabel
import com.chris.whisperloom.ui.components.offlineModelLabel
import com.chris.whisperloom.ui.components.providerShortName
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.WidgetProfilesState

/**
 * E — Einstellungen-Hub (UX-Spec §2.3), seit 3.9.0 nach Gegenstaenden: Text, Modelle & Zugaenge,
 * Bedienung, Pro, Info. Unterzeile = aktueller Wert, Zeilenname = Seitentitel.
 */
@Composable
fun SettingsHubScreen(nav: NavState) {
    val ctx = LocalContext.current
    val env = LocalAppEnv.current
    val prefs = env.prefs
    val status = env.status
    val snack = rememberSnack()
    var showAbout by rememberSaveable { mutableStateOf(false) }

    val stt = prefs.sttAccess()
    val recognition = when (prefs.engine) {
        Engine.ONLINE -> stringResource(R.string.home_val_online, providerShortName(stt.provider), modelLabel(stt))
        Engine.OFFLINE -> "Offline · ${offlineModelLabel(prefs.offlineModel)}"
        null -> stringResource(R.string.setup_chip_open)
    }
    val button = stringResource(if (status.bubbleRunning) R.string.settings_val_bubble_on else R.string.settings_val_bubble_off) +
        " · " + stringResource(if (status.imeEnabled) R.string.settings_val_kb_on else R.string.settings_val_kb_off)
    // Kein Schalter auf Hub-Ebene (Spec §2.3) — nur die eingeschalteten Pro-Funktionen als Unterzeile.
    val active = ProFeature.entries.filter { prefs.isEnabled(it) }.map { stringResource(it.hubLabel) }
    val advanced = if (active.isEmpty()) stringResource(R.string.settings_agent_off) else active.joinToString(" · ")
    // Immer sichtbar (User-Entscheidung): ohne Pro Widgets zeigt das Untermenue den Platzhalter fuer normale Widgets.
    val widgetState = remember { WidgetProfilesState(ctx) }
    LifecycleResumeEffect(widgetState) {
        widgetState.reload()
        onPauseOrDispose { }
    }
    val placed = widgetState.placed.size
    val proCount = widgetState.profiles(Tier.PRO).size
    val widgets = if (!prefs.proWidgetsEnabled) stringResource(R.string.widgets_sub_normal_soon)
    else pluralStringResource(R.plurals.widgets_sub_profiles, proCount, proCount) + " · " +
        if (placed == 0) stringResource(R.string.widgets_sub_none_placed)
        else pluralStringResource(R.plurals.widgets_sub_placed, placed, placed)
    val n = status.installedCount
    val models = if (n > 0) stringResource(R.string.home_val_models, n, fileSize(status.modelsUsedBytes))
    else stringResource(R.string.settings_models_none)

    DetailScaffold(title = stringResource(R.string.settings_title), onBack = { nav.pop() }, snack = snack) { padding ->
        // Gruppen mit Ueberschrift (User-Entscheidung U3); die letzte Zeile einer Gruppe ohne Trenner.
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionHeader(stringResource(R.string.settings_section_text)) }
            item { HubRow(R.drawable.ic_auto_fix_high, stringResource(R.string.settings_group_refine), refineValue(prefs)) { nav.push(Screen.Refine) } }
            item {
                HubRow(R.drawable.ic_checklist, stringResource(R.string.settings_group_dictionary), dictionaryValue(prefs), divider = false) {
                    nav.push(Screen.Dictionary)
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_models)) }
            item { HubRow(R.drawable.ic_graphic_eq, stringResource(R.string.settings_group_recognition), recognition) { nav.push(Screen.Recognition) } }
            item { HubRow(R.drawable.ic_cloud, stringResource(R.string.settings_group_llm), accessValue(prefs)) { nav.push(Screen.LlmAccess) } }
            item { HubRow(R.drawable.ic_download_for_offline, stringResource(R.string.settings_group_models), models, divider = false) { nav.push(Screen.Models) } }

            item { SectionHeader(stringResource(R.string.settings_section_controls)) }
            item { HubRow(R.drawable.ic_touch_app, stringResource(R.string.settings_group_button), button) { nav.push(Screen.ButtonKeyboard) } }
            item { HubRow(R.drawable.ic_layers, stringResource(R.string.settings_group_widgets), widgets, divider = false) { nav.push(Screen.Widgets()) } }

            item { SectionHeader(stringResource(R.string.settings_section_pro)) }
            item { HubRow(R.drawable.ic_build, stringResource(R.string.settings_group_advanced), advanced, divider = false) { nav.push(Screen.Advanced) } }

            item { SectionHeader(stringResource(R.string.settings_section_info)) }
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
