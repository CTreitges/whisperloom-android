package com.chris.whisperloom.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * "Modelle vom Server": Adressen/Header je Anbieter und die Filterregeln gegen Antworten in der
 * Form der Anbieter-Doku (api-models-*.md, Stand 2026-09-30). Robolectric wegen org.json.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelListsTest {

    private val today = LocalDate.parse("2026-09-30")

    private fun p(id: String) = ProviderCatalog.byId(id)

    private fun ids(provider: String, kind: ModelKind, body: String) =
        ModelLists.parse(p(provider), kind, body, today).map { it.id }

    private fun data(vararg entries: String) = """{"object":"list","data":[${entries.joinToString(",")}]}"""

    private fun stt(provider: String, url: String = "", key: String = "k") =
        AccessResolver.resolveStt(provider, url, key, "")

    private fun llm(provider: String, url: String = "", key: String = "k") =
        AccessResolver.resolveLlm(stt("groq"), provider, url, key, "")

    // --- Adresse und Header -------------------------------------------------------

    @Test fun openAiFormNutztBaseUrlPlusModelsMitBearer() {
        val r = ModelLists.request(stt("openai"), ModelKind.STT)
        assertEquals("https://api.openai.com/v1/models", r.url)
        assertNull(r.authHeader)
        assertTrue(r.headers.isEmpty())
        assertTrue(r.followRedirects)
        // DeepInfra: Liste unter der Chat-Basis (/v1/openai), der Upload hat seinen eigenen Pfad.
        assertEquals("https://api.deepinfra.com/v1/openai/models", ModelLists.request(stt("deepinfra"), ModelKind.STT).url)
        assertEquals("https://api.deepseek.com/models", ModelLists.request(llm("deepseek"), ModelKind.LLM).url)
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/openai/models",
            ModelLists.request(llm("gemini"), ModelKind.LLM).url,
        )
        assertEquals("http://h:8000/v1/models", ModelLists.request(stt("custom", "http://h:8000/v1/"), ModelKind.STT).url)
    }

    @Test fun anthropicMitLimitUndVersionOhneWeiterleitung() {
        val r = ModelLists.request(llm("anthropic"), ModelKind.LLM)
        assertEquals("https://api.anthropic.com/v1/models?limit=1000", r.url)
        assertEquals(mapOf("anthropic-version" to "2023-06-01"), r.headers)
        assertNull("Bearer wie im Chat", r.authHeader)
        assertFalse(r.followRedirects)
    }

    @Test fun openRouterFiltertErkennungsModelleSchonAmServer() {
        assertEquals(
            "https://openrouter.ai/api/v1/models?output_modalities=transcription",
            ModelLists.request(stt("openrouter"), ModelKind.STT).url,
        )
        assertEquals("https://openrouter.ai/api/v1/models", ModelLists.request(llm("openrouter"), ModelKind.LLM).url)
    }

    @Test fun elevenLabsMitEigenemKeyHeaderOhneWeiterleitung() {
        val r = ModelLists.request(stt("elevenlabs"), ModelKind.STT)
        assertEquals("https://api.elevenlabs.io/v1/models", r.url)
        assertEquals("xi-api-key", r.authHeader)
        assertFalse(r.followRedirects)
    }

    @Test fun ollamaNutztApiTags() {
        assertEquals("https://ollama.com/api/tags", ModelLists.request(llm("ollama-cloud"), ModelKind.LLM).url)
        assertEquals("http://h:11434/api/tags", ModelLists.request(llm("ollama", "http://h:11434/v1"), ModelKind.LLM).url)
    }

    // --- OpenAI ---------------------------------------------------------------------

    private val openAi = data(
        """{"id":"gpt-transcribe","object":"model","created":1,"owned_by":"system","shutdown_date":null}""",
        """{"id":"gpt-4o-transcribe","object":"model","owned_by":"system","shutdown_date":"2027-02-26"}""",
        """{"id":"gpt-4o-mini-transcribe","object":"model","owned_by":"system"}""",
        """{"id":"gpt-4o-mini-transcribe-2025-03-20","object":"model","shutdown_date":"2026-03-01"}""",
        """{"id":"gpt-4o-transcribe-diarize","object":"model"}""",
        """{"id":"gpt-live-transcribe","object":"model"}""",
        """{"id":"gpt-realtime-whisper","object":"model"}""",
        """{"id":"whisper-1","object":"model"}""",
        """{"id":"gpt-6-astra","object":"model"}""",
        """{"id":"gpt-6.1-sol","object":"model"}""",
        """{"id":"gpt-5.4-mini","object":"model"}""",
        """{"id":"gpt-5-mini","object":"model","shutdown_date":"2026-12-11"}""",
        """{"id":"gpt-4o-mini","object":"model"}""",
        """{"id":"o4-mini","object":"model"}""",
        """{"id":"chatgpt-4o-latest","object":"model"}""",
        """{"id":"gpt-4.5-preview","object":"model","shutdown_date":1752451200}""",
        """{"id":"gpt-5-pro","object":"model"}""",
        """{"id":"gpt-5-codex","object":"model"}""",
        """{"id":"gpt-5.6-cyber","object":"model"}""",
        """{"id":"gpt-daybreak-red-latest","object":"model"}""",
        """{"id":"gpt-4o-mini-search-preview","object":"model"}""",
        """{"id":"gpt-3.5-turbo-instruct","object":"model"}""",
        """{"id":"gpt-4o-mini-tts","object":"model"}""",
        """{"id":"gpt-4o-audio-preview","object":"model"}""",
        """{"id":"gpt-realtime","object":"model"}""",
        """{"id":"gpt-image-1","object":"model"}""",
        """{"id":"text-embedding-3-small","object":"model"}""",
        """{"id":"omni-moderation-latest","object":"model"}""",
        """{"id":"dall-e-3","object":"model"}""",
        """{"id":"davinci-002","object":"model"}""",
        """{"id":"computer-use-preview","object":"model"}""",
    )

    @Test fun openAiErkennungNurUploadTauglicheTranskription() {
        assertEquals(
            listOf("gpt-4o-mini-transcribe", "gpt-4o-transcribe", "gpt-transcribe", "whisper-1"),
            ids("openai", ModelKind.STT, openAi),
        )
    }

    @Test fun openAiChatOhneResponsesOnlyUndOhneAbgeschaltete() {
        assertEquals(
            listOf("chatgpt-4o-latest", "gpt-4o-mini", "gpt-5-mini", "gpt-5.4-mini", "gpt-6-astra", "gpt-6.1-sol", "o4-mini"),
            ids("openai", ModelKind.LLM, openAi),
        )
    }

    @Test fun kuenftigesAbschaltDatumWirdHinweis() {
        val models = ModelLists.parse(p("openai"), ModelKind.LLM, openAi, today)
        assertEquals("Auslauf 2026-12-11", models.first { it.id == "gpt-5-mini" }.note)
        assertNull(models.first { it.id == "gpt-4o-mini" }.note)
        assertEquals(
            "Auslauf 2027-02-26",
            ModelLists.parse(p("openai"), ModelKind.STT, openAi, today).first { it.id == "gpt-4o-transcribe" }.note,
        )
    }

    // --- Groq -----------------------------------------------------------------------

    @Test fun groqNurAktiveUndNachName() {
        val body = data(
            """{"id":"whisper-large-v3","object":"model","owned_by":"OpenAI","active":true,"context_window":448}""",
            """{"id":"whisper-large-v3-turbo","object":"model","owned_by":"OpenAI","active":true}""",
            """{"id":"openai/gpt-oss-20b","object":"model","active":true,"context_window":131072}""",
            """{"id":"openai/gpt-oss-120b","object":"model"}""",
            """{"id":"qwen/qwen3.8-27b","object":"model","active":true,"public_apps":null}""",
            """{"id":"llama-3.1-8b-instant","object":"model","active":false}""",
            """{"id":"canopylabs/orpheus-v1-english","object":"model","active":true}""",
            """{"id":"meta-llama/llama-prompt-guard-2-22m","object":"model","active":true}""",
            """{"id":"openai/gpt-oss-safeguard-20b","object":"model","active":true}""",
            """{"id":"groq/compound","object":"model","active":true}""",
        )
        assertEquals(listOf("whisper-large-v3", "whisper-large-v3-turbo"), ids("groq", ModelKind.STT, body))
        assertEquals(listOf("openai/gpt-oss-120b", "openai/gpt-oss-20b", "qwen/qwen3.8-27b"), ids("groq", ModelKind.LLM, body))
    }

    // --- Mistral --------------------------------------------------------------------

    private fun mistral(id: String, caps: String, aliases: String = "[]", deprecation: String = "null", extra: String = "") =
        """{"id":"$id","object":"model","type":"base","capabilities":{$caps},"aliases":$aliases,"deprecation":$deprecation$extra}"""

    private val mistralBody = data(
        mistral("voxtral-mini-2602", """"audio_transcription":true""", """["voxtral-mini-latest"]"""),
        mistral("voxtral-mini-latest", """"audio_transcription":true""", """["voxtral-mini-2602"]"""),
        mistral("voxtral-mini-transcribe-realtime-2602", """"audio_transcription":true,"audio_transcription_realtime":true"""),
        mistral("voxtral-mini-tts-2603", """"audio_speech":true"""),
        mistral("voxtral-small-2507", """"completion_chat":true,"audio":true""", """["voxtral-small-latest"]"""),
        mistral("mistral-small-2603", """"completion_chat":true,"function_calling":true""", """["mistral-small-latest"]"""),
        mistral("mistral-small-latest", """"completion_chat":true""", """["mistral-small-2603"]"""),
        mistral("mistral-medium-3-5", """"completion_chat":true""", deprecation = "\"2027-01-31T00:00:00Z\""),
        mistral("open-mistral-7b", """"completion_chat":true""", deprecation = "\"2026-03-30T00:00:00Z\""),
        mistral("mistral-ocr-4-1", """"completion_chat":true,"ocr":true"""),
        mistral("mistral-moderation-2603", """"moderation":true"""),
        mistral("ft:mistral-small:abc", """"completion_chat":true""", extra = ""","archived":true"""),
    )

    @Test fun mistralNachCapabilitiesUndJeAliasGruppeEinmalMitLatest() {
        assertEquals(listOf("voxtral-mini-latest"), ids("mistral", ModelKind.STT, mistralBody))
        assertEquals(
            listOf("mistral-medium-3-5", "mistral-small-latest", "voxtral-small-latest"),
            ids("mistral", ModelKind.LLM, mistralBody),
        )
        val medium = ModelLists.parse(p("mistral"), ModelKind.LLM, mistralBody, today).first { it.id == "mistral-medium-3-5" }
        assertEquals("Auslauf 2027-01-31", medium.note)
    }

    @Test fun mistralOhneCapabilitiesNachName() {
        val body = data(
            """{"id":"voxtral-mini-latest"}""",
            """{"id":"voxtral-mini-tts-2603"}""",
            """{"id":"voxtral-mini-transcribe-realtime-2602"}""",
            """{"id":"mistral-small-latest"}""",
        )
        assertEquals(listOf("voxtral-mini-latest"), ids("mistral", ModelKind.STT, body))
        assertEquals(listOf("mistral-small-latest", "voxtral-mini-latest"), ids("mistral", ModelKind.LLM, body))
    }

    // --- Together, DeepInfra ----------------------------------------------------------

    @Test fun togetherTopLevelArrayNachTypOhneDeepgram() {
        val body = """[
            {"id":"openai/whisper-large-v3","object":"model","type":"transcribe","display_name":"Whisper large-v3","pricing":{"base":0}},
            {"id":"nvidia/parakeet-tdt-0.6b-v3","object":"model","display_name":"Parakeet TDT"},
            {"id":"deepgram/nova-3-multi","object":"model","type":"transcribe"},
            {"id":"moonshotai/Kimi-K3","object":"model","type":"chat"},
            {"id":"cartesia/sonic-3","object":"model","type":"audio"},
            {"id":"BAAI/bge-large-en-v1.5","object":"model","type":"embedding"}
        ]"""
        val models = ModelLists.parse(p("together"), ModelKind.STT, body, today)
        assertEquals(listOf("nvidia/parakeet-tdt-0.6b-v3", "openai/whisper-large-v3"), models.map { it.id })
        assertEquals("Whisper large-v3", models[1].label)
        // Together kann (noch) keine Textverbesserung im Katalog -> keine LLM-Liste.
        assertEquals(emptyList<String>(), ids("together", ModelKind.LLM, body))
        // Auch die {data:[…]}-Form wird gelesen.
        assertEquals(listOf("openai/whisper-large-v3"), ids("together", ModelKind.STT, data("""{"id":"openai/whisper-large-v3","type":"transcribe"}""")))
    }

    @Test fun deepInfraNachMetadataTags() {
        fun e(id: String, tags: String) =
            """{"id":"$id","object":"model","created":0,"owned_by":"deepinfra","root":"$id","parent":null,"metadata":{"description":"x","context_length":0,"pricing":{"input_seconds":0.0001},"tags":$tags}}"""
        val body = data(
            e("openai/whisper-large-v3-turbo", """["stt"]"""),
            e("Qwen/Qwen3-ASR-1.7B", """["stt"]"""),
            e("mistralai/Voxtral-Mini-3B-2507", """["stt"]"""),
            e("moonshotai/Kimi-K3", """["chat","reasoning","reasoning_effort"]"""),
            e("hexgrad/Kokoro-82M", """["tts"]"""),
            e("BAAI/bge-m3", """["embed"]"""),
            """{"id":"openai/whisper-large-v3","object":"model"}""",
        )
        assertEquals(
            listOf("mistralai/Voxtral-Mini-3B-2507", "openai/whisper-large-v3", "openai/whisper-large-v3-turbo", "Qwen/Qwen3-ASR-1.7B"),
            ids("deepinfra", ModelKind.STT, body),
        )
    }

    // --- OpenRouter -------------------------------------------------------------------

    private fun orModel(
        id: String,
        input: String = """["text"]""",
        output: String = """["text"]""",
        params: String? = """["temperature","max_tokens"]""",
        reasoning: String? = null,
        expires: String = "null",
    ) = """{"id":"$id","canonical_slug":"$id","name":"Name $id","created":1,"context_length":1000,"expiration_date":$expires,
        "architecture":{"modality":"text->text","input_modalities":$input,"output_modalities":$output,"tokenizer":"Other"},
        "pricing":{"prompt":"0.000001","completion":"0.000005"}""" +
        (params?.let { ""","supported_parameters":$it""" } ?: "") +
        (reasoning?.let { ""","reasoning":$it""" } ?: "") + "}"

    @Test fun openRouterErkennungAusDemGefiltertenEndpunkt() {
        val body = """{"data":[
            ${orModel("openai/whisper-1", input = """["audio"]""", output = """["transcription"]""", params = "[]")},
            ${orModel("mistralai/voxtral-mini-transcribe", input = """["audio"]""", output = """["transcription"]""", params = "[]")}
        ],"links":{"next":null},"total_count":2}"""
        val models = ModelLists.parse(p("openrouter"), ModelKind.STT, body, today)
        assertEquals(listOf("mistralai/voxtral-mini-transcribe", "openai/whisper-1"), models.map { it.id })
        assertEquals("Name openai/whisper-1", models[1].label)
        assertNull(models[1].temperatureSupported)
    }

    @Test fun openRouterChatNurTextOhneBatchRouterGuardUndMitFlags() {
        val body = """{"data":[
            ${orModel("anthropic/claude-haiku-4.5", input = """["text","image"]""", reasoning = """{"mandatory":false,"supported_efforts":["low","high"]}""")},
            ${orModel("mistralai/mistral-small-2603", reasoning = """{"mandatory":false,"default_enabled":false,"supported_efforts":["none","low"],"default_effort":"none"}""")},
            ${orModel("openai/gpt-6.1-sol", params = """["reasoning","max_tokens"]""", reasoning = """{"mandatory":true,"supported_efforts":["low","medium","high"]}""")},
            ${orModel("qwen/qwen3.8-27b:free", params = null)},
            ${orModel("~anthropic/claude-haiku-latest")},
            ${orModel("google/gemini-2.5-flash-lite", expires = "\"2026-10-20\"")},
            ${orModel("google/gemini-2.0-flash-001", expires = "\"2026-06-01\"")},
            ${orModel("google/gemini-3.1-flash-image", output = """["image","text"]""")},
            ${orModel("openai/gpt-audio", output = """["text","audio"]""")},
            ${orModel("x/vision-only", input = """["image"]""")},
            ${orModel("anthropic/claude-sonnet-5.5:batch")},
            ${orModel("openrouter/auto")},
            ${orModel("meta-llama/llama-guard-4-12b")},
            ${orModel("openai/gpt-oss-safeguard-20b")}
        ],"links":{"next":null},"total_count":14}"""
        val models = ModelLists.parse(p("openrouter"), ModelKind.LLM, body, today).associateBy { it.id }
        assertEquals(
            listOf(
                "~anthropic/claude-haiku-latest", "anthropic/claude-haiku-4.5", "google/gemini-2.5-flash-lite",
                "mistralai/mistral-small-2603", "openai/gpt-6.1-sol", "qwen/qwen3.8-27b:free",
            ).sortedBy { it.lowercase() },
            models.keys.toList(),
        )
        assertEquals(true, models.getValue("anthropic/claude-haiku-4.5").temperatureSupported)
        assertNull(models.getValue("anthropic/claude-haiku-4.5").reasoningEffort)
        assertEquals("none", models.getValue("mistralai/mistral-small-2603").reasoningEffort)
        // Pflicht-Reasoning ohne temperature: Feld weglassen, kein temperature senden.
        assertEquals(false, models.getValue("openai/gpt-6.1-sol").temperatureSupported)
        assertNull(models.getValue("openai/gpt-6.1-sol").reasoningEffort)
        // Ohne supported_parameters: unbekannt, nicht "nein".
        assertNull(models.getValue("qwen/qwen3.8-27b:free").temperatureSupported)
        assertEquals("Auslauf 2026-10-20", models.getValue("google/gemini-2.5-flash-lite").note)
        assertEquals("Name anthropic/claude-haiku-4.5", models.getValue("anthropic/claude-haiku-4.5").label)
    }

    // --- Anthropic, Gemini, DeepSeek --------------------------------------------------

    @Test fun anthropicBehaeltDieReihenfolgeUndNutztDisplayName() {
        val body = """{"data":[
            {"type":"model","id":"claude-opus-5-5","display_name":"Claude Opus 5.5","created_at":"2026-09-01T00:00:00Z","capabilities":{"effort":{"low":{"supported":true}}}},
            {"type":"model","id":"claude-sonnet-5","display_name":"Claude Sonnet 5","created_at":"2026-05-01T00:00:00Z"},
            {"type":"model","id":"claude-haiku-4-5","display_name":"Claude Haiku 4.5","created_at":"1970-01-01T00:00:00Z"}
        ],"has_more":false,"first_id":"claude-opus-5-5","last_id":"claude-haiku-4-5"}"""
        val models = ModelLists.parse(p("anthropic"), ModelKind.LLM, body, today)
        assertEquals(listOf("claude-opus-5-5", "claude-sonnet-5", "claude-haiku-4-5"), models.map { it.id })
        assertEquals("Claude Opus 5.5", models[0].label)
        // Anthropic hat keine Erkennung.
        assertEquals(emptyList<String>(), ids("anthropic", ModelKind.STT, body))
    }

    @Test fun geminiOhnePraefixUndOhneNichtChatModelle() {
        val body = data(
            """{"id":"models/gemini-3.8-flash","object":"model","owned_by":"google"}""",
            """{"id":"models/gemini-3.5-flash-lite","object":"model"}""",
            """{"id":"models/gemini-flash-latest","object":"model"}""",
            """{"id":"gemini-2.5-flash-lite","object":"model"}""",
            """{"id":"models/gemini-embedding-001"}""",
            """{"id":"models/gemini-3.8-flash-tts"}""",
            """{"id":"models/gemini-3.1-flash-image"}""",
            """{"id":"models/gemini-3.8-live"}""",
            """{"id":"models/gemini-2.5-flash-native-audio-preview-12-2025"}""",
            """{"id":"models/gemini-3.5-transcribe"}""",
            """{"id":"models/veo-3.1-generate-preview"}""",
            """{"id":"models/imagen-4.0-generate"}""",
            """{"id":"models/lyria-3.5"}""",
            """{"id":"models/gemini-robotics-er-2-preview"}""",
            """{"id":"models/gemini-2.5-computer-use-preview-10-2025"}""",
            """{"id":"models/deep-research-preview-04-2026"}""",
            """{"id":"models/antigravity-preview-09-2026"}""",
            """{"id":"models/gemini-omni-1.1-flash"}""",
            """{"id":"models/aqa"}""",
        )
        assertEquals(
            listOf("gemini-2.5-flash-lite", "gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-flash-latest"),
            ids("gemini", ModelKind.LLM, body),
        )
    }

    @Test fun deepSeekAlleMitName() {
        val body = data(
            """{"id":"deepseek-flash","object":"model","owned_by":"deepseek","name":"DeepSeek-V4.1-Flash","input_modalities":["text","image"],"output_modalities":["text"],"effort":{"supported_levels":["low","high","max"],"default_level":"high"}}""",
            """{"id":"deepseek-v4-pro","object":"model","owned_by":"deepseek"}""",
        )
        val models = ModelLists.parse(p("deepseek"), ModelKind.LLM, body, today)
        assertEquals(listOf("deepseek-flash", "deepseek-v4-pro"), models.map { it.id })
        assertEquals("DeepSeek-V4.1-Flash", models[0].label)
        assertNull(models[1].label)
    }

    // --- Ollama, eigener Server, ElevenLabs ---------------------------------------------

    @Test fun ollamaOhneEinbettungsModelle() {
        val body = """{"models":[{"name":"gemma4:31b","model":"gemma4:31b","details":{"family":""}},{"name":"nomic-embed-text:latest"},{"name":"qwen3:8b"}]}"""
        assertEquals(listOf("gemma4:31b", "qwen3:8b"), ids("ollama", ModelKind.LLM, body))
        assertEquals(listOf("gemma4:31b", "qwen3:8b"), ids("ollama-cloud", ModelKind.LLM, body))
        assertEquals(emptyList<String>(), ids("ollama", ModelKind.STT, body))
    }

    @Test fun eigenerServerMitTaskWieSpeaches() {
        val body = data(
            """{"id":"Systran/faster-whisper-small","object":"model","owned_by":"Systran","language":["de","en"],"task":"automatic-speech-recognition"}""",
            """{"id":"speaches-ai/Kokoro-82M-v1.0-ONNX","object":"model","task":"text-to-speech"}""",
        )
        assertEquals(listOf("Systran/faster-whisper-small"), ids("custom", ModelKind.STT, body))
        // Reiner Audio-Server: fuer die Textverbesserung greift nichts -> lieber alle zeigen als keins.
        assertEquals(2, ids("custom", ModelKind.LLM, body).size)
    }

    @Test fun eigenerServerOhneFelderNachNameUndLeerHeisstAlle() {
        val body = data(
            """{"id":"Qwen/Qwen3-8B","object":"model","owned_by":"vllm","max_model_len":32768}""",
            """{"id":"whisper-large-v3-turbo","object":"model"}""",
            """{"id":"BAAI/bge-m3-embed","object":"model"}""",
        )
        assertEquals(listOf("whisper-large-v3-turbo"), ids("custom", ModelKind.STT, body))
        assertEquals(listOf("Qwen/Qwen3-8B"), ids("custom", ModelKind.LLM, body))
        val nurChat = data("""{"id":"llama3.2"}""", """{"id":"gemma4"}""")
        assertEquals(listOf("gemma4", "llama3.2"), ids("custom", ModelKind.STT, nurChat))
    }

    @Test fun elevenLabsNurScribeOhneRealtime() {
        val body = """[
            {"model_id":"eleven_multilingual_v2","name":"Eleven Multilingual v2","can_do_text_to_speech":true,"languages":[{"language_id":"de","name":"German"}]},
            {"model_id":"eleven_flash_v2_5","name":"Eleven Flash v2.5","can_do_text_to_speech":true},
            {"model_id":"scribe_v2","name":"Scribe v2"},
            {"model_id":"scribe_v2_realtime","name":"Scribe v2 Realtime"},
            {"model_id":"scribe_v2_medical","name":"Scribe v2 Medical"}
        ]"""
        val models = ModelLists.parse(p("elevenlabs"), ModelKind.STT, body, today)
        assertEquals(listOf("scribe_v2", "scribe_v2_medical"), models.map { it.id })
        assertEquals("Scribe v2", models[0].label)
        // Laut Doku der Normalfall: nur Sprachausgabe -> leer, kein Fehler.
        assertEquals(emptyList<String>(), ids("elevenlabs", ModelKind.STT, """[{"model_id":"eleven_v3","can_do_text_to_speech":true}]"""))
        assertEquals(emptyList<String>(), ids("elevenlabs", ModelKind.LLM, body))
    }

    // --- Robustheit -------------------------------------------------------------------

    @Test fun kaputteAntwortIstEinKlarerFehler() {
        for ((provider, body) in listOf(
            "openai" to "<html>Bad Gateway</html>",
            "openai" to """{"data":[{"id":"gpt-4o""",
            "openai" to "{}",
            "groq" to """{"data":"keine Liste"}""",
            "openrouter" to "",
            "elevenlabs" to """{"detail":"Not Found"}""",
            "ollama" to "not json",
        )) {
            val kind = if (provider == "ollama") ModelKind.LLM else ModelKind.STT
            val e = assertThrows(provider, ModelListException::class.java) { ModelLists.parse(p(provider), kind, body, today) }
            assertEquals("Antwort des Servers ist keine Modell-Liste", e.message)
        }
    }

    @Test fun unbekannteFelderUndLeereEintraegeStoerenNicht() {
        val body = data(
            """{"id":"whisper-1","neu":{"x":[1,2]},"shutdown_date":"kein Datum"}""",
            """{"id":""}""",
            """{"object":"model"}""",
            "42",
            """{"id":"whisper-1"}""",
        )
        assertEquals(listOf("whisper-1"), ids("openai", ModelKind.STT, body))
    }

    @Test fun datumAusTextZeitstempelOderSekunden() {
        assertEquals(LocalDate.parse("2027-02-26"), ModelLists.date("2027-02-26"))
        assertEquals(LocalDate.parse("2027-01-31"), ModelLists.date("2027-01-31T00:00:00Z"))
        assertEquals(LocalDate.parse("2025-07-14"), ModelLists.date(1752451200))
        assertNull(ModelLists.date("bald"))
        assertNull(ModelLists.date(null))
    }
}
