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
import org.junit.Assert.fail
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
        // Absaetze an = Verhalten bis 3.4; kein Vokabular, keine Datei.
        assertTrue(p.paragraphsFor(RefineMode.POLISH))
        assertTrue(p.paragraphsFor(RefineMode.BEAUTIFY))
        assertEquals("", p.apiPrompt)
        assertEquals("", p.vocabFileUri)
        assertEquals("", p.vocabFileName)
        assertFalse(p.welcomeSeen)
        assertFalse(p.overlaySkipped)
        assertFalse(p.a11ySkipped)
        assertFalse(p.notifSkipped)
        assertFalse(p.keyboardSkipped)
        for (way in RefineWay.entries) {
            assertEquals(way.name, PolishCleanup.PLAIN, p.polishCleanupFor(way))
            assertEquals(way.name, SummarizeForm.AUTO, p.summarizeFormFor(way))
        }
        assertEquals(6, sp.getInt("prefs_version", 0))
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
        assertEquals(Prefs.DEFAULT_LLM_MODEL, p.llmAccess().model)
        assertTrue(TranscriptionEngine.isConfigured(ctx))
        assertEquals(6, sp.getInt("prefs_version", 0))
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
        assertEquals(6, sp.getInt("prefs_version", 0))
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

    // --- Stufen-Einstellungen je Weg (3.9.0) --------------------------------------------

    @Test fun bereinigungUndFormHabenJeWegEigeneSchluessel() {
        val p = Prefs(ctx)
        p.setPolishCleanupFor(RefineWay.DICTATION, PolishCleanup.READABLE)
        p.setPolishCleanupFor(RefineWay.SHARE, PolishCleanup.CLEAN)
        p.setSummarizeFormFor(RefineWay.DICTATION, SummarizeForm.PROSE)
        assertEquals("readable", sp.getString("polish_cleanup", null))
        assertEquals("clean", sp.getString("share_polish_cleanup", null))
        assertEquals("prose", sp.getString("summarize_form", null))
        assertEquals("auto", sp.getString("share_summarize_form", null))
        val again = Prefs(ctx)
        assertEquals(PolishCleanup.READABLE, again.polishCleanupFor(RefineWay.DICTATION))
        assertEquals(PolishCleanup.CLEAN, again.polishCleanupFor(RefineWay.SHARE))
        assertEquals(SummarizeForm.PROSE, again.summarizeFormFor(RefineWay.DICTATION))
        assertEquals(SummarizeForm.AUTO, again.summarizeFormFor(RefineWay.SHARE))
        p.setSummarizeFormFor(RefineWay.SHARE, SummarizeForm.PROSE)
        assertEquals("prose", sp.getString("share_summarize_form", null))
    }

    @Test fun absaetzeHabenNurGlaettenUndVerschoenern() {
        val p = Prefs(ctx)
        p.setParagraphsFor(RefineMode.POLISH, false)
        assertFalse(Prefs(ctx).paragraphsFor(RefineMode.POLISH))
        assertFalse("Lesbar ist Glaetten", Prefs(ctx).paragraphsFor(RefineMode.READABLE))
        assertTrue("Verschoenern hat einen eigenen Schalter", Prefs(ctx).paragraphsFor(RefineMode.BEAUTIFY))
        assertFalse(sp.getBoolean("paragraphs_polish", true))
        p.setParagraphsFor(RefineMode.BEAUTIFY, false)
        assertFalse(sp.getBoolean("paragraphs_beautify", true))
        for (stage in listOf(RefineMode.OFF, RefineMode.SUMMARIZE, RefineMode.PROMPT)) {
            assertTrue(stage.name, p.paragraphsFor(stage))
            try {
                p.setParagraphsFor(stage, false)
                fail("${stage.name} hat keinen Schalter")
            } catch (e: IllegalArgumentException) {
                // erwartet
            }
        }
    }

    @Test fun unbekannteWerteGeltenAlsStandard() {
        sp.edit().putString("polish_cleanup", "quatsch").putString("share_summarize_form", "").commit()
        assertEquals(PolishCleanup.PLAIN, Prefs(ctx).polishCleanupFor(RefineWay.DICTATION))
        assertEquals(SummarizeForm.AUTO, Prefs(ctx).summarizeFormFor(RefineWay.SHARE))
    }

    @Test fun derWegHatEinenFestenSchluessel() {
        assertEquals("dictation", RefineWay.DICTATION.key)
        assertEquals("share", RefineWay.SHARE.key)
        for (way in RefineWay.entries) assertEquals(way, RefineWay.fromKey(way.key))
        assertNull(RefineWay.fromKey("quatsch"))
        assertNull(RefineWay.fromKey(null))
    }

    @Test fun eineDiktatOptionLaesstDieSprachnachrichtenInRuhe() {
        val p = Prefs(ctx)
        p.refineMode = RefineMode.POLISH
        p.shareRefineMode = RefineMode.POLISH
        p.setPolishCleanupFor(RefineWay.DICTATION, PolishCleanup.READABLE)
        p.setSummarizeFormFor(RefineWay.DICTATION, SummarizeForm.PROSE)
        p.setParagraphsFor(RefineMode.POLISH, false)
        assertEquals(Refinement(RefineMode.READABLE, paragraphs = false), p.refinementFor(RefineWay.DICTATION))
        assertEquals("Sprachnachrichten unveraendert", Refinement(RefineMode.POLISH), Prefs(ctx).refinementFor(RefineWay.SHARE))
        p.shareRefineMode = RefineMode.SUMMARIZE
        assertEquals(Refinement(RefineMode.SUMMARIZE), Prefs(ctx).refinementFor(RefineWay.SHARE))
    }

    @Test fun eineSprachnachrichtenOptionLaesstDasDiktatInRuhe() {
        val p = Prefs(ctx)
        p.refineMode = RefineMode.POLISH
        p.shareRefineMode = RefineMode.POLISH
        p.setPolishCleanupFor(RefineWay.SHARE, PolishCleanup.CLEAN)
        p.setSummarizeFormFor(RefineWay.SHARE, SummarizeForm.PROSE)
        assertEquals(Refinement(RefineMode.POLISH, smartFillers = true), p.refinementFor(RefineWay.SHARE))
        assertEquals("Diktat unveraendert", Refinement(RefineMode.POLISH), Prefs(ctx).refinementFor(RefineWay.DICTATION))
        p.refineMode = RefineMode.SUMMARIZE
        assertEquals(Refinement(RefineMode.SUMMARIZE), Prefs(ctx).refinementFor(RefineWay.DICTATION))
        p.shareRefineMode = RefineMode.SUMMARIZE
        assertEquals(Refinement(RefineMode.SUMMARIZE, paragraphs = false), Prefs(ctx).refinementFor(RefineWay.SHARE))
    }

    @Test fun gespeichertBleibtDieStufeGlaetten() {
        val p = Prefs(ctx)
        p.refineMode = RefineMode.POLISH
        p.setPolishCleanupFor(RefineWay.DICTATION, PolishCleanup.READABLE)
        assertEquals("polish", sp.getString("refine_mode", null))
        assertEquals(RefineMode.POLISH, Prefs(ctx).refineMode)
        assertEquals(RefineMode.READABLE, Prefs(ctx).refinementFor(RefineWay.DICTATION).mode)
        assertFalse(RefineMode.READABLE in RefineMode.settings(promptEnabled = true))
    }

    /** Die fruehere Stufe "Absaetze" war nie waehlbar und ist entfallen — gespeichert gilt sie als Glaetten. */
    @Test fun dieFruehereStufeAbsaetzeGiltAlsGlaetten() {
        sp.edit().putString("refine_mode", "paragraphs").commit()
        assertEquals(RefineMode.POLISH, Prefs(ctx).refineMode)
        assertEquals(RefineMode.POLISH, Prefs(ctx).refinementFor(RefineWay.DICTATION).mode)
        assertEquals("paragraphs", sp.getString("refine_mode", null))
        assertFalse(RefineMode.entries.any { it.key == "paragraphs" })
    }

    @Test fun stufenEinstellungenSpiegelnSichInCompose() {
        val state = PrefsState(Prefs(ctx))
        state.setPolishCleanupFor(RefineWay.SHARE, PolishCleanup.READABLE)
        assertEquals(PolishCleanup.READABLE, Prefs(ctx).polishCleanupFor(RefineWay.SHARE))
        assertEquals(PolishCleanup.PLAIN, state.polishCleanupFor(RefineWay.DICTATION))
        state.setSummarizeFormFor(RefineWay.DICTATION, SummarizeForm.PROSE)
        assertEquals(SummarizeForm.PROSE, Prefs(ctx).summarizeFormFor(RefineWay.DICTATION))
        state.setParagraphsFor(RefineMode.BEAUTIFY, false)
        assertFalse(Prefs(ctx).paragraphsFor(RefineMode.BEAUTIFY))
        assertTrue(state.paragraphsFor(RefineMode.POLISH))
        // Von aussen geschrieben (Tastatur, zweite Instanz) kommt es an.
        Prefs(ctx).setPolishCleanupFor(RefineWay.DICTATION, PolishCleanup.CLEAN)
        Prefs(ctx).setSummarizeFormFor(RefineWay.SHARE, SummarizeForm.PROSE)
        Prefs(ctx).setParagraphsFor(RefineMode.POLISH, false)
        assertEquals(PolishCleanup.CLEAN, state.polishCleanupFor(RefineWay.DICTATION))
        assertEquals(SummarizeForm.PROSE, state.summarizeFormFor(RefineWay.SHARE))
        assertFalse(state.paragraphsFor(RefineMode.READABLE))
        // Wirksame Verarbeitung ueber die Spiegel wie in den Prefs.
        state.refineMode = RefineMode.POLISH
        state.shareRefineMode = RefineMode.POLISH
        for (way in RefineWay.entries) assertEquals(way.name, Prefs(ctx).refinementFor(way), state.refinementFor(way))
        assertEquals(Refinement(RefineMode.POLISH, smartFillers = true, paragraphs = false), state.refinementFor(RefineWay.DICTATION))
        assertEquals(Refinement(RefineMode.READABLE), state.refinementFor(RefineWay.SHARE))
        state.dispose()
    }

    // --- v6 (3.9.0): Migration der Schalter in die Stufen-Einstellungen --------------------------

    /** Ein Bestandsnutzer von 3.8.6 (v5) mit den alten Schaltern. */
    private fun v5(readable: Boolean, shareReadable: Boolean, smart: Boolean, paragraphs: Boolean): Prefs {
        sp.edit().clear().putInt("prefs_version", 5)
            .putBoolean("polish_readable", readable)
            .putBoolean("share_polish_readable", shareReadable)
            .putBoolean("smart_fillers", smart)
            .putBoolean("refine_paragraphs", paragraphs)
            .commit()
        return Prefs(ctx)
    }

    @Test fun v6BereinigungAusLesbarUndIntelligentJeWeg() {
        for (readable in listOf(false, true)) for (shareReadable in listOf(false, true)) for (smart in listOf(false, true)) {
            val p = v5(readable, shareReadable, smart, paragraphs = true)
            val fall = "readable=$readable share=$shareReadable smart=$smart"
            // Lesbar geht vor: dort raeumt die KI Fuellwoerter ohnehin auf. "Intelligent" galt fuer beide Wege.
            val diktat = if (readable) PolishCleanup.READABLE else if (smart) PolishCleanup.CLEAN else PolishCleanup.PLAIN
            val share = if (shareReadable) PolishCleanup.READABLE else if (smart) PolishCleanup.CLEAN else PolishCleanup.PLAIN
            assertEquals(fall, diktat, p.polishCleanupFor(RefineWay.DICTATION))
            assertEquals(fall, share, p.polishCleanupFor(RefineWay.SHARE))
            assertEquals(fall, diktat.key, sp.getString("polish_cleanup", null))
            assertEquals(fall, share.key, sp.getString("share_polish_cleanup", null))
            assertEquals(6, sp.getInt("prefs_version", 0))
        }
    }

    @Test fun v6AbsaetzeUndFormAusAutomatischenAbsaetzen() {
        for (paragraphs in listOf(true, false)) {
            val p = v5(readable = false, shareReadable = false, smart = false, paragraphs = paragraphs)
            assertEquals(paragraphs, p.paragraphsFor(RefineMode.POLISH))
            assertEquals(paragraphs, p.paragraphsFor(RefineMode.BEAUTIFY))
            assertEquals(if (paragraphs) SummarizeForm.AUTO else SummarizeForm.PROSE, p.summarizeFormFor(RefineWay.DICTATION))
            // Sprachnachrichten waren immer gegliedert.
            assertEquals(SummarizeForm.AUTO, p.summarizeFormFor(RefineWay.SHARE))
            assertTrue(sp.contains("paragraphs_polish") && sp.contains("paragraphs_beautify"))
            assertEquals("auto", sp.getString("share_summarize_form", null))
        }
    }

    @Test fun v6WirktWieDieAltenSchalter() {
        // "Ohne Absaetze" + "intelligent" + Lesbar nur fuer Sprachnachrichten — so verarbeitete 3.8.6.
        val p = v5(readable = false, shareReadable = true, smart = true, paragraphs = false)
        p.refineMode = RefineMode.POLISH
        p.shareRefineMode = RefineMode.POLISH
        assertEquals(Refinement(RefineMode.POLISH, smartFillers = true, paragraphs = false), p.refinementFor(RefineWay.DICTATION))
        assertEquals(Refinement(RefineMode.READABLE), p.refinementFor(RefineWay.SHARE))
        p.refineMode = RefineMode.SUMMARIZE
        p.shareRefineMode = RefineMode.SUMMARIZE
        assertEquals(Refinement(RefineMode.SUMMARIZE, paragraphs = false), p.refinementFor(RefineWay.DICTATION))
        assertEquals(Refinement(RefineMode.SUMMARIZE), p.refinementFor(RefineWay.SHARE))
    }

    @Test fun v6VonVor386UebernimmtLesbarAuchFuerSprachnachrichten() {
        // Bis 3.8.5 galt "Lesbarer glaetten" auch fuer geteilte Audios (frueher v5) — das Verhalten bleibt.
        sp.edit().putInt("prefs_version", 4).putBoolean("polish_readable", true).commit()
        val p = Prefs(ctx)
        assertEquals(PolishCleanup.READABLE, p.polishCleanupFor(RefineWay.DICTATION))
        assertEquals(PolishCleanup.READABLE, p.polishCleanupFor(RefineWay.SHARE))
        assertEquals(6, sp.getInt("prefs_version", 0))

        sp.edit().clear().putInt("prefs_version", 4).putBoolean("polish_readable", false).putBoolean("smart_fillers", true).commit()
        assertEquals(PolishCleanup.CLEAN, Prefs(ctx).polishCleanupFor(RefineWay.SHARE))
    }

    @Test fun v6LaesstDieModelleJeStufeUnangetastet() {
        sp.edit().putInt("prefs_version", 5).putString("llm_model_polish", "a").putString("llm_model_summarize", "c").commit()
        val p = Prefs(ctx)
        assertEquals("a", p.llmModelFor(RefineMode.POLISH))
        assertEquals("c", p.llmModelFor(RefineMode.SUMMARIZE))
        // Das Modell je Stufe gilt fuer beide Wege — kein zweiter Satz Schluessel.
        assertTrue(sp.all.keys.filter { it.startsWith("llm_model") }.toSet() == setOf("llm_model_polish", "llm_model_summarize"))
        assertTrue(sp.all.keys.none { it.startsWith("share_llm_model") })
    }

    @Test fun v6LaeuftNurEinmal() {
        v5(readable = true, shareReadable = false, smart = false, paragraphs = false)
        Prefs(ctx).setPolishCleanupFor(RefineWay.DICTATION, PolishCleanup.PLAIN)
        Prefs(ctx).setParagraphsFor(RefineMode.POLISH, true)
        Prefs(ctx).setSummarizeFormFor(RefineWay.DICTATION, SummarizeForm.AUTO)
        val again = Prefs(ctx)
        assertEquals("die Nutzer-Entscheidung bleibt", PolishCleanup.PLAIN, again.polishCleanupFor(RefineWay.DICTATION))
        assertTrue(again.paragraphsFor(RefineMode.POLISH))
        assertEquals(SummarizeForm.AUTO, again.summarizeFormFor(RefineWay.DICTATION))
        // Die alten Schluessel bleiben liegen (nur noch zur Migration gelesen) und wirken nicht mehr.
        assertTrue(sp.getBoolean("polish_readable", false))
    }

    @Test fun v6BehaeltSchonGesetzteWerte() {
        sp.edit().putInt("prefs_version", 5)
            .putBoolean("polish_readable", true)
            .putBoolean("smart_fillers", true)
            .putBoolean("refine_paragraphs", false)
            .putString("polish_cleanup", "plain")
            .putString("share_polish_cleanup", "readable")
            .putBoolean("paragraphs_beautify", true)
            .putString("summarize_form", "auto")
            .putString("share_summarize_form", "prose")
            .commit()
        val p = Prefs(ctx)
        assertEquals(PolishCleanup.PLAIN, p.polishCleanupFor(RefineWay.DICTATION))
        assertEquals(PolishCleanup.READABLE, p.polishCleanupFor(RefineWay.SHARE))
        assertTrue(p.paragraphsFor(RefineMode.BEAUTIFY))
        assertFalse("nicht gesetzt: kommt aus refine_paragraphs", p.paragraphsFor(RefineMode.POLISH))
        assertEquals(SummarizeForm.AUTO, p.summarizeFormFor(RefineWay.DICTATION))
        assertEquals(SummarizeForm.PROSE, p.summarizeFormFor(RefineWay.SHARE))
    }

    @Test fun v6LaeuftNichtAufDemNeuenStand() {
        // prefs_version 6: nichts wird geschrieben, auch wenn alte Schluessel herumliegen.
        sp.edit().putInt("prefs_version", 6).putBoolean("polish_readable", true).commit()
        val p = Prefs(ctx)
        assertEquals(PolishCleanup.PLAIN, p.polishCleanupFor(RefineWay.DICTATION))
        assertFalse(sp.contains("polish_cleanup"))
    }

    // --- Modell je Stufe (3.8.6) ------------------------------------------------------

    @Test fun modellJeStufeHatEigeneSchluessel() {
        val p = Prefs(ctx)
        for (mode in RefineMode.entries) assertEquals(mode.name, "", p.llmModelFor(mode))
        p.setLlmModelFor(RefineMode.POLISH, "a")
        p.setLlmModelFor(RefineMode.BEAUTIFY, "b")
        p.setLlmModelFor(RefineMode.SUMMARIZE, "c")
        p.setLlmModelFor(RefineMode.PROMPT, "d")
        assertEquals("a", sp.getString("llm_model_polish", null))
        assertEquals("b", sp.getString("llm_model_beautify", null))
        assertEquals("c", sp.getString("llm_model_summarize", null))
        assertEquals("d", sp.getString("llm_model_prompt", null))
        // "Lesbar" rechnet mit dem Glaetten-Modell, "aus" mit keinem.
        assertEquals("a", Prefs(ctx).llmModelFor(RefineMode.READABLE))
        assertEquals("", Prefs(ctx).llmModelFor(RefineMode.OFF))
        assertEquals("", Prefs(ctx).llmModelFor(null))
        assertEquals("", p.llmModel)
    }

    @Test fun stufeAusHatKeinModell() {
        try {
            Prefs(ctx).setLlmModelFor(RefineMode.OFF, "x")
            fail("aus hat kein Modell")
        } catch (e: IllegalArgumentException) {
            assertTrue(sp.all.keys.none { it.startsWith("llm_model") })
        }
    }

    @Test fun anbieterwechselLeertZugangsModellUndAlleStufen() {
        val p = Prefs(ctx)
        p.llmProviderId = "anthropic"
        p.llmKey = "sk-ant"
        p.llmModel = "claude-sonnet-5"
        RefineMode.MODEL_STAGES.forEach { p.setLlmModelFor(it, "claude-opus-5-5") }
        p.clearLlmModels()
        val again = Prefs(ctx)
        assertEquals("", again.llmModel)
        RefineMode.MODEL_STAGES.forEach { assertEquals(it.name, "", again.llmModelFor(it)) }
        assertTrue(sp.all.keys.none { it.startsWith("llm_model") })
        // Anbieter und Key bleiben — die setzt der Aufrufer.
        assertEquals("anthropic", again.llmProviderId)
        assertEquals("sk-ant", again.llmKey)
    }

    @Test fun llmZugangNimmtDasModellDerStufe() {
        val p = Prefs(ctx)
        p.engine = Engine.ONLINE
        p.llmProviderId = "anthropic"
        p.llmKey = "sk-ant"
        assertEquals("claude-haiku-5-5", p.llmAccess().model)
        assertEquals("claude-haiku-5-5", p.llmAccess(RefineMode.READABLE).model)
        assertEquals("claude-sonnet-5-5", p.llmAccess(RefineMode.BEAUTIFY).model)
        p.setLlmModelFor(RefineMode.SUMMARIZE, "claude-opus-5-5")
        assertEquals("claude-opus-5-5", p.llmAccess(RefineMode.SUMMARIZE).model)
        // Ein bewusst gewaehltes Modell des Zugangs gilt fuer alle Stufen ohne eigenes.
        p.llmModel = "claude-sonnet-5"
        assertEquals("claude-sonnet-5", p.llmAccess(RefineMode.POLISH).model)
        assertEquals("claude-sonnet-5", p.llmAccess(RefineMode.BEAUTIFY).model)
        assertEquals("claude-opus-5-5", p.llmAccess(RefineMode.SUMMARIZE).model)
    }

    @Test fun modellJeStufeSpiegeltSichInCompose() {
        val p = Prefs(ctx)
        p.llmProviderId = "anthropic"
        val state = PrefsState(p)
        state.setLlmModelFor(RefineMode.BEAUTIFY, "claude-opus-5-5")
        assertEquals("claude-opus-5-5", Prefs(ctx).llmModelFor(RefineMode.BEAUTIFY))
        assertEquals("claude-opus-5-5", state.llmAccess(RefineMode.BEAUTIFY).model)
        // Das Standard-Modell der Stufe (fuer "Standard · …") kennt das eigene nicht.
        assertEquals("claude-sonnet-5-5", state.standardLlmAccess(RefineMode.BEAUTIFY).model)
        assertEquals("claude-haiku-5-5", state.standardLlmAccess(RefineMode.READABLE).model)
        // Von aussen geschrieben kommt es an.
        Prefs(ctx).setLlmModelFor(RefineMode.PROMPT, "claude-sonnet-5")
        assertEquals("claude-sonnet-5", state.llmModelFor(RefineMode.PROMPT))
        state.llmModel = "claude-sonnet-5"
        assertEquals("claude-sonnet-5", state.standardLlmAccess(RefineMode.BEAUTIFY).model)
        state.clearLlmModels()
        assertEquals("", state.llmModel)
        RefineMode.MODEL_STAGES.forEach { assertEquals(it.name, "", state.llmModelFor(it)) }
        assertEquals("claude-haiku-5-5", state.llmAccess(RefineMode.POLISH).model)
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
        assertEquals(6, sp.getInt("prefs_version", 0))
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

    // --- Verlauf (3.9.0): ab Werk an mit 50, ohne Versionssprung ------------------------------

    @Test fun verlaufIstAbWerkAnMitFuenfzig() {
        val p = Prefs(ctx)
        assertTrue(p.historyEnabled)
        assertEquals(50, p.historySize)
    }

    /** E7: ein Standard fuer alle — auch wer von v5 kommt, hat den Verlauf an. */
    @Test fun bestandsnutzerHabenDenVerlaufOhneMigration() {
        sp.edit().putInt("prefs_version", 5).putString("engine", "online").commit()
        val p = Prefs(ctx)
        assertTrue(p.historyEnabled)
        assertEquals(50, p.historySize)
    }

    @Test fun verlaufsgroesseNurAusDerListe() {
        val p = Prefs(ctx)
        assertEquals(listOf(10, 25, 50, 100, 250, 500), Prefs.HISTORY_SIZES)
        for (size in Prefs.HISTORY_SIZES) {
            p.historySize = size
            assertEquals(size, p.historySize)
        }
        try {
            p.historySize = 7
            fail("IllegalArgumentException erwartet")
        } catch (e: IllegalArgumentException) {
            // erwartet
        }
        sp.edit().putInt("history_size", 7).commit()
        assertEquals("Unbekanntes gilt als Standard", 50, p.historySize)
    }

    @Test fun derSpiegelZiehtDenVerlaufNach() {
        val state = PrefsState(Prefs(ctx))
        assertTrue(state.historyEnabled)
        assertEquals(50, state.historySize)
        Prefs(ctx).historyEnabled = false
        Prefs(ctx).historySize = 100
        assertFalse(state.historyEnabled)
        assertEquals(100, state.historySize)
        state.dispose()
    }
}
