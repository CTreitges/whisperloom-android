package com.chris.whisperloom.ui.access

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assert
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
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Engine
import com.chris.whisperloom.ModelCache
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.api.ApiAccess
import com.chris.whisperloom.api.ModelKind
import com.chris.whisperloom.api.RemoteModel
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.settings.RecognitionScreen
import com.chris.whisperloom.ui.settings.TextSettingsScreen
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pro "Modelle vom Server" in den Zugangs-Sektionen: aus = Dropdown wie bisher, an = Picker mit
 * "Empfohlen" und "Vom Server", Knopf "Modelle aktualisieren", stilles Nachladen nur bei veralteter
 * Liste. Ollama laedt ohne Pro wie bisher — jetzt ueber den Cache. Server = lokaler JDK-HttpServer;
 * Katalog-Anbieter (Groq …) bekommen ihre Liste direkt in den Cache und keinen Key, damit nie eine
 * echte Anfrage rausgeht.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class ModelPickerTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs
    private lateinit var server: HttpServer
    private val hits = AtomicInteger()

    @Volatile private var status = 200

    @Volatile private var models = """{"data":[{"id":"whisper-large-v3"},{"id":"whisper-1"}]}"""

    @Volatile private var tags = """{"models":[{"name":"qwen3:8b"},{"name":"nomic-embed-text"},{"name":"gemma3:4b"}]}"""

    private val url get() = "http://127.0.0.1:${server.address.port}"

    @Before fun setUp() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(ModelCache.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
        prefs.engine = Engine.ONLINE
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/models") { ex -> respond(ex, models) }
        server.createContext("/api/tags") { ex -> respond(ex, tags) }
        server.start()
    }

    @After fun tearDown() {
        server.stop(0)
    }

    private fun respond(ex: HttpExchange, body: String) {
        hits.incrementAndGet()
        val out = (if (status == 200) body else """{"error":{"message":"Server kaputt"}}""").toByteArray()
        ex.sendResponseHeaders(status, out.size.toLong())
        ex.responseBody.use { it.write(out) }
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

    private fun erkennung() = show { RecognitionScreen(it) }

    private fun text() {
        prefs.refineMode = RefineMode.POLISH
        show { TextSettingsScreen(it) }
    }

    /** Legt eine Liste ab, als waere sie vor [ageMs] geladen worden. */
    private fun cache(access: ApiAccess, kind: ModelKind, vararg ids: String, ageMs: Long = 0) {
        val entry = ModelCache.Entry(System.currentTimeMillis() - ageMs, ids.map { RemoteModel(it) })
        ctx.getSharedPreferences(ModelCache.FILE, Context.MODE_PRIVATE).edit()
            .putString(ModelCache.key(access.provider.id, kind, access.baseUrl), ModelCache.encode(entry)).commit()
    }

    private fun cachedIds(access: ApiAccess, kind: ModelKind) = Prefs(ctx).modelCache.get(access, kind)?.models?.map { it.id }

    private fun click(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    /** Nach der Tipp-Pause des stillen Nachladens (700 ms). */
    private fun afterPause() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun openPicker(label: String = "Modell") {
        compose.onNodeWithTag("picker:$label").performClick()
        compose.waitForIdle()
    }

    private fun row(text: String) = compose.onNode(isSelectable() and hasText(text))

    private fun groq() {
        prefs.serverModelsEnabled = true
        prefs.sttProviderId = "groq"
    }

    // --- Pro aus ----------------------------------------------------------------------

    @Test fun proAusZeigtDenBisherigenDropdownUndLaedtNichts() {
        prefs.sttProviderId = "custom"
        prefs.apiBaseUrl = "$url/v1"
        erkennung()
        compose.onNodeWithTag("picker:Modell").assertDoesNotExist()
        compose.onNodeWithText("Modelle aktualisieren").assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("Modell")).assertExists() // freie Eingabe wie bisher
        afterPause()
        Thread.sleep(200)
        assertEquals(0, hits.get())
    }

    @Test fun proAusBeiGroqBleibtDasAuswahlfeld() {
        prefs.sttProviderId = "groq"
        cache(prefs.sttAccess(), ModelKind.STT, "whisper-large-v3")
        erkennung()
        compose.onNodeWithTag("dropdown:Modell").assertTextContains("Whisper Large v3 Turbo").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Eigenes Modell …").assertExists()
        compose.onNodeWithText("Vom Server", substring = true).assertDoesNotExist()
    }

    // --- Pro an: Picker -----------------------------------------------------------------

    @Test fun proAnZeigtPickerMitEmpfohlenUndServerListe() {
        groq()
        cache(prefs.sttAccess(), ModelKind.STT, "distil-whisper-large-v3-en", "whisper-large-v3", "whisper-large-v3-turbo")
        erkennung()
        compose.onNodeWithTag("dropdown:Modell").assertDoesNotExist()
        compose.onNodeWithText("Modelle aktualisieren").assertExists()
        compose.onNodeWithTag("picker:Modell").assertTextContains("Whisper Large v3 Turbo")
        openPicker()
        compose.onNodeWithText("Modell wählen").assertExists()
        compose.onNodeWithText("Empfohlen").assertExists()
        compose.onNodeWithText("Vom Server · Stand", substring = true).assertExists()
        compose.onNodeWithText("Eigenes Modell …").assertExists()
        // Katalog mit Notiz; Default gewaehlt, weil der Nutzer noch nichts ausgesucht hat.
        row("Whisper Large v3 Turbo").assertIsSelected().assertTextContains("Sehr schnell", substring = true)
        row("distil-whisper-large-v3-en").assertIsNotSelected()
        compose.onNodeWithText("nicht mehr gelistet", substring = true).assertDoesNotExist()
    }

    @Test fun dieSucheFiltertBeideAbschnitte() {
        groq()
        cache(prefs.sttAccess(), ModelKind.STT, "distil-whisper-large-v3-en", "whisper-large-v3")
        erkennung()
        openPicker()
        compose.onNode(hasSetTextAction() and hasText("Modell suchen")).performTextInput("DISTIL")
        compose.waitForIdle()
        row("distil-whisper-large-v3-en").assertExists()
        row("whisper-large-v3").assertDoesNotExist()
        row("Whisper Large v3 Turbo").assertDoesNotExist()
        compose.onNodeWithText("Empfohlen").assertDoesNotExist()
    }

    @Test fun auswahlAusDerServerListeSpeichertDieId() {
        groq()
        cache(prefs.sttAccess(), ModelKind.STT, "distil-whisper-large-v3-en", "whisper-large-v3")
        erkennung()
        openPicker()
        row("distil-whisper-large-v3-en").performClick()
        compose.waitForIdle()
        assertEquals("distil-whisper-large-v3-en", Prefs(ctx).apiModel)
        compose.onNodeWithText("Modell wählen").assertDoesNotExist()
        compose.onNodeWithTag("picker:Modell").assertTextContains("distil-whisper-large-v3-en")
    }

    @Test fun eineEmpfehlungDieDerServerNichtMehrListetIstMarkiert() {
        groq()
        cache(prefs.sttAccess(), ModelKind.STT, "whisper-large-v3")
        erkennung()
        openPicker()
        // Gewaehlt bleibt sie trotzdem — die Markierung ist nur ein Hinweis.
        row("Whisper Large v3 Turbo").assertIsSelected().assertTextContains("nicht mehr gelistet", substring = true)
        row("Whisper Large v3").assert(!hasText("nicht mehr gelistet", substring = true))
        assertEquals("", Prefs(ctx).apiModel)
    }

    @Test fun einGewaehltesModellOhneListenplatzBleibtGueltig() {
        groq()
        prefs.apiModel = "whisper-large-v2"
        cache(prefs.sttAccess(), ModelKind.STT, "whisper-large-v3")
        erkennung()
        compose.onNodeWithTag("picker:Modell").assertTextContains("whisper-large-v2")
        openPicker()
        row("whisper-large-v2").assertIsSelected().assertTextContains("nicht mehr gelistet", substring = true)
        click("Schließen")
        assertEquals("whisper-large-v2", Prefs(ctx).apiModel)
    }

    @Test fun eineLeereListeMarkiertNichts() {
        groq()
        cache(prefs.sttAccess(), ModelKind.STT)
        erkennung()
        openPicker()
        compose.onNodeWithText("Keine passenden Modelle gefunden.").assertExists()
        compose.onNodeWithText("nicht mehr gelistet", substring = true).assertDoesNotExist()
    }

    @Test fun beiElevenLabsWirdNichtsAlsNichtMehrGelistetMarkiert() {
        // /v1/models listet Scribe wohl nicht — die Empfehlungen bleiben unmarkiert.
        prefs.serverModelsEnabled = true
        prefs.sttProviderId = "elevenlabs"
        cache(prefs.sttAccess(), ModelKind.STT, "scribe_v2_experimental")
        erkennung()
        openPicker()
        row("scribe_v2_experimental").assertExists()
        compose.onNodeWithText("nicht mehr gelistet", substring = true).assertDoesNotExist()
    }

    @Test fun eigenesModellAusDemPicker() {
        groq()
        erkennung()
        openPicker()
        compose.onNodeWithText("Vom Server", substring = true).assertDoesNotExist() // noch nichts geladen
        click("Eigenes Modell …")
        compose.onNode(hasSetTextAction() and hasText("Modell-ID")).performTextInput("whisper-large-v3-custom")
        click("Übernehmen")
        assertEquals("whisper-large-v3-custom", Prefs(ctx).apiModel)
    }

    @Test fun textverbesserungWaehltAusDerServerListe() {
        prefs.serverModelsEnabled = true
        prefs.llmProviderId = "groq"
        cache(prefs.llmAccess(), ModelKind.LLM, "moonshotai/kimi-k3", "openai/gpt-oss-20b")
        text()
        compose.onNodeWithTag("dropdown:Modell").assertDoesNotExist()
        openPicker()
        row("GPT-OSS 20B").assertIsSelected()
        row("Qwen 3.8 27B (Preview)").assertTextContains("nicht mehr gelistet", substring = true)
        row("moonshotai/kimi-k3").performClick()
        compose.waitForIdle()
        assertEquals("moonshotai/kimi-k3", Prefs(ctx).llmModel)
    }

    // --- Knopf "Modelle aktualisieren" ----------------------------------------------------

    private fun eigenerServerMitFrischerListe() {
        prefs.serverModelsEnabled = true
        prefs.sttProviderId = "custom"
        prefs.apiBaseUrl = "$url/v1"
        cache(prefs.sttAccess(), ModelKind.STT, "alt")
    }

    @Test fun derKnopfLaedtDieListeUndMeldetDieAnzahl() {
        eigenerServerMitFrischerListe()
        erkennung()
        click("Modelle aktualisieren")
        waitForText("2 Modelle gefunden")
        assertEquals(1, hits.get())
        assertEquals(listOf("whisper-1", "whisper-large-v3"), cachedIds(prefs.sttAccess(), ModelKind.STT))
        openPicker()
        row("whisper-1").assertExists()
        row("alt").assertDoesNotExist()
    }

    @Test fun eineLeereAntwortMeldetKeineModelle() {
        eigenerServerMitFrischerListe()
        models = """{"data":[]}"""
        erkennung()
        click("Modelle aktualisieren")
        waitForText("Keine passenden Modelle gefunden.")
    }

    @Test fun einFehlerKommtAlsSnackbarUndDieAlteListeBleibt() {
        eigenerServerMitFrischerListe()
        status = 500
        erkennung()
        click("Modelle aktualisieren")
        waitForText("Modelle nicht geladen:")
        compose.onNodeWithText("Server kaputt", substring = true).assertExists()
        assertEquals(listOf("alt"), cachedIds(prefs.sttAccess(), ModelKind.STT))
    }

    // --- Stilles Nachladen -------------------------------------------------------------

    @Test fun eineFrischeListeWirdNichtNachgeladen() {
        eigenerServerMitFrischerListe()
        erkennung()
        afterPause()
        Thread.sleep(200)
        assertEquals(0, hits.get())
    }

    @Test fun eineVeralteteListeWirdBeimOeffnenNachgeladen() {
        prefs.serverModelsEnabled = true
        prefs.sttProviderId = "custom"
        prefs.apiBaseUrl = "$url/v1"
        cache(prefs.sttAccess(), ModelKind.STT, "alt", ageMs = ModelCache.MAX_AGE_MS + 60_000)
        erkennung()
        afterPause()
        compose.waitUntil(5_000) { cachedIds(prefs.sttAccess(), ModelKind.STT) == listOf("whisper-1", "whisper-large-v3") }
        assertEquals(1, hits.get())
        compose.onNodeWithText("Modelle gefunden", substring = true).assertDoesNotExist() // still
    }

    @Test fun ohneKeyWirdNichtNachgeladen() {
        // Groq braucht einen Key; ohne ihn gaebe es nur einen 401.
        groq()
        erkennung()
        afterPause()
        assertEquals(null, cachedIds(prefs.sttAccess(), ModelKind.STT))
    }

    @Test fun einFehlerBeimNachladenBleibtStumm() {
        prefs.serverModelsEnabled = true
        prefs.sttProviderId = "custom"
        prefs.apiBaseUrl = "$url/v1"
        status = 500
        erkennung()
        afterPause()
        compose.waitUntil(5_000) { hits.get() == 1 }
        compose.waitForIdle()
        compose.onNodeWithText("Modelle nicht geladen:", substring = true).assertDoesNotExist()
    }

    /** Eigener Server ohne Liste, das letzte Laden ist gescheitert (vor [ageMs]). */
    private fun eigenerServerZuletztGescheitert(ageMs: Long = 0) {
        prefs.serverModelsEnabled = true
        prefs.sttProviderId = "custom"
        prefs.apiBaseUrl = "$url/v1"
        status = 500
        val then = System.currentTimeMillis() - ageMs
        runCatching { ModelCache(ctx) { then }.refresh(prefs.sttAccess(), ModelKind.STT) }
        assertEquals(1, hits.get())
    }

    @Test fun einFehlschlagWirdNichtBeiJedemOeffnenWiederholt() {
        // z. B. ElevenLabs-Key ohne "Models: Lesen": sonst bei jedem Oeffnen derselbe 401.
        eigenerServerZuletztGescheitert()
        erkennung()
        afterPause()
        Thread.sleep(200)
        assertEquals(1, hits.get())
        // Der Knopf laedt trotzdem.
        click("Modelle aktualisieren")
        compose.waitUntil(5_000) { hits.get() == 2 }
    }

    @Test fun nachEinemTagVersuchtEsDasStilleNachladenWieder() {
        eigenerServerZuletztGescheitert(ageMs = ModelCache.MAX_AGE_MS + 60_000)
        status = 200
        erkennung()
        afterPause()
        compose.waitUntil(5_000) { cachedIds(prefs.sttAccess(), ModelKind.STT) == listOf("whisper-1", "whisper-large-v3") }
        assertEquals(2, hits.get())
    }

    @Test fun einNeuerKeyVersuchtEsSofortWieder() {
        eigenerServerZuletztGescheitert()
        status = 200
        erkennung()
        compose.onNode(hasSetTextAction() and hasText("API-Key (optional)")).performTextInput("neuer-key")
        compose.waitForIdle()
        afterPause()
        compose.waitUntil(5_000) { hits.get() == 2 }
    }

    // --- Ollama: ohne Pro wie bisher, jetzt ueber den Cache ------------------------------

    @Test fun ollamaOhneProLaedtBeiJedemOeffnenUndUebernimmtDasErsteModell() {
        prefs.llmProviderId = "ollama"
        prefs.llmUrl = url
        // Auch eine frische Liste wird bei Ollama neu geholt (wie bisher bei jedem Oeffnen).
        cache(prefs.llmAccess(), ModelKind.LLM, "alt:1b")
        text()
        compose.onNodeWithTag("picker:Modell").assertDoesNotExist()
        compose.onNodeWithText("Modelle vom Server laden").assertExists()
        afterPause()
        compose.waitUntil(5_000) { Prefs(ctx).llmModel.isNotBlank() }
        // Alphabetisch, ohne Einbettungs-Modell.
        assertEquals("gemma3:4b", Prefs(ctx).llmModel)
        assertEquals(listOf("gemma3:4b", "qwen3:8b"), cachedIds(prefs.llmAccess(), ModelKind.LLM))
        compose.onNodeWithTag("dropdown:Modell").assertTextContains("gemma3:4b")

        click("Modelle vom Server laden")
        waitForText("2 Modelle gefunden")
        assertEquals(2, hits.get())
    }

    @Test fun ollamaOhneModelleNenntDenPullBefehl() {
        prefs.llmProviderId = "ollama"
        prefs.llmUrl = url
        tags = """{"models":[]}"""
        text()
        click("Modelle vom Server laden")
        waitForText("ollama pull")
    }

    @Test fun ollamaMitProNutztDenPicker() {
        prefs.serverModelsEnabled = true
        prefs.llmProviderId = "ollama"
        prefs.llmUrl = url
        text()
        afterPause()
        compose.waitUntil(5_000) { Prefs(ctx).llmModel.isNotBlank() }
        compose.onNodeWithTag("dropdown:Modell").assertDoesNotExist()
        openPicker()
        row("gemma3:4b").assertIsSelected()
        row("qwen3:8b").assertIsNotSelected()
    }

    // --- Ollama Cloud: der Hinweis nennt den Knopf, ohne seinen Namen (der haengt an Pro) ---

    /** Hinweis sichtbar, darunter das Feld "Modell", darunter der Knopf [knopf]. Ohne Key: keine Anfrage. */
    private fun ollamaCloudHinweisPasstZumKnopf(pro: Boolean, feld: String, knopf: String) {
        prefs.serverModelsEnabled = pro
        prefs.llmProviderId = "ollama-cloud"
        text()
        val hinweis = ctx.getString(R.string.text_ollama_cloud_note)
        compose.onNodeWithText(hinweis).assertExists()
        assertTrue(hinweis.contains("unter dem Feld „Modell“"))
        val modell = compose.onNodeWithTag(feld).fetchSemanticsNode().boundsInRoot
        val unten = compose.onNodeWithText(knopf).fetchSemanticsNode().boundsInRoot
        assertTrue("$knopf steht unter dem Feld „Modell“", unten.top >= modell.bottom)
        assertEquals(0, hits.get())
    }

    @Test fun ollamaCloudHinweisStimmtOhnePro() =
        ollamaCloudHinweisPasstZumKnopf(pro = false, feld = "dropdown:Modell", knopf = "Modelle vom Server laden")

    @Test fun ollamaCloudHinweisStimmtMitPro() =
        ollamaCloudHinweisPasstZumKnopf(pro = true, feld = "picker:Modell", knopf = "Modelle aktualisieren")
}
