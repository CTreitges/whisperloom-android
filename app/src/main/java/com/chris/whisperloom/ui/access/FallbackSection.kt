package com.chris.whisperloom.ui.access

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.SetupState
import com.chris.whisperloom.Vocabulary
import com.chris.whisperloom.api.ApiAccess
import com.chris.whisperloom.api.Provider
import com.chris.whisperloom.api.ProviderCatalog
import com.chris.whisperloom.api.RefineBlock
import com.chris.whisperloom.api.ServerUrlCheck
import com.chris.whisperloom.ui.components.ApiKeyField
import com.chris.whisperloom.ui.components.LoomDropdown
import com.chris.whisperloom.ui.components.SwitchRow
import com.chris.whisperloom.ui.components.modelLabel
import com.chris.whisperloom.ui.components.providerLabel
import com.chris.whisperloom.ui.components.providerShortName
import com.chris.whisperloom.ui.state.LocalAppEnv

/**
 * Fallback der Spracherkennung (3.9.1) unter dem ersten Anbieter: ein Schalter, an zeigt er Anbieter,
 * Adresse (eigener Server), Key, Modell und "Fallback pruefen". Scheitert die Erkennung beim ersten
 * Anbieter, schickt WhisperLoom dieselbe Aufnahme an den Fallback ([com.chris.whisperloom.FallbackBackend]).
 */
@Composable
fun SttFallbackSection() {
    val prefs = LocalAppEnv.current.prefs
    val primary = prefs.sttAccess()
    val access = prefs.sttFallbackAccess()
    val provider = access.provider
    var showCustomModel by rememberSaveable { mutableStateOf(false) }

    FallbackFrame(
        enabled = prefs.sttFallbackEnabled,
        primary = providerShortName(primary.provider),
        ready = SetupState.sttFallbackReady(access),
        onEnabledChange = { on ->
            if (on && prefs.sttFallbackProviderId == primary.provider.id && untouched(prefs.sttFallbackUrl, prefs.sttFallbackKey, prefs.sttFallbackModel)) {
                prefs.sttFallbackProviderId = defaultFallback(ProviderCatalog.sttProviders, primary.provider).id
            }
            prefs.sttFallbackEnabled = on
        },
    ) {
        FallbackProviderFields(
            providers = ProviderCatalog.sttProviders,
            access = access,
            url = prefs.sttFallbackUrl,
            key = prefs.sttFallbackKey,
            keyFrom = primary.takeIf { it.provider.id == provider.id && prefs.sttFallbackKey.isBlank() && it.apiKey.isNotBlank() }
                ?.let { stringResource(R.string.fallback_key_from_stt) },
            onProvider = { p ->
                if (p.id != prefs.sttFallbackProviderId) {
                    // Wie beim ersten Anbieter: kein Key und kein Modell gehen an einen anderen Anbieter.
                    prefs.sttFallbackProviderId = p.id
                    prefs.sttFallbackUrl = ""
                    prefs.sttFallbackKey = ""
                    prefs.sttFallbackModel = ""
                }
            },
            onUrl = { prefs.sttFallbackUrl = it },
            onKey = { prefs.sttFallbackKey = it },
        )
        if (provider.isCustom) {
            OutlinedTextField(
                value = prefs.sttFallbackModel,
                onValueChange = { prefs.sttFallbackModel = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.fallback_model)) },
                placeholder = { Text("whisper-1") },
                singleLine = true,
                isError = prefs.sttFallbackModel.isBlank(),
                supportingText = { Text(stringResource(R.string.model_custom_info)) },
            )
        } else {
            LoomDropdown(
                label = stringResource(R.string.fallback_model),
                value = modelLabel(access),
                options = provider.sttModels,
                optionLabel = { it.label },
                onSelect = { prefs.sttFallbackModel = it.id },
                extraOption = stringResource(R.string.rec_model_custom),
                onExtra = { showCustomModel = true },
            )
        }
        TestAccessRow(label = stringResource(R.string.fallback_test)) {
            AccessTest.stt(prefs.sttFallbackAccess(), Vocabulary.prompt(Vocabulary.entries(prefs.apiPrompt)).text, prefs.language)
        }
        PrivacyLine(stringResource(R.string.fallback_stt_privacy, providerShortName(provider)))
    }

    if (showCustomModel) {
        CustomModelSheet(
            placeholder = "whisper-1",
            initial = if (provider.sttModel(access.model) == null) access.model else "",
            onApply = { prefs.sttFallbackModel = it },
            onDismiss = { showCustomModel = false },
        )
    }
}

/**
 * Fallback der Textverbesserung (3.9.1) unter dem KI-Zugang. Ein Modell fuer alle Stufen, ab Werk die
 * Empfehlung des Anbieters je Stufe — die Modelle je Stufe gehoeren zum ersten Anbieter. Scheitert
 * der KI-Zugang, verbessert der Fallback, erst danach (falls eingestellt) das lokale Textmodell.
 */
@Composable
fun LlmFallbackSection() {
    val prefs = LocalAppEnv.current.prefs
    val primary = prefs.llmAccess()
    // Offline ohne eigenen Zugang geht nie Text online, ElevenLabs "wie Erkennung" hat keinen Chat: dort
    // steht oben schon der Hinweis "Eigenen Zugang eintragen" — erst der, dann ein Fallback.
    if (primary.sameAsOffline || primary.refineBlock == RefineBlock.NO_CHAT) return
    val access = prefs.llmFallbackAccess()
    val provider = access.provider
    var showCustomModel by rememberSaveable { mutableStateOf(false) }
    val catalog = provider.llmModels.isNotEmpty()
    val recommended = stringResource(R.string.text_models_recommended)
    val recommendedSub = stringResource(
        R.string.text_models_recommended_sub,
        recommendedLabel(provider, RefineMode.POLISH),
        recommendedLabel(provider, RefineMode.BEAUTIFY),
    )

    FallbackFrame(
        enabled = prefs.llmFallbackEnabled,
        primary = providerShortName(primary.provider),
        ready = SetupState.llmReady(access),
        onEnabledChange = { on ->
            if (on && prefs.llmFallbackProviderId == primary.provider.id && untouched(prefs.llmFallbackUrl, prefs.llmFallbackKey, prefs.llmFallbackModel)) {
                prefs.llmFallbackProviderId = defaultFallback(ProviderCatalog.llmProviders, primary.provider).id
            }
            prefs.llmFallbackEnabled = on
        },
    ) {
        // Woher ein leerer Key kommt — dieselbe Reihenfolge wie AccessResolver.resolveLlmFallback.
        val keyFrom = when {
            prefs.llmFallbackKey.isNotBlank() || access.apiKey.isBlank() -> null
            prefs.llmUseOwn && primary.provider.id == provider.id -> stringResource(R.string.fallback_key_from_llm)
            else -> stringResource(R.string.fallback_key_from_stt)
        }
        FallbackProviderFields(
            providers = ProviderCatalog.llmProviders,
            access = access,
            url = prefs.llmFallbackUrl,
            key = prefs.llmFallbackKey,
            keyFrom = keyFrom,
            onProvider = { p ->
                if (p.id != prefs.llmFallbackProviderId) {
                    prefs.llmFallbackProviderId = p.id
                    prefs.llmFallbackUrl = ""
                    prefs.llmFallbackKey = ""
                    prefs.llmFallbackModel = ""
                }
            },
            onUrl = { prefs.llmFallbackUrl = it },
            onKey = { prefs.llmFallbackKey = it },
        )
        if (catalog) {
            LoomDropdown(
                label = stringResource(R.string.fallback_model),
                value = if (prefs.llmFallbackModel.isBlank()) recommended else modelLabel(access),
                options = listOf(null) + provider.llmModels,
                optionLabel = { it?.label ?: recommended },
                optionSupporting = { option -> recommendedSub.takeIf { option == null } },
                onSelect = { prefs.llmFallbackModel = it?.id.orEmpty() },
                extraOption = stringResource(R.string.text_model_custom),
                onExtra = { showCustomModel = true },
            )
        } else {
            // Eigener Server, Ollama lokal: kein Katalog, also keine Empfehlung — das Modell ist Pflicht.
            OutlinedTextField(
                value = prefs.llmFallbackModel,
                onValueChange = { prefs.llmFallbackModel = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.fallback_model)) },
                placeholder = { Text(stringResource(R.string.text_llm_model_placeholder)) },
                singleLine = true,
                isError = prefs.llmFallbackModel.isBlank(),
                supportingText = { Text(stringResource(R.string.model_custom_info)) },
            )
        }
        TestAccessRow(label = stringResource(R.string.fallback_test), enabled = access.model.isNotBlank()) {
            AccessTest.llm(prefs.llmFallbackAccess(), prefs.language, prefs.prefs.modelCache::rememberNoTemperature)
        }
        PrivacyLine(stringResource(R.string.fallback_llm_privacy, providerShortName(provider)))
    }

    if (showCustomModel) {
        CustomModelSheet(
            placeholder = stringResource(R.string.text_llm_model_placeholder),
            initial = if (provider.llmModel(prefs.llmFallbackModel) == null) prefs.llmFallbackModel else "",
            onApply = { prefs.llmFallbackModel = it },
            onDismiss = { showCustomModel = false },
        )
    }
}

/** Schalter "Fallback verwenden" und darunter, nur wenn an, die Felder. */
@Composable
private fun FallbackFrame(
    enabled: Boolean,
    primary: String,
    ready: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SwitchRow(
            headline = stringResource(R.string.fallback_switch),
            supporting = when {
                !enabled -> stringResource(R.string.fallback_switch_off, primary)
                // Unvollstaendig springt er nicht ein (Review 3.9.1) — das muss der Schalter sagen.
                !ready -> stringResource(R.string.fallback_incomplete)
                else -> stringResource(R.string.fallback_switch_on, primary)
            },
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
        AnimatedVisibility(visible = enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

/**
 * Anbieter, Adresse (nur wenn der Anbieter keine feste hat) und Key des Fallbacks.
 *
 * @param keyFrom woher ein leer gelassener Key kommt ("Key der Spracherkennung"); null = nirgends.
 */
@Composable
private fun FallbackProviderFields(
    providers: List<Provider>,
    access: ApiAccess,
    url: String,
    key: String,
    keyFrom: String?,
    onProvider: (Provider) -> Unit,
    onUrl: (String) -> Unit,
    onKey: (String) -> Unit,
) {
    val provider = access.provider
    val labels = providers.associate { it.id to providerLabel(it) }
    LoomDropdown(
        label = stringResource(R.string.fallback_provider),
        value = labels[provider.id] ?: provider.name,
        options = providers,
        optionLabel = { labels[it.id] ?: it.name },
        onSelect = onProvider,
        supportingText = if (!provider.needsUrl) ({ Text(access.baseUrl) }) else null,
    )
    if (provider.needsUrl) {
        val problem = if (url.isBlank()) null else ServerUrlCheck.check(url, provider)
        OutlinedTextField(
            value = url,
            onValueChange = onUrl,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.fallback_url)) },
            placeholder = { Text(if (provider.isOllama) "http://homeserver:11434" else "https://server:8000/v1") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            isError = access.baseUrl.isBlank() || problem?.severity == ServerUrlCheck.Severity.ERROR,
            supportingText = {
                Text(
                    when {
                        problem != null -> urlProblemText(problem)
                        provider.isOllama -> stringResource(R.string.text_ollama_url_hint)
                        else -> stringResource(R.string.rec_base_url_hint)
                    },
                )
            },
        )
    }
    ApiKeyField(
        value = key,
        onValueChange = onKey,
        optional = !provider.needsKey || keyFrom != null,
        label = stringResource(if (!provider.needsKey || keyFrom != null) R.string.fallback_key_optional else R.string.fallback_key),
    )
    if (keyFrom != null) {
        Text(keyFrom, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Noch nichts eingetragen: dann darf das Einschalten einen anderen Anbieter vorschlagen. */
private fun untouched(url: String, key: String, model: String) = url.isBlank() && key.isBlank() && model.isBlank()

/** Vorschlag beim ersten Einschalten: der erste andere Anbieter mit fester Adresse (OpenAI, sonst Groq …). */
internal fun defaultFallback(providers: List<Provider>, primary: Provider): Provider =
    providers.firstOrNull { it.id != primary.id && !it.needsUrl } ?: primary
