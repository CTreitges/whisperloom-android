package com.chris.whisperloom.ui.access

import android.content.Context
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Engine
import com.chris.whisperloom.ModelCache
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.api.ModelKind
import com.chris.whisperloom.api.RemoteModel
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.settings.LlmAccessScreen
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.Collections

/**
 * Seite "KI-Zugang" (3.9.0, vorher Text › Online-Zugang & Modelle): Abschnitt "Modell je Stufe" (Standard-Label je Rolle,
 * eigenes Modell schreiben, Zuruecksetzen beim Anbieterwechsel, Hinweis ohne Online-Zugang oder ohne Modell
 * des Zugangs, "Modell pruefen") und der Eintrag "Empfehlung je Stufe" im Modellfeld des Zugangs. Nur
 * "Modell pruefen" fragt, und zwar einen lokalen JDK-HttpServer; sonst geht nichts raus (Liste im Cache).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class StageModelsUiTest {

    private companion object {
        const val NO_MODEL = "Trag oben zuerst ein Modell ein — es ist der Standard aller Stufen. Danach kannst du hier je Stufe ein anderes wählen."
    }

    @get:Rule
    val compose = createComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs

    @Before fun setUp() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(ModelCache.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "groq"
        prefs.apiKey = "gsk"
        prefs.refineMode = RefineMode.POLISH
    }

    /** Eigener Zugang Anthropic; mit Pro ohne Key, sonst laedt die Seite die Liste echt nach. */
    private fun anthropic(key: String = "sk-ant") {
        prefs.llmProviderId = "anthropic"
        prefs.llmKey = key
    }

    private fun show() {
        val status = SystemStatus()
        val env = AppEnv(PrefsState(prefs), status) { status }
        val nav = NavState(listOf(Screen.Home, Screen.SettingsHub, Screen.LlmAccess))
        compose.setContent {
            WhisperLoomTheme { CompositionLocalProvider(LocalAppEnv provides env) { LlmAccessScreen(nav) } }
        }
        compose.waitForIdle()
    }

    private fun click(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    private fun row(text: String) = compose.onNode(isSelectable() and hasText(text))

    // --- Abschnitt "Modell je Stufe" ---------------------------------------------------------

    @Test fun standardJeStufeIstDieEmpfehlungFuerIhreRolle() {
        anthropic()
        show()
        compose.onNodeWithText("Modell je Stufe").assertExists()
        // Glaetten schnell, Umformulieren staerker (Messung 2026-10-07).
        compose.onNodeWithText("Standard · Claude Haiku 5.5").assertExists()
        compose.onAllNodesWithText("Standard · Claude Sonnet 5.5").fetchSemanticsNodes().let { assertEquals(2, it.size) }
        compose.onNodeWithText("Prompt").assertDoesNotExist() // nur mit Pro
    }

    @Test fun promptZeileNurMitPro() {
        anthropic()
        prefs.promptLevelEnabled = true
        show()
        compose.onNodeWithText("Prompt").assertExists()
        compose.onAllNodesWithText("Standard · Claude Sonnet 5.5").fetchSemanticsNodes().let { assertEquals(3, it.size) }
    }

    @Test fun dasModellDesZugangsIstDerStandardAllerStufen() {
        anthropic()
        prefs.llmModel = "claude-opus-5-5"
        show()
        compose.onAllNodesWithText("Standard · Claude Opus 5.5").fetchSemanticsNodes().let { assertEquals(3, it.size) }
    }

    @Test fun eineStufeBekommtEinEigenesModellUndZurueckAufStandard() {
        anthropic()
        show()
        click("Verschönern")
        compose.onNodeWithText("Modell für Verschönern").assertExists()
        compose.onNodeWithText("Modell prüfen").assertExists()
        row("Standard (Claude Sonnet 5.5)").assertIsSelected()
        row("Claude Opus 5.5").performClick()
        compose.waitForIdle()
        assertEquals("claude-opus-5-5", Prefs(ctx).llmModelFor(RefineMode.BEAUTIFY))
        assertEquals("die anderen Stufen bleiben", "", Prefs(ctx).llmModelFor(RefineMode.POLISH))
        compose.onNodeWithText("Modell für Verschönern").assertDoesNotExist()
        compose.onNodeWithText("Claude Opus 5.5").assertExists()

        click("Verschönern")
        row("Claude Opus 5.5").assertIsSelected()
        row("Standard (Claude Sonnet 5.5)").assertIsNotSelected().performClick()
        compose.waitForIdle()
        assertEquals("", Prefs(ctx).llmModelFor(RefineMode.BEAUTIFY))
    }

    @Test fun eigenesModellFuerEineStufe() {
        anthropic()
        show()
        click("Zusammenfassen")
        click("Eigenes Modell …")
        compose.onNode(hasSetTextAction() and hasText("Modell-ID")).performTextInput("claude-fable-6")
        click("Übernehmen")
        assertEquals("claude-fable-6", Prefs(ctx).llmModelFor(RefineMode.SUMMARIZE))
        compose.onNodeWithText("claude-fable-6").assertExists()
    }

    @Test fun mitProBietetDieStufeAuchDieServerListe() {
        anthropic(key = "")
        prefs.serverModelsEnabled = true
        val entry = ModelCache.Entry(System.currentTimeMillis(), listOf(RemoteModel("claude-mythos-6")))
        val access = prefs.llmAccess()
        ctx.getSharedPreferences(ModelCache.FILE, Context.MODE_PRIVATE).edit()
            .putString(ModelCache.key(access.provider.id, ModelKind.LLM, access.baseUrl), ModelCache.encode(entry)).commit()
        show()
        click("Glätten")
        compose.onNodeWithText("Vom Server", substring = true).assertExists()
        row("claude-mythos-6").performClick()
        compose.waitForIdle()
        assertEquals("claude-mythos-6", Prefs(ctx).llmModelFor(RefineMode.POLISH))
    }

    @Test fun anbieterwechselSetztDieZeilenAufStandardZurueck() {
        anthropic()
        prefs.setLlmModelFor(RefineMode.BEAUTIFY, "claude-opus-5-5")
        show()
        compose.onNodeWithText("Claude Opus 5.5").assertExists()
        compose.onNodeWithText("Ein neuer Anbieter setzt alle Stufen auf „Standard“ zurück.").assertExists()
        compose.onNodeWithTag("dropdown:Anbieter").performClick()
        compose.waitForIdle()
        click("OpenRouter")
        assertEquals("", Prefs(ctx).llmModelFor(RefineMode.BEAUTIFY))
        compose.onNodeWithText("Claude Opus 5.5").assertDoesNotExist()
        compose.onAllNodesWithText("Standard ·", substring = true).fetchSemanticsNodes().let { assertEquals(3, it.size) }
    }

    @Test fun beiElevenLabsWieErkennungEinHinweisStattDerAuswahl() {
        prefs.sttProviderId = "elevenlabs"
        show()
        compose.onNodeWithText("Ohne Online-Zugang für die Textverbesserung gibt es hier nichts zu wählen.").assertExists()
        compose.onNodeWithText("Standard ·", substring = true).assertDoesNotExist()
    }

    @Test fun offlineOhneEigenenZugangEinHinweisStattDerAuswahl() {
        prefs.engine = Engine.OFFLINE
        show()
        compose.onNodeWithText("Ohne Online-Zugang für die Textverbesserung gibt es hier nichts zu wählen.").assertExists()
        compose.onNodeWithText("Standard ·", substring = true).assertDoesNotExist()
    }

    @Test fun offlineMitEigenemZugangGibtEsDieAuswahl() {
        prefs.engine = Engine.OFFLINE
        anthropic()
        show()
        compose.onNodeWithText("Ohne Online-Zugang", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Standard · Claude Haiku 5.5").assertExists()
    }

    @Test fun togetherWieErkennungOhneModellEinHinweisStattDerAuswahl() {
        prefs.sttProviderId = "together"
        prefs.apiKey = "k"
        prefs.setLlmModelFor(RefineMode.BEAUTIFY, "Y")
        show()
        compose.onNodeWithText(NO_MODEL).assertExists()
        compose.onNodeWithText("Standard ·", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Y").assertDoesNotExist()
    }

    @Test fun ohneKatalogMitModellDesZugangsGibtEsDieAuswahl() {
        prefs.llmProviderId = "custom"
        prefs.llmUrl = "http://127.0.0.1:1/v1"
        prefs.llmModel = "qwen3:8b"
        show()
        compose.onNodeWithText(NO_MODEL).assertDoesNotExist()
        compose.onAllNodesWithText("Standard · qwen3:8b").fetchSemanticsNodes().let { assertEquals(3, it.size) }
    }

    // --- Modellfeld des Zugangs: "Empfehlung je Stufe" ------------------------------------------

    @Test fun modellfeldBietetEmpfehlungJeStufeMitBeidenModellen() {
        anthropic()
        show()
        compose.onNodeWithTag("dropdown:Modell").assertTextContains("Empfehlung je Stufe")
        compose.onNodeWithTag("dropdown:Modell").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Glätten: Claude Haiku 5.5 · Umformulieren: Claude Sonnet 5.5").assertExists()
        click("Claude Opus 5.5")
        assertEquals("claude-opus-5-5", Prefs(ctx).llmModel)
        compose.onNodeWithTag("dropdown:Modell").assertTextContains("Claude Opus 5.5")
        compose.onAllNodesWithText("Standard · Claude Opus 5.5").fetchSemanticsNodes().let { assertEquals(3, it.size) }

        compose.onNodeWithTag("dropdown:Modell").performClick()
        compose.waitForIdle()
        click("Empfehlung je Stufe")
        assertEquals("", Prefs(ctx).llmModel)
        compose.onNodeWithText("Standard · Claude Haiku 5.5").assertExists()
    }

    @Test fun ohneKatalogKeineEmpfehlungJeStufe() {
        // Eigener Server: das Modell ist Pflicht, eine Empfehlung gibt es nicht. Ohne Modell des Zugangs
        // wirkt kein Stufen-Modell (Review 3.8.6, L1) — also ein Hinweis statt der Auswahl.
        prefs.llmProviderId = "custom"
        prefs.llmUrl = "http://127.0.0.1:1/v1"
        show()
        compose.onNodeWithText("Empfehlung je Stufe").assertDoesNotExist()
        compose.onNodeWithText(NO_MODEL).assertExists()
        compose.onNodeWithText("Standard ·", substring = true).assertDoesNotExist()
    }

    @Test fun pickerMitProHatEmpfehlungJeStufeOben() {
        anthropic(key = "")
        prefs.serverModelsEnabled = true
        prefs.llmModel = "claude-sonnet-5"
        show()
        compose.onNodeWithTag("picker:Modell").assertTextContains("Claude Sonnet 5").performClick()
        compose.waitForIdle()
        row("Empfehlung je Stufe").assertIsNotSelected()
        row("Claude Sonnet 5").assertIsSelected()
        row("Empfehlung je Stufe").performClick()
        compose.waitForIdle()
        assertEquals("", Prefs(ctx).llmModel)
        compose.onNodeWithTag("picker:Modell").assertTextContains("Empfehlung je Stufe")
    }

    // --- "Modell pruefen" im Sheet der Stufe ----------------------------------------------------

    @Test fun modellPruefenPrueftDasModellDerStufe() {
        // Review 3.8.6 (UI-4): geprueft wird das Modell, mit dem die Stufe rechnet, nicht das des Zugangs.
        val bodies: MutableList<JSONObject> = Collections.synchronizedList(mutableListOf())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { ex ->
            bodies += JSONObject(ex.requestBody.readBytes().toString(Charsets.UTF_8))
            val out = """{"choices":[{"message":{"content":"Hallo."},"finish_reason":"stop"}]}""".toByteArray()
            ex.sendResponseHeaders(200, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.start()
        try {
            anthropic()
            prefs.llmUrl = "http://127.0.0.1:${server.address.port}/v1"
            prefs.setLlmModelFor(RefineMode.SUMMARIZE, "claude-opus-5-5")
            show()
            click("Zusammenfassen")
            click("Modell prüfen")
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Verbunden", substring = true).fetchSemanticsNodes().isNotEmpty() }
            assertEquals("claude-opus-5-5", bodies.single().getString("model"))
        } finally {
            server.stop(0)
        }
    }
}
