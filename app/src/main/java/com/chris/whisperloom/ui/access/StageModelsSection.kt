package com.chris.whisperloom.ui.access

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.api.ModelKind
import com.chris.whisperloom.api.RefineBlock
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.levelLabel
import com.chris.whisperloom.ui.components.modelLabel
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState

/**
 * Abschnitt "Modell je Stufe" im KI-Zugang, seit 3.9.0 eine kompakte Uebersicht: je Stufe das Modell,
 * mit dem sie rechnet ("Standard" oder ein eigenes desselben Zugangs). Gewaehlt wird auf der Seite der
 * Stufe beim Diktat ([onStage]); das Modell gilt fuer Diktat und Sprachnachrichten gemeinsam. "Prompt"
 * nur mit Pro. Kann der Zugang keinen Text verbessern oder hat er kein Modell, steht dort ein Hinweis
 * ([stageModelBlock]).
 */
@Composable
fun StageModelsSection(onStage: (RefineMode) -> Unit) {
    val prefs = LocalAppEnv.current.prefs
    val block = stageModelBlock(prefs)
    if (block != null) {
        Text(
            stringResource(if (block == StageModelBlock.NO_ACCESS) R.string.text_models_no_access else R.string.text_models_no_model),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Text(
        stringResource(R.string.text_models_intro),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    stageModels(prefs.promptLevelEnabled).forEach { stage ->
        val value = stageValue(stage)
        LoomRow(
            headline = levelLabel(stage),
            supporting = value,
            trailing = { LoomIcon(R.drawable.ic_chevron_right, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = { onStage(stage) },
            stateDescription = value,
        )
    }
    Text(
        stringResource(R.string.text_models_reset_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Warum es kein Modell je Stufe zu waehlen gibt. */
internal enum class StageModelBlock {
    /** Offline ohne eigenen Zugang, ElevenLabs: der Zugang kann keinen Text verbessern. */
    NO_ACCESS,

    /**
     * Ollama lokal, eigener Server, Together "wie Erkennung": ohne Modell des Zugangs wirkt kein
     * Stufen-Modell ([com.chris.whisperloom.api.AccessResolver.resolveLlm]).
     */
    NO_MODEL,
}

/** null = je Stufe laesst sich ein Modell waehlen. */
internal fun stageModelBlock(prefs: PrefsState): StageModelBlock? {
    val llm = prefs.llmAccess()
    return when {
        llm.refineBlock == RefineBlock.OFFLINE || llm.refineBlock == RefineBlock.NO_CHAT -> StageModelBlock.NO_ACCESS
        llm.model.isBlank() -> StageModelBlock.NO_MODEL
        else -> null
    }
}

/** Das eigene Modell der Stufe ("Claude Opus 5.5"), wenn sie eins hat und es wirkt; sonst null ("Standard"). */
internal fun ownStageModel(prefs: PrefsState, stage: RefineMode): String? =
    modelLabel(prefs.llmAccess(stage)).takeIf { prefs.llmModelFor(stage).isNotBlank() && stageModelBlock(prefs) == null }

/**
 * Modell einer Stufe waehlen (Stufen-Seite): das Auswahl-Sheet, "Eigenes Modell …" ersetzt es durch
 * das Eingabe-Sheet. [onDismiss], sobald das jeweils offene Sheet zu ist.
 */
@Composable
internal fun StageModelPicker(stage: RefineMode, onDismiss: () -> Unit) {
    val prefs = LocalAppEnv.current.prefs
    var custom by rememberSaveable { mutableStateOf(false) }
    if (custom) {
        val own = prefs.llmModelFor(stage)
        CustomModelSheet(
            placeholder = stringResource(R.string.text_llm_model_placeholder),
            initial = if (prefs.llmAccess().provider.llmModel(own) == null) own else "",
            onApply = { prefs.setLlmModelFor(stage, it) },
            onDismiss = onDismiss,
        )
    } else {
        StageModelSheet(stage = stage, onCustom = { custom = true }, onDismiss = onDismiss)
    }
}

/** Die Zeilen von "Modell je Stufe" — "Prompt" nur fuer die, die die Stufe eingeschaltet haben. */
internal fun stageModels(promptEnabled: Boolean): List<RefineMode> =
    RefineMode.MODEL_STAGES.filter { it != RefineMode.PROMPT || promptEnabled }

/** "Standard · Claude Haiku 5.5" oder das eigene Modell der Stufe. */
@Composable
internal fun stageValue(stage: RefineMode): String {
    val prefs = LocalAppEnv.current.prefs
    if (prefs.llmModelFor(stage).isNotBlank()) return modelLabel(prefs.llmAccess(stage))
    return stringResource(R.string.text_models_standard, modelLabel(prefs.standardLlmAccess(stage)))
}

/**
 * Auswahl fuer eine Stufe: Standard, Katalog, Server-Liste (Ollama immer, sonst mit Pro wie im Zugang),
 * "Eigenes Modell …" und "Modell pruefen" fuer das Modell, mit dem die Stufe gerade rechnet.
 */
@Composable
private fun StageModelSheet(stage: RefineMode, onCustom: () -> Unit, onDismiss: () -> Unit) {
    val prefs = LocalAppEnv.current.prefs
    val llm = prefs.llmAccess()
    val provider = llm.provider
    val server = rememberServerModels(llm, ModelKind.LLM)
    ModelPickerSheet(
        recommended = provider.llmModels,
        server = server.entry.takeIf { provider.hasLlm && (prefs.serverModelsEnabled || provider.isOllama) },
        selected = prefs.llmModelFor(stage),
        onSelect = { prefs.setLlmModelFor(stage, it) },
        onCustom = onCustom,
        onDismiss = onDismiss,
        title = stringResource(R.string.text_models_pick_title, levelLabel(stage)),
        standard = stringResource(R.string.text_models_standard_option, modelLabel(prefs.standardLlmAccess(stage))),
        footer = {
            TestAccessRow(label = stringResource(R.string.text_models_test)) {
                AccessTest.llm(prefs.llmAccess(stage), prefs.language, prefs.prefs.modelCache::rememberNoTemperature)
            }
        },
    )
}
