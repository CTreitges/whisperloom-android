package com.chris.whisperloom.api

import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.SetupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * JVM-Unit-Tests fuer die Zugangs-Aufloesung. Hier entscheidet sich, an welchen Server
 * welcher Key geht — ein Fehler hier schickt den OpenAI-Key an Groq.
 */
class AccessResolverTest {

    @Test fun neuerNutzerBekommtOpenAiDefaults() {
        val a = AccessResolver.resolveStt("openai", "", "sk-x", "", 0)
        assertEquals("https://api.openai.com/v1", a.baseUrl)
        assertEquals("gpt-transcribe", a.model)
        assertEquals(90_000, a.readTimeoutMs)
        assertEquals("openai", a.provider.id)
        assertEquals("languages[]", a.modelOption!!.languageField)
    }

    @Test fun bestandsnutzerBehaeltAltesModellUndUrl() {
        // Vor v3: kein stt_provider, aber Key + gpt-4o-transcribe gespeichert.
        val a = AccessResolver.resolveStt("", "https://api.openai.com/v1", "sk-x", "gpt-4o-transcribe", 0)
        assertEquals("openai", a.provider.id)
        assertEquals("gpt-4o-transcribe", a.model)
        assertTrue(a.modelOption!!.label.contains("Auslauf"))
        assertEquals("language", a.modelOption!!.languageField)
    }

    @Test fun werteWerdenGetrimmt() {
        val a = AccessResolver.resolveStt("groq", "  https://api.groq.com/openai/v1/ ", " gsk ", " whisper-large-v3 ", 0)
        assertEquals("https://api.groq.com/openai/v1/", a.baseUrl)
        assertEquals("gsk", a.apiKey)
        assertEquals("whisper-large-v3", a.model)
        assertNotNull(a.modelOption)
    }

    @Test fun eigenerServerOhneUrlBleibtLeer() {
        val a = AccessResolver.resolveStt("custom", "", "", "", 0)
        assertEquals("", a.baseUrl)
        assertEquals("", a.model)
        assertEquals(600_000, a.readTimeoutMs)
        assertNull(a.modelOption)
        assertFalse(a.provider.needsKey)
    }

    @Test fun timeoutAusDenEinstellungenGewinnt() {
        assertEquals(120_000, AccessResolver.resolveStt("custom", "http://x/v1", "", "m", 120).readTimeoutMs)
        assertEquals(45_000, AccessResolver.resolveStt("openai", "", "k", "", 45).readTimeoutMs)
    }

    @Test fun unbekanntesModellHatKeineOption() {
        val a = AccessResolver.resolveStt("openai", "", "k", "mein-eigenes", 0)
        assertEquals("mein-eigenes", a.model)
        assertNull(a.modelOption)
    }

    // --- LLM ------------------------------------------------------------------

    private val stt = AccessResolver.resolveStt("openai", "", "sk-x", "", 45)

    @Test fun sameUebernimmtDenTranskriptionsZugang() {
        val l = AccessResolver.resolveLlm(stt, "same", "", "", "")
        assertEquals(stt.baseUrl, l.baseUrl)
        assertEquals("sk-x", l.apiKey)
        assertEquals(45_000, l.readTimeoutMs)
        assertEquals("openai", l.provider.id)
        assertEquals("gpt-6-luna", l.model)
        assertFalse(l.modelOption!!.temperatureSupported)
    }

    @Test fun leererProviderZaehltAlsSame() {
        val l = AccessResolver.resolveLlm(stt, "", "", "", "gpt-5.6-luna")
        assertEquals("sk-x", l.apiKey)
        assertEquals("gpt-5.6-luna", l.model)
        assertFalse(l.modelOption!!.temperatureSupported)
        assertEquals("none", l.modelOption!!.reasoningEffort)
    }

    @Test fun sameIgnoriertStaleLlmFelder() {
        // Bei "same" sind URL/Key im UI versteckt — alte Werte duerfen nicht durchsickern.
        val l = AccessResolver.resolveLlm(stt, "same", "http://alt/v1", "alter-key", "")
        assertEquals(stt.baseUrl, l.baseUrl)
        assertEquals("sk-x", l.apiKey)
    }

    @Test fun andererAnbieterBekommtNieDenSttKey() {
        val l = AccessResolver.resolveLlm(stt, "groq", "", "", "")
        assertEquals("groq", l.provider.id)
        assertEquals("https://api.groq.com/openai/v1", l.baseUrl)
        assertEquals("", l.apiKey)
        assertEquals("openai/gpt-oss-20b", l.model)
        assertEquals(90_000, l.readTimeoutMs)
    }

    @Test fun andererAnbieterMitEigenenFeldern() {
        val l = AccessResolver.resolveLlm(stt, "groq", " https://proxy/v1 ", " gsk ", " qwen/qwen3.8-27b ")
        assertEquals("https://proxy/v1", l.baseUrl)
        assertEquals("gsk", l.apiKey)
        assertEquals("qwen/qwen3.8-27b", l.model)
        assertEquals("none", l.modelOption!!.reasoningEffort)
    }

    @Test fun gleicherAnbieterExplizitErbtLeereFelder() {
        val l = AccessResolver.resolveLlm(stt, "openai", "", "", "gpt-4.1-mini")
        assertEquals(stt.baseUrl, l.baseUrl)
        assertEquals("sk-x", l.apiKey)
        assertEquals(45_000, l.readTimeoutMs)
        assertEquals("gpt-4.1-mini", l.model)
    }

    @Test fun eigenerServerErbtUrlVomEigenenSttServer() {
        val sttCustom = AccessResolver.resolveStt("custom", "http://192.168.1.50:8000/v1", "", "whisper-1", 0)
        val l = AccessResolver.resolveLlm(sttCustom, "custom", "", "", "qwen3:8b")
        assertEquals("http://192.168.1.50:8000/v1", l.baseUrl)
        assertEquals("", l.apiKey)
        assertEquals(600_000, l.readTimeoutMs)
        assertEquals("qwen3:8b", l.model)
        assertNull(l.modelOption)
    }

    @Test fun ollamaCloudNutztDenKatalogUndDenEigenenKey() {
        val l = AccessResolver.resolveLlm(stt, "ollama-cloud", "", "ok-key", "")
        assertEquals("https://ollama.com", l.baseUrl)
        assertEquals("ok-key", l.apiKey)
        assertEquals("gemma4:31b", l.model)
        assertEquals(90_000, l.readTimeoutMs)
        // Der Key des Transkriptions-Anbieters darf nie an ollama.com gehen.
        assertEquals("", AccessResolver.resolveLlm(stt, "ollama-cloud", "", "", "").apiKey)
    }

    @Test fun ollamaLokalOhneAdresseBleibtLeer() {
        val l = AccessResolver.resolveLlm(stt, "ollama", "", "", "")
        assertEquals("", l.baseUrl)
        assertEquals("", l.model)
        val mitAdresse = AccessResolver.resolveLlm(stt, "ollama", "http://192.168.1.10:11434", "", "gemma3")
        assertEquals("http://192.168.1.10:11434", mitAdresse.baseUrl)
        assertEquals(600_000, mitAdresse.readTimeoutMs)
    }

    @Test fun ollamaNebenCloudStt() {
        val l = AccessResolver.resolveLlm(stt, "custom", "http://192.168.1.50:11434/v1", "", "qwen3:8b")
        assertEquals("http://192.168.1.50:11434/v1", l.baseUrl)
        assertEquals("", l.apiKey)
        assertEquals(600_000, l.readTimeoutMs)
        assertTrue(l.provider.isCustom)
    }

    // --- Modell je Stufe (3.8.6) ------------------------------------------------------------

    private fun anthropic(model: String = "", stageModel: String = "", stage: RefineMode?) =
        AccessResolver.resolveLlm(stt, "anthropic", "", "sk-ant", model, stageModel = stageModel, stage = stage).model

    @Test fun ohneModellGiltDieEmpfehlungJeStufe() {
        for (mode in listOf(RefineMode.POLISH, RefineMode.READABLE)) {
            assertEquals(mode.name, "claude-haiku-5-5", anthropic(stage = mode))
        }
        for (mode in listOf(RefineMode.BEAUTIFY, RefineMode.SUMMARIZE, RefineMode.PROMPT)) {
            assertEquals(mode.name, "claude-sonnet-5-5", anthropic(stage = mode))
        }
        // Ohne Stufe (Bereitschaft, Tastatur, "Zugang pruefen") und bei "aus": das erste Katalogmodell.
        assertEquals("claude-haiku-5-5", anthropic(stage = null))
        assertEquals("claude-haiku-5-5", anthropic(stage = RefineMode.OFF))
    }

    @Test fun reihenfolgeStufenModellDannZugangsModellDannEmpfehlung() {
        assertEquals("claude-opus-5-5", anthropic(model = "claude-sonnet-5", stageModel = " claude-opus-5-5 ", stage = RefineMode.SUMMARIZE))
        // Das Modell des Zugangs, bewusst gesetzt, gilt fuer alle Stufen ohne eigenes — auch zum Umformulieren.
        assertEquals("claude-sonnet-5", anthropic(model = "claude-sonnet-5", stage = RefineMode.BEAUTIFY))
        assertEquals("claude-sonnet-5", anthropic(model = "claude-sonnet-5", stage = RefineMode.POLISH))
        assertEquals("claude-sonnet-5-5", anthropic(stageModel = "  ", stage = RefineMode.BEAUTIFY))
    }

    @Test fun ohneUmformulierenEmpfehlungDasErsteKatalogmodell() {
        val p = Provider(id = "x", name = "X", baseUrl = "https://x", llmModels = listOf(ModelOption("a", "A"), ModelOption("b", "B")))
        assertEquals("a", p.recommendedLlmModel(RefineMode.BEAUTIFY))
        assertEquals("b", p.copy(rewriteLlmModel = "b").recommendedLlmModel(RefineMode.PROMPT))
        assertEquals("a", p.copy(rewriteLlmModel = "b").recommendedLlmModel(RefineMode.READABLE))
        assertEquals("", ProviderCatalog.custom.recommendedLlmModel(RefineMode.BEAUTIFY))
    }

    @Test fun wieErkennungNutztDieEmpfehlungDesErkennungsAnbieters() {
        val l = AccessResolver.resolveLlm(stt, "same", "", "", "", stage = RefineMode.BEAUTIFY)
        assertEquals("gpt-6-sol", l.model)
        assertEquals("sk-x", l.apiKey)
        assertEquals("gpt-4.1-mini", AccessResolver.resolveLlm(stt, "same", "", "", "", stageModel = "gpt-4.1-mini", stage = RefineMode.BEAUTIFY).model)
        // Offline ohne eigenen Zugang: auch ein Stufen-Modell schickt nichts an den alten Zugang.
        val offline = AccessResolver.resolveLlm(stt, "same", "", "", "", sttOffline = true, stageModel = "gpt-6-sol", stage = RefineMode.BEAUTIFY)
        assertEquals(RefineBlock.OFFLINE, offline.refineBlock)
        assertEquals("", offline.model)
    }

    @Test fun ollamaLokalOhneKatalogNimmtDasModellDesZugangs() {
        val url = "http://192.168.1.10:11434"
        assertEquals("gemma4:12b", AccessResolver.resolveLlm(stt, "ollama", url, "", "gemma4:12b", stage = RefineMode.BEAUTIFY).model)
        assertEquals("gemma4:26b", AccessResolver.resolveLlm(stt, "ollama", url, "", "gemma4:12b", stageModel = "gemma4:26b", stage = RefineMode.BEAUTIFY).model)
        assertEquals("", AccessResolver.resolveLlm(stt, "ollama", url, "", "", stage = RefineMode.BEAUTIFY).model)
    }

    @Test fun stufenModellZaehltNurMitModellDesZugangs() {
        // Review 3.8.6 (L1): Ohne Modell des Zugangs sagen Bereitschaft, Tastatur und Banner (ohne Stufe)
        // "kein Modell" — dann darf auch die Stufe nicht mit ihrem eigenen Modell rausgehen.
        val together = AccessResolver.resolveStt("together", "", "k", "", 0)
        val same = AccessResolver.resolveLlm(together, "same", "", "", "", stageModel = "Y", stage = RefineMode.BEAUTIFY)
        assertEquals(RefineBlock.NO_MODEL, same.refineBlock)
        assertFalse(SetupState.llmReady(same))
        for (id in listOf("ollama", "custom")) {
            val l = AccessResolver.resolveLlm(stt, id, "http://192.168.1.10:11434", "", "", stageModel = "gemma4:26b", stage = RefineMode.BEAUTIFY)
            assertEquals(id, "", l.model)
            assertFalse(id, SetupState.llmReady(l))
        }
    }

    // --- Server-Modelle (Flags aus dem Cache bzw. Heuristik) ----------------------------

    /** Merkt sich die Anfragen; liefert fuer jede ID ein Modell mit den gegebenen Flags. */
    private class FakeLookup(private val model: (String) -> RemoteModel?) : ServerModelLookup {
        val asked = mutableListOf<String>()
        override fun find(providerId: String, kind: ModelKind, baseUrl: String, id: String): RemoteModel? {
            asked += "$providerId|${kind.key}|$baseUrl|$id"
            return model(id)
        }
    }

    @Test fun gespeichertesServerModellBekommtFlagsAusDemCache() {
        val lookup = FakeLookup { RemoteModel(it, label = "OpenAI: GPT-6.1 Sol", temperatureSupported = false) }
        val l = AccessResolver.resolveLlm(stt, "openrouter", "", "or-key", "openai/gpt-6.1-sol", lookup)
        assertEquals(listOf("openrouter|llm|https://openrouter.ai/api/v1|openai/gpt-6.1-sol"), lookup.asked)
        assertFalse(l.modelOption!!.temperatureSupported)
        assertEquals("OpenAI: GPT-6.1 Sol", l.modelOption!!.label)
        assertNull(ChatPayload.sampling(l).temperature)
    }

    @Test fun sameFragtDenCacheMitDemErkennungsZugang() {
        val lookup = FakeLookup { null }
        AccessResolver.resolveLlm(stt, "same", "", "", "gpt-6-astra", lookup)
        assertEquals(listOf("openai|llm|https://api.openai.com/v1|gpt-6-astra"), lookup.asked)
    }

    @Test fun katalogTrefferFragtDenCacheNicht() {
        val lookup = FakeLookup { error("darf nicht gefragt werden") }
        AccessResolver.resolveStt("openai", "", "k", "gpt-transcribe", 0, lookup)
        AccessResolver.resolveLlm(stt, "same", "", "", "gpt-4o-mini", lookup)
        AccessResolver.resolveStt("custom", "http://h/v1", "", "", 0, lookup)
        assertTrue(lookup.asked.isEmpty())
    }

    /** Merkt sich temperature-Ablehnungen wie der ModelCache: Schluessel Anbieter|Adresse|Modell. */
    private class RejectingLookup(private vararg val rejected: String) : ServerModelLookup {
        override fun find(providerId: String, kind: ModelKind, baseUrl: String, id: String): RemoteModel? = null
        override fun rejectsTemperature(providerId: String, baseUrl: String, id: String) = "$providerId|$baseUrl|$id" in rejected
    }

    @Test fun abgelehnteTemperatureGiltAuchFuerKatalogModelle() {
        val lookup = RejectingLookup("openai|https://api.openai.com/v1|gpt-4o-mini", "openai|https://api.openai.com/v1|gpt-neu")
        val katalog = AccessResolver.resolveLlm(stt, "same", "", "", "gpt-4o-mini", lookup)
        assertFalse(katalog.modelOption!!.temperatureSupported)
        assertEquals("GPT-4o mini", katalog.modelOption!!.label)
        assertNull(ChatPayload.sampling(katalog).temperature)
        // Frei getippt: eine Option nur fuer das Flag.
        val frei = AccessResolver.resolveLlm(stt, "same", "", "", "gpt-neu", lookup)
        assertEquals(ModelOption("gpt-neu", "gpt-neu", temperatureSupported = false), frei.modelOption)
        // Nicht gemerkt bleibt es beim Katalog.
        assertTrue(AccessResolver.resolveLlm(stt, "same", "", "", "gpt-4.1-mini", lookup).modelOption!!.temperatureSupported)
        // Die Erkennung kennt kein temperature-Merken.
        assertNull(AccessResolver.resolveStt("openai", "", "k", "gpt-neu", 0, lookup).modelOption)
    }

    @Test fun erkennungsModellVomServerMitHeuristik() {
        val lookup = FakeLookup { RemoteModel(it) }
        val a = AccessResolver.resolveStt("openai", "", "k", "gpt-transcribe-2026-08-01", 0, lookup)
        assertEquals(listOf("openai|stt|https://api.openai.com/v1|gpt-transcribe-2026-08-01"), lookup.asked)
        assertEquals("languages[]", a.modelOption!!.languageField)
        // Ohne Cache (frei getippt) greift dieselbe Ableitung.
        assertEquals("languages[]", AccessResolver.resolveStt("openai", "", "k", "gpt-transcribe-2026-08-01").modelOption!!.languageField)
    }

    // --- "wie Erkennung" bei reinen Erkennungs-Anbietern (Review 3.8.0) ---------------------

    @Test fun elevenLabsWieErkennungKannKeinenTextVerbessernAuchMitAltemModell() {
        val stt = AccessResolver.resolveStt("elevenlabs", "", "xi", "", 0)
        assertEquals(RefineBlock.NO_CHAT, AccessResolver.resolveLlm(stt, "same", "", "", "").refineBlock)
        // Ein von frueher gespeichertes llm_model aendert daran nichts: ElevenLabs hat keinen Chat.
        assertEquals(RefineBlock.NO_CHAT, AccessResolver.resolveLlm(stt, "same", "", "", "gpt-4o-mini").refineBlock)
    }

    @Test fun togetherUndDeepInfraWieErkennungBrauchenNurEinModell() {
        for (id in listOf("together", "deepinfra")) {
            val stt = AccessResolver.resolveStt(id, "", "k", "", 0)
            assertEquals(id, RefineBlock.NO_MODEL, AccessResolver.resolveLlm(stt, "same", "", "", " ").refineBlock)
            val typed = AccessResolver.resolveLlm(stt, "same", "", "", "meta-llama/Llama-3.3-70B-Instruct-Turbo")
            assertNull(id, typed.refineBlock)
        }
    }

    @Test fun anbieterMitTextmodellenSindNieGesperrt() {
        val stt = AccessResolver.resolveStt("groq", "", "gsk", "", 0)
        assertNull(AccessResolver.resolveLlm(stt, "same", "", "", "").refineBlock)
        // Eigener Server / Ollama ohne Modell: kein Sperrgrund hier (das Modellfeld zeigt den Fehler).
        assertNull(AccessResolver.resolveLlm(stt, "custom", "http://h:1/v1", "", "").refineBlock)
        assertNull(AccessResolver.resolveLlm(stt, "ollama", "http://h:11434", "", "").refineBlock)
    }

    @Test fun wieErkennungBeiOfflineErkennungSchicktNichtsAnDenAltenZugang() {
        // Offline gewaehlt, der Online-Zugang von frueher bleibt gespeichert (Anleitung 9.6).
        val stt = AccessResolver.resolveStt("openai", "", "sk-alt", "", 0)
        val llm = AccessResolver.resolveLlm(stt, "same", "", "", "gpt-4o-mini", sttOffline = true)
        assertEquals(RefineBlock.OFFLINE, llm.refineBlock)
        assertEquals("", llm.baseUrl)
        assertEquals("", llm.apiKey)
        try {
            TextRefiner(llm).refine("hallo welt", "de", RefineMode.POLISH, smartFillers = false)
            fail("Offline ohne eigenen Zugang darf nichts senden")
        } catch (e: ApiNotConfiguredException) {
            assertEquals(TextRefiner.MSG_OFFLINE, e.message)
        }
        // Online bleibt "wie Erkennung" wie bisher.
        assertNull(AccessResolver.resolveLlm(stt, "same", "", "", "gpt-4o-mini").refineBlock)
    }

    @Test fun eigenerTextZugangGiltAuchBeiOfflineErkennung() {
        val stt = AccessResolver.resolveStt("openai", "", "sk-alt", "", 0)
        val llm = AccessResolver.resolveLlm(stt, "groq", "", "gsk", "", sttOffline = true)
        assertNull(llm.refineBlock)
        assertEquals("groq", llm.provider.id)
        assertEquals("gsk", llm.apiKey)
        assertEquals("https://api.groq.com/openai/v1", llm.baseUrl)
    }
}
