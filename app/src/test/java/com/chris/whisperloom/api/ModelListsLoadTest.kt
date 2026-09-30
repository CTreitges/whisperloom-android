package com.chris.whisperloom.api

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

/**
 * [ModelLists.load] gegen lokale JDK-HttpServer: Adresse, Header je Anbieter und der Schutz davor,
 * dass ein eigener Key-Header per Weiterleitung bei einem fremden Host landet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelListsLoadTest {

    private lateinit var server: HttpServer
    private lateinit var fremd: HttpServer
    private lateinit var umleitung: HttpServer

    private var status = 200
    private var response = """{"object":"list","data":[{"id":"whisper-1"},{"id":"gpt-4o-mini"}]}"""
    private var path: String? = null
    private var query: String? = null
    private var auth: String? = "unset"
    private var xiKey: String? = "unset"
    private var version: String? = "unset"
    private var fremdAufrufe = 0
    private var fremdGeheim: String? = null

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            path = ex.requestURI.path
            query = ex.requestURI.query
            auth = ex.requestHeaders.getFirst("Authorization")
            xiKey = ex.requestHeaders.getFirst("xi-api-key")
            version = ex.requestHeaders.getFirst("anthropic-version")
            val out = response.toByteArray()
            ex.sendResponseHeaders(status, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.start()

        fremd = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        fremd.createContext("/") { ex ->
            fremdAufrufe++
            fremdGeheim = ex.requestHeaders.getFirst("xi-api-key") ?: ex.requestHeaders.getFirst("anthropic-version")
            val out = "[]".toByteArray()
            ex.sendResponseHeaders(200, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        fremd.start()

        umleitung = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        umleitung.createContext("/") { ex ->
            ex.responseHeaders.add("Location", "http://127.0.0.1:${fremd.address.port}/woanders")
            ex.sendResponseHeaders(302, -1)
            ex.close()
        }
        umleitung.start()
    }

    @After fun stop() {
        server.stop(0)
        fremd.stop(0)
        umleitung.stop(0)
    }

    private fun base(s: HttpServer = server) = "http://127.0.0.1:${s.address.port}/v1"

    private fun stt(provider: String, url: String = base(), key: String = "geheim") =
        AccessResolver.resolveStt(provider, url, key, "")

    private fun llm(provider: String, url: String = base(), key: String = "geheim") =
        AccessResolver.resolveLlm(stt("groq"), provider, url, key, "")

    @Test fun openAiFormMitBearer() {
        assertEquals(listOf("whisper-1"), ModelLists.load(stt("openai"), ModelKind.STT).map { it.id })
        assertEquals("/v1/models", path)
        assertEquals("Bearer geheim", auth)
        assertNull(query)
    }

    @Test fun ohneKeyKeinAuthorizationHeader() {
        ModelLists.load(stt("custom", key = ""), ModelKind.STT)
        assertNull(auth)
    }

    @Test fun anthropicMitVersionUndLimit() {
        response = """{"data":[{"type":"model","id":"claude-sonnet-5","display_name":"Claude Sonnet 5"}],"has_more":false}"""
        val models = ModelLists.load(llm("anthropic"), ModelKind.LLM)
        assertEquals(listOf("claude-sonnet-5"), models.map { it.id })
        assertEquals("/v1/models", path)
        assertEquals("limit=1000", query)
        assertEquals("2023-06-01", version)
        assertEquals("Bearer geheim", auth)
    }

    @Test fun elevenLabsMitXiApiKeyOhneBearer() {
        response = """[{"model_id":"scribe_v2","name":"Scribe v2"},{"model_id":"eleven_v3"}]"""
        assertEquals(listOf("scribe_v2"), ModelLists.load(stt("elevenlabs"), ModelKind.STT).map { it.id })
        assertEquals("/v1/models", path)
        assertEquals("geheim", xiKey)
        assertNull(auth)
    }

    @Test fun openRouterErkennungMitFilterImQuery() {
        response = """{"data":[{"id":"openai/whisper-1","name":"OpenAI: Whisper","architecture":{"output_modalities":["transcription"]}}]}"""
        assertEquals(listOf("openai/whisper-1"), ModelLists.load(stt("openrouter"), ModelKind.STT).map { it.id })
        assertEquals("output_modalities=transcription", query)
    }

    @Test fun ollamaUeberApiTags() {
        response = """{"models":[{"name":"gemma4:31b"},{"name":"nomic-embed-text"}]}"""
        assertEquals(listOf("gemma4:31b"), ModelLists.load(llm("ollama"), ModelKind.LLM).map { it.id })
        assertEquals("/api/tags", path)
    }

    @Test fun eigenerKeyHeaderFolgtKeinerWeiterleitung() {
        val eleven = assertThrows(ApiHttpException::class.java) {
            ModelLists.load(stt("elevenlabs", base(umleitung)), ModelKind.STT)
        }
        assertEquals(302, eleven.code)
        val anthropic = assertThrows(ApiHttpException::class.java) {
            ModelLists.load(llm("anthropic", base(umleitung)), ModelKind.LLM)
        }
        assertEquals(302, anthropic.code)
        assertEquals("Der fremde Host wird nie angefragt", 0, fremdAufrufe)
        assertNull(fremdGeheim)
    }

    @Test fun kaputteAntwortWirdKlarerFehler() {
        response = "<html>Bad Gateway</html>"
        val e = assertThrows(ModelListException::class.java) { ModelLists.load(stt("openai"), ModelKind.STT) }
        assertEquals("Antwort des Servers ist keine Modell-Liste", e.message)
    }

    @Test fun fehlerstatusBleibtLesbar() {
        status = 401
        response = """{"error":{"message":"Incorrect API key provided"}}"""
        val e = assertThrows(ApiHttpException::class.java) { ModelLists.load(stt("openai"), ModelKind.STT) }
        assertEquals(401, e.code)
        assertEquals("Incorrect API key provided", e.detail)
    }

    @Test fun ohneAdresseKeineAnfrage() {
        assertThrows(ApiNotConfiguredException::class.java) { ModelLists.load(stt("custom", url = ""), ModelKind.STT) }
        assertNull(path)
    }
}
