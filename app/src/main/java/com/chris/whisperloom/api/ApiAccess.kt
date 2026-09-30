package com.chris.whisperloom.api

/**
 * Aufgeloester Zugang zu einem OpenAI-kompatiblen Endpunkt — alles, was ein Aufruf
 * braucht. [modelOption] kommt aus dem Katalog; fuer IDs ohne Katalog-Treffer (vom Server
 * geladen, "Eigenes Modell…") aus [ModelLists.optionFor]. Null = die konservativen Defaults.
 */
data class ApiAccess(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val readTimeoutMs: Int,
    val provider: Provider,
    val modelOption: ModelOption?,
)

/**
 * Leitet aus den rohen Einstellungswerten den Zugang ab. Rein (ohne Android), damit
 * die Fallback-Regeln JVM-unit-testbar sind — hier entscheidet sich, gegen welchen
 * Server mit welchem Key gesprochen wird.
 */
object AccessResolver {

    /** Wert von `llm_provider`, der den Transkriptions-Zugang wiederverwendet. */
    const val LLM_SAME = "same"

    /**
     * @param readTimeoutSec 0 = Anbieter-Default (90 s, eigener Server 600 s).
     * @param serverModels geladene Modell-Listen (ModelCache) — liefern die Flags fuer Server-Modelle.
     */
    fun resolveStt(
        providerId: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        readTimeoutSec: Int = 0,
        serverModels: ServerModelLookup = ServerModelLookup.NONE,
    ): ApiAccess {
        val provider = ProviderCatalog.byId(providerId)
        val modelId = model.trim().ifBlank { provider.defaultSttModel }
        val url = baseUrl.trim().ifBlank { provider.baseUrl }
        return ApiAccess(
            baseUrl = url,
            apiKey = apiKey.trim(),
            model = modelId,
            readTimeoutMs = timeoutMs(readTimeoutSec, provider),
            provider = provider,
            modelOption = option(provider, ModelKind.STT, url, modelId, serverModels),
        )
    }

    /**
     * Textverbesserung: `same` (oder leer) uebernimmt den Transkriptions-Zugang komplett.
     * Ist derselbe Anbieter explizit gewaehlt, fuellen leere Felder sich aus dem
     * Transkriptions-Zugang; ein anderer Anbieter bekommt seine eigenen Defaults —
     * der OpenAI-Key darf nie versehentlich an Groq gehen.
     */
    fun resolveLlm(
        stt: ApiAccess,
        providerId: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        serverModels: ServerModelLookup = ServerModelLookup.NONE,
    ): ApiAccess {
        val same = providerId.isBlank() || providerId == LLM_SAME
        val provider = if (same) stt.provider else ProviderCatalog.byId(providerId)
        val sameProvider = provider.id == stt.provider.id
        val modelId = model.trim().ifBlank { provider.defaultLlmModel }

        val url = when {
            same -> stt.baseUrl
            baseUrl.isNotBlank() -> baseUrl.trim()
            sameProvider -> stt.baseUrl
            else -> provider.baseUrl
        }
        val key = when {
            same -> stt.apiKey
            apiKey.isNotBlank() -> apiKey.trim()
            sameProvider -> stt.apiKey
            else -> ""
        }
        return ApiAccess(
            baseUrl = url,
            apiKey = key,
            model = modelId,
            readTimeoutMs = if (sameProvider) stt.readTimeoutMs else timeoutMs(0, provider),
            provider = provider,
            modelOption = option(provider, ModelKind.LLM, url, modelId, serverModels),
        )
    }

    /** Katalog zuerst; nur ohne Treffer den Cache fragen (der liest JSON) und Flags ableiten. */
    private fun option(
        provider: Provider,
        kind: ModelKind,
        url: String,
        id: String,
        serverModels: ServerModelLookup,
    ): ModelOption? {
        val catalog = if (kind == ModelKind.STT) provider.sttModel(id) else provider.llmModel(id)
        if (catalog != null || id.isBlank()) return catalog
        return ModelLists.optionFor(provider, kind, id, serverModels.find(provider.id, kind, url, id))
    }

    private fun timeoutMs(seconds: Int, provider: Provider): Int =
        (if (seconds > 0) seconds else provider.defaultReadTimeoutSec) * 1000
}
