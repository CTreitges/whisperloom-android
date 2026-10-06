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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.Engine
import com.chris.whisperloom.ModelCache
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.R
import com.chris.whisperloom.api.AccessResolver
import com.chris.whisperloom.api.ModelKind
import com.chris.whisperloom.api.Provider
import com.chris.whisperloom.api.ProviderCatalog
import com.chris.whisperloom.api.RefineBlock
import com.chris.whisperloom.api.ServerUrlCheck
import com.chris.whisperloom.ui.components.ApiKeyField
import com.chris.whisperloom.ui.components.InfoCard
import com.chris.whisperloom.ui.components.SnackController
import com.chris.whisperloom.ui.components.SwitchRow
import com.chris.whisperloom.ui.components.LoomDropdown
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.LoomPickerField
import com.chris.whisperloom.ui.components.modelLabel
import com.chris.whisperloom.ui.components.providerLabel
import com.chris.whisperloom.ui.components.providerShortName
import com.chris.whisperloom.ui.models.offlineRule
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.theme.loom

/**
 * Karte "Online-Zugang fuer die Textverbesserung" (E2, Spec §2.5): Schalter, eigener Anbieter, Modell, Test.
 * Ollama laedt seine Modelle immer vom Server; mit Pro "Modelle vom Server" jeder Anbieter
 * ([ModelPickerSheet]).
 */
@Composable
fun LlmAccessSection(snack: SnackController) {
    val prefs = LocalAppEnv.current.prefs
    val status = LocalAppEnv.current.status
    val stt = prefs.sttAccess()
    val llm = prefs.llmAccess()
    val provider = llm.provider
    val useOwn = prefs.llmUseOwn
    val offlineWithoutOwn = prefs.engine == Engine.OFFLINE && !useOwn
    // ElevenLabs hat keinen Chat — "wie Erkennung" hiesse dort: keine Textverbesserung. Together
    // und DeepInfra koennen Chat, nur ohne Katalog-Modell: dort bleibt das freie Modellfeld.
    val noChatWithoutOwn = !useOwn && llm.refineBlock == RefineBlock.NO_CHAT
    val noLlmWithoutOwn = offlineWithoutOwn || noChatWithoutOwn
    val providers = ProviderCatalog.llmProviders
    val labels = providers.associate { it.id to providerLabel(it) }
    var showKeySheet by rememberSaveable { mutableStateOf(false) }
    var showCustomModel by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val pro = prefs.serverModelsEnabled
    // Modelle vom Server (Cache je Anbieter und Adresse); Anbieter- oder Adresswechsel = andere Liste.
    val server = rememberServerModels(llm, ModelKind.LLM)
    // Together/DeepInfra "wie Erkennung": ihre Liste taugt nur fuer die Erkennung (ModelLists) —
    // also kein Picker, sondern das freie Feld wie ohne Pro.
    val pickable = pro && provider.hasLlm
    val loadable = !noLlmWithoutOwn && (pickable || provider.isOllama)

    // Lokal gibt es kein Default-Modell: das erste gefundene uebernehmen.
    val takeFirst: (ModelCache.Entry) -> Unit = { e ->
        if (provider.isOllama && e.models.isNotEmpty() && prefs.llmModel.isBlank() && provider.llmModels.isEmpty()) {
            prefs.llmModel = e.models.first().id
        }
    }
    // Zugang steht (Adresse da, bei Bedarf der Key): Liste still nachladen — Ollama bei jedem
    // Oeffnen wie bisher, damit das Auswahlfeld sofort die Server-Modelle zeigt; mit Pro jeder
    // andere Anbieter, wenn die Liste fehlt oder aelter als ein Tag ist.
    AutoLoadModels(
        server,
        ready = loadable && llm.baseUrl.isNotBlank() && (!provider.needsKey || llm.apiKey.isNotBlank()),
        apiKey = llm.apiKey,
        always = provider.isOllama,
        onLoaded = takeFirst,
    ) { prefs.llmAccess() }

    // Eigener Zugang: den Erkennungs-Anbieter uebernehmen, wenn er Textmodelle hat, sonst OpenAI.
    // Alte Felder leeren wie beim Anbieterwechsel: sonst ginge nach aus/an z. B. die Ollama-Adresse
    // mit dem Groq-Key (oder der ollama.com-Key an Groq) raus — Review 3.5.0, HOCH.
    fun switchToOwn() {
        prefs.llmProviderId = if (stt.provider.hasLlm) stt.provider.id else ProviderCatalog.OPENAI_ID
        prefs.llmUrl = ""
        prefs.llmKey = ""
        prefs.llmModel = ""
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
            offlineWithoutOwn -> OfflineAccessNote(offlineRule(prefs, status)) { switchToOwn() }
            noChatWithoutOwn -> InfoCard(
                text = stringResource(R.string.text_no_llm, providerShortName(stt.provider)),
                icon = R.drawable.ic_warning,
                container = MaterialTheme.loom.warningContainer,
                onContainer = MaterialTheme.loom.onWarningContainer,
                action = {
                    FilledTonalButton(onClick = { switchToOwn() }) { Text(stringResource(R.string.text_add_access)) }
                },
            )
            pickable -> LoomPickerField(
                label = stringResource(R.string.text_llm_model),
                value = modelLabel(llm),
                onClick = { showPicker = true },
                isError = llm.model.isBlank(),
                supportingText = if (provider.llmModels.isEmpty()) ({ Text(stringResource(R.string.model_custom_info)) }) else null,
            )
            provider.isOllama && (server.ids.isNotEmpty() || provider.llmModels.isNotEmpty()) -> LoomDropdown(
                label = stringResource(R.string.text_llm_model),
                value = if (llm.model.isBlank()) "" else modelLabel(llm),
                options = (provider.llmModels.map { it.id } + server.ids).distinct(),
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

        if (loadable) {
            // Ohne Pro nur Ollama, mit Knopftext wie bisher.
            LoadModelsButton(
                server,
                enabled = llm.baseUrl.isNotBlank(),
                snack = snack,
                label = if (pro) R.string.models_refresh else R.string.text_ollama_load_models,
                icon = if (pro) R.drawable.ic_refresh else R.drawable.ic_download_for_offline,
                noneText = if (provider.isOllama) R.string.text_ollama_no_models else R.string.models_none,
                onLoaded = takeFirst,
            ) { prefs.llmAccess() }
        }

        // Ohne Modell (Together/DeepInfra "wie Erkennung") ginge die Pruefung ins Leere.
        TestAccessRow(label = stringResource(R.string.text_test), enabled = !noLlmWithoutOwn && llm.refineBlock == null) {
            AccessTest.llm(prefs.llmAccess(), prefs.language)
        }
    }

    if (showKeySheet) KeySheet(providers, snack) { showKeySheet = false }
    if (showPicker) {
        ModelPickerSheet(
            recommended = provider.llmModels,
            server = server.entry,
            selected = llm.model,
            onSelect = { prefs.llmModel = it },
            onCustom = {
                showPicker = false
                showCustomModel = true
            },
            onDismiss = { showPicker = false },
        )
    }
    if (showCustomModel) {
        CustomModelSheet(
            placeholder = stringResource(R.string.text_llm_model_placeholder),
            initial = if (provider.llmModel(llm.model) == null) llm.model else "",
            onApply = { prefs.llmModel = it },
            onDismiss = { showCustomModel = false },
        )
    }
}

/**
 * Offline ohne eigenen Zugang: was bei Offline-Erkennung mit dem Text passiert, je nach Regel
 * (Spec §4). "Lokal" fragt den Online-Zugang offline nie — dort ist nichts zu tun, also kein Knopf.
 */
@Composable
private fun OfflineAccessNote(rule: OfflineRefineRule, onAddAccess: () -> Unit) {
    val loom = MaterialTheme.loom
    val add: @Composable () -> Unit = {
        FilledTonalButton(onClick = onAddAccess) { Text(stringResource(R.string.text_add_access)) }
    }
    when (rule) {
        OfflineRefineRule.LOCAL -> InfoCard(stringResource(R.string.text_access_offline_local))
        OfflineRefineRule.ONLINE_LOCAL -> InfoCard(stringResource(R.string.text_access_offline_online_local), action = add)
        OfflineRefineRule.SKIP -> InfoCard(
            text = stringResource(R.string.text_access_offline_skip),
            icon = R.drawable.ic_warning,
            container = loom.warningContainer,
            onContainer = loom.onWarningContainer,
            action = add,
        )
    }
}

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
