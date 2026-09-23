package com.chris.whisperloom.api

/**
 * Ein Modell fuer die Dropdowns. Rein (ohne Android), damit JVM-unit-testbar.
 *
 * @param languageField Name des Multipart-Felds fuer die Sprache: "language" (Whisper-Familie)
 *   oder "languages[]" (OpenAI gpt-transcribe erwartet ein Array).
 * @param temperatureSupported Reasoning-Modelle (OpenAI gpt-5.x) lehnen `temperature` mit 400 ab.
 * @param reasoningEffort Wert fuer `reasoning_effort`, wenn das Modell einen braucht/vertraegt
 *   ("none", "minimal", "low"); null = Feld weglassen.
 */
data class ModelOption(
    val id: String,
    val label: String,
    val note: String = "",
    val temperatureSupported: Boolean = true,
    val reasoningEffort: String? = null,
    val languageField: String = "language",
)

/**
 * Welches Protokoll ein Anbieter spricht. [OLLAMA] = native Ollama-API (POST /api/chat,
 * GET /api/tags) — lokal und auf ollama.com gleich, deshalb dieselbe Code-Strecke.
 */
enum class ApiStyle { OPENAI, OLLAMA }

/**
 * Ein Anbieter mit OpenAI-kompatibler API (oder nativer Ollama-API, siehe [api]).
 * Inhalt siehe [ProviderCatalog].
 *
 * @param allowsHttp Unverschluesseltes http:// nur fuer den eigenen Server (LAN/VPN).
 * @param sttSendsPrompt / [sttSendsResponseFormat] Extra-Felder, die nicht jeder Anbieter
 *   kennt (Mistral validiert streng, OpenRouter dokumentiert `prompt` nicht).
 * @param sttPathOverride Kompletter Transkriptions-Endpunkt, wenn er nicht unter
 *   `baseUrl + /audio/transcriptions` liegt (DeepInfra).
 * @param sttMaxBytes Dokumentiertes Upload-Limit (null = unbekannt).
 * @param api Protokoll; bei [ApiStyle.OLLAMA] ist [baseUrl] die Server-Wurzel ohne /api bzw. /v1.
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
) {
    val isCustom: Boolean get() = id == ProviderCatalog.CUSTOM_ID

    val isOllama: Boolean get() = api == ApiStyle.OLLAMA

    /** Der Nutzer traegt die Server-Adresse selbst ein (eigener Server, Ollama im Heimnetz). */
    val needsUrl: Boolean get() = baseUrl.isBlank()

    /** Taugt fuer die Transkriptions-Auswahl (eigener Server: Modell-ID frei). Ollama kann kein Audio. */
    val hasStt: Boolean get() = isCustom || sttModels.isNotEmpty()

    /** Taugt fuer die Textverbesserungs-Auswahl. Ollama-Modelle kommen vom Server selbst (/api/tags). */
    val hasLlm: Boolean get() = isCustom || isOllama || llmModels.isNotEmpty()

    /** Erstes Modell der Liste = Empfehlung; "" beim eigenen Server. */
    val defaultSttModel: String get() = sttModels.firstOrNull()?.id.orEmpty()
    val defaultLlmModel: String get() = llmModels.firstOrNull()?.id.orEmpty()

    fun sttModel(id: String): ModelOption? = sttModels.firstOrNull { it.id == id }
    fun llmModel(id: String): ModelOption? = llmModels.firstOrNull { it.id == id }
}

/**
 * Anbieter- und Modell-Katalog (Stand 2026-09-06, Recherche gegen die offizielle Doku).
 * Als Kotlin-Objekte statt JSON, damit nichts zur Laufzeit geparst werden muss.
 *
 * Reihenfolge = Reihenfolge im Dropdown; das erste Modell je Liste ist der Default.
 */
object ProviderCatalog {

    const val CATALOG_DATE = "2026-09-06"
    const val CUSTOM_ID = "custom"
    const val OPENAI_ID = "openai"
    const val OLLAMA_ID = "ollama"
    const val OLLAMA_CLOUD_ID = "ollama-cloud"

    private const val MB_25 = 26_214_400
    private const val MB_80 = 83_886_080

    val providers: List<Provider> = listOf(
        Provider(
            id = OPENAI_ID,
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            keyUrl = "https://platform.openai.com/api-keys",
            sttMaxBytes = MB_25,
            sttModels = listOf(
                ModelOption(
                    "gpt-transcribe", "GPT Transcribe (empfohlen)",
                    "\$0.0045/min. Nutzt languages[] statt language, kennt keywords[] + prompt.",
                    languageField = "languages[]",
                ),
                ModelOption(
                    "gpt-4o-transcribe", "GPT-4o Transcribe (Auslauf 02/2027)",
                    "\$0.006/min, nur response_format=json, Shutdown 2027-02-26.",
                ),
                ModelOption(
                    "gpt-4o-mini-transcribe", "GPT-4o mini Transcribe (Auslauf 02/2027)",
                    "\$0.003/min, nur json, Shutdown 2027-02-26.",
                ),
                ModelOption(
                    "whisper-1", "Whisper v2 (Legacy, Auslauf 02/2027)",
                    "\$0.006/min, prompt max 224 Token.",
                ),
            ),
            llmModels = listOf(
                ModelOption("gpt-4o-mini", "GPT-4o mini", "\$0.15/\$0.60 je 1M. Schnell, günstig, klassisch."),
                ModelOption("gpt-4.1-mini", "GPT-4.1 mini", "\$0.40/\$1.60 je 1M."),
                ModelOption(
                    "gpt-5.6-luna", "GPT-5.6 Luna",
                    "\$0.20/\$1.20 je 1M. Reasoning-Modell: kein temperature, reasoning_effort=none senden.",
                    temperatureSupported = false, reasoningEffort = "none",
                ),
                ModelOption(
                    "gpt-5.4-nano", "GPT-5.4 nano",
                    "\$0.20/\$1.25 je 1M. Kein temperature; reasoning_effort=none.",
                    temperatureSupported = false, reasoningEffort = "none",
                ),
                ModelOption(
                    "gpt-5-mini", "GPT-5 mini (Auslauf 12/2026)",
                    "\$0.25/\$2.00. Kein temperature; reasoning_effort=minimal. Shutdown 2026-12-11.",
                    temperatureSupported = false, reasoningEffort = "minimal",
                ),
                ModelOption(
                    "gpt-5-nano", "GPT-5 nano (Auslauf 12/2026)",
                    "\$0.05/\$0.40. Kein temperature; reasoning_effort=minimal. Shutdown 2026-12-11.",
                    temperatureSupported = false, reasoningEffort = "minimal",
                ),
            ),
            notes = "Bezahlpflichtig (Tier 1 ab \$5). 25-MB-Limit. Alle 4o-/whisper-STT-Modelle enden 2027-02-26.",
        ),
        Provider(
            id = "groq",
            name = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            keyUrl = "https://console.groq.com/keys",
            sttMaxBytes = MB_25,
            sttModels = listOf(
                ModelOption(
                    "whisper-large-v3-turbo", "Whisper Large v3 Turbo",
                    "\$0.04/h. Free-Plan: 2 h Audio/Std, 8 h/Tag. Sehr schnell.",
                ),
                ModelOption("whisper-large-v3", "Whisper Large v3", "\$0.111/h. Höhere Genauigkeit (WER 8,4 %)."),
            ),
            llmModels = listOf(
                ModelOption(
                    "openai/gpt-oss-20b", "GPT-OSS 20B",
                    "\$0.075/\$0.30 je 1M. Free: 30 RPM, 1000/Tag. Reasoning separat im Feld reasoning; reasoning_effort=low empfohlen.",
                    reasoningEffort = "low",
                ),
                ModelOption(
                    "openai/gpt-oss-120b", "GPT-OSS 120B",
                    "\$0.15/\$0.60 je 1M. Free: 30 RPM, 1000/Tag.",
                    reasoningEffort = "low",
                ),
                ModelOption(
                    "qwen/qwen3.6-27b", "Qwen 3.6 27B (Preview)",
                    "Preview. reasoning_effort=none senden, sonst <think>-Tags im Text.",
                    reasoningEffort = "none",
                ),
            ),
            notes = "Free-Plan ohne Zahlungsmittel. 25 MB (Free) / 100 MB (Dev). Llama-Modelle seit 2026-08-16 abgeschaltet.",
        ),
        Provider(
            id = "mistral",
            name = "Mistral (Voxtral)",
            baseUrl = "https://api.mistral.ai/v1",
            keyUrl = "https://console.mistral.ai/api-keys",
            sttSendsPrompt = false,
            sttSendsResponseFormat = false,
            sttModels = listOf(
                ModelOption(
                    "voxtral-mini-latest", "Voxtral Mini Transcribe 2",
                    "\$0.003/min. 13 Sprachen inkl. Deutsch, bis 3 h. prompt/response_format nicht dokumentiert -> nicht senden.",
                ),
            ),
            llmModels = listOf(
                ModelOption("mistral-small-latest", "Mistral Small 4", "ca. \$0.15/\$0.60 je 1M (Drittquelle)."),
                ModelOption("ministral-8b-latest", "Ministral 3 8B", "Sehr günstig, klein."),
            ),
            notes = "EU-Anbieter. Experiment-Plan gratis (Telefonverifizierung, 1 req/s). Kompatibilität der Extra-Felder live testen.",
        ),
        Provider(
            id = "together",
            name = "Together AI",
            baseUrl = "https://api.together.ai/v1",
            keyUrl = "https://api.together.ai/settings/api-keys",
            sttMaxBytes = MB_80,
            sttModels = listOf(
                ModelOption(
                    "openai/whisper-large-v3", "Whisper Large v3",
                    "\$0.0015/min. prompt unterstützt, language ISO-639-1 oder auto. 80 MB.",
                ),
            ),
            notes = "OpenAI-kompatibel. Startguthaben für neue Konten. LLM-IDs nicht verifiziert -> vorerst nur STT.",
        ),
        Provider(
            id = "deepinfra",
            name = "DeepInfra",
            baseUrl = "https://api.deepinfra.com/v1/openai",
            keyUrl = "https://deepinfra.com/dash/api_keys",
            sttPathOverride = "https://api.deepinfra.com/v1/audio/transcriptions",
            sttModels = listOf(
                ModelOption("openai/whisper-large-v3-turbo", "Whisper Large v3 Turbo", "\$0.0002/min."),
                ModelOption("openai/whisper-large-v3", "Whisper Large v3", "\$0.00045/min."),
            ),
            notes = "Audio-Endpunkt liegt unter /v1/audio/transcriptions (nicht /v1/openai) -> Pfad-Override nötig; live testen.",
        ),
        Provider(
            id = "openrouter",
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            keyUrl = "https://openrouter.ai/settings/keys",
            sttMaxBytes = MB_25,
            sttSendsPrompt = false,
            sttModels = listOf(
                ModelOption(
                    "mistralai/voxtral-mini-transcribe", "Voxtral Mini Transcribe (via OpenRouter)",
                    "\$0.003/min. 60-s-Timeout, 25 MB.",
                ),
                ModelOption(
                    "openai/gpt-4o-mini-transcribe", "GPT-4o mini Transcribe (via OpenRouter)",
                    "Token-Preis \$1.25/\$5 je 1M (~\$0.003/min).",
                ),
                ModelOption(
                    "openai/whisper-large-v3-turbo", "Whisper Large v3 Turbo (via OpenRouter)",
                    "Sekundenpreis, günstig.",
                ),
            ),
            llmModels = listOf(
                ModelOption("openai/gpt-4o-mini", "GPT-4o mini", "\$0.15/\$0.60 je 1M."),
                ModelOption("google/gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite", "\$0.10/\$0.40 je 1M."),
                ModelOption("anthropic/claude-haiku-4.5", "Claude Haiku 4.5", "\$1/\$5 je 1M."),
                ModelOption("mistralai/mistral-small-2603", "Mistral Small 4", "günstig, EU-Provider wählbar."),
            ),
            notes = "Ein Key für viele Modelle. :free-Modelle 20 RPM / 50-1000 RPD. prompt bei STT nicht dokumentiert -> nicht senden.",
        ),
        Provider(
            id = "anthropic",
            name = "Anthropic (Claude)",
            baseUrl = "https://api.anthropic.com/v1",
            keyUrl = "https://platform.claude.com/settings/keys",
            llmModels = listOf(
                ModelOption("claude-haiku-4-5", "Claude Haiku 4.5", "\$1/\$5 je 1M. temperature 0-1."),
                ModelOption("claude-sonnet-5", "Claude Sonnet 5", "\$2/\$10 je 1M. Höchste Textqualität."),
            ),
            notes = "Nur Textverbesserung. OpenAI-Kompatibilitätsschicht offiziell 'zum Testen', funktional stabil.",
        ),
        Provider(
            id = "gemini",
            name = "Google Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            keyUrl = "https://aistudio.google.com/apikey",
            llmModels = listOf(
                ModelOption(
                    "gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite",
                    "\$0.10/\$0.40 je 1M, Free-Tier. reasoning_effort=none möglich.",
                    reasoningEffort = "none",
                ),
                ModelOption(
                    "gemini-2.5-flash", "Gemini 2.5 Flash",
                    "\$0.30/\$2.50 je 1M, Free-Tier. reasoning_effort=none möglich.",
                    reasoningEffort = "none",
                ),
                ModelOption(
                    "gemini-3.8-flash", "Gemini 3.8 Flash",
                    "\$0.75/\$3.75 je 1M. Reasoning nicht abschaltbar -> langsamer.",
                ),
            ),
            notes = "Nur Textverbesserung. Free-Tier: Inhalte werden zum Training genutzt -> Warnhinweis in der App.",
        ),
        Provider(
            id = "deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            keyUrl = "https://platform.deepseek.com/api_keys",
            llmModels = listOf(
                ModelOption(
                    "deepseek-v4-flash", "DeepSeek V4 Flash",
                    "Peak/Off-Peak-Preise (~\$0.14-0.44 / \$0.28-1.32 je 1M, unsicher). Alias deepseek-chat abgeschaltet.",
                ),
            ),
            notes = "Nur Textverbesserung. Kein Free-Tier. Server in China -> Datenschutz-Hinweis.",
        ),
        Provider(
            id = OLLAMA_ID,
            name = "Ollama (lokal)",
            baseUrl = "",
            needsKey = false,
            allowsHttp = true,
            // Ein Homeserver ohne GPU braucht fuer ein langes Diktat mehr als 90 s.
            defaultReadTimeoutSec = 600,
            api = ApiStyle.OLLAMA,
            notes = "Eigener Ollama-Server im Heimnetz/VPN, z. B. http://homeserver:11434. Modelle kommen per /api/tags vom Server. " +
                "Auf dem Server OLLAMA_HOST=0.0.0.0 setzen, sonst lauscht Ollama nur auf localhost.",
        ),
        Provider(
            id = OLLAMA_CLOUD_ID,
            name = "Ollama Cloud",
            baseUrl = "https://ollama.com",
            keyUrl = "https://ollama.com/settings/keys",
            api = ApiStyle.OLLAMA,
            // Live gemessen 2026-09-24 (ein Satz, ohne think-Parameter): gemma4 0,8 s ohne Nachdenken,
            // glm-5.3-flash 1,4 s, gpt-oss:20b 3,7 s (Nachdenken nur auf "low" drosselbar).
            llmModels = listOf(
                ModelOption("gemma4:31b", "Gemma 4 31B (empfohlen)", "Schnell, denkt nicht nach — gut für Diktate."),
                ModelOption("glm-5.3-flash", "GLM 5.3 Flash", "Schnell, denkt kurz nach."),
                ModelOption("gpt-oss:20b", "GPT-OSS 20B", "Denkt nach (think=low), dadurch langsamer."),
                ModelOption("gpt-oss:120b", "GPT-OSS 120B", "Groß, denkt nach (think=low)."),
            ),
            notes = "Cloud-Modelle auf ollama.com, Key unter ollama.com/settings/keys. Weitere Modelle per /api/tags.",
        ),
        Provider(
            id = CUSTOM_ID,
            name = "Eigener Server (OpenAI-kompatibel)",
            baseUrl = "",
            needsKey = false,
            keyUrl = "",
            allowsHttp = true,
            // CPU-Server brauchen fuer ein 5-Minuten-Stueck Minuten, nicht Sekunden.
            defaultReadTimeoutSec = 600,
            notes = "Freie Base-URL + Modell-IDs (z. B. faster-whisper-server, speaches, LocalAI). Für Ollama gibt es eigene Einträge. Key optional.",
        ),
    )

    val custom: Provider = providers.first { it.id == CUSTOM_ID }
    val openai: Provider = providers.first { it.id == OPENAI_ID }

    /** Anbieter fuer das Transkriptions-Dropdown. */
    val sttProviders: List<Provider> = providers.filter { it.hasStt }

    /** Anbieter fuer das Textverbesserungs-Dropdown. */
    val llmProviders: List<Provider> = providers.filter { it.hasLlm }

    fun find(id: String): Provider? = providers.firstOrNull { it.id == id }

    /**
     * Unbekannte oder leere ID -> OpenAI. Bestehende Nutzer (vor v3) haben keinen
     * Provider gespeichert und liefen immer gegen OpenAI.
     */
    fun byId(id: String): Provider = find(id) ?: openai
}
