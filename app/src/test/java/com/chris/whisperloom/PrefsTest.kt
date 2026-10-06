package com.chris.whisperloom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.api.RefineBlock
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
        assertEquals(OfflineRefineRule.LOCAL, p.offlineRefine)
        assertEquals("gemma4_e2b", p.localLlmModel)
        assertTrue(p.shareHideFillers)
        // Automatische Absaetze an = Verhalten bis 3.4; kein Vokabular, keine Datei.
        assertTrue(p.refineParagraphs)
        assertEquals("", p.apiPrompt)
        assertEquals("", p.vocabFileUri)
        assertEquals("", p.vocabFileName)
        assertFalse(p.welcomeSeen)
        assertFalse(p.overlaySkipped)
        assertFalse(p.a11ySkipped)
        assertFalse(p.notifSkipped)
        assertFalse(p.keyboardSkipped)
        assertEquals(4, sp.getInt("prefs_version", 0))
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
        assertEquals(4, sp.getInt("prefs_version", 0))
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

    // --- v4 (3.8.0): Gemini-Bestandsnutzer behalten 2.5 Flash-Lite --------------------------

    @Test fun geminiBestandsnutzerOhneModellBehaeltGemini25() {
        // Stand 3.7: prefs_version 3, Gemini gewaehlt, Modell leer = damalige Voreinstellung 2.5 Flash-Lite.
        sp.edit().putInt("prefs_version", 3).putString("llm_provider", "gemini").putString("llm_key", "AIza").commit()
        val p = Prefs(ctx)
        assertEquals("gemini-2.5-flash-lite", p.llmModel)
        assertEquals("gemini-2.5-flash-lite", p.llmAccess().model)
        assertEquals(4, sp.getInt("prefs_version", 0))
    }

    @Test fun geminiMitGewaehltemModellUndAndereAnbieterBleibenUnveraendert() {
        sp.edit().putInt("prefs_version", 3).putString("llm_provider", "gemini").putString("llm_model", "gemini-2.5-flash").commit()
        assertEquals("gemini-2.5-flash", Prefs(ctx).llmModel)

        sp.edit().clear().putInt("prefs_version", 3).putString("llm_provider", "deepseek").commit()
        assertEquals("", Prefs(ctx).llmModel)
        sp.edit().clear().putInt("prefs_version", 3).commit() // "wie Erkennung"
        assertEquals("", Prefs(ctx).llmModel)
    }

    @Test fun geminiMigrationLaeuftNurEinmal() {
        sp.edit().putInt("prefs_version", 3).putString("llm_provider", "gemini").commit()
        Prefs(ctx)
        // Wer danach (ab 3.8) Gemini neu waehlt, bekommt die neue Voreinstellung — keine zweite Migration.
        Prefs(ctx).llmModel = ""
        assertEquals("", Prefs(ctx).llmModel)
        assertEquals("gemini-3.5-flash-lite", Prefs(ctx).llmAccess().model)
    }

    @Test fun neuinstallationMitGeminiBekommtNeueVoreinstellung() {
        Prefs(ctx).llmProviderId = "gemini"
        assertEquals("gemini-3.5-flash-lite", Prefs(ctx).llmAccess().model)
    }

    // --- Stufe "Prompt" ------------------------------------------------------

    @Test fun promptStufeIstStandardmaessigAus() {
        assertFalse(Prefs(ctx).promptLevelEnabled)
        assertEquals(RefineMode.SETTINGS, RefineMode.settings(promptEnabled = false))
        assertEquals(RefineMode.SETTINGS + RefineMode.PROMPT, RefineMode.settings(promptEnabled = true))
    }

    @Test fun promptGiltNurMitSchalterUndKommtDanachZurueck() {
        val p = Prefs(ctx)
        p.promptLevelEnabled = true
        p.refineMode = RefineMode.PROMPT
        assertEquals(RefineMode.PROMPT, Prefs(ctx).refineMode)

        // Unsichtbar und trotzdem aktiv waere die schlimmste Kombination.
        p.promptLevelEnabled = false
        assertEquals(RefineMode.POLISH, Prefs(ctx).refineMode)
        assertEquals("gespeicherte Wahl bleibt", "prompt", sp.getString("refine_mode", null))

        p.promptLevelEnabled = true
        assertEquals(RefineMode.PROMPT, Prefs(ctx).refineMode)
    }

    // --- "Lesbarer glaetten" ------------------------------------------------------

    @Test fun lesbarerGlaettenIstAbWerkAus() {
        val p = Prefs(ctx)
        assertFalse(p.polishReadable)
        assertEquals(RefineMode.POLISH, p.effective(RefineMode.POLISH))
    }

    @Test fun lesbarerGlaettenWirktNurAufGlaetten() {
        val p = Prefs(ctx)
        p.polishReadable = true
        assertTrue(Prefs(ctx).polishReadable)
        assertEquals(RefineMode.READABLE, p.effective(RefineMode.POLISH))
        for (mode in RefineMode.entries - RefineMode.POLISH) assertEquals(mode.name, mode, p.effective(mode))
        // Gespeichert bleibt die Wahl "Glaetten" — READABLE ist nie eine waehlbare Stufe.
        p.refineMode = RefineMode.POLISH
        assertEquals("polish", sp.getString("refine_mode", null))
        assertFalse(RefineMode.READABLE in RefineMode.settings(promptEnabled = true))
    }

    @Test fun lesbarerGlaettenSpiegeltSichInCompose() {
        val state = PrefsState(Prefs(ctx))
        state.polishReadable = true
        assertTrue(Prefs(ctx).polishReadable)
        Prefs(ctx).polishReadable = false
        assertFalse(state.polishReadable)
        state.dispose()
    }

    // --- Lokales Textmodell -----------------------------------------------------

    @Test fun offlineRegelUndTextmodellRundreise() {
        val p = Prefs(ctx)
        p.offlineRefine = OfflineRefineRule.SKIP
        p.localLlmModel = "gemma4_e4b"
        assertEquals(OfflineRefineRule.SKIP, Prefs(ctx).offlineRefine)
        assertEquals("gemma4_e4b", Prefs(ctx).localLlmModel)
        assertEquals("skip", sp.getString("offline_refine", null))
        assertEquals("gemma4_e4b", sp.getString("local_llm_model", null))
        p.offlineRefine = OfflineRefineRule.ONLINE_LOCAL
        assertEquals("online_local", sp.getString("offline_refine", null))
        assertEquals(OfflineRefineRule.ONLINE_LOCAL, Prefs(ctx).offlineRefine)
    }

    @Test fun bestandsnutzerMitOfflineUndStufeGiltOhneMigrationAlsLokal() {
        // Spec §0.5: nichts wird geschrieben — wer vor 3.9 offline mit Stufe erkannt hat, gilt als "lokal".
        sp.edit().putInt("prefs_version", 4).putString("engine", "offline").putString("refine_mode", "polish").commit()
        val p = Prefs(ctx)
        assertEquals(OfflineRefineRule.LOCAL, p.offlineRefine)
        assertEquals(Prefs.DEFAULT_LOCAL_LLM_MODEL, p.localLlmModel)
        assertFalse(sp.contains("offline_refine"))
        assertFalse(sp.contains("local_llm_model"))
        assertEquals(4, sp.getInt("prefs_version", 0))
    }

    @Test fun unbekannteOfflineRegelGiltAlsLokal() {
        sp.edit().putString("offline_refine", "no_net").commit()
        assertEquals(OfflineRefineRule.LOCAL, Prefs(ctx).offlineRefine)
    }

    @Test fun offlineRegelUndTextmodellSpiegelnSichInCompose() {
        val state = PrefsState(Prefs(ctx))
        assertEquals(OfflineRefineRule.LOCAL, state.offlineRefine)
        assertEquals("gemma4_e2b", state.localLlmModel)
        state.offlineRefine = OfflineRefineRule.SKIP
        state.localLlmModel = "gemma4_e4b"
        assertEquals(OfflineRefineRule.SKIP, Prefs(ctx).offlineRefine)
        assertEquals("gemma4_e4b", Prefs(ctx).localLlmModel)
        // Und andersherum: eine Aenderung von aussen (Tastatur, Pflichtkarte im Dienst) kommt an.
        Prefs(ctx).offlineRefine = OfflineRefineRule.ONLINE_LOCAL
        Prefs(ctx).localLlmModel = "gemma4_e2b"
        assertEquals(OfflineRefineRule.ONLINE_LOCAL, state.offlineRefine)
        assertEquals("gemma4_e2b", state.localLlmModel)
        state.dispose()
    }

    // --- Geteilte Sprachnachrichten ---------------------------------------------

    @Test fun shareStufeIstAbWerkAusUndUnabhaengigVomDiktat() {
        val p = Prefs(ctx)
        assertEquals(RefineMode.OFF, p.shareRefineMode)
        p.refineMode = RefineMode.BEAUTIFY
        assertEquals("Diktat-Stufe faerbt nicht ab", RefineMode.OFF, Prefs(ctx).shareRefineMode)
        p.shareRefineMode = RefineMode.SUMMARIZE
        assertEquals(RefineMode.SUMMARIZE, Prefs(ctx).shareRefineMode)
        assertEquals(RefineMode.BEAUTIFY, Prefs(ctx).refineMode)
        assertEquals("summarize", sp.getString("share_refine_mode", null))
    }

    @Test fun shareStufeKenntWederPromptNochAltlastNochUnbekanntes() {
        val p = Prefs(ctx)
        p.promptLevelEnabled = true
        for (key in listOf("prompt", "paragraphs", "readable", "quatsch")) {
            sp.edit().putString("share_refine_mode", key).commit()
            assertEquals(key, RefineMode.OFF, p.shareRefineMode)
        }
    }

    @Test fun shareStufeSpiegeltSichInCompose() {
        val state = PrefsState(Prefs(ctx))
        state.shareRefineMode = RefineMode.POLISH
        assertEquals(RefineMode.POLISH, Prefs(ctx).shareRefineMode)
        Prefs(ctx).shareRefineMode = RefineMode.OFF
        assertEquals(RefineMode.OFF, state.shareRefineMode)
        state.dispose()
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

    @Test fun offlineWieErkennungNimmtNichtDenAltenOnlineZugang() {
        // Regression: Offline + "wie Erkennung" + noch gespeicherter OpenAI-Key -> der erkannte Text
        // ging still an OpenAI, waehrend die Karte "Textverbesserung braucht einen Online-Zugang" zeigte.
        val p = Prefs(ctx)
        p.sttProviderId = "openai"
        p.apiKey = "sk-alt"
        p.engine = Engine.OFFLINE
        p.refineMode = RefineMode.POLISH
        val llm = p.llmAccess()
        assertEquals(RefineBlock.OFFLINE, llm.refineBlock)
        assertEquals("", llm.apiKey)
        assertFalse(SetupState.llmReady(llm))
        val state = PrefsState(p)
        assertEquals(RefineBlock.OFFLINE, state.llmAccess().refineBlock)
        state.dispose()
        // Zurueck auf online: wieder der Erkennungs-Zugang.
        p.engine = Engine.ONLINE
        assertNull(p.llmAccess().refineBlock)
        assertEquals("sk-alt", p.llmAccess().apiKey)
    }

    // --- Pro-Funktionen ("Erweitert") -------------------------------------------

    @Test fun proFunktionenSindFrischAus() {
        val p = Prefs(ctx)
        assertFalse("Wer das Feature nicht nutzt, soll es nicht bemerken", p.proWidgetsEnabled)
        ProFeature.entries.forEach { assertFalse(it.name, p.isEnabled(it)) }
        assertFalse(p.agentTutorialSeen)
    }

    @Test fun proWidgetsBehaltenDenAltenSchluessel() {
        // Bestandsnutzer mit eingeschaltetem "Sprachauftrag" haben danach Pro Widgets an — ohne Migration.
        sp.edit().putBoolean("agent_enabled", true).commit()
        assertTrue(Prefs(ctx).proWidgetsEnabled)
        Prefs(ctx).proWidgetsEnabled = false
        assertFalse(sp.getBoolean("agent_enabled", true))
    }

    @Test fun jedeProFunktionHatIhrenEigenenSchalter() {
        val p = Prefs(ctx)
        p.setEnabled(ProFeature.PROMPT, true)
        assertTrue(p.promptLevelEnabled)
        assertTrue(sp.getBoolean("refine_prompt_enabled", false))
        assertFalse(p.isEnabled(ProFeature.WIDGETS))

        p.setEnabled(ProFeature.WIDGETS, true)
        assertTrue(p.proWidgetsEnabled)
        p.promptLevelEnabled = false
        assertFalse(p.isEnabled(ProFeature.PROMPT))
        assertTrue(p.isEnabled(ProFeature.WIDGETS))
    }

    @Test fun modelleVomServerHabenEinenEigenenSchluessel() {
        val p = Prefs(ctx)
        p.setEnabled(ProFeature.SERVER_MODELS, true)
        assertTrue(p.serverModelsEnabled)
        assertTrue(sp.getBoolean("pro_server_models", false))
        assertFalse(p.isEnabled(ProFeature.WIDGETS))
        assertFalse(p.isEnabled(ProFeature.PROMPT))

        val state = PrefsState(p)
        assertTrue(state.isEnabled(ProFeature.SERVER_MODELS))
        state.setEnabled(ProFeature.SERVER_MODELS, false)
        assertFalse(Prefs(ctx).serverModelsEnabled)
        assertFalse(state.serverModelsEnabled)
        state.dispose()
    }

    @Test fun dieAltenServerSchluesselSindFestgeschrieben() {
        // Nur noch fuer die Migration in die Widget-Profile — ein Umbenennen liesse den Server liegen.
        assertEquals("agent_url", Prefs.LEGACY_KEY_AGENT_URL)
        assertEquals("agent_token", Prefs.LEGACY_KEY_AGENT_TOKEN)
        assertEquals("whisperloom", Prefs.FILE)
    }

    @Test fun derSpiegelKenntDieProFunktionen() {
        val state = PrefsState(Prefs(ctx))
        state.proWidgetsEnabled = true
        assertTrue(Prefs(ctx).proWidgetsEnabled)
        assertTrue(state.isEnabled(ProFeature.WIDGETS))
        assertFalse(state.isEnabled(ProFeature.PROMPT))
        state.promptLevelEnabled = true
        assertTrue(state.isEnabled(ProFeature.PROMPT))
        // Und andersherum: eine Aenderung von aussen (Widget-Einrichtung) kommt an.
        Prefs(ctx).proWidgetsEnabled = false
        assertFalse(state.proWidgetsEnabled)
        assertFalse(state.isEnabled(ProFeature.WIDGETS))
        state.dispose()
    }

    @Test fun derSpiegelSchaltetJedeProFunktionEinzeln() {
        // Die Schalter in "Erweitert" iterieren ProFeature.entries und schreiben ueber setEnabled.
        val state = PrefsState(Prefs(ctx))
        state.setEnabled(ProFeature.PROMPT, true)
        assertTrue(Prefs(ctx).promptLevelEnabled)
        assertTrue(state.promptLevelEnabled)
        assertFalse(Prefs(ctx).proWidgetsEnabled)

        state.setEnabled(ProFeature.WIDGETS, true)
        state.setEnabled(ProFeature.PROMPT, false)
        assertTrue(Prefs(ctx).proWidgetsEnabled)
        assertTrue(state.isEnabled(ProFeature.WIDGETS))
        assertFalse(Prefs(ctx).promptLevelEnabled)
        assertFalse(state.isEnabled(ProFeature.PROMPT))
        state.dispose()
    }
}
