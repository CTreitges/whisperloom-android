package com.chris.whisperloom.sync

import com.chris.whisperloom.Prefs
import com.chris.whisperloom.api.AccessResolver
import com.chris.whisperloom.api.ApiAccess
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
