package com.chris.whisperloom.api

import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

/**
 * Native Ollama-API: Adress-Normalisierung, Payload, Parser und Modell-Liste gegen einen
 * lokalen JDK-HttpServer. Robolectric wegen org.json.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OllamaApiTest {

    private lateinit var server: HttpServer
    private var tagsAuth: String? = "unset"
    private var tagsStatus = 200
    private var tagsResponse = """{"models":[{"name":"qwen3:8b","model":"qwen3:8b"},{"name":"Gemma4:31b"},{"model":"llama3.2"},{"name":"qwen3:8b"}]}"""

    @Before fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/tags") { ex ->
            tagsAuth = ex.requestHeaders.getFirst("Authorization")
            val out = tagsResponse.toByteArray()
            ex.sendResponseHeaders(tagsStatus, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.start()
    }

    @After fun stopServer() {
        server.stop(0)
    }

    private fun access(provider: String, url: String, key: String = ""): ApiAccess =
        AccessResolver.resolveLlm(AccessResolver.resolveStt("groq", "", "gsk", "", 0), provider, url, key, "")

    @Test fun wurzelOhneApiUndV1() {
        assertEquals("http://h:11434", OllamaApi.root("http://h:11434"))
        assertEquals("http://h:11434", OllamaApi.root(" http://h:11434/ "))
        assertEquals("http://h:11434", OllamaApi.root("http://h:11434/v1"))
        assertEquals("http://h:11434", OllamaApi.root("http://h:11434/api/"))
        assertEquals("http://h:11434", OllamaApi.root("http://h:11434/v1/api"))
        assertEquals("https://ollama.com/api/chat", OllamaApi.chatUrl("https://ollama.com"))
        assertEquals("https://ollama.com/api/tags", OllamaApi.tagsUrl("https://ollama.com/api"))
    }

    @Test fun payloadOhneThinkUndOhneStreaming() {
        val json = JSONObject(OllamaApi.chatPayload("gemma4:31b", "SYSTEM", "hallo welt"))
        assertEquals("gemma4:31b", json.getString("model"))
        assertFalse(json.getBoolean("stream"))
        // think:false wuerde bei glm das Nachdenken in den Text schreiben (live gemessen) — also weglassen.
        assertFalse(json.has("think"))
        assertEquals(0, json.getJSONObject("options").getInt("temperature"))
        val messages = json.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("SYSTEM", messages.getJSONObject(0).getString("content"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals("hallo welt", messages.getJSONObject(1).getString("content"))
    }

    @Test fun gptOssBekommtThinkLow() {
        assertEquals("low", JSONObject(OllamaApi.chatPayload("gpt-oss:20b", "S", "U")).getString("think"))
        assertEquals("low", JSONObject(OllamaApi.chatPayload("GPT-OSS:120b", "S", "U")).getString("think"))
    }

    @Test fun antwortParser() {
        assertEquals(
            "Hallo Welt.",
            OllamaApi.parseChat("""{"message":{"role":"assistant","content":"Hallo Welt.","thinking":"lang"},"done":true}"""),
        )
        assertNull(OllamaApi.parseChat("""{"message":{"role":"assistant","content":"  "}}"""))
        assertNull(OllamaApi.parseChat("""{"done":true}"""))
    }

    @Test fun tagsParserSortiertUndEntferntDuplikate() {
        assertEquals(listOf("Gemma4:31b", "llama3.2", "qwen3:8b"), OllamaApi.parseTags(tagsResponse))
        assertEquals(emptyList<String>(), OllamaApi.parseTags("""{"models":[]}"""))
        assertEquals(emptyList<String>(), OllamaApi.parseTags("""{}"""))
    }

    @Test fun modellListeVomLokalenServerOhneKey() {
        val names = OllamaApi.listModels(access("ollama", "http://127.0.0.1:${server.address.port}/v1"))
        assertEquals(listOf("Gemma4:31b", "llama3.2", "qwen3:8b"), names)
        assertNull(tagsAuth)
    }

    @Test fun modellListeDerCloudMitKey() {
        OllamaApi.listModels(access("ollama-cloud", "http://127.0.0.1:${server.address.port}", "ok-key"))
        assertEquals("Bearer ok-key", tagsAuth)
    }

    @Test fun ohneAdresseKeineAnfrage() {
        try {
            OllamaApi.listModels(access("ollama", ""))
            fail("ApiNotConfiguredException erwartet")
        } catch (e: ApiNotConfiguredException) {
            // erwartet
        }
    }

    @Test fun fehlerDerOllamaFormWirdLesbar() {
        tagsStatus = 401
        tagsResponse = """{"error":"Unauthorized"}"""
        try {
            OllamaApi.listModels(access("ollama-cloud", "http://127.0.0.1:${server.address.port}", "falsch"))
            fail("401 erwartet")
        } catch (e: ApiHttpException) {
            assertEquals(401, e.code)
            assertEquals("Unauthorized", e.detail)
            assertTrue(e.message!!.contains("API-Key"))
        }
    }
}
