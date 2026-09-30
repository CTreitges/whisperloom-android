package com.chris.whisperloom.api

import com.chris.whisperloom.RefineMode
import org.json.JSONObject

/**
 * Optionale zweite Runde: laesst ein Sprachmodell den Rohtext bearbeiten (glaetten,
 * verschoenern, zusammenfassen, in Absaetze gliedern). Kostet eine zusaetzliche
 * Anfrage und etwas Latenz — deshalb in den Einstellungen abschaltbar.
 *
 * Spricht POST /chat/completions des [ApiAccess] bzw. bei Ollama POST /api/chat — das kann
 * ein anderer Anbieter als bei der Transkription sein (z. B. Groq-STT + Ollama-LLM).
 */
class TextRefiner(private val access: ApiAccess) {

    /**
     * Liefert den bearbeiteten Text. Bei leerer Eingabe, [RefineMode.OFF] oder leerer
     * Antwort wird der Originaltext zurueckgegeben. Fehler des Sprachmodells (HTTP, Netz)
     * werden geworfen — [com.chris.whisperloom.TranscriptionEngine] faengt sie und faellt
     * auf den Rohtext zurueck: die Veredelung darf ein Diktat niemals verschlucken.
     *
     * @throws ApiNotConfiguredException wenn die Base-URL leer ist (eigener Server ohne URL) —
     *   sonst ginge die Anfrage an "/chat/completions" ohne Host — oder der Anbieter keine
     *   Textmodelle hat ([MSG_NO_LLM]).
     * @throws RefineRejectedException wenn die Stufe "Prompt" eine Antwort statt eines Prompts liefert
     *   oder das Modell an seiner Laengengrenze abgebrochen hat ([MSG_TRUNCATED]).
     */
    fun refine(
        raw: String,
        language: String,
        mode: RefineMode,
        smartFillers: Boolean,
        paragraphs: Boolean = true,
    ): String {
        if (raw.isBlank() || mode == RefineMode.OFF) return raw
        // "Wie Erkennung" bei einem reinen Erkennungs-Anbieter (Together, DeepInfra, ElevenLabs):
        // dort gibt es keinen Chat-Endpunkt — klarer Hinweis statt 404 und ohne Anfrage.
        if (!access.provider.hasLlm) throw ApiNotConfiguredException(MSG_NO_LLM)
        if (access.baseUrl.isBlank()) throw ApiNotConfiguredException()

        val german = language == "de"
        val short = mode == RefineMode.PROMPT && RefinePrompt.isShort(raw)
        val systemPrompt = RefinePrompt.build(mode, german, smartFillers, paragraphs, short)
        val userText = RefinePrompt.userText(mode, raw, german)
        val text = (if (access.provider.isOllama) ollama(systemPrompt, userText) else openAi(systemPrompt, userText))
            ?.let { stripThinking(it) }
            ?.trim()
            ?.let { if (mode == RefineMode.PROMPT) cleanPrompt(raw, it) else it }

        return if (text.isNullOrBlank()) raw else text
    }

    /**
     * OpenAI-kompatibel: POST {baseUrl}/chat/completions.
     *
     * Sicherheitsnetz fuer Modelle, deren Flags niemand kennt (vom Server, frei getippt): lehnt der
     * Server `temperature` mit 400 ab, folgt genau ein zweiter Versuch ohne — wie bei
     * Reasoning-Modellen mit `max_completion_tokens`.
     */
    private fun openAi(systemPrompt: String, raw: String): String? {
        val body = try {
            chat(access, systemPrompt, raw)
        } catch (e: ApiHttpException) {
            if (!rejectsTemperature(e) || ChatPayload.sampling(access).temperature == null) throw e
            val option = access.modelOption ?: ModelOption(access.model, access.model)
            chat(access.copy(modelOption = option.copy(temperatureSupported = false)), systemPrompt, raw)
        }
        val choice = JSONObject(body).optJSONArray("choices")?.optJSONObject(0)
        if (choice?.optString("finish_reason") == LENGTH) throw RefineRejectedException(MSG_TRUNCATED)
        return choice?.optJSONObject("message")?.optString("content")
    }

    private fun chat(access: ApiAccess, systemPrompt: String, raw: String): String {
        val payload = ChatPayload.build(access = access, systemPrompt = systemPrompt, userText = raw)
        return Http.post(
            url = Http.endpoint(access.baseUrl, "/chat/completions"),
            apiKey = access.apiKey,
            contentType = "application/json",
            readTimeoutMs = access.readTimeoutMs,
        ) { os -> os.write(payload.toByteArray(Charsets.UTF_8)) }
    }

    /** Ollama (lokal oder ollama.com): POST {Wurzel}/api/chat, siehe [OllamaApi]. */
    private fun ollama(systemPrompt: String, raw: String): String? {
        val payload = OllamaApi.chatPayload(access.model, systemPrompt, raw)
        val body = Http.post(
            url = OllamaApi.chatUrl(access.baseUrl),
            apiKey = access.apiKey,
            contentType = "application/json",
            readTimeoutMs = access.readTimeoutMs,
        ) { os -> os.write(payload.toByteArray(Charsets.UTF_8)) }
        if (JSONObject(body).optString("done_reason") == LENGTH) throw RefineRejectedException(MSG_TRUNCATED)
        return OllamaApi.parseChat(body)
    }

    companion object {
        /**
         * Das Modell hat an seiner Laengengrenze aufgehoert (`finish_reason`/`done_reason` "length",
         * z. B. max_completion_tokens bei Reasoning-Modellen). Die Antwort ist dann nur der Anfang
         * des Texts — sie einzufuegen hiesse, den Rest still zu verschlucken.
         */
        const val MSG_TRUNCATED = "Antwort des Modells abgeschnitten (Längengrenze)"

        /** Der Erkennungs-Anbieter hat keine Textmodelle, und es ist kein eigener Zugang eingetragen. */
        const val MSG_NO_LLM = "Der Erkennungs-Anbieter kann keinen Text verbessern — unter „Text“ einen eigenen Zugang eintragen"
        private const val LENGTH = "length"

        /** OpenAI: "Unsupported parameter: 'temperature' is not supported with this model." */
        private fun rejectsTemperature(e: ApiHttpException): Boolean =
            e.code == 400 && e.detail.contains("temperature", ignoreCase = true)

        // Qwen3 & Co. schreiben ihr Nachdenken als <think>…</think> in den Text, wenn
        // der Server reasoning_effort ignoriert. Das gehoert nie ins Diktat.
        private val THINK_BLOCK = Regex("(?s)^\\s*<think>.*?</think>\\s*")

        fun stripThinking(content: String): String = THINK_BLOCK.replace(content, "")

        /**
         * Nacharbeit der Stufe "Prompt". Kleine Modelle lassen gern Reste stehen: eine Vorrede
         * ("Hier ist dein Prompt:"), das "Prompt:" aus den Beispielen, die Markierung um das
         * Diktat, Anfuehrungszeichen oder einen Codeblock um alles.
         *
         * Dazu die Plausibilitaet: Ist die Ausgabe weit laenger als das Diktat, hat das Modell die
         * Bitte erfuellt statt sie umzuschreiben ("schreib mir ein Gedicht" -> Gedicht). Dann
         * werfen, der Aufrufer faellt mit Hinweis auf den Rohtext zurueck. Die Grenze ist
         * grosszuegig, weil Beschriftungen den Text verlaengern — sie faengt deshalb nur LANGE
         * Antworten. Eine kurze ("17 mal 23 ist 391.", ein Haiku) kommt durch; dagegen steht
         * allein der System-Prompt.
         *
         * @throws RefineRejectedException bei unplausibel langer Ausgabe.
         */
        fun cleanPrompt(raw: String, output: String): String {
            var text = MARKER.replace(output, "").trim()
            text = stripPreamble(text)
            // unwrap vor UND nach dem Label: "```\nPrompt: …\n```" wie "Prompt: „…“".
            text = unwrap(text)
            text = unwrap(PROMPT_LABEL.replace(text, "").trim())
            if (RefinePrompt.wordCount(text) > MAX_GROWTH * RefinePrompt.wordCount(raw) + GROWTH_SLACK) {
                throw RefineRejectedException("Modell hat geantwortet, statt einen Prompt zu formulieren")
            }
            return text
        }

        private const val MAX_GROWTH = 2
        private const val GROWTH_SLACK = 30
        private const val QUOTES = "\"„“”«»"
        private const val FENCE_MARK = "```"

        private val MARKER = Regex("</?(diktat|dictation)>", RegexOption.IGNORE_CASE)

        // Eine erste Zeile, die wie eine Vorrede anfaengt ("Hier ist dein Prompt:"), vom Prompt
        // DES NUTZERS spricht und mit "Prompt:" endet. "Hier ist ein Prompt, den ich nutze. Mach
        // ihn besser:" und "Schreib mir einen Prompt fuer Midjourney:" sind Anweisungen — bleiben.
        private val PREAMBLE = Regex(
            "^(hier|here|sure|klar|gerne?|natürlich|okay|ok|certainly)\\b[^\\n]*" +
                "\\b(dein|deine|deines|der|die|ihr|your|the)\\b[^\\n]*\\bprompts?\\s*:[ \\t]*\\r?\\n",
            RegexOption.IGNORE_CASE,
        )
        private val PROMPT_LABEL = Regex("^prompt\\s*:\\s*", RegexOption.IGNORE_CASE)
        private val FENCE = Regex("(?s)^```[\\w-]*\\r?\\n(.*)\\r?\\n```$")
        private val QUOTED = Regex("(?s)^[\"„“«»](.*)[\"“”»«]$")

        /** Bleibt nach der Vorrede nur Material ("<text>…") uebrig, war sie die Anweisung selbst. */
        private fun stripPreamble(text: String): String {
            val match = PREAMBLE.find(text) ?: return text
            val rest = text.substring(match.range.last + 1).trim()
            return if (rest.isEmpty() || rest.startsWith("<")) text else rest
        }

        /** Codeblock oder Anfuehrungszeichen um den GANZEN Text weg — innen stehende bleiben. */
        private fun unwrap(text: String): String {
            FENCE.matchEntire(text)?.groupValues?.get(1)?.let { inner ->
                return if (FENCE_MARK in inner) text else inner.trim()
            }
            val inner = QUOTED.matchEntire(text)?.groupValues?.get(1) ?: return text
            return if (inner.none { it in QUOTES }) inner.trim() else text
        }
    }
}
