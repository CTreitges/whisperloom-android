package com.chris.whisperloom.api

import com.chris.whisperloom.RefineMode
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

/**
 * Sicherheitsnetz: ein Modell ohne bekannte Flags lehnt `temperature` mit 400 ab -> genau ein
 * zweiter Versuch ohne temperature, mit max_completion_tokens. Lokaler JDK-HttpServer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextRefinerRetryTest {

    private lateinit var server: HttpServer
    private val bodies = mutableListOf<JSONObject>()

    /** Antwort je Anfrage: (Status, Body). Default: temperature ablehnen wie OpenAI, sonst Text. */
    private var answer: (JSONObject) -> Pair<Int, String> = { body ->
        if (body.has("temperature")) 400 to TEMPERATURE_ERROR
        else 200 to """{"choices":[{"message":{"content":"Hallo Welt."},"finish_reason":"stop"}]}"""
    }

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { ex ->
            val body = JSONObject(ex.requestBody.readBytes().toString(Charsets.UTF_8))
            bodies += body
            val (status, text) = answer(body)
            val out = text.toByteArray()
            ex.sendResponseHeaders(status, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.start()
    }

    @After fun stop() {
        server.stop(0)
    }

    private fun refiner(model: String): TextRefiner {
        val stt = AccessResolver.resolveStt("groq", "", "gsk", "")
        return TextRefiner(AccessResolver.resolveLlm(stt, "openai", "http://127.0.0.1:${server.address.port}/v1", "sk", model))
    }

    private fun refine(model: String) = refiner(model).refine("hallo welt", "de", RefineMode.POLISH, smartFillers = false)

    @Test fun abgelehnteTemperatureEinmalOhneWiederholen() {
        assertEquals("Hallo Welt.", refine("gpt-neu-2026"))
        assertEquals(2, bodies.size)
        assertEquals(0, bodies[0].getInt("temperature"))
        assertFalse(bodies[1].has("temperature"))
        assertEquals(ChatPayload.MAX_COMPLETION_TOKENS, bodies[1].getInt("max_completion_tokens"))
        assertEquals("gpt-neu-2026", bodies[1].getString("model"))
    }

    @Test fun hoechstensEinZweiterVersuch() {
        answer = { 400 to TEMPERATURE_ERROR }
        val e = assertThrows(ApiHttpException::class.java) { refine("gpt-neu-2026") }
        assertEquals(400, e.code)
        assertEquals(2, bodies.size)
    }

    @Test fun andererFehlerOhneWiederholen() {
        answer = { 400 to """{"error":{"message":"Invalid value for 'messages'"}}""" }
        assertThrows(ApiHttpException::class.java) { refine("gpt-neu-2026") }
        answer = { 500 to """{"error":{"message":"temperature service down"}}""" }
        assertThrows(ApiHttpException::class.java) { refine("gpt-neu-2026") }
        assertEquals(2, bodies.size)
    }

    @Test fun ohneTemperatureGesendetKeinZweiterVersuch() {
        // gpt-5.6-luna steht im Katalog ohne temperature — ein 400 dazu ist ein anderes Problem.
        answer = { 400 to TEMPERATURE_ERROR }
        assertThrows(ApiHttpException::class.java) { refine("gpt-5.6-luna") }
        assertEquals(1, bodies.size)
        assertTrue(bodies[0].has("max_completion_tokens"))
    }

    private companion object {
        const val TEMPERATURE_ERROR =
            """{"error":{"message":"Unsupported parameter: 'temperature' is not supported with this model.","type":"invalid_request_error","param":"temperature","code":"unsupported_parameter"}}"""
    }
}
