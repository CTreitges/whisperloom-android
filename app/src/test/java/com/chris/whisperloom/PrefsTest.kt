package com.chris.whisperloom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.api.ServerUrlCheck
import com.chris.whisperloom.ui.nav.SetupFacts
import com.chris.whisperloom.ui.nav.SetupRouter
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.state.PrefsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric-Tests fuer Migration und die neuen v3-Schluessel (SharedPreferences). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrefsTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val sp = ctx.getSharedPreferences("whisperloom", Context.MODE_PRIVATE)

    @Before fun clear() {
        sp.edit().clear().commit()
    }

    @Test fun frischeInstallationHatKeineEngineUndDefaults() {
        val p = Prefs(ctx)
        assertNull(p.engine)
        assertEquals(RefineMode.OFF, p.refineMode)
        assertEquals("openai", p.sttProviderId)
        assertEquals("", p.apiBaseUrl)
        assertEquals("", p.apiKey)
        assertEquals("", p.apiModel)
        assertEquals(0, p.apiReadTimeoutSec)
        assertEquals("same", p.llmProviderId)
        assertEquals("small", p.offlineModel)
        assertTrue(p.offlineAccurate)
        assertTrue(p.shareHideFillers)
        assertFalse(p.welcomeSeen)
        assertFalse(p.overlaySkipped)
        assertFalse(p.a11ySkipped)
        assertFalse(p.notifSkipped)
        assertFalse(p.keyboardSkipped)
        assertEquals(3, sp.getInt("prefs_version", 0))
        // Ohne Engine ist die App nicht eingerichtet — auch nicht mit Key.
        assertFalse(TranscriptionEngine.isConfigured(ctx))
    }

    @Test fun bestandsnutzerWirdMigriert() {
        sp.edit()
            .putString("api_key", "sk-alt")
            .putString("api_url", "https://api.openai.com/v1")
            .putString("api_model", "gpt-4o-transcribe")
            .putBoolean("llm_polish", true)
            .commit()

        val p = Prefs(ctx)
        assertEquals(Engine.ONLINE, p.engine)
        assertEquals(RefineMode.POLISH, p.refineMode)
        val stt = p.sttAccess()
        assertEquals("openai", stt.provider.id)
        assertEquals("gpt-4o-transcribe", stt.model) // nicht automatisch umgeschrieben
        assertEquals("sk-alt", stt.apiKey)
        assertEquals("gpt-4o-mini", p.llmAccess().model)
        assertTrue(TranscriptionEngine.isConfigured(ctx))
        assertEquals(3, sp.getInt("prefs_version", 0))
    }

    // --- Review KOR-2/SEC-3: v2 hatte eine freie api_url ohne Anbieter ---------------------

    @Test fun v2EigenerServerWirdZuCustomMigriert() {
        sp.edit()
            .putString("api_url", "http://192.168.1.5:8000/v1")
            .putString("api_model", "Systran/faster-whisper-medium")
            .commit()

        val p = Prefs(ctx)
        assertEquals("custom", p.sttProviderId)
        assertEquals(Engine.ONLINE, p.engine) // eigener Server braucht keinen Key
        val stt = p.sttAccess()
        assertEquals("http://192.168.1.5:8000/v1", stt.baseUrl)
        assertEquals("Systran/faster-whisper-medium", stt.model)
        assertEquals(600_000, stt.readTimeoutMs)
        assertNull(ServerUrlCheck.check(stt.baseUrl, stt.provider)) // http zu LAN-Adresse ist beim eigenen Server ok
        assertTrue(SetupRouter.recognitionReady(SetupFacts.from(PrefsState(p), SystemStatus())))
        assertTrue(TranscriptionEngine.isConfigured(ctx))
    }

    @Test fun v2KatalogUrlWirdDemAnbieterZugeordnet() {
        sp.edit()
            .putString("api_url", "https://api.groq.com/openai/v1/")
            .putString("api_key", "gsk-alt")
            .putString("api_model", "whisper-large-v3")
            .commit()
        val p = Prefs(ctx)
        assertEquals("groq", p.sttProviderId)
        assertEquals("whisper-large-v3", p.sttAccess().model)
        assertEquals("https://api.groq.com/openai/v1/", p.sttAccess().baseUrl) // gespeicherte URL bleibt
        assertEquals(Engine.ONLINE, p.engine)
    }

    @Test fun vorhandenerAnbieterUndFehlendeUrlBleibenUnangetastet() {
        sp.edit().putString("stt_provider", "openai").putString("api_url", "http://192.168.1.5:8000/v1").commit()
        assertEquals("openai", Prefs(ctx).sttProviderId)

        sp.edit().clear().putString("api_key", "sk").commit()
        assertEquals("openai", Prefs(ctx).sttProviderId)
        assertFalse(sp.contains("stt_provider"))
    }

    @Test fun migrationLaeuftNurEinmal() {
        sp.edit().putString("api_key", "sk-alt").putBoolean("llm_polish", true).commit()
        val p = Prefs(ctx)
        p.refineMode = RefineMode.OFF
        p.engine = null
        // Zweite Instanz darf die Nutzer-Entscheidung nicht wieder ueberschreiben.
        val again = Prefs(ctx)
        assertEquals(RefineMode.OFF, again.refineMode)
        assertNull(again.engine)
    }

    @Test fun altesLlmPolishFalseBleibtOff() {
        sp.edit().putString("api_key", "sk").putBoolean("llm_polish", false).commit()
        assertEquals(RefineMode.OFF, Prefs(ctx).refineMode)
    }

    @Test fun neuerNutzerBekommtGptTranscribe() {
        val p = Prefs(ctx)
        p.engine = Engine.ONLINE
        p.apiKey = "sk-neu"
        val stt = p.sttAccess()
        assertEquals("gpt-transcribe", stt.model)
        assertEquals("https://api.openai.com/v1", stt.baseUrl)
        assertEquals(90_000, stt.readTimeoutMs)
        assertTrue(TranscriptionEngine.isConfigured(ctx))
    }

    @Test fun engineWirdGespeichertUndGelesen() {
        val p = Prefs(ctx)
        p.engine = Engine.OFFLINE
        assertEquals(Engine.OFFLINE, Prefs(ctx).engine)
        assertEquals("offline", sp.getString("engine", null))
        sp.edit().putString("engine", "kaputt").commit()
        assertNull(Prefs(ctx).engine)
    }

    @Test fun timeoutWirdBegrenzt() {
        val p = Prefs(ctx)
        p.apiReadTimeoutSec = 5
        assertEquals(30, p.apiReadTimeoutSec)
        p.apiReadTimeoutSec = 99_999
        assertEquals(1800, p.apiReadTimeoutSec)
        p.apiReadTimeoutSec = 0
        assertEquals(0, p.apiReadTimeoutSec)
        assertFalse(sp.contains("api_read_timeout_sec"))
    }

    @Test fun fuellwoerterAlsStringSet() {
        val p = Prefs(ctx)
        p.customFillers = setOf(" Halt ", "sozusagen", "halt", "")
        assertEquals(setOf("halt", "sozusagen"), p.customFillers)
        assertEquals(setOf("halt", "sozusagen"), sp.getStringSet("custom_fillers", null))
        p.disabledFillers = setOf("Hmm")
        assertEquals(setOf("hmm"), p.disabledFillers)
        assertEquals(setOf("hmm"), sp.getStringSet("disabled_fillers", null))
    }

    @Test fun flagsRundreise() {
        val p = Prefs(ctx)
        p.welcomeSeen = true
        p.overlaySkipped = true
        p.a11ySkipped = true
        p.notifSkipped = true
        p.keyboardSkipped = true
        p.shareHideFillers = false
        p.offlineAccurate = false
        p.offlineModel = "large-v3-turbo"
        assertTrue(sp.getBoolean("welcome_seen", false))
        assertTrue(sp.getBoolean("overlay_skipped", false))
        assertTrue(sp.getBoolean("setup_skip_a11y", false))
        assertTrue(sp.getBoolean("setup_skip_notif", false))
        assertTrue(sp.getBoolean("setup_skip_keyboard", false))
        assertFalse(Prefs(ctx).shareHideFillers)
        assertFalse(Prefs(ctx).offlineAccurate)
        assertEquals("large-v3-turbo", Prefs(ctx).offlineModel)
    }

    @Test fun llmZugangMitEigenemAnbieter() {
        val p = Prefs(ctx)
        p.engine = Engine.ONLINE
        p.sttProviderId = "groq"
        p.apiKey = "gsk"
        p.llmProviderId = "custom"
        p.llmUrl = "http://192.168.1.50:11434/v1"
        p.llmModel = "qwen3:8b"
        val llm = p.llmAccess()
        assertEquals("custom", llm.provider.id)
        assertEquals("http://192.168.1.50:11434/v1", llm.baseUrl)
        assertEquals("", llm.apiKey)
        assertEquals("qwen3:8b", llm.model)
        assertEquals("whisper-large-v3-turbo", p.sttAccess().model)
    }

    // --- Sprachauftrag ------------------------------------------------------

    @Test fun derSprachauftragIstFrischAus() {
        val p = Prefs(ctx)
        assertFalse("Wer das Feature nicht nutzt, soll es nicht bemerken", p.agentEnabled)
        assertEquals("", p.agentUrl)
        assertEquals("", p.agentToken)
        assertFalse(p.agentTutorialSeen)
        assertFalse(p.agentReady)
    }

    @Test fun derSprachauftragIstErstMitAdresseUndTokenBereit() {
        val p = Prefs(ctx)
        p.agentEnabled = true
        assertFalse("Ohne Adresse kann nichts gesendet werden", p.agentReady)
        p.agentUrl = "https://bridge.example.de"
        assertFalse("Ohne Token kann nichts gesendet werden", p.agentReady)
        p.agentToken = "geheim"
        assertTrue(p.agentReady)
        p.agentEnabled = false
        assertFalse("Der Schalter hat das letzte Wort", p.agentReady)
    }

    @Test fun dieSprachauftragWerteLandenInDerBekanntenDatei() {
        Prefs(ctx).apply {
            agentEnabled = true
            agentUrl = "https://bridge.example.de"
            agentToken = "geheim"
        }
        assertTrue(sp.getBoolean("agent_enabled", false))
        assertEquals("https://bridge.example.de", sp.getString("agent_url", ""))
        assertEquals("geheim", sp.getString("agent_token", ""))
    }

    @Test fun dieSprachauftragFelderSpiegelnSichInCompose() {
        val state = PrefsState(Prefs(ctx))
        state.agentEnabled = true
        state.agentUrl = "https://bridge.example.de"
        state.agentToken = "geheim"
        assertTrue(state.agentReady)
        // Und andersherum: eine Aenderung von aussen (Widget-Einrichtung) kommt an.
        Prefs(ctx).agentEnabled = false
        assertFalse(state.agentEnabled)
        state.dispose()
    }
}
