package com.chris.whisperloom.ui.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.ui.access.stageModels
import com.chris.whisperloom.ui.components.DetailScaffold
import com.chris.whisperloom.ui.components.HubRow
import com.chris.whisperloom.ui.components.ScrollColumn
import com.chris.whisperloom.ui.components.SectionHeader
import com.chris.whisperloom.ui.components.SnackController
import com.chris.whisperloom.ui.components.levelLabel
import com.chris.whisperloom.ui.components.modelLabel
import com.chris.whisperloom.ui.components.offlineModelLabel
import com.chris.whisperloom.ui.components.offlineRuleLabel
import com.chris.whisperloom.ui.components.providerShortName
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.models.offlineRule
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.TextSection
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState

/**
 * Text-Hub (3.8.6, vorher eine lange Seite): KI-Stufen (Diktat, Sprachnachrichten), Modelle & Zugang
 * (Online-Zugang mit Modell je Stufe, Offline-Erkennung nur wo offline geht), Ohne KI (Regeln).
 * Unterzeile = aktueller Wert, wie im Einstellungen-Hub.
 */
@Composable
fun TextSettingsScreen(nav: NavState) {
    val env = LocalAppEnv.current
    val prefs = env.prefs
    val offline = env.status.offlineSupported
    val snack = rememberSnack()
    fun open(section: TextSection) = nav.push(Screen.TextPage(section))

    val share = if (prefs.shareRefineMode == RefineMode.OFF) stringResource(R.string.text_hub_val_share_off)
    else stageLabel(prefs.shareRefineMode, prefs.sharePolishReadable)
    val rules = textRules(prefs).ifEmpty { listOf(stringResource(R.string.text_hub_val_rules_none)) }.joinToString(" · ")

    DetailScaffold(title = stringResource(R.string.text_title), onBack = { nav.pop() }, snack = snack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionHeader(stringResource(R.string.text_hub_section_stages)) }
            item {
                HubRow(R.drawable.ic_mic, stringResource(R.string.text_hub_dictation), stageLabel(prefs.refineMode, prefs.polishReadable)) {
                    open(TextSection.DICTATION)
                }
            }
            item { HubRow(R.drawable.ic_voicemail, stringResource(R.string.text_hub_share), share, divider = false) { open(TextSection.SHARE) } }

            item { SectionHeader(stringResource(R.string.text_hub_section_models)) }
            item { HubRow(R.drawable.ic_cloud, stringResource(R.string.text_hub_access), accessValue(prefs), divider = offline) { open(TextSection.ACCESS) } }
            if (offline) {
                item {
                    HubRow(R.drawable.ic_offline_bolt, stringResource(R.string.text_card_offline), offlineValue(), divider = false) {
                        open(TextSection.OFFLINE)
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.text_hub_section_rules)) }
            item { HubRow(R.drawable.ic_checklist, stringResource(R.string.text_card_rules), rules, divider = false) { open(TextSection.RULES) } }
        }
    }
}

/** Eine Unterseite von "Text" ([Screen.TextPage]). */
@Composable
fun TextPageScreen(section: TextSection, nav: NavState) = when (section) {
    TextSection.DICTATION -> TextDictationScreen(nav)
    TextSection.SHARE -> TextShareScreen(nav)
    TextSection.ACCESS -> TextAccessScreen(nav)
    TextSection.OFFLINE -> TextOfflineScreen(nav)
    TextSection.RULES -> TextRulesScreen(nav)
}

/** Geruest der Unterseiten: grosser Titel, Zurueck, scrollende Spalte. */
@Composable
internal fun TextPageScaffold(title: Int, nav: NavState, content: @Composable ColumnScope.(SnackController) -> Unit) {
    val snack = rememberSnack()
    DetailScaffold(title = stringResource(title), onBack = { nav.pop() }, snack = snack) { padding ->
        ScrollColumn(padding) { content(snack) }
    }
}

/** Irgendeine KI-Stufe an (Diktat oder Sprachnachrichten) — dann wirken KI-Fuellwoerter und die Offline-Regel. */
internal fun anyAiStage(prefs: PrefsState): Boolean =
    prefs.refineMode != RefineMode.OFF || prefs.shareRefineMode != RefineMode.OFF

/** Eingeschaltete Regeln ohne KI als Kurzform ("Füllwörter", "Groß-Schreibung", "Leerzeichen"). */
@Composable
internal fun textRules(prefs: PrefsState): List<String> = buildList {
    if (prefs.removeFillers) add(stringResource(R.string.settings_rule_fillers))
    if (prefs.autoCapitalize) add(stringResource(R.string.settings_rule_cap))
    if (prefs.trailingSpace) add(stringResource(R.string.settings_rule_space))
}

/** "Glätten · lesbarer" mit Schalter, sonst die Stufe. */
@Composable
private fun stageLabel(mode: RefineMode, readable: Boolean): String =
    if (mode == RefineMode.POLISH && readable) stringResource(R.string.text_hub_val_readable, levelLabel(mode)) else levelLabel(mode)

/** "Wie Erkennung · OpenAI · Empfehlung je Stufe", "Anthropic · 1 Stufe mit eigenem Modell" … */
@Composable
private fun accessValue(prefs: PrefsState): String {
    val llm = prefs.llmAccess()
    if (llm.sameAsOffline) return stringResource(R.string.text_hub_val_access_none)
    val access = if (prefs.llmUseOwn) providerShortName(llm.provider)
    else stringResource(R.string.text_hub_val_access_same, providerShortName(llm.provider))
    if (llm.refineBlock != null) return access
    // Ohne Modell im Zugang wirken Stufen-Modelle nicht (AccessResolver), also auch nicht mitzaehlen.
    val custom = if (llm.model.isBlank()) 0
    else stageModels(prefs.promptLevelEnabled).count { prefs.llmModelFor(it).isNotBlank() }
    val models = when {
        custom > 0 -> pluralStringResource(R.plurals.text_hub_val_models_custom, custom, custom)
        prefs.llmModel.isBlank() && llm.provider.llmModels.isNotEmpty() -> stringResource(R.string.text_models_recommended)
        else -> modelLabel(llm)
    }
    return listOf(access, models).filter { it.isNotBlank() }.joinToString(" · ")
}

/** "Lokales Textmodell · Gemma 4 E2B", ohne geladenes Modell "… · Textmodell fehlt". */
@Composable
private fun offlineValue(): String {
    val env = LocalAppEnv.current
    val rule = offlineRule(env.prefs, env.status)
    val label = offlineRuleLabel(rule)
    return when {
        rule == OfflineRefineRule.SKIP -> label
        env.status.textModelReady(env.prefs.localLlmModel) -> "$label · ${offlineModelLabel(env.prefs.localLlmModel)}"
        else -> stringResource(R.string.text_hub_val_offline_missing, label)
    }
}
