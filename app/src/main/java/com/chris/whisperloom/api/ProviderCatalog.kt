package com.chris.whisperloom.api

/**
 * Anbieter- und Modell-Katalog (Stand 2026-10-08, Recherche gegen die offizielle Doku).
 * Als Kotlin-Objekte statt JSON, damit nichts zur Laufzeit geparst werden muss.
 *
 * Reihenfolge = Reihenfolge im Dropdown; das erste Modell je Liste ist der Default (beim
 * Textmodell die Empfehlung zum Glaetten, [Provider.rewriteLlmModel] die zum Umformulieren).
 */
object ProviderCatalog {

    const val CATALOG_DATE = "2026-10-08"
    const val CUSTOM_ID = "custom"
    const val OPENAI_ID = "openai"
    const val OLLAMA_ID = "ollama"
    const val OLLAMA_CLOUD_ID = "ollama-cloud"
    const val ELEVENLABS_ID = "elevenlabs"

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
            // gpt-6-luna/-sol: ungemessen (Stand 2026-10-08), Umformulieren kostet mit Sol 13-17x mehr als gpt-4o-mini.
            llmModels = listOf(
                ModelOption(
                    "gpt-6-luna", "GPT-6 Luna",
                    "\$0.10/\$0.50 je 1M. Reasoning-Modell: kein temperature, reasoning_effort=none senden. Empfohlen zum Glätten.",
                    temperatureSupported = false, reasoningEffort = "none",
                ),
                ModelOption(
                    "gpt-6-sol", "GPT-6 Sol",
                    "\$2/\$10 je 1M. Kein temperature; reasoning_effort=none. Empfohlen zum Umformulieren.",
                    temperatureSupported = false, reasoningEffort = "none",
                ),
                ModelOption("gpt-4o-mini", "GPT-4o mini", "\$0.15/\$0.60 je 1M. Schnell, günstig, klassisch."),
                ModelOption("gpt-4.1-mini", "GPT-4.1 mini", "\$0.40/\$1.60 je 1M."),
                ModelOption(
                    "gpt-5.6-luna", "GPT-5.6 Luna",
                    "\$0.20/\$1.20 je 1M. Reasoning-Modell: kein temperature, reasoning_effort=none senden.",
                    temperatureSupported = false, reasoningEffort = "none",
                ),
                ModelOption(
                    "gpt-5.4-nano", "GPT-5.4 nano (Auslauf 04/2027)",
                    "\$0.20/\$1.25 je 1M. Kein temperature; reasoning_effort=none. Shutdown 2027-04-01, Nachfolger GPT-6 Luna.",
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
            rewriteLlmModel = "gpt-6-sol",
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
                    "qwen/qwen3.8-27b", "Qwen 3.8 27B (Preview)",
                    "\$0.80/\$4.00 je 1M, Preview. reasoning_effort=none senden, sonst <think>-Tags im Text.",
                    reasoningEffort = "none",
                ),
            ),
            notes = "Free-Plan ohne Zahlungsmittel. 25 MB (Free) / 100 MB (Dev). Llama-Modelle seit 2026-08-16 abgeschaltet.",
            rewriteLlmModel = "openai/gpt-oss-120b",
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
                ModelOption("mistral-small-latest", "Mistral Small 4", "\$0.15/\$0.60 je 1M. Denkt ab Werk nicht nach. Empfohlen zum Glätten."),
                ModelOption("mistral-large-2512", "Mistral Large 3", "\$0.50/\$1.50 je 1M. Ohne Nachdenken, gutes Deutsch. Empfohlen zum Umformulieren."),
                ModelOption("ministral-8b-latest", "Ministral 3 8B", "Sehr günstig, klein."),
            ),
            notes = "EU-Anbieter. Experiment-Plan gratis (Telefonverifizierung, 1 req/s). Kompatibilität der Extra-Felder live testen.",
            rewriteLlmModel = "mistral-large-2512",
        ),
        Provider(
            id = ELEVENLABS_ID,
            name = "ElevenLabs (Scribe)",
            baseUrl = "https://api.elevenlabs.io/v1",
            keyUrl = "https://elevenlabs.io/app/settings/api-keys",
            api = ApiStyle.ELEVENLABS,
            // Nie scribe_v2_realtime (nur WebSocket) oder scribe_v1 (abgekuendigt).
            sttModels = listOf(
                ModelOption("scribe_v2", "Scribe v2", "\$0.22/h, 90+ Sprachen."),
                ModelOption("scribe_v2_medical", "Scribe v2 Medical", "\$0.22/h. Für medizinische Diktate."),
            ),
            notes = "Eigenes Protokoll (xi-api-key, model_id), nur Erkennung. Vokabular geht als keyterms (ca. +20 % Kosten).",
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
            // Claude 5.x: temperature vorsorglich aus (OpenRouter listet es, Anthropic lehnt es ab). Das
            // thinking-Feld hat hier eine andere Syntax -> keins; gegen leere Antworten hilft das Limit.
            llmModels = listOf(
                ModelOption(
                    "anthropic/claude-haiku-5.5", "Claude Haiku 5.5",
                    "\$0.10/\$0.50 je 1M. Kein temperature. Empfohlen zum Glätten.",
                    temperatureSupported = false,
                ),
                ModelOption(
                    "anthropic/claude-sonnet-5.5", "Claude Sonnet 5.5",
                    "\$2/\$10 je 1M. Kein temperature. Empfohlen zum Umformulieren.",
                    temperatureSupported = false,
                ),
                ModelOption(
                    "openai/gpt-6-luna", "GPT-6 Luna",
                    "\$0.10/\$0.50 je 1M. Kein temperature; reasoning_effort=none.",
                    temperatureSupported = false, reasoningEffort = "none",
                ),
                ModelOption("openai/gpt-4o-mini", "GPT-4o mini", "\$0.15/\$0.60 je 1M."),
                // google/gemini-2.5-* laeuft bei OpenRouter am 2026-10-20 aus (live expiration_date).
                ModelOption(
                    "google/gemini-3.8-flash", "Gemini 3.8 Flash",
                    "\$0.75/\$3.75 je 1M. Reasoning nicht abschaltbar, reasoning_effort=low hält es schnell. Kein temperature.",
                    temperatureSupported = false, reasoningEffort = "low",
                ),
                ModelOption("mistralai/mistral-small-2603", "Mistral Small 4", "\$0.15/\$0.60 je 1M, EU-Provider wählbar."),
                ModelOption("anthropic/claude-haiku-4.5", "Claude Haiku 4.5 (Legacy)", "\$1/\$5 je 1M, 10x teurer als Haiku 5.5."),
            ),
            notes = "Ein Key für viele Modelle. :free-Modelle 20 RPM / 50-1000 RPD. prompt bei STT nicht dokumentiert -> nicht senden.",
            rewriteLlmModel = "anthropic/claude-sonnet-5.5",
        ),
        Provider(
            id = "anthropic",
            name = "Anthropic (Claude)",
            baseUrl = "https://api.anthropic.com/v1",
            keyUrl = "https://platform.claude.com/settings/keys",
            // Ab Claude 4.7 liefert jedes gesetzte temperature HTTP 400; die Kompatibilitaetsschicht ignoriert
            // reasoning_effort. Claude 5 denkt ab Werk, die Denk-Token zaehlen gegen das Limit -> thinking
            // abschalten, wo das Modell es zulaesst (gemessen 2026-10-08).
            llmModels = listOf(
                ModelOption(
                    "claude-haiku-5-5", "Claude Haiku 5.5",
                    "\$0.10/\$0.50 je 1M (bis 100k Token Eingabe). Schnell. Empfohlen zum Glätten.",
                    temperatureSupported = false, thinkingType = "disabled",
                ),
                ModelOption(
                    "claude-sonnet-5-5", "Claude Sonnet 5.5",
                    "\$2/\$10 je 1M. Empfohlen zum Umformulieren.",
                    temperatureSupported = false, thinkingType = "between_tools",
                ),
                // Opus lehnt "disabled" wie "between_tools" ab und denkt immer adaptiv.
                ModelOption(
                    "claude-opus-5-5", "Claude Opus 5.5",
                    "\$4/\$20 je 1M. Höchste Textqualität, langsamer.",
                    temperatureSupported = false,
                ),
                ModelOption(
                    "claude-sonnet-5", "Claude Sonnet 5",
                    "\$2/\$10 je 1M.",
                    temperatureSupported = false, thinkingType = "disabled",
                ),
                ModelOption("claude-haiku-4-5", "Claude Haiku 4.5 (Legacy)", "\$1/\$5 je 1M. Abschaltung frühestens 2026-10-15."),
            ),
            notes = "Nur Textverbesserung. OpenAI-Kompatibilitätsschicht offiziell 'zum Testen', funktional stabil.",
            rewriteLlmModel = "claude-sonnet-5-5",
        ),
        Provider(
            id = "gemini",
            name = "Google Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            keyUrl = "https://aistudio.google.com/apikey",
            // Google gibt 2.5 nur noch an Konten, die es schon genutzt haben; fuer neue Projekte
            // empfiehlt es 3.5 Flash-Lite oder 3.8 Flash. Reasoning ist ab 3 nicht abschaltbar, und
            // Google raet bei Gemini 3 von temperature < 1 ab (Schleifen) -> Feld weglassen.
            llmModels = listOf(
                ModelOption(
                    "gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite",
                    "\$0.30/\$2.50 je 1M. Googles Empfehlung für neue Projekte. Reasoning nicht abschaltbar, kein temperature.",
                    temperatureSupported = false,
                ),
                // Denkt ohne Angabe auf "medium" (langsam); "low" ist die kleinste Stufe.
                ModelOption(
                    "gemini-3.8-flash", "Gemini 3.8 Flash",
                    "\$0.75/\$3.75 je 1M bis Ende 2026, ab 2027 \$1.50/\$7.50. reasoning_effort=low. Kein temperature. Empfohlen zum Umformulieren.",
                    temperatureSupported = false, reasoningEffort = "low",
                ),
                ModelOption(
                    "gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite (nur Bestandskonten)",
                    "\$0.10/\$0.40 je 1M, Free-Tier. reasoning_effort=none möglich.",
                    reasoningEffort = "none",
                ),
                ModelOption(
                    "gemini-2.5-flash", "Gemini 2.5 Flash (nur Bestandskonten)",
                    "\$0.30/\$2.50 je 1M, Free-Tier. reasoning_effort=none möglich.",
                    reasoningEffort = "none",
                ),
            ),
            notes = "Nur Textverbesserung. Free-Tier: Inhalte werden zum Training genutzt -> Warnhinweis in der App.",
            rewriteLlmModel = "gemini-3.8-flash",
        ),
        Provider(
            id = "deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            keyUrl = "https://platform.deepseek.com/api_keys",
            // Denkt ab Werk auf "high" (langsam, temperature wird dann still ignoriert). Abschalten geht
            // nur mit thinking.type=disabled (api-docs.deepseek.com/guides/thinking_mode, 2026-10-08).
            llmModels = listOf(
                ModelOption(
                    "deepseek-flash", "DeepSeek Flash (V4.1)",
                    "\$0.15/\$0.60 je 1M außerhalb der Spitzenzeit, sonst doppelt. Denken aus. Empfohlen zum Glätten.",
                    thinkingType = "disabled",
                ),
                ModelOption(
                    "deepseek-v4-pro", "DeepSeek V4 Pro",
                    "\$0.66/\$1.98 je 1M außerhalb der Spitzenzeit, sonst doppelt. Denken aus. Empfohlen zum Umformulieren.",
                    thinkingType = "disabled",
                ),
                // Alter Name: wird noch angenommen und landet bei V4.1-Flash — bleibt fuer gespeicherte Auswahlen.
                ModelOption(
                    "deepseek-v4-flash", "DeepSeek V4 Flash (alter Name)",
                    "Wird noch angenommen, das Modell dahinter ist aber abgelöst (V4.1-Flash).",
                    thinkingType = "disabled",
                ),
            ),
            notes = "Nur Textverbesserung. Kein Free-Tier. Server in China -> Datenschutz-Hinweis.",
            rewriteLlmModel = "deepseek-v4-pro",
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
                "Auf dem Server OLLAMA_HOST=0.0.0.0 setzen, sonst lauscht Ollama nur auf localhost. " +
                "Empfehlung: gemma4:12b zum Glätten (schwache Hardware: gemma4:e4b), gemma4:26b zum Umformulieren (24-GB-GPU: gemma4:31b).",
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
                ModelOption("mistral-large-3:675b", "Mistral Large 3", "Denkt nicht nach, gutes Deutsch. Empfohlen zum Umformulieren (Tempo ungemessen)."),
                ModelOption("glm-5.3-flash", "GLM 5.3 Flash", "Schnell, denkt kurz nach."),
                ModelOption("gpt-oss:20b", "GPT-OSS 20B", "Denkt nach (think=low), dadurch langsamer."),
                ModelOption("gpt-oss:120b", "GPT-OSS 120B", "Groß, denkt nach (think=low)."),
            ),
            notes = "Cloud-Modelle auf ollama.com, Key unter ollama.com/settings/keys. Weitere Modelle per /api/tags.",
            rewriteLlmModel = "mistral-large-3:675b",
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
