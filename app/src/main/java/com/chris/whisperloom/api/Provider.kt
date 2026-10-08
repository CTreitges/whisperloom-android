package com.chris.whisperloom.api

import com.chris.whisperloom.RefineMode

/**
 * Ein Modell fuer die Dropdowns. Rein (ohne Android), damit JVM-unit-testbar.
 *
 * @param languageField Name des Multipart-Felds fuer die Sprache: "language" (Whisper-Familie)
 *   oder "languages[]" (OpenAI gpt-transcribe erwartet ein Array).
 * @param temperatureSupported Reasoning-Modelle (OpenAI gpt-5.x) lehnen `temperature` mit 400 ab.
 * @param reasoningEffort Wert fuer `reasoning_effort`, wenn das Modell einen braucht/vertraegt
 *   ("none", "minimal", "low"); null = Feld weglassen.
 * @param thinkingType Wert fuer `"thinking": {"type": …}` (Claude ueber die Kompatibilitaetsschicht,
 *   DeepSeek): "disabled" schaltet das Nachdenken ab, Sonnet 5.5 kennt nur "between_tools";
 *   null = Feld weglassen. Gemessen 2026-10-08.
 */
data class ModelOption(
    val id: String,
    val label: String,
    val note: String = "",
    val temperatureSupported: Boolean = true,
    val reasoningEffort: String? = null,
    val languageField: String = "language",
    val thinkingType: String? = null,
)

/**
 * Welches Protokoll ein Anbieter spricht. [OLLAMA] = native Ollama-API (POST /api/chat,
 * GET /api/tags) — lokal und auf ollama.com gleich, deshalb dieselbe Code-Strecke.
 * [ELEVENLABS] = ElevenLabs Speech-to-Text (POST /speech-to-text, Header xi-api-key, Feld
 * model_id, siehe [ElevenLabsStt]) — nur Erkennung, keine Textverbesserung.
 */
enum class ApiStyle { OPENAI, OLLAMA, ELEVENLABS }

/**
 * Ein Anbieter mit OpenAI-kompatibler API (oder nativer Ollama-API, siehe [api]).
 * Inhalt siehe [ProviderCatalog].
 *
 * @param allowsHttp Unverschluesseltes http:// nur fuer den eigenen Server (LAN/VPN).
 * @param sttSendsPrompt / [sttSendsResponseFormat] Extra-Felder, die nicht jeder Anbieter
 *   kennt (Mistral validiert streng, OpenRouter dokumentiert `prompt` nicht). [sttSendsPrompt]
 *   heisst zugleich "Vokabular kommt an" — bei ElevenLabs als keyterms statt als `prompt`.
 * @param sttPathOverride Kompletter Transkriptions-Endpunkt, wenn er nicht unter
 *   `baseUrl + /audio/transcriptions` liegt (DeepInfra).
 * @param sttMaxBytes Dokumentiertes Upload-Limit (null = unbekannt).
 * @param api Protokoll; bei [ApiStyle.OLLAMA] ist [baseUrl] die Server-Wurzel ohne /api bzw. /v1.
 * @param rewriteLlmModel Empfehlung fuer die Umformulieren-Stufen (Verschoenern, Zusammenfassen,
 *   Prompt); "" = wie Glaetten ([defaultLlmModel]). Steht immer in [llmModels].
 */
data class Provider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val needsKey: Boolean = true,
    val keyUrl: String = "",
    val allowsHttp: Boolean = false,
    val defaultReadTimeoutSec: Int = 90,
    val sttModels: List<ModelOption> = emptyList(),
    val llmModels: List<ModelOption> = emptyList(),
    val sttSendsPrompt: Boolean = true,
    val sttSendsResponseFormat: Boolean = true,
    val sttPathOverride: String? = null,
    val sttMaxBytes: Int? = null,
    val api: ApiStyle = ApiStyle.OPENAI,
    val notes: String = "",
    val rewriteLlmModel: String = "",
) {
    val isCustom: Boolean get() = id == ProviderCatalog.CUSTOM_ID

    val isOllama: Boolean get() = api == ApiStyle.OLLAMA

    /** Der Nutzer traegt die Server-Adresse selbst ein (eigener Server, Ollama im Heimnetz). */
    val needsUrl: Boolean get() = baseUrl.isBlank()

    /** Taugt fuer die Transkriptions-Auswahl (eigener Server: Modell-ID frei). Ollama kann kein Audio. */
    val hasStt: Boolean get() = isCustom || sttModels.isNotEmpty()

    /** Taugt fuer die Textverbesserungs-Auswahl. Ollama-Modelle kommen vom Server selbst (/api/tags). */
    val hasLlm: Boolean get() = isCustom || isOllama || llmModels.isNotEmpty()

    /** Erstes Modell der Liste = Empfehlung (beim Textmodell: zum Glaetten); "" beim eigenen Server. */
    val defaultSttModel: String get() = sttModels.firstOrNull()?.id.orEmpty()
    val defaultLlmModel: String get() = llmModels.firstOrNull()?.id.orEmpty()

    /**
     * Empfehlung des Anbieters fuer eine Stufe: zum Umformulieren (Verschoenern, Zusammenfassen,
     * Prompt) [rewriteLlmModel], sonst — Glaetten, "Lesbarer glaetten", ohne Stufe — [defaultLlmModel].
     */
    fun recommendedLlmModel(mode: RefineMode?): String = when (mode?.modelStage) {
        RefineMode.BEAUTIFY, RefineMode.SUMMARIZE, RefineMode.PROMPT -> rewriteLlmModel.ifBlank { defaultLlmModel }
        else -> defaultLlmModel
    }

    fun sttModel(id: String): ModelOption? = sttModels.firstOrNull { it.id == id }
    fun llmModel(id: String): ModelOption? = llmModels.firstOrNull { it.id == id }
}
