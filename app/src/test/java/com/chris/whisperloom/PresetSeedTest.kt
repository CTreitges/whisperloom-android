package com.chris.whisperloom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.ui.nav.SetupFacts
import com.chris.whisperloom.ui.nav.SetupRouter
import com.chris.whisperloom.ui.nav.StepState
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.state.PrefsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Vorbelegung eines Einzel-Builds: schreibt nur Fehlendes, gruppenweise, genau einmal. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PresetSeedTest {

    private val app: WhisperLoomApplication = ApplicationProvider.getApplicationContext()
    private val sp get() = app.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)

    @Before fun leeren() {
        sp.edit().clear().commit()
    }

    @Test fun frischeInstallationBekommtAlleGruppen() {
        assertEquals(5, PresetSeed.apply(sp, PRESET))

        val p = Prefs(app)
        assertEquals(Engine.ONLINE, p.engine)
        assertEquals("elevenlabs", p.sttProviderId)
        assertEquals("scribe_v2", p.sttAccess().model)
        assertEquals("https://api.elevenlabs.io/v1", p.sttAccess().baseUrl)
        assertEquals("anthropic", p.llmProviderId)
        assertEquals("claude-haiku-5-5", p.llmAccess(RefineMode.POLISH).model)
        assertEquals("claude-sonnet-5-5", p.llmAccess(RefineMode.BEAUTIFY).model)
        assertEquals("claude-sonnet-5-5", p.llmAccess(RefineMode.SUMMARIZE).model)
        assertEquals("claude-opus-5-5", p.llmAccess(RefineMode.PROMPT).model)
        assertEquals("ant-test", p.llmAccess(RefineMode.POLISH).apiKey)
        assertTrue(SetupState.llmReady(p.llmAccess(RefineMode.PROMPT)))
        assertTrue(p.promptLevelEnabled)
        assertEquals(RefineMode.POLISH, p.refineMode)
        assertTrue(RefineMode.PROMPT in RefineMode.settings(p.promptLevelEnabled))
        val fallback = p.llmFallbackAccess(RefineMode.POLISH)!!
        assertEquals("ollama-cloud", fallback.provider.id)
        assertTrue(SetupState.llmReady(fallback))
    }

    @Test fun derAssistentZeigtAnbieterUndZugangAlsErledigt() {
        PresetSeed.apply(sp, PRESET)
        val state = PrefsState(Prefs(app))
        val facts = SetupFacts.from(state, SystemStatus())
        state.dispose()

        assertEquals(StepState.DONE, SetupRouter.stepState(SetupRouter.STEP_ENGINE, facts))
        assertEquals(StepState.DONE, SetupRouter.stepState(SetupRouter.STEP_ACCESS, facts))
        // Die Willkommensseite bleibt, danach geht es beim Mikrofon weiter.
        assertEquals(SetupRouter.STEP_MIC, SetupRouter.firstOpenStep(facts))
        assertFalse(Prefs(app).welcomeSeen)
    }

    @Test fun vorhandeneWerteBleibenUndIhreGruppeEntfaellt() {
        // Wie ein Bestandsnutzer: eigene Erkennung (OpenAI) und eine gewaehlte Stufe.
        sp.edit().putString("stt_provider", "openai").putString("refine_mode", "beautify").commit()

        assertEquals(3, PresetSeed.apply(sp, PRESET))

        val p = Prefs(app)
        assertEquals("openai", p.sttProviderId)
        assertEquals("Kein halber Zugang: der ElevenLabs-Key kommt nicht zum OpenAI-Anbieter", "", p.apiKey)
        assertEquals(RefineMode.BEAUTIFY, p.refineMode)
        assertEquals("anthropic", p.llmProviderId)
        assertTrue(p.promptLevelEnabled)
    }

    @Test fun laeuftNurEinmal() {
        PresetSeed.apply(sp, PRESET)
        // Spaeter selbst geaendert bzw. entfernt (z. B. Anbieterwechsel raeumt die Stufen-Modelle).
        Prefs(app).clearLlmModels()

        assertEquals(0, PresetSeed.apply(sp, PRESET))
        assertEquals("", Prefs(app).llmModelFor(RefineMode.POLISH))
    }

    @Test fun unbekannterTypSchreibtNichts() {
        try {
            PresetSeed.apply(sp, """{"groups":[{"engine":"online"},{"zahl":1.5}]}""")
            fail("Kommazahl ist kein Einstellungswert")
        } catch (_: IllegalArgumentException) {
        }
        assertFalse(sp.contains("engine"))
        assertFalse(sp.contains(PresetSeed.KEY_APPLIED))
    }

    @Test fun normalerBuildOhneAssetLaesstAllesWieEsIst() {
        app.onCreate()

        assertFalse(sp.contains("engine"))
        assertFalse(sp.contains(PresetSeed.KEY_APPLIED))
        assertEquals(null, Prefs(app).engine)
    }

    private companion object {
        /** Aufbau wie das echte preset.json des Einzel-Builds, nur mit Platzhalter-Keys. */
        val PRESET = """
            {"groups": [
              {"engine": "online", "stt_provider": "elevenlabs", "api_key": "xi-test", "api_model": "scribe_v2"},
              {"llm_provider": "anthropic", "llm_key": "ant-test",
               "llm_model_polish": "claude-haiku-5-5", "llm_model_beautify": "claude-sonnet-5-5",
               "llm_model_summarize": "claude-sonnet-5-5", "llm_model_prompt": "claude-opus-5-5"},
              {"llm_fallback": true, "llm_fallback_provider": "ollama-cloud", "llm_fallback_key": "ollama-test"},
              {"refine_prompt_enabled": true},
              {"refine_mode": "polish"}
            ]}
        """.trimIndent()
    }
}
