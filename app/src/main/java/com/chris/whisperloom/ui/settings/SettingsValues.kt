package com.chris.whisperloom.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.chris.whisperloom.Engine
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.R
import com.chris.whisperloom.Vocabulary
import com.chris.whisperloom.ui.access.stageModels
import com.chris.whisperloom.ui.components.levelLabel
import com.chris.whisperloom.ui.components.modelLabel
import com.chris.whisperloom.ui.components.offlineModelLabel
import com.chris.whisperloom.ui.components.offlineRuleLabel
import com.chris.whisperloom.ui.components.offlineRuleShort
import com.chris.whisperloom.ui.components.providerShortName
import com.chris.whisperloom.ui.models.offlineRule
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState

// Unterzeilen der Einstellungen (Hub und Verweis-Zeilen): immer der aktuelle Wert, nie ein Schalter.

/**
 * "Diktat: Glätten · Sprachnachrichten: Aus"; offline erkannt mit einer KI-Stufe dazu die Regel als
 * Kurzform ("… · lokal bei Offline").
 */
@Composable
internal fun refineValue(prefs: PrefsState): String {
    val env = LocalAppEnv.current
    val stages = stringResource(R.string.settings_val_refine, levelLabel(prefs.refineMode), levelLabel(prefs.shareRefineMode))
    return if (prefs.engine == Engine.OFFLINE && anyAiStage(prefs)) "$stages · ${offlineRuleShort(offlineRule(prefs, env.status))}" else stages
}

/** "2 Begriffe · Datei: namen.md · Füllwörter · Groß-Schreibung". */
@Composable
internal fun dictionaryValue(prefs: PrefsState): String = (listOf(vocabValue(prefs)) + textRules(prefs)).joinToString(" · ")

/** Vokabular als Kurzform: "Noch keine Begriffe", "2 Begriffe · Datei: namen.md". */
@Composable
internal fun vocabValue(prefs: PrefsState): String {
    val count = Vocabulary.entries(prefs.apiPrompt).size
    return listOfNotNull(
        if (count == 0) stringResource(R.string.vocab_none) else pluralStringResource(R.plurals.vocab_count, count, count),
        prefs.vocabFileName.takeIf { prefs.vocabFileUri.isNotBlank() }
            ?.let { stringResource(R.string.vocab_row_file, it.ifBlank { "…" }) },
    ).joinToString(" · ")
}

/** Eingeschaltete feste Regeln ohne KI als Kurzform ("Füllwörter", "Groß-Schreibung"). */
@Composable
internal fun textRules(prefs: PrefsState): List<String> = buildList {
    if (prefs.removeFillers) add(stringResource(R.string.settings_rule_fillers))
    if (prefs.autoCapitalize) add(stringResource(R.string.settings_rule_cap))
}

/** "Wie Erkennung · OpenAI · Empfehlung je Stufe", "Anthropic · 1 Stufe mit eigenem Modell" … */
@Composable
internal fun accessValue(prefs: PrefsState): String {
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

/** Regel bei Offline-Erkennung: "Lokales Textmodell · Gemma 4 E2B", ohne geladenes Modell "… · Textmodell fehlt". */
@Composable
internal fun offlineValue(): String {
    val env = LocalAppEnv.current
    val rule = offlineRule(env.prefs, env.status)
    val label = offlineRuleLabel(rule)
    return when {
        rule == OfflineRefineRule.SKIP -> label
        env.status.textModelReady(env.prefs.localLlmModel) -> "$label · ${offlineModelLabel(env.prefs.localLlmModel)}"
        else -> stringResource(R.string.text_hub_val_offline_missing, label)
    }
}
