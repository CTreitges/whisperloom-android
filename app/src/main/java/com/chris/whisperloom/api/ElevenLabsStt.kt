package com.chris.whisperloom.api

import java.util.Locale

/**
 * Was an ElevenLabs Speech-to-Text (Scribe) geht — Endpunkt, Key-Header und Multipart-Felder.
 * ElevenLabs ist nicht OpenAI-kompatibel: eigener Pfad, `model_id` statt `model`, Key im Header
 * `xi-api-key` statt Bearer, Vokabular als `keyterms` statt `prompt`. Rein (ohne Netz und
 * Android), damit JVM-unit-testbar — Quelle: elevenlabs.io/docs/api-reference/speech-to-text/convert.
 */
object ElevenLabsStt {

    const val PATH = "/speech-to-text"
    const val AUTH_HEADER = "xi-api-key"

    /**
     * Rohes PCM, 16 bit, 16 kHz, mono, little-endian — genau das Aufnahmeformat der App (auch
     * geteiltes Audio wird auf 16 kHz gebracht). Die Datei ist dann OHNE WAV-Kopf, sonst hielte
     * ElevenLabs die 44 Kopf-Bytes fuer Audio.
     */
    const val FILE_FORMAT = "pcm_s16le_16"
    const val FILE_NAME = "audio.pcm"
    const val FILE_TYPE = "application/octet-stream"

    /** Grenzen laut Doku: hoechstens 1000 Begriffe, je unter 50 Zeichen und hoechstens 5 Woerter. */
    const val MAX_KEYTERMS = 1000
    private const val MAX_KEYTERM_CHARS = 49
    private const val MAX_KEYTERM_WORDS = 5

    /** Zeichen, die ElevenLabs in keyterms ablehnt. */
    private val FORBIDDEN = Regex("[<>{}\\[\\]\\\\]")
    private val WHITESPACE = Regex("\\s+")

    fun url(access: ApiAccess): String = Http.endpoint(access.baseUrl, PATH)

    /**
     * Textfelder in Sendereihenfolge (die Datei kommt zuletzt, siehe [ApiTranscriber]).
     *
     * - keine Sprache bei "auto" (ElevenLabs erkennt sie selbst); sonst der ISO-639-1-Code der App.
     * - `tag_audio_events=false`: sonst stuende "(Lachen)" im Diktat.
     * - `timestamps_granularity=none`: die App braucht keine Wort-Zeiten.
     * - kein `enable_logging` — Zero-Retention gibt es nur fuer Enterprise-Konten.
     */
    fun fields(access: ApiAccess, language: String, prompt: String): List<Pair<String, String>> {
        val out = mutableListOf("model_id" to access.model)
        val lang = language.trim()
        if (lang.isNotEmpty() && lang != "auto") out += "language_code" to lang
        out += "tag_audio_events" to "false"
        out += "timestamps_granularity" to "none"
        out += "file_format" to FILE_FORMAT
        keyterms(prompt).forEach { out += "keyterms" to it }
        return out
    }

    /**
     * Vokabular als keyterms: [prompt] ist die kommagetrennte Liste aus
     * [com.chris.whisperloom.Vocabulary.prompt]. Begriffe, die ElevenLabs ablehnen wuerde
     * (zu lang, zu viele Woerter, Sonderzeichen), fallen weg, statt das Diktat zu kippen.
     */
    fun keyterms(prompt: String): List<String> =
        prompt.split(',')
            .map { it.trim() }
            .filter { term ->
                term.isNotEmpty() &&
                    term.length <= MAX_KEYTERM_CHARS &&
                    term.split(WHITESPACE).size <= MAX_KEYTERM_WORDS &&
                    !FORBIDDEN.containsMatchIn(term)
            }
            .distinct()
            .take(MAX_KEYTERMS)

    /**
     * Die erkannte Sprache kommt 2- oder 3-stellig ("de" bzw. "deu"). Die App rechnet mit
     * ISO-639-1 (Fuellwoerter, deutscher Prompt der Textverbesserung) — also abbilden;
     * Unbekanntes bleibt, wie es ist.
     */
    fun isoLanguage(code: String): String {
        val c = code.trim().lowercase(Locale.ROOT)
        return if (c.length == 3) ISO3_TO_ISO1[c] ?: c else c
    }

    private val ISO3_TO_ISO1: Map<String, String> by lazy {
        Locale.getISOLanguages().mapNotNull { iso1 ->
            runCatching { Locale.forLanguageTag(iso1).isO3Language }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { it to iso1 }
        }.toMap()
    }
}
