package com.chris.whisperloom.sync

import com.chris.whisperloom.Prefs
import com.chris.whisperloom.api.AccessResolver
import com.chris.whisperloom.api.ApiAccess
import com.chris.whisperloom.api.ProviderCatalog
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Was ein Import an den vier Zugaengen zusaetzlich zu den Feldern der Datei aendert — dieselben Regeln wie beim
 * Anbieterwechsel in der UI (`LlmAccessSection`, `FallbackSection`, `SttAccessSection`) und `KeyRoutes` auf dem Desktop.
 */
internal object SyncAccessRules {
    const val STT = "stt"
    const val STT_FALLBACK = "sttFallback"
    const val LLM = "llm"
    const val LLM_FALLBACK = "llmFallback"

    private const val PROVIDER = "providerId"
    private const val BASE_URL = "baseUrl"
    private const val MODEL = "model"
    private const val STAGE_MODELS = "stageModels"
    private const val KEY_MARKER = "key:"
    private const val PROVIDERS = "providers"
    private const val CUSTOM_ENTRIES = "custom"
    private const val CUSTOM_PREFIX = "custom:"
    private const val LOCAL_PREFIX = "local:"
    private const val OPENAI_PROTOCOL = "openai"

    /** Felder, die den Zugang ausmachen; `stt.vocabulary` und der Timeout gelten auch ohne ihn. */
    private val ACCESS_FIELDS = listOf(PROVIDER, BASE_URL, MODEL, STAGE_MODELS, "enabled")

    /**
     * Eigene Anbieter des Desktops in [incoming], vor dem Mischen: `custom:<id>` mit Eintrag in `providers.custom`,
     * Protokoll `openai` und Adresse wird zum Android-Anbieter `custom` mit der Adresse des Eintrags (sie gilt immer,
     * nie `*.baseUrl`). Alles andere gibt es auf Android nicht; dann bleibt der Zugang des Geraets.
     *
     * @return die uebersprungenen Zugaenge
     */
    fun mapDesktopProviders(before: JSONObject, incoming: JSONObject): List<String> {
        val entries = customEntries(incoming) ?: customEntries(before)
        return listOf(STT, STT_FALLBACK, LLM, LLM_FALLBACK).filter { name ->
            val section = incoming.opt(name) as? JSONObject ?: return@filter false
            val id = section.opt(PROVIDER) as? String ?: return@filter false
            if (!id.startsWith(CUSTOM_PREFIX) && !id.startsWith(LOCAL_PREFIX)) return@filter false
            val url = if (id.startsWith(CUSTOM_PREFIX)) openAiEntryUrl(entries, id.removePrefix(CUSTOM_PREFIX)) else null
            if (url != null) {
                section.put(PROVIDER, ProviderCatalog.CUSTOM_ID).put(BASE_URL, url)
            } else {
                ACCESS_FIELDS.forEach { section.remove(it) }
            }
            url == null
        }
    }

    private fun customEntries(tree: JSONObject): JSONArray? =
        (tree.opt(PROVIDERS) as? JSONObject)?.opt(CUSTOM_ENTRIES) as? JSONArray

    /** Adresse des Eintrags, null = nicht abbildbar. Ohne `protocol` gilt wie im Format `openai`. */
    private fun openAiEntryUrl(entries: JSONArray?, id: String): String? {
        if (entries == null) return null
        val entry = (0 until entries.length()).mapNotNull { entries.opt(it) as? JSONObject }
            .firstOrNull { it.opt("id") == id } ?: return null
        if ((entry.opt("protocol") ?: OPENAI_PROTOCOL) != OPENAI_PROTOCOL) return null
        return (entry.opt(BASE_URL) as? String)?.takeIf { it.isNotBlank() }
    }

    /**
     * Neuer Anbieter: Adresse und Modelle gehoeren zum alten, gelten also nur noch, wenn die Datei sie selbst nennt
     * ([STAGE_MODELS] ganz statt feldweise). Bei "wie Erkennung" haengen die Textmodelle am Anbieter der Erkennung.
     */
    fun resetOnProviderChange(before: JSONObject, incoming: JSONObject, merged: JSONObject) {
        listOf(STT, STT_FALLBACK, LLM, LLM_FALLBACK).forEach { name ->
            val section = merged.opt(name) as? JSONObject ?: return@forEach
            if (before.getJSONObject(name).opt(PROVIDER) == section.opt(PROVIDER)) return@forEach
            val file = incoming.optJSONObject(name)
            if (file?.has(BASE_URL) != true) section.put(BASE_URL, "")
            if (name != LLM && file?.has(MODEL) != true) section.put(MODEL, "")
        }
        val llm = merged.opt(LLM) as? JSONObject ?: return
        if (llmProvider(before) == llmProvider(merged)) return
        val file = incoming.optJSONObject(LLM)
        if (file?.has(MODEL) != true) llm.put(MODEL, "")
        llm.put(STAGE_MODELS, file?.opt(STAGE_MODELS) ?: JSONObject())
    }

    /**
     * Abgeschaltete OpenAI-Modelle werden still zum Nachfolger ([ProviderCatalog.successorOf], wie `PrefsMigration` v8,
     * die nach einem Import nicht mehr laeuft). Erst auf dem gemischten Stand ist der Anbieter jedes Felds bekannt.
     */
    fun migrateRetiredModels(tree: JSONObject) {
        fun migrate(obj: JSONObject?, field: String, providerId: String?) {
            if (obj == null || providerId == null) return
            val model = obj.opt(field) as? String ?: return
            ProviderCatalog.successorOf(providerId, model)?.let { obj.put(field, it) }
        }
        fun section(name: String) = tree.opt(name) as? JSONObject
        listOf(STT, STT_FALLBACK, LLM_FALLBACK).forEach { migrate(section(it), MODEL, section(it)?.opt(PROVIDER) as? String) }
        val provider = llmProvider(tree)
        migrate(section(LLM), MODEL, provider)
        val stages = section(LLM)?.opt(STAGE_MODELS) as? JSONObject ?: return
        stages.keys().asSequence().toList().forEach { migrate(stages, it, provider) }
    }

    /** Anbieter, bei dem die Textverbesserung wirklich laeuft; null bei falschem Typ (meldet [SyncSettings.read]). */
    fun llmProvider(tree: JSONObject): String? {
        val id = tree.optJSONObject(LLM)?.opt(PROVIDER) as? String ?: return null
        return if (id.isBlank() || id == AccessResolver.LLM_SAME) tree.optJSONObject(STT)?.opt(PROVIDER) as? String else id
    }

    /**
     * Namen der gespeicherten Keys, die nach dem Import ein Ziel haetten, das sie vorher nicht hatten. Die Ziele kommen
     * aus derselben Aufloesung wie beim Diktat, also zaehlt auch ein geerbter Key; abgeschaltete Fallbacks zaehlen mit.
     */
    fun droppedKeys(prefs: Prefs, after: SyncSettings): List<String> {
        val stored = mapOf(
            STT to prefs.apiKey,
            STT_FALLBACK to prefs.sttFallbackKey,
            LLM to prefs.llmKey,
            LLM_FALLBACK to prefs.llmFallbackKey,
        ).filterValues { it.isNotBlank() }.keys
        val old = routes(
            stored,
            SyncAccess(true, prefs.sttProviderId, prefs.apiBaseUrl, prefs.apiModel),
            SyncAccess(true, prefs.sttFallbackProviderId, prefs.sttFallbackUrl, prefs.sttFallbackModel),
            SyncAccess(true, prefs.llmProviderId, prefs.llmUrl, prefs.llmModel),
            SyncAccess(true, prefs.llmFallbackProviderId, prefs.llmFallbackUrl, prefs.llmFallbackModel),
        )
        val new = routes(stored, after.stt, after.sttFallback, after.llm, after.llmFallback)
        return stored.filter { (new[it].orEmpty() - old[it].orEmpty()).isNotEmpty() }
    }

    fun clearKeys(prefs: Prefs, names: List<String>) = names.forEach {
        when (it) {
            STT -> prefs.apiKey = ""
            STT_FALLBACK -> prefs.sttFallbackKey = ""
            LLM -> prefs.llmKey = ""
            LLM_FALLBACK -> prefs.llmFallbackKey = ""
        }
    }

    /** Je Key-Name die Ziele. Statt der Keys laufen Marken durch den [AccessResolver]. */
    private fun routes(
        stored: Set<String>,
        stt: SyncAccess,
        sttFallback: SyncAccess,
        llm: SyncAccess,
        llmFallback: SyncAccess,
    ): Map<String, Set<String>> {
        fun key(name: String) = if (name in stored) KEY_MARKER + name else ""
        val sttAccess = AccessResolver.resolveStt(stt.providerId, stt.baseUrl, key(STT), stt.model)
        val llmAccess = AccessResolver.resolveLlm(sttAccess, llm.providerId, llm.baseUrl, key(LLM), llm.model)
        val accesses = listOf(
            sttAccess,
            llmAccess,
            AccessResolver.resolveSttFallback(sttAccess, sttFallback.providerId, sttFallback.baseUrl, key(STT_FALLBACK), sttFallback.model),
            AccessResolver.resolveLlmFallback(sttAccess, llmAccess, llmFallback.providerId, llmFallback.baseUrl, key(LLM_FALLBACK), llmFallback.model),
        )
        return accesses.filter { it.apiKey.startsWith(KEY_MARKER) }
            .groupBy({ it.apiKey.removePrefix(KEY_MARKER) }, ::target)
            .mapValues { it.value.toSet() }
    }

    /** Ein anderer Anbieter ist immer ein neues Ziel (wie in der UI), sonst zaehlt der Host (wie auf dem Desktop). */
    private fun target(access: ApiAccess): String {
        val url = access.baseUrl.trim()
        val uri = runCatching { URI(url) }.getOrNull()
        val origin = if (uri?.host != null) "${uri.scheme.orEmpty()}://${uri.host}:${uri.port}".lowercase() else url.lowercase()
        return "${access.provider.id}|$origin"
    }
}
