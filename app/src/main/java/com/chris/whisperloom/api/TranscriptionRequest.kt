package com.chris.whisperloom.api

import com.chris.whisperloom.Prefs
import com.chris.whisperloom.Vocabulary
import org.json.JSONObject

/**
 * Was an POST /audio/transcriptions geht — Endpunkt und Multipart-Felder je
 * Anbieter/Modell. Rein (ohne Netz), damit JVM-unit-testbar: ein falsches Feld
 * heisst 400/422 beim Anbieter, und das faellt sonst erst live auf.
 */
object TranscriptionRequest {

    const val PATH = "/audio/transcriptions"

    /** Sprachfeld von gpt-transcribe: Liste erwarteter Sprachen ([ModelOption.languageField]). */
    private const val LANGUAGES_FIELD = "languages[]"

    /** DeepInfra legt Audio nicht unter der Chat-Base-URL ab -> Override aus dem Katalog. */
    fun url(access: ApiAccess): String =
        access.provider.sttPathOverride ?: Http.endpoint(access.baseUrl, PATH)

    /**
     * Textfelder in Sendereihenfolge (die Datei kommt zuletzt, siehe [ApiTranscriber]).
     *
     * - `languages[]` statt `language` bei OpenAI gpt-transcribe (Array-Feld).
     * - keine Sprache bei "auto" (der Anbieter erkennt sie selbst).
     * - "Deutsch + Englisch" ([Prefs.LANG_DE_EN]): gpt-transcribe bekommt beide Codes als Liste
     *   erwarteter Sprachen, alle anderen kennen nur einen Code und erkennen selbst.
     * - `prompt`/`response_format` nur, wenn der Anbieter sie kennt (Mistral, OpenRouter nicht).
     * - Vokabular zusaetzlich als Einzelbegriffe: `keywords[]` (gpt-transcribe) bzw. statt `prompt`
     *   als `context_bias` (Mistral).
     * - kein `model`, wenn keins eingetragen ist (eigener Server ohne Default: whisper.cpp
     *   ignoriert das Feld, speaches/LocalAI melden bei "" einen 422 ohne brauchbaren Text).
     */
    fun fields(access: ApiAccess, language: String, prompt: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        if (access.model.isNotBlank()) out += "model" to access.model
        if (access.provider.sttSendsResponseFormat) out += "response_format" to "json"
        val lang = language.trim()
        val field = access.modelOption?.languageField ?: "language"
        if (lang == Prefs.LANG_DE_EN) {
            if (field == LANGUAGES_FIELD) {
                out += field to "de"
                out += field to "en"
            }
        } else if (lang.isNotEmpty() && lang != "auto") {
            out += field to lang
        }
        if (access.provider.sttSendsPrompt && prompt.isNotBlank()) out += "prompt" to prompt.trim()
        access.modelOption?.keywordsField?.let { field -> keywords(prompt).forEach { out += field to it } }
        access.provider.sttBiasField?.let { field -> contextBias(prompt).forEach { out += field to it } }
        return out
    }

    /**
     * Vokabular als `keywords[]` fuer gpt-transcribe: OpenAI lehnt die GANZE Anfrage ab, wenn ein
     * Begriff `<`, `>` oder einen Zeilenumbruch enthaelt — solche Begriffe fallen weg, ebenso
     * Freitext-Stuecke (siehe [isTerm]).
     * Quelle: developers.openai.com/api/docs/guides/speech-to-text (Stand 2026-10-28).
     */
    fun keywords(prompt: String): List<String> =
        Vocabulary.terms(prompt, Int.MAX_VALUE) { term -> isTerm(term) && KEYWORD_FORBIDDEN.find(term) == null }

    /**
     * Vokabular als `context_bias` fuer Mistral: hoechstens [MAX_CONTEXT_BIAS] Eintraege, je einer
     * ohne Leerraum und Komma (openapi.yaml, Pattern `^[^,\s]+$`). Mehrteilige Begriffe werden
     * wie im Doku-Beispiel `affordable_health_care` mit `_` verbunden — auch bei geschuetztem
     * Leerzeichen (U+00A0), das aus kopierten Texten in die Vokabular-Datei kommt.
     * Quelle: docs.mistral.ai/api/endpoint/audio/transcriptions (Stand 2026-10-28).
     */
    fun contextBias(prompt: String): List<String> =
        Vocabulary.terms(prompt, MAX_CONTEXT_BIAS, transform = { it.replace(WHITESPACE, "_") }, accept = ::isTerm)

    /**
     * Ein Begriff, kein Freitext: hoechstens [MAX_TERM_WORDS] Woerter und [MAX_TERM_CHARS] Zeichen
     * — dieselben Grenzen wie bei ElevenLabs. Ein langer Satz aus altem Freitext nuetzt als
     * Schlagwort nichts und belegt bei Mistral einen der 100 Plaetze.
     */
    private fun isTerm(term: String): Boolean =
        term.length <= MAX_TERM_CHARS && term.split(WHITESPACE).size <= MAX_TERM_WORDS

    const val MAX_CONTEXT_BIAS = 100
    private const val MAX_TERM_CHARS = 49
    private const val MAX_TERM_WORDS = 5
    private val KEYWORD_FORBIDDEN = Regex("[<>\\r\\n]")

    /** Jeder Leerraum, auch Unicode (U+00A0, U+202F …) — Mistral prueft `\s` nicht nur auf ASCII. */
    private val WHITESPACE = Regex("[\\s\\p{Z}]+")

    /**
     * Erkannte Sprache aus der Antwort; null = keine gemeldet.
     *
     * - OpenAI gpt-transcribe: `languages: [{"code": "de"}]`, leer bei unsicherer Erkennung — die
     *   erste zaehlt (Quelle: developers.openai.com/api/reference, audio/transcriptions/create).
     *   Kommt sie 3-stellig ("deu"), wird sie wie bei ElevenLabs zu ISO-639-1.
     * - verbose_json und eigene Server: `language`. Sonst (json) kommt keine Sprache.
     */
    fun detectedLanguage(json: JSONObject): String? {
        val first = json.optJSONArray("languages")?.optJSONObject(0)?.optString("code", "")
        if (!first.isNullOrBlank()) return ElevenLabsStt.isoLanguage(first)
        return json.optString("language", "").takeIf { it.isNotBlank() }
    }
}
