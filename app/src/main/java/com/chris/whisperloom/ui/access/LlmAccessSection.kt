package com.chris.whisperloom.ui.access

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.Engine
import com.chris.whisperloom.R
import com.chris.whisperloom.api.AccessResolver
import com.chris.whisperloom.api.OllamaApi
import com.chris.whisperloom.api.Provider
import com.chris.whisperloom.api.ProviderCatalog
import com.chris.whisperloom.api.ServerUrlCheck
import com.chris.whisperloom.ui.components.ApiKeyField
import com.chris.whisperloom.ui.components.InfoCard
import com.chris.whisperloom.ui.components.SnackController
import com.chris.whisperloom.ui.components.SwitchRow
import com.chris.whisperloom.ui.components.LoomDropdown
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.modelLabel
import com.chris.whisperloom.ui.components.providerLabel
import com.chris.whisperloom.ui.components.providerShortName
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.theme.loom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Karte "Zugang fuer die Textverbesserung" (E2, Spec §2.5): Schalter, eigener Anbieter, Modell, Test. */
@Composable
fun LlmAccessSection(snack: SnackController) {
    val prefs = LocalAppEnv.current.prefs
    val stt = prefs.sttAccess()
    val llm = prefs.llmAccess()
    val provider = llm.provider
    val useOwn = prefs.llmUseOwn
    val offlineWithoutOwn = prefs.engine == Engine.OFFLINE && !useOwn
    val providers = ProviderCatalog.llmProviders
    val labels = providers.associate { it.id to providerLabel(it) }
    var showKeySheet by rememberSaveable { mutableStateOf(false) }
    var showCustomModel by rememberSaveable { mutableStateOf(false) }
    // Modelle, die der Ollama-Server gemeldet hat; bei Anbieter- oder Adresswechsel verworfen.
    var serverModels by remember(provider.id, llm.baseUrl) { mutableStateOf(emptyList<String>()) }
    var loadingModels by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val res = LocalResources.current

    // Ollama verbunden (Adresse da, bei der Cloud auch der Key): Modell-Liste still im Hintergrund
    // holen, damit das Auswahlfeld sofort die Server-Modelle zeigt. Die Pause entprellt das
    // Tippen in Adress-/Key-Feld; ein Fehler bleibt hier stumm (der Knopf meldet ihn).
    val ollamaConnected = useOwn && provider.isOllama && llm.baseUrl.isNotBlank() &&
        (!provider.needsKey || llm.apiKey.isNotBlank())
    LaunchedEffect(ollamaConnected, llm.baseUrl, llm.apiKey) {
        if (!ollamaConnected) return@LaunchedEffect
        delay(OLLAMA_AUTOLOAD_DELAY_MS)
        val names = withContext(Dispatchers.IO) { runCatching { OllamaApi.listModels(prefs.llmAccess()) }.getOrNull() }
        if (!names.isNullOrEmpty()) {
            serverModels = names
            if (prefs.llmModel.isBlank() && provider.llmModels.isEmpty()) prefs.llmModel = names.first()
        }
    }

    // Eigener Zugang: den Erkennungs-Anbieter uebernehmen, wenn er Textmodelle hat, sonst OpenAI.
    fun switchToOwn() {
        prefs.llmProviderId = if (stt.provider.hasLlm) stt.provider.id else ProviderCatalog.OPENAI_ID
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SwitchRow(
            headline = stringResource(R.string.text_own_access),
            supporting = if (useOwn) stringResource(R.string.text_own_access_on)
            else stringResource(R.string.text_own_access_off, providerShortName(stt.provider)),
            checked = useOwn,
            onCheckedChange = { on -> if (on) switchToOwn() else prefs.llmProviderId = AccessResolver.LLM_SAME },
        )

        AnimatedVisibility(visible = useOwn) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LoomDropdown(
                    label = stringResource(R.string.text_llm_provider),
                    value = labels[provider.id] ?: provider.name,
                    options = providers,
                    optionLabel = { labels[it.id] ?: it.name },
                    onSelect = { p ->
                        if (p.id != prefs.llmProviderId) {
                            // Kein Key darf versehentlich an einen anderen Anbieter gehen.
                            prefs.llmProviderId = p.id
                            prefs.llmUrl = ""
                            prefs.llmKey = ""
                            prefs.llmModel = ""
                        }
                    },
                    supportingText = if (!provider.needsUrl) ({ Text(llm.baseUrl) }) else null,
                )
                if (provider.needsUrl) {
                    val problem = if (prefs.llmUrl.isBlank()) null else ServerUrlCheck.check(prefs.llmUrl, provider)
                    OutlinedTextField(
                        value = prefs.llmUrl,
                        onValueChange = { prefs.llmUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(if (provider.isOllama) R.string.text_ollama_url else R.string.rec_base_url)) },
                        placeholder = { Text(if (provider.isOllama) "http://homeserver:11434" else "http://server:11434/v1") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        // Leer = Pflichtfeld offen: ohne URL ginge die Anfrage an "/chat/completions".
                        isError = prefs.llmUrl.isBlank() || problem?.severity == ServerUrlCheck.Severity.ERROR,
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
                ApiKeyField(value = prefs.llmKey, onValueChange = { prefs.llmKey = it }, optional = !provider.needsKey)
                ProviderNote(provider)
                TextButton(onClick = { showKeySheet = true }) {
                    LoomIcon(R.drawable.ic_help, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.rec_key_where))
                }
            }
        }

        when {
            offlineWithoutOwn -> InfoCard(
                text = stringResource(R.string.text_needs_online),
                icon = R.drawable.ic_warning,
                container = MaterialTheme.loom.warningContainer,
                onContainer = MaterialTheme.loom.onWarningContainer,
                action = {
                    FilledTonalButton(onClick = { switchToOwn() }) { Text(stringResource(R.string.text_add_access)) }
                },
            )
            provider.isOllama && (serverModels.isNotEmpty() || provider.llmModels.isNotEmpty()) -> LoomDropdown(
                label = stringResource(R.string.text_llm_model),
                value = if (llm.model.isBlank()) "" else modelLabel(llm),
                options = (provider.llmModels.map { it.id } + serverModels).distinct(),
                optionLabel = { id -> provider.llmModel(id)?.label ?: id },
                onSelect = { prefs.llmModel = it },
                extraOption = stringResource(R.string.text_model_custom),
                onExtra = { showCustomModel = true },
            )
            provider.llmModels.isEmpty() -> OutlinedTextField(
                value = prefs.llmModel,
                onValueChange = { prefs.llmModel = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.text_llm_model)) },
                placeholder = { Text(stringResource(R.string.text_llm_model_placeholder)) },
                singleLine = true,
                isError = llm.model.isBlank(),
                supportingText = { Text(stringResource(R.string.model_custom_info)) },
            )
            else -> LoomDropdown(
                label = stringResource(R.string.text_llm_model),
                value = modelLabel(llm),
                options = provider.llmModels,
                optionLabel = { it.label },
                onSelect = { prefs.llmModel = it.id },
                extraOption = stringResource(R.string.text_model_custom),
                onExtra = { showCustomModel = true },
            )
        }

        if (provider.isOllama && useOwn) {
            TextButton(
                enabled = !loadingModels && llm.baseUrl.isNotBlank(),
                onClick = {
                    loadingModels = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { OllamaApi.listModels(prefs.llmAccess()) } }
                        loadingModels = false
                        result.onSuccess { names ->
                            serverModels = names
                            // Lokal gibt es kein Default-Modell: das erste gefundene uebernehmen.
                            if (names.isNotEmpty() && prefs.llmModel.isBlank() && provider.llmModels.isEmpty()) {
                                prefs.llmModel = names.first()
                            }
                            snack.show(
                                if (names.isEmpty()) res.getString(R.string.text_ollama_no_models)
                                else res.getQuantityString(R.plurals.text_ollama_models_found, names.size, names.size),
                            )
                        }.onFailure { e ->
                            snack.show(res.getString(R.string.text_ollama_models_failed, e.message ?: e.javaClass.simpleName))
                        }
                    }
                },
            ) {
                LoomIcon(R.drawable.ic_download_for_offline, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (loadingModels) R.string.text_ollama_loading else R.string.text_ollama_load_models))
            }
        }

        TestAccessRow(label = stringResource(R.string.text_test), enabled = !offlineWithoutOwn) {
            AccessTest.llm(prefs.llmAccess(), prefs.language)
        }
    }

    if (showKeySheet) KeySheet(providers, snack) { showKeySheet = false }
    if (showCustomModel) {
        CustomModelSheet(
            placeholder = stringResource(R.string.text_llm_model_placeholder),
            initial = if (llm.modelOption == null) llm.model else "",
            onApply = { prefs.llmModel = it },
            onDismiss = { showCustomModel = false },
        )
    }
}

/** Wartezeit nach der letzten Eingabe, bevor die Ollama-Modelle automatisch geladen werden. */
private const val OLLAMA_AUTOLOAD_DELAY_MS = 700L

/** Hinweis-Chips zu Gemini (Training), DeepSeek (China), Anthropic (Kompatibilitaetsschicht). */
@Composable
private fun ProviderNote(provider: Provider) {
    val loom = MaterialTheme.loom
    when (provider.id) {
        "gemini" -> InfoCard(stringResource(R.string.text_gemini_warning), icon = R.drawable.ic_warning, container = loom.warningContainer, onContainer = loom.onWarningContainer)
        "deepseek" -> InfoCard(stringResource(R.string.text_deepseek_warning), icon = R.drawable.ic_warning, container = loom.warningContainer, onContainer = loom.onWarningContainer)
        "anthropic" -> InfoCard(stringResource(R.string.text_anthropic_note))
        ProviderCatalog.OLLAMA_ID -> InfoCard(stringResource(R.string.text_ollama_local_note))
        ProviderCatalog.OLLAMA_CLOUD_ID -> InfoCard(stringResource(R.string.text_ollama_cloud_note))
    }
}
