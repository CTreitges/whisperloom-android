package com.chris.whisperloom.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** org.json gibt es auf der JVM nur ueber Robolectric — deshalb der JSON-Zusammenbau hier. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatPayloadJsonTest {

    private fun llm(model: String, provider: String = "same"): ApiAccess =
        AccessResolver.resolveLlm(AccessResolver.resolveStt("openai", "", "k", "", 0), provider, "", "", model)

    @Test fun klassischerBodyMitTemperatureUndNachrichten() {
        val json = JSONObject(ChatPayload.build(llm("gpt-4o-mini"), "SYSTEM", "hallo welt"))
        assertEquals("gpt-4o-mini", json.getString("model"))
        assertEquals(0, json.getInt("temperature"))
        assertFalse(json.has("reasoning_effort"))
        assertFalse(json.has("max_completion_tokens"))
        val messages = json.getJSONArray("messages")
        assertEquals(2, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("SYSTEM", messages.getJSONObject(0).getString("content"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals("hallo welt", messages.getJSONObject(1).getString("content"))
    }

    @Test fun reasoningBodyOhneTemperature() {
        val json = JSONObject(ChatPayload.build(llm("gpt-5.6-luna"), "S", "U"))
        assertFalse(json.has("temperature"))
        assertEquals("none", json.getString("reasoning_effort"))
        assertEquals(16384, json.getInt("max_completion_tokens"))
        assertFalse(json.has("thinking"))
    }

    @Test fun thinkingNurWennDasModellEsBraucht() {
        val haiku = JSONObject(ChatPayload.build(llm("claude-haiku-5-5", "anthropic"), "S", "U"))
        assertEquals("disabled", haiku.getJSONObject("thinking").getString("type"))
        assertEquals(1, haiku.getJSONObject("thinking").length())
        assertFalse(haiku.has("temperature"))
        assertFalse(haiku.has("reasoning_effort"))
        assertEquals(16384, haiku.getInt("max_completion_tokens"))
        assertEquals("between_tools", JSONObject(ChatPayload.build(llm("claude-sonnet-5-5", "anthropic"), "S", "U")).getJSONObject("thinking").getString("type"))
        assertFalse(JSONObject(ChatPayload.build(llm("claude-opus-5-5", "anthropic"), "S", "U")).has("thinking"))
        assertFalse(JSONObject(ChatPayload.build(llm("gpt-4o-mini"), "S", "U")).has("thinking"))
    }

    @Test fun sonderzeichenWerdenEscaped() {
        val json = JSONObject(ChatPayload.build(llm("gpt-4o-mini"), "S", "Zeile 1\n\"Zitat\" \\ Ende"))
        assertEquals("Zeile 1\n\"Zitat\" \\ Ende", json.getJSONArray("messages").getJSONObject(1).getString("content"))
        assertTrue(json.toString().contains("\\n"))
    }
}
