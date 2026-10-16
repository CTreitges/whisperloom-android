package com.chris.whisperloom.api

import com.chris.whisperloom.RefineMode

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
    /** "Wie Erkennung", waehrend offline erkannt wird: es gibt keinen Online-Zugang zum Uebernehmen. */
    val sameAsOffline: Boolean = false,
) {
    /**
     * Warum dieser Zugang keinen Text verbessern kann; null = er kann es (soweit bekannt). Betrifft
     * "wie Erkennung": ElevenLabs hat gar keinen Chat-Endpunkt. Together und DeepInfra sprechen
     * /chat/completions, nur kennt der Katalog dort kein Textmodell — mit eingetipptem geht es.
     */
    val refineBlock: RefineBlock?
        get() = when {
            sameAsOffline -> RefineBlock.OFFLINE
            provider.api == ApiStyle.ELEVENLABS -> RefineBlock.NO_CHAT
            !provider.hasLlm && model.isBlank() -> RefineBlock.NO_MODEL
            else -> null
        }
}

/** Siehe [ApiAccess.refineBlock]. */
enum class RefineBlock {
    /** Der Anbieter hat keinen Chat-Endpunkt (ElevenLabs). */
    NO_CHAT,

    /** Reiner Erkennungs-Anbieter ohne eingetragenes Textmodell (Together, DeepInfra). */
    NO_MODEL,

    /** "Wie Erkennung" bei Offline-Erkennung: ein Offline-Modell kann keinen Text verbessern. */
    OFFLINE,
}

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
     *
     * @param sttOffline Erkennung laeuft offline. Dann gibt es nichts zu uebernehmen: der noch
     *   gespeicherte Online-Zugang ist abgewaehlt, und der erkannte Text darf nicht still dorthin
     *   gehen ([RefineBlock.OFFLINE], ohne Adresse und Key).
     * @param stageModel eigenes Modell der Stufe ("" = Standard). Reihenfolge: [stageModel] →
     *   [model] (das Modell des Zugangs, wenn gesetzt) → Empfehlung des Anbieters fuer [stage]
     *   ([Provider.recommendedLlmModel]) → erstes Katalog-Modell. Ohne Katalog und ohne [model] gilt
     *   auch [stageModel] nicht (Ollama lokal, eigener Server, Together "wie Erkennung").
     */
    fun resolveLlm(
        stt: ApiAccess,
        providerId: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        serverModels: ServerModelLookup = ServerModelLookup.NONE,
        sttOffline: Boolean = false,
        stageModel: String = "",
        stage: RefineMode? = null,
    ): ApiAccess {
        val same = providerId.isBlank() || providerId == LLM_SAME
        if (same && sttOffline) {
            return ApiAccess(
                baseUrl = "",
                apiKey = "",
                model = "",
                readTimeoutMs = stt.readTimeoutMs,
                provider = stt.provider,
                modelOption = null,
                sameAsOffline = true,
            )
        }
        val provider = if (same) stt.provider else ProviderCatalog.byId(providerId)
        val sameProvider = provider.id == stt.provider.id
        // Das Stufen-Modell zaehlt nur, wenn der Zugang selbst eins hat (Katalog oder llm_model). Sonst
        // sagen Bereitschaft, Tastatur und Banner (ohne Stufe) "kein Modell", und die Stufe ginge doch raus.
        val accessModel = model.trim().ifBlank { provider.recommendedLlmModel(stage) }
        val modelId = if (accessModel.isBlank()) "" else stageModel.trim().ifBlank { accessModel }

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

    /**
     * Fallback der Erkennung (3.9.1): eigener Anbieter, Key und Modell. Leere Felder fuellen sich nur aus
     * [primary], wenn es derselbe Anbieter ist (etwa ein anderes Modell mit demselben Key) — ein Key geht
     * nie an einen anderen Anbieter. Leeres Modell = Empfehlung des Anbieters wie beim ersten.
     */
    fun resolveSttFallback(
        primary: ApiAccess,
        providerId: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        serverModels: ServerModelLookup = ServerModelLookup.NONE,
    ): ApiAccess {
        val same = ProviderCatalog.byId(providerId).id == primary.provider.id
        return resolveStt(
            providerId = providerId,
            baseUrl = baseUrl.ifBlank { if (same) primary.baseUrl else "" },
            apiKey = apiKey.ifBlank { if (same && sameServer(baseUrl, primary)) primary.apiKey else "" },
            model = model,
            serverModels = serverModels,
        )
    }

    /**
     * Darf ein leerer Key des Fallbacks den von [donor] erben? Bei Anbietern mit fester Adresse ist
     * derselbe Anbieter derselbe Server. Beim eigenen Server und bei Ollama lokal nur, wenn die Adresse
     * leer (= die des Donors) oder dieselbe ist — sonst ginge Server As Key an Server B (Review 3.9.1).
     */
    private fun sameServer(baseUrl: String, donor: ApiAccess): Boolean =
        !donor.provider.needsUrl || baseUrl.isBlank() || normalized(baseUrl) == normalized(donor.baseUrl)

    private fun normalized(url: String) = url.trim().trimEnd('/').lowercase()

    /**
     * Fallback der Textverbesserung (3.9.1). Leere Adresse und leerer Key kommen von einem Zugang
     * desselben Anbieters: erst vom KI-Zugang ([primary]), dann von der Erkennung ([stt], nur online
     * erkannt — offline ist sie abgewaehlt). So reicht z. B. "Anthropic, Fallback OpenAI" ohne den
     * OpenAI-Key ein zweites Mal. Das Modell gilt fuer alle Stufen; leer = Empfehlung des Anbieters
     * fuer [stage]. Die Stufen-Modelle gehoeren zum ersten Anbieter und zaehlen hier nie.
     */
    fun resolveLlmFallback(
        stt: ApiAccess,
        primary: ApiAccess,
        providerId: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        serverModels: ServerModelLookup = ServerModelLookup.NONE,
        sttOffline: Boolean = false,
        stage: RefineMode? = null,
    ): ApiAccess {
        val provider = ProviderCatalog.byId(providerId)
        val donor = listOfNotNull(primary.takeIf { !it.sameAsOffline }, stt.takeIf { !sttOffline })
            .firstOrNull { it.provider.id == provider.id && sameServer(baseUrl, it) }
        val url = baseUrl.trim().ifBlank { donor?.baseUrl ?: provider.baseUrl }
        val modelId = model.trim().ifBlank { provider.recommendedLlmModel(stage) }
        return ApiAccess(
            baseUrl = url,
            apiKey = apiKey.trim().ifBlank { donor?.apiKey.orEmpty() },
            model = modelId,
            readTimeoutMs = timeoutMs(0, provider),
            provider = provider,
            modelOption = option(provider, ModelKind.LLM, url, modelId, serverModels),
        )
    }

    /**
     * Katalog zuerst; nur ohne Treffer den Cache fragen (der liest JSON) und Flags ableiten. Hat ein
     * Textmodell `temperature` schon einmal abgelehnt (gemerkt in [serverModels]), geht es ohne raus —
     * auch ein Katalog-Modell, der Katalog darf irren (Claude Sonnet 5 bis 3.8.5).
     */
    private fun option(
        provider: Provider,
        kind: ModelKind,
        url: String,
        id: String,
        serverModels: ServerModelLookup,
    ): ModelOption? {
        val catalog = if (kind == ModelKind.STT) provider.sttModel(id) else provider.llmModel(id)
        val option = if (catalog != null || id.isBlank()) {
            catalog
        } else {
            ModelLists.optionFor(provider, kind, id, serverModels.find(provider.id, kind, url, id))
        }
        val rejected = kind == ModelKind.LLM && id.isNotBlank() && option?.temperatureSupported != false &&
            serverModels.rejectsTemperature(provider.id, url, id)
        return if (rejected) (option ?: ModelOption(id, id)).copy(temperatureSupported = false) else option
    }

    private fun timeoutMs(seconds: Int, provider: Provider): Int =
        (if (seconds > 0) seconds else provider.defaultReadTimeoutSec) * 1000
}
