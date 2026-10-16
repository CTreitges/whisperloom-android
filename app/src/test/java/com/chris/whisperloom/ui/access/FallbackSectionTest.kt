package com.chris.whisperloom.ui.access

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Engine
import com.chris.whisperloom.ModelCache
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.settings.LlmAccessScreen
import com.chris.whisperloom.ui.settings.RecognitionScreen
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fallback (3.9.1) in Spracherkennung und KI-Zugang: ein Schalter unter dem ersten Anbieter, die
 * Felder erst, wenn er an ist; beim ersten Einschalten ein anderer Anbieter als der erste; ein
 * Anbieterwechsel leert Key, Adresse und Modell; leer gelassene Keys desselben Anbieters werden
 * genannt. Es geht keine Anfrage raus (kein "Fallback pruefen").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class FallbackSectionTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs

    @Before fun setUp() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(ModelCache.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "openai"
        prefs.apiKey = "sk-openai"
        prefs.refineMode = RefineMode.POLISH
    }

    private fun show(content: @Composable (NavState) -> Unit) {
        val status = SystemStatus()
        val env = AppEnv(PrefsState(prefs), status) { status }
        val nav = NavState(listOf(Screen.Home, Screen.SettingsHub))
        compose.setContent {
            WhisperLoomTheme { CompositionLocalProvider(LocalAppEnv provides env) { content(nav) } }
        }
        compose.waitForIdle()
    }

    private fun click(text: String) {
        compose.onNodeWithText(text).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun fallbackOn() = click("Fallback verwenden")

    // --- Spracherkennung -------------------------------------------------------------------------

    @Test fun erkennungSchalterAusZeigtKeineFelder() {
        show { RecognitionScreen(it) }
        compose.onNodeWithText("Fallback verwenden").assertExists()
        compose.onNodeWithText("Ein zweiter Anbieter für den Fall, dass OpenAI scheitert.").assertExists()
        compose.onNodeWithTag("dropdown:Fallback-Anbieter").assertDoesNotExist()
        compose.onNodeWithText("Fallback prüfen").assertDoesNotExist()
    }

    @Test fun erkennungEinschaltenSchlaegtEinenAnderenAnbieterVor() {
        show { RecognitionScreen(it) }
        fallbackOn()
        val p = Prefs(ctx)
        assertTrue(p.sttFallbackEnabled)
        assertEquals("groq", p.sttFallbackProviderId)
        // Ohne Key springt Groq nicht ein — der Schalter sagt es (Review 3.9.1).
        compose.onNodeWithText("Noch unvollständig", substring = true).assertExists()
        compose.onNodeWithTag("dropdown:Fallback-Anbieter").assertTextContains("Groq", substring = true)
        compose.onNodeWithTag("dropdown:Modell des Fallbacks").assertExists()
        compose.onNodeWithText("Fallback prüfen").assertExists()
        compose.onNodeWithText("Scheitert die Erkennung, geht das Audio zusätzlich an Groq.").assertExists()
        // Fremder Anbieter: kein Key wird uebernommen, das Feld ist Pflicht.
        compose.onNode(hasSetTextAction() and hasText("API-Key des Fallbacks")).performTextInput("gsk-fallback")
        compose.waitForIdle()
        assertEquals("gsk-fallback", Prefs(ctx).sttFallbackKey)
        assertEquals("sk-openai", Prefs(ctx).apiKey)
        compose.onNodeWithText("Scheitert OpenAI, springt dieser Anbieter ein.").assertExists()
        compose.onNodeWithText("Noch unvollständig", substring = true).assertDoesNotExist()
    }

    @Test fun erkennungAusschaltenBehaeltDieEingaben() {
        prefs.sttFallbackEnabled = true
        prefs.sttFallbackProviderId = "groq"
        prefs.sttFallbackKey = "gsk"
        show { RecognitionScreen(it) }
        fallbackOn() // schaltet aus
        val p = Prefs(ctx)
        assertFalse(p.sttFallbackEnabled)
        assertEquals("gsk", p.sttFallbackKey)
        compose.onNodeWithTag("dropdown:Fallback-Anbieter").assertDoesNotExist()
    }

    @Test fun erkennungAnbieterwechselLeertKeyUndModell() {
        prefs.sttFallbackEnabled = true
        prefs.sttFallbackProviderId = "groq"
        prefs.sttFallbackKey = "gsk"
        prefs.sttFallbackModel = "whisper-large-v3"
        show { RecognitionScreen(it) }
        compose.onNodeWithTag("dropdown:Fallback-Anbieter").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Mistral", substring = true).performClick()
        compose.waitForIdle()
        val p = Prefs(ctx)
        assertEquals("mistral", p.sttFallbackProviderId)
        assertEquals("", p.sttFallbackKey)
        assertEquals("", p.sttFallbackModel)
    }

    @Test fun erkennungDerselbeAnbieterNenntDenUebernommenenKey() {
        prefs.sttFallbackEnabled = true
        prefs.sttFallbackProviderId = "openai"
        prefs.sttFallbackModel = "whisper-1"
        show { RecognitionScreen(it) }
        compose.onNodeWithText("Leer gelassen gilt der Key der Spracherkennung.").assertExists()
        compose.onNode(hasSetTextAction() and hasText("API-Key des Fallbacks (optional)")).assertExists()
    }

    // --- KI-Zugang -------------------------------------------------------------------------------

    private fun anthropic() {
        prefs.llmProviderId = "anthropic"
        prefs.llmKey = "sk-ant"
    }

    @Test fun kiZugangEinschaltenNimmtOpenAiMitDemKeyDerErkennung() {
        anthropic()
        show { LlmAccessScreen(it) }
        fallbackOn()
        val p = Prefs(ctx)
        assertTrue(p.llmFallbackEnabled)
        assertEquals("openai", p.llmFallbackProviderId)
        // OpenAI ist der Anbieter der Erkennung: ohne zweiten Key geht es mit dessen Key.
        compose.onNodeWithText("Leer gelassen gilt der Key der Spracherkennung.").assertExists()
        assertEquals("sk-openai", requireNotNull(p.llmFallbackAccess(RefineMode.POLISH)).apiKey)
        compose.onNodeWithTag("dropdown:Modell des Fallbacks").assertTextContains("Empfehlung je Stufe")
        compose.onNodeWithText("Scheitert die Textverbesserung, geht der erkannte Text zusätzlich an OpenAI.").assertExists()
    }

    @Test fun kiZugangModellDesFallbacksGiltFuerAlleStufen() {
        anthropic()
        prefs.llmFallbackEnabled = true
        prefs.llmFallbackProviderId = "openai"
        show { LlmAccessScreen(it) }
        compose.onNodeWithTag("dropdown:Modell des Fallbacks").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Glätten:", substring = true).assertExists() // Empfehlung je Stufe mit beiden Modellen
        compose.onNodeWithText("Eigenes Modell …").performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasText("Modell-ID")).performTextInput("gpt-6")
        click("Übernehmen")
        val p = Prefs(ctx)
        assertEquals("gpt-6", p.llmFallbackModel)
        RefineMode.MODEL_STAGES.forEach { assertEquals(it.name, "gpt-6", p.llmFallbackAccess(it)?.model) }
        // Die Stufen-Modelle des ersten Anbieters bleiben unberuehrt.
        RefineMode.MODEL_STAGES.forEach { assertEquals(it.name, "", p.llmModelFor(it)) }
    }

    @Test fun kiZugangOllamaLokalVerlangtAdresseUndModell() {
        anthropic()
        prefs.llmFallbackEnabled = true
        prefs.llmFallbackProviderId = "ollama"
        show { LlmAccessScreen(it) }
        compose.onNode(hasSetTextAction() and hasText("Base-URL des Fallbacks")).performTextInput("http://192.168.1.5:11434")
        compose.onNode(hasSetTextAction() and hasText("Modell des Fallbacks")).performTextInput("qwen3:8b")
        compose.waitForIdle()
        val access = requireNotNull(Prefs(ctx).llmFallbackAccess(RefineMode.POLISH))
        assertEquals("http://192.168.1.5:11434", access.baseUrl)
        assertEquals("qwen3:8b", access.model)
    }

    @Test fun kiZugangOfflineOhneEigenenZugangHatKeinenFallback() {
        // Dort fehlt erst der eigene Zugang (Hinweis darueber), ein Fallback kaeme nie zum Zug.
        prefs.engine = Engine.OFFLINE
        show { LlmAccessScreen(it) }
        compose.onNodeWithText("Fallback verwenden").assertDoesNotExist()
    }

    @Test fun kiZugangUndErkennungHabenGetrennteSchalter() {
        anthropic()
        show { LlmAccessScreen(it) }
        fallbackOn()
        val p = Prefs(ctx)
        assertTrue(p.llmFallbackEnabled)
        assertFalse(p.sttFallbackEnabled)
    }
}
