package com.chris.whisperloom.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.ui.access.LlmAccessSection
import com.chris.whisperloom.ui.components.DetailScaffold
import com.chris.whisperloom.ui.components.ScrollColumn
import com.chris.whisperloom.ui.components.SectionCard
import com.chris.whisperloom.ui.components.SwitchRow
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.fileSize
import com.chris.whisperloom.ui.components.levelLabel
import com.chris.whisperloom.ui.components.offlineModelLabel
import com.chris.whisperloom.ui.components.offlineRuleDetails
import com.chris.whisperloom.ui.components.offlineRuleLabel
import com.chris.whisperloom.ui.components.textModelSize
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.models.DownloadProgress
import com.chris.whisperloom.ui.models.LocalModelRequiredCard
import com.chris.whisperloom.ui.models.localModelMissing
import com.chris.whisperloom.ui.models.offlineRule
import com.chris.whisperloom.ui.models.rememberModelDownload
import com.chris.whisperloom.ui.models.textModelToLoad
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.whisper.DownloadState
import com.chris.whisperloom.whisper.ModelDownloads
import com.chris.whisperloom.whisper.OfflineSupport
import com.chris.whisperloom.whisper.TextModelCatalog

/** Die Stufen-Auswahl fuer geteilte Audios — ihre Labels gibt es in der Diktat-Karte ein zweites Mal. */
const val SHARE_REFINE_TAG = "share-refine"

/**
 * E2 — Text (UX-Spec §2.5): Stufe, "Lesbarer glaetten", KI-Fuellwoerter, Stufe fuer geteilte Audios,
 * Offline-Erkennung (lokales Textmodell und Regel), Online-Zugang, Regeln ohne KI, Sheet B3.
 */
@Composable
fun TextSettingsScreen(nav: NavState) {
    val env = LocalAppEnv.current
    val prefs = env.prefs
    val snack = rememberSnack()
    var showFillers by rememberSaveable { mutableStateOf(false) }
    val off = prefs.refineMode == RefineMode.OFF
    // "Intelligent entfernen" wirkt auch auf geteilte Audios — bedienbar, sobald irgendeine KI-Stufe gilt.
    val noAi = off && prefs.shareRefineMode == RefineMode.OFF
    // "Lesbarer glaetten" aendert nur "Glaetten" — fuers Diktat wie fuer geteilte Audios.
    val polish = prefs.refineMode == RefineMode.POLISH || prefs.shareRefineMode == RefineMode.POLISH

    DetailScaffold(title = stringResource(R.string.text_title), onBack = { nav.pop() }, snack = snack) { padding ->
        ScrollColumn(padding) {
            SectionCard(
                title = stringResource(R.string.text_card_refine),
                titleIcon = R.drawable.ic_auto_fix_high,
                titleIconTint = MaterialTheme.colorScheme.tertiary,
                gap = 4.dp,
            ) {
                Column(Modifier.selectableGroup()) {
                    RefineMode.settings(prefs.promptLevelEnabled).forEach { mode ->
                        val selected = prefs.refineMode == mode
                        LoomRow(
                            headline = levelLabel(mode),
                            supporting = stringResource(levelSubtitle(mode)),
                            modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) { prefs.refineMode = mode },
                            trailing = { RadioButton(selected = selected, onClick = null) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.text_level_cost),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow(
                    headline = stringResource(R.string.pref_polish_readable),
                    supporting = stringResource(if (polish) R.string.pref_polish_readable_info else R.string.text_readable_needs_polish),
                    checked = prefs.polishReadable,
                    onCheckedChange = { prefs.polishReadable = it },
                    enabled = polish,
                )
                SwitchRow(
                    headline = stringResource(R.string.pref_smart_fillers),
                    supporting = stringResource(if (noAi) R.string.text_smart_needs_level else R.string.pref_smart_fillers_info),
                    checked = prefs.smartFillers,
                    onCheckedChange = { prefs.smartFillers = it },
                    enabled = !noAi,
                )
                SwitchRow(
                    headline = stringResource(R.string.pref_refine_paragraphs),
                    supporting = stringResource(if (off) R.string.text_smart_needs_level else R.string.pref_refine_paragraphs_info),
                    checked = prefs.refineParagraphs,
                    onCheckedChange = { prefs.refineParagraphs = it },
                    enabled = !off,
                )
            }

            SectionCard(
                title = stringResource(R.string.text_card_share),
                titleIcon = R.drawable.ic_voicemail,
                gap = 4.dp,
            ) {
                Text(
                    stringResource(R.string.text_share_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.selectableGroup().testTag(SHARE_REFINE_TAG)) {
                    RefineMode.SETTINGS.forEach { mode ->
                        val selected = prefs.shareRefineMode == mode
                        LoomRow(
                            headline = levelLabel(mode),
                            supporting = stringResource(if (mode == RefineMode.OFF) R.string.text_share_off_sub else levelSubtitle(mode)),
                            modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) { prefs.shareRefineMode = mode },
                            trailing = { RadioButton(selected = selected, onClick = null) },
                        )
                    }
                }
            }

            if (env.status.offlineSupported) OfflineRefineCard(nav, noAi)

            SectionCard(title = stringResource(R.string.text_card_access)) {
                LlmAccessSection(snack)
            }

            SectionCard(title = stringResource(R.string.text_card_rules), gap = 4.dp) {
                SwitchRow(
                    headline = stringResource(R.string.pref_remove_fillers),
                    supporting = stringResource(R.string.text_fillers_sub),
                    checked = prefs.removeFillers,
                    onCheckedChange = { prefs.removeFillers = it },
                )
                TextButton(onClick = { showFillers = true }, enabled = prefs.removeFillers) {
                    Text(stringResource(R.string.text_fillers_edit))
                }
                SwitchRow(
                    headline = stringResource(R.string.pref_auto_cap),
                    checked = prefs.autoCapitalize,
                    onCheckedChange = { prefs.autoCapitalize = it },
                )
                SwitchRow(
                    headline = stringResource(R.string.pref_trailing_space),
                    checked = prefs.trailingSpace,
                    onCheckedChange = { prefs.trailingSpace = it },
                )
                if (prefs.smartFillers && !noAi) {
                    Text(
                        stringResource(R.string.text_fillers_paused),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showFillers) FillersSheet { showFillers = false }
}

/**
 * Karte "Offline-Erkennung" (Spec §4): Stand des lokalen Textmodells — fehlt es, wo es gebraucht
 * wird, die Pflichtkarte — und die Regel "Textverbesserung bei Offline-Erkennung" mit drei Optionen.
 * [noAi]: beide Stufen "Aus", die Regel wirkt dann nicht.
 */
@Composable
private fun OfflineRefineCard(nav: NavState, noAi: Boolean) {
    val env = LocalAppEnv.current
    val prefs = env.prefs
    SectionCard(
        title = stringResource(R.string.text_card_offline),
        titleIcon = R.drawable.ic_offline_bolt,
        titleIconTint = MaterialTheme.colorScheme.tertiary,
        gap = 4.dp,
    ) {
        if (localModelMissing(prefs, env.status)) {
            LocalModelRequiredCard(Modifier.padding(vertical = 8.dp))
        } else {
            LocalModelRow(nav)
        }
        Text(
            stringResource(R.string.text_rule_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        Column(Modifier.selectableGroup()) {
            OfflineRefineRule.entries.forEach { rule ->
                // Passt kein Textmodell ins Geraet, bleibt nur "Ueberspringen" (der Grund steht darueber).
                val enabled = env.status.textModelFits || rule == OfflineRefineRule.SKIP
                val selected = offlineRule(prefs, env.status) == rule
                LoomRow(
                    headline = offlineRuleLabel(rule),
                    supporting = offlineRuleDetails(rule),
                    modifier = Modifier
                        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) { prefs.offlineRefine = rule }
                        .alpha(if (enabled) 1f else 0.38f),
                    trailing = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
                )
            }
        }
        if (noAi) {
            Text(
                stringResource(R.string.text_smart_needs_level),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Das gewaehlte Textmodell: geladen mit [Aendern] (-> Offline-Modelle), sonst "Kein Textmodell
 * geladen" mit [Laden]. Der Knopf laedt nur — die Regel waehlt man darunter. Passt kein
 * Textmodell ins Geraet, steht statt des Knopfs der Grund.
 */
@Composable
private fun LocalModelRow(nav: NavState) {
    val env = LocalAppEnv.current
    val prefs = env.prefs
    val states by ModelDownloads.states.collectAsStateWithLifecycle()
    val download = rememberModelDownload { prefs.localLlmModel = it.id }
    if (!env.status.textModelFits) {
        LoomRow(headline = stringResource(R.string.text_local_none), supporting = stringResource(R.string.text_local_needs_ram))
    } else if (prefs.localLlmModel in env.status.installedTextModels) {
        val model = TextModelCatalog.byId(prefs.localLlmModel)
        LoomRow(
            headline = offlineModelLabel(model.id),
            supporting = stringResource(R.string.text_local_loaded, fileSize(model.bytes)),
            trailing = { TextButton(onClick = { nav.push(Screen.Models) }) { Text(stringResource(R.string.common_change)) } },
        )
    } else {
        val model = textModelToLoad(prefs.localLlmModel, env.status.totalRamBytes)
        val state = states[model.id] ?: DownloadState.Idle
        LoomRow(
            headline = stringResource(R.string.text_local_none),
            supporting = "${offlineModelLabel(model.id)} · ${textModelSize(model)}",
            trailing = {
                if (state !is DownloadState.Running) {
                    FilledTonalButton(
                        onClick = { download.start(model) },
                        enabled = states.values.none { it is DownloadState.Running } &&
                            OfflineSupport.fitsDevice(env.status.totalRamBytes, model),
                    ) { Text(stringResource(R.string.models_load, fileSize(model.bytes))) }
                }
            },
        )
        if (state is DownloadState.Running) DownloadProgress(state)
        if (state is DownloadState.Failed) {
            Text(
                stringResource(R.string.models_failed, state.message),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun levelSubtitle(mode: RefineMode): Int = when (mode) {
    RefineMode.OFF -> R.string.text_level_off_sub
    RefineMode.POLISH, RefineMode.PARAGRAPHS, RefineMode.READABLE -> R.string.text_level_smooth_sub
    RefineMode.BEAUTIFY -> R.string.text_level_beautify_sub
    RefineMode.SUMMARIZE -> R.string.text_level_summarize_sub
    RefineMode.PROMPT -> R.string.text_level_prompt_sub
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
