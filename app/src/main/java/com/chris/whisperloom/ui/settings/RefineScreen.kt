package com.chris.whisperloom.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.PolishCleanup
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.RefineWay
import com.chris.whisperloom.SummarizeForm
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.SectionCard
import com.chris.whisperloom.ui.components.SwitchRow
import com.chris.whisperloom.ui.components.levelLabel
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState

/** Die Gruppen der Seite (Tests: dieselben Stufen und Schalter stehen in beiden). */
const val DICTATION_REFINE_TAG = "dictation-refine"
const val SHARE_REFINE_TAG = "share-refine"

/**
 * Textverbesserung (3.9.0, vorher Text-Hub mit den Seiten Diktat und Sprachnachrichten): beide
 * Stufen-Gruppen auf einer Seite, die Schalter wie bisher bei ihrer Gruppe. Unter dem Diktat der Weg
 * zu Woerterbuch & Regeln; wo das Geraet offline erkennen kann, die Regel bei Offline-Erkennung
 * (zweiter Einstieg, die Regel selbst steht bei den Offline-Modellen).
 */
@Composable
fun RefineScreen(nav: NavState) {
    val env = LocalAppEnv.current
    val prefs = env.prefs

    SettingsPageScaffold(R.string.settings_group_refine, nav) {
        DictationStages(prefs) { nav.push(Screen.Dictionary) }
        ShareStages(prefs)
        if (env.status.offlineSupported) {
            SectionCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                PageLinkRow(R.drawable.ic_offline_bolt, stringResource(R.string.refine_offline), offlineValue()) {
                    nav.push(Screen.Models)
                }
            }
        }
    }
}

/**
 * "Beim Diktieren": Stufe und — bis die Stufen-Seiten kommen — die Bereinigung beim Glaetten als
 * zwei Schalter ("Lesbarer glaetten", "Fuellwoerter intelligent", schliessen sich aus) und die
 * Absaetze (gelten fuer alle Diktat-Stufen). Alles nur fuers Diktat.
 */
@Composable
private fun DictationStages(prefs: PrefsState, onRules: () -> Unit) {
    val off = prefs.refineMode == RefineMode.OFF

    SectionCard(
        modifier = Modifier.testTag(DICTATION_REFINE_TAG),
        title = stringResource(R.string.text_card_refine),
        titleIcon = R.drawable.ic_mic,
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
        ReadableSwitch(prefs, RefineWay.DICTATION)
        // Die Unterzeile nennt "Glaetten" schon — kein zweites "Wirkt mit der Stufe" daneben.
        CleanupSwitch(prefs, RefineWay.DICTATION, PolishCleanup.CLEAN, R.string.pref_smart_fillers, stringResource(R.string.pref_smart_fillers_info))
        SwitchRow(
            headline = stringResource(R.string.pref_refine_paragraphs),
            supporting = stringResource(if (off) R.string.text_smart_needs_level else R.string.pref_refine_paragraphs_info),
            checked = prefs.paragraphsFor(RefineMode.POLISH),
            onCheckedChange = { on ->
                RefineMode.PARAGRAPH_STAGES.forEach { prefs.setParagraphsFor(it, on) }
                prefs.setSummarizeFormFor(RefineWay.DICTATION, if (on) SummarizeForm.AUTO else SummarizeForm.PROSE)
            },
            enabled = !off,
        )
        PageLinkRow(R.drawable.ic_checklist, stringResource(R.string.settings_group_dictionary), dictionaryValue(prefs), onRules)
    }
}

/** "Bei geteilten Sprachnachrichten": eigene Stufe (ohne "Prompt") und eigene Bereinigung beim Glaetten. */
@Composable
private fun ShareStages(prefs: PrefsState) {
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
        Column(Modifier.selectableGroup()) {
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
        ReadableSwitch(prefs, RefineWay.SHARE)
    }
}

/** "Lesbarer glaetten" des Wegs; ohne "Glaetten" sagt die Unterzeile, wann es wirkt. */
@Composable
private fun ReadableSwitch(prefs: PrefsState, way: RefineWay) {
    val polish = prefs.refineModeFor(way) == RefineMode.POLISH
    val supporting = stringResource(if (polish) R.string.pref_polish_readable_info else R.string.text_readable_needs_polish)
    CleanupSwitch(prefs, way, PolishCleanup.READABLE, R.string.pref_polish_readable, supporting)
}

/**
 * Zwischenstand bis zu den Stufen-Seiten: eine Bereinigung des Wegs als Schalter. An = [cleanup],
 * aus = "Nur Zeichensetzung". Immer bedienbar (ein gesperrter Schalter taeuschte sonst ein "an" vor).
 */
@Composable
private fun CleanupSwitch(prefs: PrefsState, way: RefineWay, cleanup: PolishCleanup, headline: Int, supporting: String) {
    SwitchRow(
        headline = stringResource(headline),
        supporting = supporting,
        checked = prefs.polishCleanupFor(way) == cleanup,
        onCheckedChange = { prefs.setPolishCleanupFor(way, if (it) cleanup else PolishCleanup.PLAIN) },
    )
}

/** Unterzeile einer Stufe in den Radios von Diktat und Sprachnachrichten. */
internal fun levelSubtitle(mode: RefineMode): Int = when (mode) {
    RefineMode.OFF -> R.string.text_level_off_sub
    RefineMode.POLISH, RefineMode.READABLE -> R.string.text_level_smooth_sub
    RefineMode.BEAUTIFY -> R.string.text_level_beautify_sub
    RefineMode.SUMMARIZE -> R.string.text_level_summarize_sub
    RefineMode.PROMPT -> R.string.text_level_prompt_sub
}
