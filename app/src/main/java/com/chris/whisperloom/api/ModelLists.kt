package com.chris.whisperloom.api

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Wofuer eine Modell-Liste gebraucht wird; [key] steht so im Cache-Schluessel. */
enum class ModelKind(val key: String) { STT("stt"), LLM("llm") }

/**
 * Ein Modell aus der Liste des Anbieters.
 *
 * @param label Anzeigename, falls der Server einen liefert (OpenRouter, Anthropic …); null = ID zeigen.
 * @param note z. B. "Auslauf 2027-02-26".
 * @param temperatureSupported / [reasoningEffort] nur, wenn der Server sie verraet (OpenRouter);
 *   null = unbekannt.
 */
data class RemoteModel(
    val id: String,
    val label: String? = null,
    val note: String? = null,
    val temperatureSupported: Boolean? = null,
    val reasoningEffort: String? = null,
)

/**
 * GET-Anfrage fuer eine Modell-Liste. Der Key steht bewusst nicht darin — [ModelLists.load]
 * nimmt ihn aus dem Zugang.
 *
 * @param authHeader eigener Key-Header (ElevenLabs: xi-api-key); null = `Authorization: Bearer`.
 * @param headers weitere Header (Anthropic: anthropic-version).
 */
data class ModelListRequest(
    val url: String,
    val authHeader: String? = null,
    val headers: Map<String, String> = emptyMap(),
) {
    /**
     * Eigene Header streift die JVM bei einer Weiterleitung nicht ab (HttpRedirectTest) — dann
     * keiner folgen. Nur mit Authorization folgt die Liste wie bisher (Ollama, eigener Server).
     */
    val followRedirects: Boolean get() = authHeader == null && headers.isEmpty()
}

/**
 * Findet ein zwischengespeichertes Server-Modell (siehe `ModelCache`). Als Schnittstelle, damit
 * [AccessResolver] rein bleibt.
 */
fun interface ServerModelLookup {
    fun find(providerId: String, kind: ModelKind, baseUrl: String, id: String): RemoteModel?

    companion object {
        val NONE = ServerModelLookup { _, _, _, _ -> null }
    }
}

/**
 * "Modelle vom Server": welche Adresse mit welchen Headern, und welche Eintraege der Antwort
 * fuer die Erkennung bzw. die Textverbesserung taugen. [request] und [parse] sind rein
 * (JVM-unit-testbar); nur [load] spricht ueber [Http] mit dem Server.
 *
 * Filterregeln nach der Anbieter-Doku (Stand 2026-09-30). Liefert ein Anbieter keine Felder,
 * entscheidet der Name. Unbekannte Felder werden ignoriert.
 */
object ModelLists {

    /** Eine Liste ist klein — wer in 15 s nicht antwortet, antwortet gar nicht. */
    const val TIMEOUT_MS = 15_000

    const val ANTHROPIC_VERSION = "2023-06-01"

    private const val MODELS = "/models"
    private const val LATEST = "-latest"
    private const val ASR_TASK = "automatic-speech-recognition"

    private const val OPENAI = "openai"
    private const val GROQ = "groq"
    private const val MISTRAL = "mistral"
    private const val TOGETHER = "together"
    private const val DEEPINFRA = "deepinfra"
    private const val OPENROUTER = "openrouter"
    private const val ANTHROPIC = "anthropic"
    private const val GEMINI = "gemini"
    private const val DEEPSEEK = "deepseek"

    private fun rx(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    // Generische Namensregeln fuer Anbieter ohne Felder (eigener Server, Rueckfall).
    private val STT_NAME = rx("whisper|transcribe|voxtral-mini|parakeet|canary|(^|[-/_])asr([-_.]|$)|nova-3|(^|[-/_])stt([-_]|$)")
    private val STT_NAME_NOT = rx("realtime|(^|[-_])live([-_]|$)|streaming|diarize|tts")
    private val NOT_CHAT = rx(
        "whisper|transcribe|(^|[-/_])asr|parakeet|canary|tts|speech|orpheus|playai|kokoro|sonic|" +
            "embed|rerank|moderation|guard|safeguard|shieldstral|ocr|image|dall-e|sora|video|" +
            "realtime|(^|[-_])live([-_]|$)|(^|[-_])audio|search|computer-use|deep-research",
    )

    // OpenAI: Realtime/Diarize passen nicht zum Upload; -pro, codex, cyber, daybreak & Co. gibt es
    // nur ueber die Responses-API (die App spricht /chat/completions).
    private val OPENAI_STT_NOT = rx("realtime|live|diarize")
    private val OPENAI_CHAT = rx("^(ft:)?(gpt-|o\\d|chatgpt-)")
    private val OPENAI_NOT_CHAT = rx(
        "transcribe|tts|whisper|realtime|(^|-)live(-|$)|audio|image|dall-e|sora|embedding|moderation|search|" +
            "computer-use|deep-research|codex|-pro(\\b|-|$)|instruct|cyber|daybreak",
    )
    private val WHISPER = rx("whisper")
    private val GROQ_NOT_CHAT = rx("whisper|tts|orpheus|playai|guard|safeguard|compound")
    private val MISTRAL_STT_NAME = rx("voxtral.*(transcribe|mini)")
    private val MISTRAL_STT_NAME_NOT = rx("realtime|tts")
    private val TOGETHER_STT = rx("whisper|parakeet|asr|voxtral|nova-3|deepgram/")
    private val TOGETHER_OTHER_TYPES = setOf("chat", "language", "code", "image", "embedding", "moderation", "rerank")
    private val GUARD = rx("guard|safeguard")
    private val GEMINI_NOT_CHAT = rx(
        "embed|aqa|imagen|veo|lyria|tts|image|live|native-audio|transcribe|robotics|computer-use|" +
            "deep-research|antigravity|omni",
    )
    private val EMBED = rx("embed")

    // Heuristik fuer Flags (optionFor), angewandt auf den Namen ohne Anbieter-Praefix.
    private val OPENAI_REASONING = rx("^(ft:)?(o\\d|gpt-5|gpt-6)")
    private val GEMINI_3 = rx("^gemini-3")
    private val GPT_TRANSCRIBE = rx("^gpt-.*transcribe")
    private val QWEN3 = rx("^qwen3")
    private val GPT_OSS = rx("^gpt-oss")

    /** Adresse und Header der Liste; Ollama nutzt die vorhandene native Schnittstelle. */
    fun request(access: ApiAccess, kind: ModelKind): ModelListRequest {
        val provider = access.provider
        val base = access.baseUrl
        return when {
            provider.isOllama -> ModelListRequest(OllamaApi.tagsUrl(base))
            provider.api == ApiStyle.ELEVENLABS ->
                ModelListRequest(Http.endpoint(base, MODELS), authHeader = ElevenLabsStt.AUTH_HEADER)
            // Ohne limit liefert Anthropic 20 Modelle je Seite; 1000 ist das Maximum.
            provider.id == ANTHROPIC -> ModelListRequest(
                Http.endpoint(base, "$MODELS?limit=1000"),
                headers = mapOf("anthropic-version" to ANTHROPIC_VERSION),
            )
            // Ohne Filter liefert OpenRouter nur Modelle mit Text-Ausgabe.
            provider.id == OPENROUTER && kind == ModelKind.STT ->
                ModelListRequest(Http.endpoint(base, "$MODELS?output_modalities=transcription"))
            else -> ModelListRequest(Http.endpoint(base, MODELS))
        }
    }

    /**
     * Holt die Liste und filtert sie. Blockierend — aus einem Hintergrund-Thread aufrufen.
     *
     * @throws ApiNotConfiguredException wenn keine Adresse eingetragen ist.
     * @throws ModelListException wenn die Antwort keine Modell-Liste ist.
     * @throws ApiNetworkException / [ApiHttpException] wie [Http.get].
     */
    fun load(access: ApiAccess, kind: ModelKind): List<RemoteModel> {
        if (access.baseUrl.isBlank()) throw ApiNotConfiguredException()
        val r = request(access, kind)
        val body = Http.get(r.url, access.apiKey, TIMEOUT_MS, r.followRedirects, r.authHeader, r.headers)
        return parse(access.provider, kind, body)
    }

    /**
     * Die Modelle der Antwort, die fuer [kind] taugen: ohne Duplikate, alphabetisch (Anthropic in
     * Server-Reihenfolge, neueste zuerst). Leer ist kein Fehler (ElevenLabs listet Scribe wohl nicht).
     *
     * @param today fuer Abschalt-Daten (OpenAI shutdown_date, Mistral deprecation, OpenRouter expiration_date).
     * @throws ModelListException bei kaputtem JSON oder einer Antwort ohne Liste.
     */
    fun parse(
        provider: Provider,
        kind: ModelKind,
        body: String,
        today: LocalDate = LocalDate.now(ZoneOffset.UTC),
    ): List<RemoteModel> {
        if (!provider.offers(kind)) return emptyList()
        val models = try {
            select(provider, kind == ModelKind.STT, body, today)
        } catch (e: JSONException) {
            throw ModelListException(e)
        }
        val unique = models.distinctBy { it.id }
        return if (provider.id == ANTHROPIC) unique else unique.sortedBy { it.id.lowercase() }
    }

    /**
     * Flags fuer eine Modell-ID ohne exakten Katalog-Treffer (vom Server geladen oder frei getippt).
     * Mit den Defaults scheitert sonst z. B. ein gpt-5-Snapshot an `temperature: 0` (HTTP 400) oder
     * eine gpt-transcribe-Variante an `language` statt `languages[]`.
     *
     * Je Flag gilt das Erste, was etwas weiss: Server-Metadaten ([server], nur OpenRouter) →
     * Katalog-Modell, dessen Snapshot die ID ist (`gpt-5-mini-2025-08-07` erbt von `gpt-5-mini`) →
     * Heuristik je Familie. Weiss niemand etwas, bleibt es bei null = Defaults wie bisher.
     */
    fun optionFor(provider: Provider, kind: ModelKind, id: String, server: RemoteModel?): ModelOption? {
        val catalog = if (kind == ModelKind.STT) provider.sttModels else provider.llmModels
        catalog.firstOrNull { it.id == id }?.let { return it }
        if (id.isBlank()) return null
        val base = catalog.filter { id.startsWith(it.id + "-") }.maxByOrNull { it.id.length }
        val name = id.substringAfterLast('/')
        val chat = kind == ModelKind.LLM
        val temperature = server?.temperatureSupported ?: base?.temperatureSupported
            ?: if (chat && noTemperature(provider, name)) false else null
        val effort = server?.reasoningEffort ?: base?.reasoningEffort ?: if (chat) heuristicEffort(provider, name) else null
        val languageField = base?.languageField
            ?: if (!chat && provider.id == OPENAI && GPT_TRANSCRIBE.containsMatchIn(name)) "languages[]" else null
        if (server == null && base == null && temperature == null && effort == null && languageField == null) return null
        return ModelOption(
            id = id,
            label = server?.label ?: id,
            note = server?.note.orEmpty(),
            temperatureSupported = temperature ?: true,
            reasoningEffort = effort,
            languageField = languageField ?: "language",
        )
    }

    /** OpenAI-Reasoning-Modelle lehnen temperature ab (400); Google raet bei Gemini 3 von temperature < 1 ab. */
    private fun noTemperature(provider: Provider, name: String): Boolean =
        OPENAI_REASONING.containsMatchIn(name) || (provider.id == GEMINI && GEMINI_3.containsMatchIn(name))

    /**
     * Groq: Qwen3 schreibt sonst <think>-Tags in den Text; gpt-oss kennt nur Stufen, "low" haelt es schnell.
     * OpenAI: o-Serie und gpt-5/6 denken ohne Angabe auf "medium" (langsam) — "low" nehmen alle diese
     * Familien an, "none" bzw. "minimal" nicht jede.
     */
    private fun heuristicEffort(provider: Provider, name: String): String? = when {
        provider.id == GROQ && QWEN3.containsMatchIn(name) -> "none"
        provider.id == GROQ && GPT_OSS.containsMatchIn(name) -> "low"
        provider.id == OPENAI && OPENAI_REASONING.containsMatchIn(name) -> "low"
        else -> null
    }

    private fun Provider.offers(kind: ModelKind): Boolean = if (kind == ModelKind.STT) hasStt else hasLlm

    private fun select(provider: Provider, stt: Boolean, body: String, today: LocalDate): List<RemoteModel> {
        // Einbettungs-Modelle (nomic-embed-text …) koennen keinen Text verbessern.
        if (provider.isOllama) return OllamaApi.parseTags(body).filterNot { EMBED.containsMatchIn(it) }.map { RemoteModel(it) }
        val list = entries(body)
        if (provider.api == ApiStyle.ELEVENLABS) return elevenLabs(list)
        return when (provider.id) {
            OPENAI -> openAi(stt, list, today)
            GROQ -> groq(stt, list)
            MISTRAL -> mistral(stt, list, today)
            TOGETHER -> together(list)
            DEEPINFRA -> deepInfra(list)
            OPENROUTER -> openRouter(stt, list, today)
            // Nur Chat-Modelle in der Liste; display_name ist ein guter Anzeigename.
            ANTHROPIC -> list.mapNotNull { o -> o.text("id")?.let { RemoteModel(it, label = o.text("display_name")) } }
            GEMINI -> gemini(list)
            DEEPSEEK -> list.mapNotNull { o -> o.text("id")?.let { RemoteModel(it, label = o.text("name")) } }
            else -> custom(stt, list)
        }
    }

    /** `{"data":[…]}` (OpenAI-Form) oder ein Array auf oberster Ebene (Together, ElevenLabs). */
    private fun entries(body: String): List<JSONObject> {
        val text = body.trim()
        val array = if (text.startsWith("[")) JSONArray(text)
        else JSONObject(text).optJSONArray("data") ?: throw JSONException("keine Liste")
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun openAi(stt: Boolean, list: List<JSONObject>, today: LocalDate) = list.mapNotNull { o ->
        val id = o.text("id") ?: return@mapNotNull null
        val shutdown = date(o.opt("shutdown_date"))
        val keep = if (stt) {
            id == "whisper-1" || (id.contains("transcribe", ignoreCase = true) && !OPENAI_STT_NOT.containsMatchIn(id))
        } else {
            OPENAI_CHAT.containsMatchIn(id) && !OPENAI_NOT_CHAT.containsMatchIn(id)
        }
        if (keep && !expired(shutdown, today)) RemoteModel(id, note = expiryNote(shutdown)) else null
    }

    /** `active` steht nur im Doku-Beispiel, nicht im SDK-Schema — fehlend zaehlt als aktiv. */
    private fun groq(stt: Boolean, list: List<JSONObject>) = list.mapNotNull { o ->
        val id = o.text("id") ?: return@mapNotNull null
        val keep = o.optBoolean("active", true) &&
            (if (stt) WHISPER.containsMatchIn(id) else !GROQ_NOT_CHAT.containsMatchIn(id))
        if (keep) RemoteModel(id) else null
    }

    private fun mistral(stt: Boolean, list: List<JSONObject>, today: LocalDate): List<RemoteModel> {
        val kept = list.mapNotNull { o ->
            val id = o.text("id") ?: return@mapNotNull null
            val caps = o.optJSONObject("capabilities")
            val deprecation = date(o.opt("deprecation"))
            val keep = when {
                expired(deprecation, today) || o.optBoolean("archived", false) -> false
                caps == null && stt -> MISTRAL_STT_NAME.containsMatchIn(id) && !MISTRAL_STT_NAME_NOT.containsMatchIn(id)
                caps == null -> chatByName(id)
                stt -> caps.optBoolean("audio_transcription") && !caps.optBoolean("audio_transcription_realtime") &&
                    !id.contains("realtime", ignoreCase = true)
                else -> caps.optBoolean("completion_chat") && !caps.optBoolean("ocr") &&
                    !caps.optBoolean("moderation") && !caps.optBoolean("audio_speech")
            }
            if (keep) RemoteModel(id, note = expiryNote(deprecation)) to o.optJSONArray("aliases").strings().orEmpty() else null
        }
        return preferLatest(kept)
    }

    /**
     * Mistral fuehrt ein Modell unter jedem Namen einzeln (`…-latest` und datiert, verknuepft ueber
     * `aliases`). Je Gruppe bleibt einer, bevorzugt `-latest` — so heissen auch die Empfehlungen.
     */
    private fun preferLatest(models: List<Pair<RemoteModel, List<String>>>): List<RemoteModel> {
        val seen = mutableSetOf<String>()
        return models.sortedByDescending { it.first.id.endsWith(LATEST) }.mapNotNull { (model, aliases) ->
            val group = aliases + model.id
            if (group.any { it in seen }) return@mapNotNull null
            seen += group
            val latest = if (model.id.endsWith(LATEST)) model.id else aliases.firstOrNull { it.endsWith(LATEST) }
            model.copy(id = latest ?: model.id)
        }
    }

    /**
     * Together liefert `type` (Whisper wohl "transcribe", unbelegt) — sonst entscheidet der Name.
     * Deepgram laeuft dort nur auf eigenen Instanzen, nicht mit einem normalen Key.
     */
    private fun together(list: List<JSONObject>) = list.mapNotNull { o ->
        val id = o.text("id") ?: return@mapNotNull null
        val type = o.text("type")?.lowercase()
        val keep = (type == "transcribe" || (type !in TOGETHER_OTHER_TYPES && TOGETHER_STT.containsMatchIn(id))) &&
            !id.startsWith("deepgram/", ignoreCase = true)
        if (keep) RemoteModel(id, label = o.text("display_name")) else null
    }

    /** Jeder DeepInfra-Eintrag traegt genau ein Typ-Tag (stt, chat, tts, embed …). */
    private fun deepInfra(list: List<JSONObject>) = list.mapNotNull { o ->
        val id = o.text("id") ?: return@mapNotNull null
        val tags = o.optJSONObject("metadata")?.optJSONArray("tags").strings()
        if (if (tags != null) "stt" in tags else sttByName(id)) RemoteModel(id) else null
    }

    /**
     * OpenRouter: nur reine Text-Ausgabe; ohne Batch-Varianten (asynchron), ohne eigene Router
     * (Kosten unvorhersehbar) und ohne Moderations-Klassifikatoren. Einzige Liste mit Flags.
     */
    private fun openRouter(stt: Boolean, list: List<JSONObject>, today: LocalDate) = list.mapNotNull { o ->
        val id = o.text("id") ?: return@mapNotNull null
        val architecture = o.optJSONObject("architecture")
        val output = architecture?.optJSONArray("output_modalities").strings()
        val input = architecture?.optJSONArray("input_modalities").strings()
        val expires = date(o.opt("expiration_date"))
        val keep = if (stt) {
            output == null || "transcription" in output
        } else {
            (output == null || output == listOf("text")) && (input == null || "text" in input) &&
                !id.endsWith(":batch") && !id.startsWith("openrouter/") && !GUARD.containsMatchIn(id)
        }
        if (!keep || expired(expires, today)) return@mapNotNull null
        RemoteModel(
            id = id,
            label = o.text("name"),
            note = expiryNote(expires),
            temperatureSupported = if (stt) null else o.optJSONArray("supported_parameters").strings()?.let { "temperature" in it },
            reasoningEffort = if (stt) null else effortNone(o.optJSONObject("reasoning")),
        )
    }

    /** "none" nur, wenn das Modell das Nachdenken abschalten laesst; Pflicht-Reasoning = Feld weglassen. */
    private fun effortNone(reasoning: JSONObject?): String? {
        if (reasoning == null || reasoning.optBoolean("mandatory", false)) return null
        return if ("none" in reasoning.optJSONArray("supported_efforts").strings().orEmpty()) "none" else null
    }

    /**
     * Gemini (OpenAI-Kompatschicht): IDs kommen als "models/gemini-…", im Chat-Request stehen sie
     * ohne Praefix. Keine Felder — Sprachausgabe, Bild, Live & Co. fallen ueber den Namen raus.
     */
    private fun gemini(list: List<JSONObject>) = list.mapNotNull { o ->
        val id = o.text("id")?.removePrefix("models/")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        if (GEMINI_NOT_CHAT.containsMatchIn(id)) null else RemoteModel(id, label = o.text("display_name"))
    }

    /**
     * /v1/models listet laut Doku nur Sprachausgabe; ob Scribe dabei ist, ist offen. Leer ist also
     * normal. Realtime spricht WebSocket, nicht den Upload.
     */
    private fun elevenLabs(list: List<JSONObject>) = list.mapNotNull { o ->
        val id = o.text("model_id") ?: return@mapNotNull null
        val keep = id.startsWith("scribe", ignoreCase = true) && !id.contains("realtime", ignoreCase = true)
        if (keep) RemoteModel(id, label = o.text("name")) else null
    }

    /**
     * Eigener Server: speaches kennzeichnet Modelle mit `task`, sonst entscheidet der Name. Greift
     * der Filter bei keinem Modell, lieber alle zeigen als keins.
     */
    private fun custom(stt: Boolean, list: List<JSONObject>): List<RemoteModel> {
        val all = list.mapNotNull { o -> o.text("id")?.let { it to o.text("task") } }
        val kept = all.filter { (id, task) ->
            when {
                task != null -> stt && task == ASR_TASK
                stt -> sttByName(id)
                else -> chatByName(id)
            }
        }
        return kept.ifEmpty { all }.map { RemoteModel(it.first) }
    }

    private fun sttByName(id: String) = STT_NAME.containsMatchIn(id) && !STT_NAME_NOT.containsMatchIn(id)

    private fun chatByName(id: String) = !NOT_CHAT.containsMatchIn(id)

    /** Datum aus "2027-02-26", "2027-02-26T00:00:00Z" oder Unix-Sekunden; alles andere = keins. */
    internal fun date(value: Any?): LocalDate? = when (value) {
        is Number -> Instant.ofEpochSecond(value.toLong()).atZone(ZoneOffset.UTC).toLocalDate()
        is String -> runCatching { LocalDate.parse(value.trim().take(10)) }.getOrNull()
        else -> null
    }

    /** Abgeschaltet ist, was heute oder frueher endet. */
    private fun expired(date: LocalDate?, today: LocalDate) = date != null && !date.isAfter(today)

    private fun expiryNote(date: LocalDate?): String? = date?.let { "Auslauf $it" }

    /** Nicht-leerer Text; org.json liefert fuer null den String "null". */
    private fun JSONObject.text(key: String): String? =
        optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }

    private fun JSONArray?.strings(): List<String>? =
        this?.let { a -> (0 until a.length()).mapNotNull { i -> a.optString(i).takeIf { it.isNotEmpty() } } }
}
