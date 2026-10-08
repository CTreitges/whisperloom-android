package com.chris.whisperloom.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.PolishCleanup
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.RefineWay
import com.chris.whisperloom.SummarizeForm
import com.chris.whisperloom.ui.access.StageModelBlock
import com.chris.whisperloom.ui.access.StageModelPicker
import com.chris.whisperloom.ui.access.stageModelBlock
import com.chris.whisperloom.ui.access.stageValue
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.SectionCard
import com.chris.whisperloom.ui.components.StatusIcon
import com.chris.whisperloom.ui.components.SwitchRow
import com.chris.whisperloom.ui.components.Tone
import com.chris.whisperloom.ui.components.levelLabel
import com.chris.whisperloom.ui.components.modelLabel
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState

/** Innenabstand der Karten, die nur aus Zeilen bestehen (die Zeilen bringen 8 dp mit). */
private val RowCardPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)

/**
 * Seite einer Stufe auf einem Weg (3.9.0): oben die Kurzbeschreibung und "Fuer Diktat verwenden"
 * bzw. "Fuer Sprachnachrichten verwenden", darunter nur, was fuer diese Stufe auf diesem Weg gilt.
 * Diktat: Aus feste Regeln · Glaetten Bereinigung, Absaetze, Modell · Verschoenern Absaetze, Modell ·
 * Zusammenfassen Form, Modell · Prompt Modell. Sprachnachrichten: Aus wortgetreu; eigene Bereinigung
 * und Form, immer gegliedert, das Modell vom Diktat (ein Satz Stufen-Modelle fuer beide Wege).
 */
@Composable
fun StageScreen(stage: RefineMode, way: RefineWay, nav: NavState) {
    val prefs = LocalAppEnv.current.prefs
    SettingsPageScaffold(levelLabel(stage), nav) {
        UseCard(prefs, stage, way)
        when (stage) {
            RefineMode.OFF -> OffCard(prefs, way, nav)
            RefineMode.POLISH -> OptionCard(R.string.stage_cleanup, PolishCleanup.entries, prefs.polishCleanupFor(way), ::cleanupLabel, ::cleanupSubtitle) {
                prefs.setPolishCleanupFor(way, it)
            }
            RefineMode.SUMMARIZE -> OptionCard(R.string.stage_form, SummarizeForm.entries, prefs.summarizeFormFor(way), ::formLabel, ::formSubtitle) {
                prefs.setSummarizeFormFor(way, it)
            }
            else -> Unit
        }
        if (stage in RefineMode.PARAGRAPH_STAGES) {
            SectionCard(contentPadding = RowCardPadding) {
                if (way == RefineWay.DICTATION) {
                    SwitchRow(
                        headline = stringResource(R.string.stage_paragraphs),
                        supporting = stringResource(R.string.stage_paragraphs_sub),
                        checked = prefs.paragraphsFor(stage),
                        onCheckedChange = { prefs.setParagraphsFor(stage, it) },
                    )
                } else {
                    LoomRow(headline = stringResource(R.string.stage_paragraphs), supporting = stringResource(R.string.stage_paragraphs_share))
                }
            }
        }
        if (stage in RefineMode.MODEL_STAGES) ModelCard(prefs, stage, way, nav)
    }
}

/** Weg, Kurzbeschreibung und ob die Stufe dort gerade gilt — sonst der Knopf, der sie waehlt. */
@Composable
private fun UseCard(prefs: PrefsState, stage: RefineMode, way: RefineWay) {
    val dictation = way == RefineWay.DICTATION
    SectionCard(
        title = stringResource(if (dictation) R.string.text_card_refine else R.string.text_card_share),
        titleIcon = if (dictation) R.drawable.ic_mic else R.drawable.ic_voicemail,
        titleIconTint = if (dictation) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
    ) {
        Text(stringResource(levelSubtitle(stage, way)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (prefs.refineModeFor(way) == stage) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusIcon(Tone.SUCCESS)
                Text(stringResource(if (dictation) R.string.stage_active_dictation else R.string.stage_active_share), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            FilledTonalButton(onClick = { prefs.setRefineModeFor(way, stage) }) {
                Text(stringResource(if (dictation) R.string.stage_use_dictation else R.string.stage_use_share))
            }
        }
    }
}

/**
 * "Aus": was ohne KI mit dem Text geschieht, und der Weg zu Woerterbuch & Regeln. Beim Diktat gelten
 * die festen Regeln (Wert wie am Fusslink der Textverbesserung). Sprachnachrichten bleiben wortgetreu,
 * von dort wirkt nur die Fuellwort-Liste, und zwar fuer den Schalter im Fenster.
 */
@Composable
private fun OffCard(prefs: PrefsState, way: RefineWay, nav: NavState) {
    val dictation = way == RefineWay.DICTATION
    SectionCard(contentPadding = RowCardPadding, gap = 0.dp) {
        Text(
            stringResource(if (dictation) R.string.stage_off_dictation_info else R.string.stage_off_share_info),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        val value = if (dictation) dictionaryValue(prefs) else stringResource(R.string.stage_off_share_fillers)
        PageLinkRow(R.drawable.ic_checklist, stringResource(R.string.settings_group_dictionary), value) { nav.push(Screen.Dictionary) }
    }
}

/** Eine Einstellung der Stufe als Auswahl (Bereinigung, Form). Die einzige Auswahl-Gruppe der Seite. */
@Composable
private fun <T> OptionCard(title: Int, options: List<T>, current: T, label: (T) -> Int, subtitle: (T) -> Int, onSelect: (T) -> Unit) {
    SectionCard(title = stringResource(title), gap = 4.dp) {
        Column(Modifier.selectableGroup()) {
            options.forEach { option ->
                val selected = option == current
                LoomRow(
                    headline = stringResource(label(option)),
                    supporting = stringResource(subtitle(option)),
                    modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) { onSelect(option) },
                    trailing = { RadioButton(selected = selected, onClick = null) },
                )
            }
        }
    }
}

/**
 * Modell der Stufe. Beim Diktat waehlbar (gilt auch fuer Sprachnachrichten); bei Sprachnachrichten
 * nur genannt, die Zeile fuehrt zur Diktat-Seite. Kann der Zugang keinen Text verbessern oder fehlt
 * sein Modell, steht beim Diktat ein Hinweis mit dem Weg zum KI-Zugang.
 */
@Composable
private fun ModelCard(prefs: PrefsState, stage: RefineMode, way: RefineWay, nav: NavState) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val block = stageModelBlock(prefs)
    val chevron: @Composable () -> Unit = {
        LoomIcon(R.drawable.ic_chevron_right, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SectionCard(contentPadding = RowCardPadding, gap = 0.dp) {
        when {
            way == RefineWay.SHARE -> {
                val value = if (block == null) stringResource(R.string.stage_model_like_dictation, modelLabel(prefs.llmAccess(stage)))
                else stringResource(R.string.stage_model_like_dictation_plain)
                LoomRow(
                    headline = stringResource(R.string.stage_model),
                    supporting = value,
                    trailing = chevron,
                    onClick = { nav.push(Screen.Stage(stage, RefineWay.DICTATION)) },
                )
            }
            block != null -> {
                Text(
                    stringResource(if (block == StageModelBlock.NO_ACCESS) R.string.text_models_no_access else R.string.stage_model_no_model),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                PageLinkRow(R.drawable.ic_cloud, stringResource(R.string.settings_group_llm), accessValue(prefs)) { nav.push(Screen.LlmAccess) }
            }
            else -> {
                val value = stageValue(stage)
                LoomRow(
                    headline = stringResource(R.string.stage_model),
                    supporting = value,
                    trailing = chevron,
                    onClick = { picking = true },
                    stateDescription = value,
                )
                Text(
                    stringResource(R.string.stage_model_both),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
    if (picking) StageModelPicker(stage) { picking = false }
}

internal fun cleanupLabel(cleanup: PolishCleanup): Int = when (cleanup) {
    PolishCleanup.PLAIN -> R.string.stage_cleanup_plain
    PolishCleanup.CLEAN -> R.string.stage_cleanup_clean
    PolishCleanup.READABLE -> R.string.stage_cleanup_readable
}

internal fun cleanupSubtitle(cleanup: PolishCleanup): Int = when (cleanup) {
    PolishCleanup.PLAIN -> R.string.stage_cleanup_plain_sub
    PolishCleanup.CLEAN -> R.string.stage_cleanup_clean_sub
    PolishCleanup.READABLE -> R.string.stage_cleanup_readable_sub
}

private fun formLabel(form: SummarizeForm): Int = when (form) {
    SummarizeForm.AUTO -> R.string.stage_form_auto
    SummarizeForm.PROSE -> R.string.stage_form_prose
}

private fun formSubtitle(form: SummarizeForm): Int = when (form) {
    SummarizeForm.AUTO -> R.string.stage_form_auto_sub
    SummarizeForm.PROSE -> R.string.stage_form_prose_sub
}
