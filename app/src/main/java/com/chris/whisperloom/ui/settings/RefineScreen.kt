package com.chris.whisperloom.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.PolishCleanup
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.RefineWay
import com.chris.whisperloom.SummarizeForm
import com.chris.whisperloom.ui.access.ownStageModel
import com.chris.whisperloom.ui.components.SectionCard
import com.chris.whisperloom.ui.components.StageRow
import com.chris.whisperloom.ui.components.levelLabel
import com.chris.whisperloom.ui.components.stageList
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState

/** Die Gruppen der Seite (Tests: dieselben Stufen stehen in beiden). */
const val DICTATION_REFINE_TAG = "dictation-refine"
const val SHARE_REFINE_TAG = "share-refine"

/**
 * Textverbesserung (3.9.0, vorher Text-Hub mit den Seiten Diktat und Sprachnachrichten): die Stufen
 * fuer Diktat und Sprachnachrichten, jede mit eigener Seite ([StageRow]), auch "Aus". Unter dem
 * Diktat der Weg zu Woerterbuch & Regeln; wo das Geraet offline erkennen kann, die Regel bei
 * Offline-Erkennung (zweiter Einstieg, die Regel selbst steht bei den Offline-Modellen).
 */
@Composable
fun RefineScreen(nav: NavState) {
    val env = LocalAppEnv.current
    val prefs = env.prefs

    SettingsPageScaffold(R.string.settings_group_refine, nav) {
        SectionCard(
            modifier = Modifier.testTag(DICTATION_REFINE_TAG),
            title = stringResource(R.string.text_card_refine),
            titleIcon = R.drawable.ic_mic,
            titleIconTint = MaterialTheme.colorScheme.tertiary,
            gap = 4.dp,
        ) {
            StageList(prefs, RefineWay.DICTATION, RefineMode.settings(prefs.promptLevelEnabled), nav)
            Text(
                stringResource(R.string.text_level_cost),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PageLinkRow(R.drawable.ic_checklist, stringResource(R.string.settings_group_dictionary), dictionaryValue(prefs)) {
                nav.push(Screen.Dictionary)
            }
        }
        // Eigene Stufe ohne "Prompt": nie fuer eine fremde Nachricht.
        SectionCard(
            modifier = Modifier.testTag(SHARE_REFINE_TAG),
            title = stringResource(R.string.text_card_share),
            titleIcon = R.drawable.ic_voicemail,
            gap = 4.dp,
        ) {
            Text(
                stringResource(R.string.text_share_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StageList(prefs, RefineWay.SHARE, RefineMode.SETTINGS, nav)
        }
        if (env.status.offlineSupported) {
            SectionCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                PageLinkRow(R.drawable.ic_offline_bolt, stringResource(R.string.refine_offline), offlineValue()) {
                    nav.push(Screen.Models)
                }
            }
        }
    }
}

/** Die Stufen eines Wegs, jede eine [StageRow]: der Text oeffnet ihre Seite, die Zone rechts waehlt sie. */
@Composable
private fun StageList(prefs: PrefsState, way: RefineWay, stages: List<RefineMode>, nav: NavState) {
    val current = prefs.refineModeFor(way)
    Column(Modifier.stageList(stages.size)) {
        stages.forEachIndexed { index, stage ->
            StageRow(
                headline = levelLabel(stage),
                supporting = stageSupporting(prefs, stage, way),
                selected = current == stage,
                selectLabel = stringResource(useLabel(way), levelLabel(stage)),
                index = index,
                onOpen = { nav.push(Screen.Stage(stage, way)) },
                onSelect = { prefs.setRefineModeFor(way, stage) },
            )
        }
    }
}

/** TalkBack-Name der Auswahl-Zone: "Glaetten fuer Diktat verwenden". */
private fun useLabel(way: RefineWay): Int = when (way) {
    RefineWay.DICTATION -> R.string.stage_use_dictation_cd
    RefineWay.SHARE -> R.string.stage_use_share_cd
}

/**
 * Unterzeile einer Stufe: die Kurzbeschreibung, dahinter, was vom Standard abweicht
 * ("… Inhalt unverändert · Lesbar · Claude Opus 5.5"; der Schlusspunkt entfaellt dann).
 */
@Composable
internal fun stageSupporting(prefs: PrefsState, stage: RefineMode, way: RefineWay): String {
    val description = stringResource(levelSubtitle(stage, way))
    val state = stageState(prefs, stage, way)
    return if (state.isEmpty()) description else (listOf(description.removeSuffix(".")) + state).joinToString(" · ")
}

/** Abweichungen vom Standard der Stufe auf dem Weg; das Modell gilt fuer beide Wege. */
@Composable
private fun stageState(prefs: PrefsState, stage: RefineMode, way: RefineWay): List<String> = buildList {
    if (stage == RefineMode.POLISH) {
        prefs.polishCleanupFor(way).takeIf { it != PolishCleanup.PLAIN }?.let { add(stringResource(cleanupLabel(it))) }
    }
    if (stage == RefineMode.SUMMARIZE && prefs.summarizeFormFor(way) == SummarizeForm.PROSE) {
        add(stringResource(R.string.stage_form_prose))
    }
    if (way == RefineWay.DICTATION && stage in RefineMode.PARAGRAPH_STAGES && !prefs.paragraphsFor(stage)) {
        add(stringResource(R.string.stage_paragraphs_off))
    }
    ownStageModel(prefs, stage)?.let { add(it) }
}

/** Unterzeile einer Stufe ohne ihre Einstellungen (Liste, Kopf der Stufen-Seite); nur "Aus" je Weg. */
internal fun levelSubtitle(mode: RefineMode, way: RefineWay): Int = when (mode) {
    RefineMode.OFF -> if (way == RefineWay.DICTATION) R.string.text_level_off_sub else R.string.text_share_off_sub
    RefineMode.POLISH, RefineMode.READABLE -> R.string.text_level_smooth_sub
    RefineMode.BEAUTIFY -> R.string.text_level_beautify_sub
    RefineMode.SUMMARIZE -> R.string.text_level_summarize_sub
    RefineMode.PROMPT -> R.string.text_level_prompt_sub
}
