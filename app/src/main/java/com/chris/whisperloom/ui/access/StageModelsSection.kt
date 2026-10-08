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

/**
 * Abschnitt "Modell je Stufe" (3.8.6): je Stufe "Standard" oder ein eigenes Modell desselben Zugangs
 * (Diktat und Sprachnachrichten gemeinsam). "Prompt" nur mit Pro. Kann der Zugang keinen Text
 * verbessern (offline ohne eigenen Zugang, ElevenLabs) oder hat er kein Modell (Ollama lokal, eigener
 * Server, Together "wie Erkennung"), steht dort ein Hinweis: ohne Modell des Zugangs wirkt kein
 * Stufen-Modell ([com.chris.whisperloom.api.AccessResolver.resolveLlm]).
 */
@Composable
fun StageModelsSection() {
    val prefs = LocalAppEnv.current.prefs
    var picking by rememberSaveable { mutableStateOf<RefineMode?>(null) }
    var custom by rememberSaveable { mutableStateOf<RefineMode?>(null) }

    val llm = prefs.llmAccess()
    val hint = when {
        llm.refineBlock == RefineBlock.OFFLINE || llm.refineBlock == RefineBlock.NO_CHAT -> R.string.text_models_no_access
        llm.model.isBlank() -> R.string.text_models_no_model
        else -> null
    }
    if (hint != null) {
        Text(
            stringResource(hint),
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
            onClick = { picking = stage },
            stateDescription = value,
        )
    }
    Text(
        stringResource(R.string.text_models_reset_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    picking?.let { stage ->
        StageModelSheet(
            stage = stage,
            onCustom = {
                picking = null
                custom = stage
            },
            onDismiss = { picking = null },
        )
    }
    custom?.let { stage ->
        val own = prefs.llmModelFor(stage)
        CustomModelSheet(
            placeholder = stringResource(R.string.text_llm_model_placeholder),
            initial = if (llm.provider.llmModel(own) == null) own else "",
            onApply = { prefs.setLlmModelFor(stage, it) },
            onDismiss = { custom = null },
        )
    }
}

/** Die Zeilen von "Modell je Stufe" — "Prompt" nur fuer die, die die Stufe eingeschaltet haben. */
internal fun stageModels(promptEnabled: Boolean): List<RefineMode> =
    RefineMode.MODEL_STAGES.filter { it != RefineMode.PROMPT || promptEnabled }

/** "Standard · Claude Haiku 5.5" oder das eigene Modell der Stufe. */
@Composable
private fun stageValue(stage: RefineMode): String {
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
