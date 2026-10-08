package com.chris.whisperloom.ui.components

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.api.ApiAccess
import com.chris.whisperloom.api.Provider
import com.chris.whisperloom.whisper.ModelCatalog
import com.chris.whisperloom.whisper.OfflineModel
import com.chris.whisperloom.whisper.TextModelCatalog

/** Dropdown-Text eines Anbieters (Spec §6.1); unbekannte IDs zeigen den Katalognamen. */
@Composable
fun providerLabel(provider: Provider): String = when (provider.id) {
    "openai" -> stringResource(R.string.provider_openai)
    "groq" -> stringResource(R.string.provider_groq)
    "mistral" -> stringResource(R.string.provider_mistral)
    "elevenlabs" -> stringResource(R.string.provider_elevenlabs)
    "together" -> stringResource(R.string.provider_together)
    "deepinfra" -> stringResource(R.string.provider_deepinfra)
    "openrouter" -> stringResource(R.string.provider_openrouter)
    "anthropic" -> stringResource(R.string.provider_anthropic)
    "gemini" -> stringResource(R.string.provider_gemini)
    "deepseek" -> stringResource(R.string.provider_deepseek)
    "custom" -> stringResource(R.string.provider_custom)
    "ollama" -> stringResource(R.string.provider_ollama)
    "ollama-cloud" -> stringResource(R.string.provider_ollama_cloud)
    else -> provider.name
}

/** Kurzname fuer Status-Zeilen ("Groq", "Eigener Server") — Katalogname ohne Klammerzusatz. */
fun providerShortName(provider: Provider): String = provider.name.substringBefore(" (").trim()

/** Modell-Label aus dem Katalog, sonst die freie ID. */
fun modelLabel(access: ApiAccess): String = access.modelOption?.label ?: access.model

@Composable
fun offlineModelLabel(id: String): String = when (id) {
    ModelCatalog.TINY.id -> stringResource(R.string.models_tiny)
    ModelCatalog.BASE.id -> stringResource(R.string.models_base)
    ModelCatalog.SMALL.id -> stringResource(R.string.models_small)
    ModelCatalog.LARGE_V3_TURBO.id -> stringResource(R.string.models_large)
    TextModelCatalog.GEMMA4_E2B.id -> stringResource(R.string.models_gemma_e2b)
    TextModelCatalog.GEMMA4_E4B.id -> stringResource(R.string.models_gemma_e4b)
    else -> ModelCatalog.byId(id).label
}

@Composable
fun offlineModelDetails(id: String): String = when (id) {
    ModelCatalog.TINY.id -> stringResource(R.string.models_tiny_sub)
    ModelCatalog.BASE.id -> stringResource(R.string.models_base_sub)
    ModelCatalog.SMALL.id -> stringResource(R.string.models_small_sub)
    TextModelCatalog.GEMMA4_E2B.id -> stringResource(R.string.models_gemma_e2b_sub, textModelSize(TextModelCatalog.GEMMA4_E2B))
    TextModelCatalog.GEMMA4_E4B.id -> stringResource(R.string.models_gemma_e4b_sub, textModelSize(TextModelCatalog.GEMMA4_E4B))
    else -> stringResource(R.string.models_large_sub)
}

/** Groesse eines Textmodells ehrlich: Download plus Cache, den es beim ersten Start anlegt ("2,6 GB · +800 MB beim ersten Start"). */
@Composable
fun textModelSize(model: OfflineModel): String =
    stringResource(R.string.models_llm_size, fileSize(model.bytes), fileSize(model.extraDiskBytes))

@Composable
fun levelLabel(mode: RefineMode): String = when (mode) {
    RefineMode.OFF -> stringResource(R.string.level_off)
    RefineMode.POLISH, RefineMode.READABLE -> stringResource(R.string.level_smooth)
    RefineMode.BEAUTIFY -> stringResource(R.string.level_beautify)
    RefineMode.SUMMARIZE -> stringResource(R.string.level_summarize)
    RefineMode.PROMPT -> stringResource(R.string.level_prompt)
}

/** Optionen der Regel "Textverbesserung bei Offline-Erkennung" (E2): Titel und Unterzeile. */
@Composable
fun offlineRuleLabel(rule: OfflineRefineRule): String = when (rule) {
    OfflineRefineRule.LOCAL -> stringResource(R.string.text_rule_local)
    OfflineRefineRule.ONLINE_LOCAL -> stringResource(R.string.text_rule_online_local)
    OfflineRefineRule.SKIP -> stringResource(R.string.text_rule_skip)
}

@Composable
fun offlineRuleDetails(rule: OfflineRefineRule): String = when (rule) {
    OfflineRefineRule.LOCAL -> stringResource(R.string.text_rule_local_sub)
    OfflineRefineRule.ONLINE_LOCAL -> stringResource(R.string.text_rule_online_local_sub)
    OfflineRefineRule.SKIP -> stringResource(R.string.text_rule_skip_sub)
}

/** Kurzform der Regel fuer die Hub-Unterzeile von "Textverbesserung". */
@Composable
fun offlineRuleShort(rule: OfflineRefineRule): String = when (rule) {
    OfflineRefineRule.LOCAL -> stringResource(R.string.settings_rule_offline_local)
    OfflineRefineRule.ONLINE_LOCAL -> stringResource(R.string.settings_rule_offline_online_local)
    OfflineRefineRule.SKIP -> stringResource(R.string.settings_rule_offline_skip)
}

@Composable
fun languageLabel(code: String): String = Prefs.LANGUAGES.firstOrNull { it.first == code }?.second ?: code

/** "190 MB" — Android-Formatierung (Spec: Groessen in MB). */
@Composable
fun fileSize(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)
