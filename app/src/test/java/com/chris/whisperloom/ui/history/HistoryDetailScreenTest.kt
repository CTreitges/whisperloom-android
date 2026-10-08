package com.chris.whisperloom.ui.history

import android.content.ClipboardManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.chris.whisperloom.Engine
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.RefinePlan
import com.chris.whisperloom.Refinement
import com.chris.whisperloom.api.NetworkCheck
import com.chris.whisperloom.history.History
import com.chris.whisperloom.history.HistoryVersion
import com.chris.whisperloom.history.Processing
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.share.SKELETON_TAG
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Eintrag des Verlaufs (Plan §6.3): Chips, sichtbare Fassung, Kopieren, Fuellwoerter beim Ursprung,
 * Hinweiszeile und "Andere Stufe …" gegen einen lokalen Chat-Server als KI (wie HistoryTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class HistoryDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val ui = HistoryUi(compose)
    private lateinit var server: HttpServer
    private val originalNetwork = RefinePlan.networkCheck
    private val chatRequests = AtomicInteger()

    /** Haelt die Antwort der KI an, bis der Test sie freigibt. */
    @Volatile private var gate: CountDownLatch? = null
    @Volatile private var net = true

    @Before fun setUp() {
        ui.setUp()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { ex ->
            chatRequests.incrementAndGet()
            ex.requestBody.readBytes()
            gate?.await(10, TimeUnit.SECONDS)
            val out = """{"choices":[{"message":{"content":"Neu verschönert."}}]}""".toByteArray()
            ex.sendResponseHeaders(200, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.start()
        RefinePlan.networkCheck = { NetworkCheck { _, _ -> net } }
    }

    @After fun tearDown() {
        gate?.countDown()
        server.stop(0)
        RefinePlan.networkCheck = originalNetwork
        ui.tearDown()
    }

    /** Offline erkannt, eigener Online-Zugang = der lokale Server (wie HistoryTest.ownAccess). */
    private fun ownAccess() {
        ui.prefs.engine = Engine.OFFLINE
        ui.prefs.offlineRefine = OfflineRefineRule.SKIP
        ui.prefs.llmProviderId = "custom"
        ui.prefs.llmUrl = "http://127.0.0.1:${server.address.port}/v1"
        ui.prefs.llmModel = "qwen3:8b"
    }

    private fun eintrag(id: String, show: String? = null): NavState = ui.show(Screen.Home, Screen.History, Screen.HistoryDetail(id, show))

    private fun chip(label: String) = compose.onNode(hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))

    private fun clipboard(): String? =
        ui.ctx.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()

    @Test fun oeffnetAufDerDamalsErzeugtenFassungMitKopfkarteUndHinweis() {
        val entry = ui.record(at = ui.at(0, "14:32"))
        eintrag(entry.id)

        ui.waitFor("Komme morgen später.")
        compose.onNodeWithText("Heute, 14:32").assertIsDisplayed()
        compose.onNodeWithText("Tastatur · 0:41 · 11 Wörter").assertIsDisplayed()
        chip("Zusammenfassen").assertIsSelected()
        compose.onNodeWithText("Zusammenfassen · Claude Sonnet 5.5").assertIsDisplayed()
        // Fuellwoerter ausblenden gibt es nur beim Ursprung.
        compose.onNodeWithText("Füllwörter ausblenden").assertDoesNotExist()
    }

    @Test fun chipsWechselnDenTextUndMerkenSichDieWahlImScreen() {
        val entry = ui.record()
        val nav = eintrag(entry.id)
        ui.waitFor("Komme morgen später.")

        chip("Ursprung").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("ähm also ich wollte kurz sagen dass ich morgen später komme").assertIsDisplayed()
        compose.onNodeWithText("Komme morgen später.").assertDoesNotExist()
        chip("Ursprung").assertIsSelected()
        assertEquals(Screen.HistoryDetail(entry.id, Screen.HistoryDetail.ORIGIN), nav.current)
        assertEquals("Zurueck fuehrt weiter zur Liste", Screen.History, nav.snapshot()[nav.snapshot().lastIndex - 1])
    }

    @Test fun ursprungBlendetFuellwoerterAusWieImSprachnachrichtenFenster() {
        val entry = ui.record()
        eintrag(entry.id, Screen.HistoryDetail.ORIGIN)
        ui.waitFor("ähm also ich wollte kurz sagen dass ich morgen später komme")

        compose.onNodeWithText("Füllwörter ausblenden").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("also ich wollte kurz sagen dass ich morgen später komme").assertIsDisplayed()
        compose.onNodeWithText("ähm also ich wollte kurz sagen dass ich morgen später komme").assertDoesNotExist()
    }

    @Test fun kopierenNimmtDieSichtbareFassung() {
        val entry = ui.record()
        eintrag(entry.id)
        ui.waitFor("Komme morgen später.")

        compose.onNodeWithText("Kopieren").performClick()
        assertEquals("Komme morgen später.", clipboard())

        chip("Ursprung").performClick()
        compose.onNodeWithText("Kopieren").performClick()
        assertEquals("ähm also ich wollte kurz sagen dass ich morgen später komme", clipboard())
    }

    @Test fun ohneKiZeigtDenGrundInDerHinweiszeile() {
        val entry = ui.record(text = "Also ich komme morgen später.", model = null, skipped = "Kein Netz für den Online-Zugang")
        eintrag(entry.id)

        ui.waitFor("Zusammenfassen · ohne KI: Kein Netz für den Online-Zugang")
    }

    @Test fun unbekannterEintragFuehrtZurListe() {
        val nav = eintrag("1791456000000-0badcafe")
        ui.waitUntil { nav.current == Screen.History }
        assertEquals(listOf(Screen.Home, Screen.History), nav.snapshot())
    }

    @Test fun loeschenImMenueFuehrtZurListeMitRueckgaengig() {
        val entry = ui.record(raw = "gleich weg")
        val nav = eintrag(entry.id)
        ui.waitFor("Komme morgen später.")

        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Löschen").performClick()

        ui.waitUntil { nav.current == Screen.History }
        ui.waitFor("Eintrag gelöscht")
        assertNull(History.get(ui.ctx, entry.id))
        compose.onNodeWithText("Rückgängig").performClick()
        ui.waitFor("gleich weg")
    }

    // --- Andere Stufe … ------------------------------------------------------------------------

    @Test fun andereStufeOhneNetzZeigtDenHinweisUndLaesstDenEintragUnveraendert() {
        ownAccess()
        net = false
        val entry = ui.record()
        eintrag(entry.id)
        ui.waitFor("Komme morgen später.")

        compose.onNodeWithText("Andere Stufe …").performClick()
        compose.onNodeWithText("Verschönern").performClick()

        ui.waitFor("Kein Netz für den Online-Zugang · Eintrag unverändert")
        assertEquals(entry, History.get(ui.ctx, entry.id))
        assertEquals(0, chatRequests.get())
        // Kein Chip fuer die gescheiterte Fassung; sichtbar bleibt die damalige.
        ui.waitUntil { compose.onAllNodes(hasText("Verschönern")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Komme morgen später.").assertIsDisplayed()
        assertEquals("die eingestellte Stufe bleibt", RefineMode.OFF, ui.prefs.refineMode)
    }

    @Test fun andereStufeHaengtEineFassungAnMitLadeanzeigeWaehrendDerRechnung() {
        ownAccess()
        val entry = ui.record()
        eintrag(entry.id)
        ui.waitFor("Komme morgen später.")
        gate = CountDownLatch(1)

        compose.onNodeWithText("Andere Stufe …").performClick()
        compose.onNodeWithText("Verschönern").performClick()

        // Waehrend die KI rechnet: neuer Chip mit Ladeanzeige, gewaehlt, Platzhalter statt Text.
        ui.waitUntil { compose.onAllNodes(hasStateDescription("wird verarbeitet")).fetchSemanticsNodes().isNotEmpty() }
        chip("Verschönern").assertIsSelected()
        compose.onNodeWithTag(SKELETON_TAG).assertExists()
        gate!!.countDown()

        ui.waitFor("Neu verschönert.")
        compose.onNode(hasStateDescription("wird verarbeitet")).assertDoesNotExist()
        compose.onNodeWithText("Verschönern · qwen3:8b").assertIsDisplayed()
        val saved = History.get(ui.ctx, entry.id)!!
        assertEquals(listOf(Processing.SUMMARIZE, Processing.BEAUTIFY), saved.versions.keys.toList())
        assertEquals("damalige Verarbeitung bleibt", Processing.SUMMARIZE, saved.processing)
        assertEquals(entry.raw, saved.raw)
    }

    @Test fun dieRechnungUeberstehtDasNeuAufbauenDesScreens() {
        ownAccess()
        val entry = ui.record()
        val nav = NavState(listOf(Screen.Home, Screen.History, Screen.HistoryDetail(entry.id)))
        val env = ui.env()
        val restore = StateRestorationTester(compose)
        restore.setContent { ui.Themed(env) { ui.Router(nav) } }
        ui.waitFor("Komme morgen später.")
        gate = CountDownLatch(1)
        compose.onNodeWithText("Andere Stufe …").performClick()
        compose.onNodeWithText("Verschönern").performClick()
        ui.waitUntil { compose.onAllNodes(hasStateDescription("wird verarbeitet")).fetchSemanticsNodes().isNotEmpty() }

        // Rotation: Composition weg und wieder da, die Rechnung laeuft im prozessweiten Scope weiter.
        restore.emulateSavedInstanceStateRestore()
        ui.waitUntil { compose.onAllNodes(hasStateDescription("wird verarbeitet")).fetchSemanticsNodes().isNotEmpty() }
        gate!!.countDown()

        ui.waitFor("Neu verschönert.")
        assertEquals(1, chatRequests.get())
    }

    @Test fun vorhandeneFassungenSindImSheetMarkiert() {
        ui.prefs.promptLevelEnabled = true
        val entry = ui.record(refinement = Refinement(RefineMode.BEAUTIFY))
        eintrag(entry.id)
        ui.waitFor("Komme morgen später.")

        compose.onNodeWithText("Andere Stufe …").performClick()

        compose.onNode(hasText("Verschönern") and hasStateDescription("vorhanden")).assertExists()
        compose.onNode(hasText("Zusammenfassen") and hasStateDescription("vorhanden")).assertDoesNotExist()
        listOf("Nur Zeichensetzung", "Ohne Füllwörter", "Lesbar", "Zusammenfassen", "Prompt").forEach {
            compose.onNode(hasText(it) and !hasStateDescription("vorhanden")).assertExists()
        }
        compose.onNodeWithText("Aus").assertDoesNotExist()
    }

    @Test fun bearbeiteteFassungErsetzenFragtVorher() {
        ownAccess()
        val entry = ui.record(refinement = Refinement(RefineMode.BEAUTIFY), text = "Schön.")
        History.edit(ui.ctx, entry.id, Processing.BEAUTIFY, "Von Hand schön.")
        eintrag(entry.id)
        ui.waitFor("Von Hand schön.")
        compose.onNodeWithText("Verschönern · Claude Sonnet 5.5 · bearbeitet").assertIsDisplayed()

        compose.onNodeWithText("Andere Stufe …").performClick()
        compose.onNode(hasText("Verschönern") and hasStateDescription("vorhanden")).performClick()
        compose.onNodeWithText("Bearbeitete Fassung ersetzen?").assertIsDisplayed()
        compose.onNodeWithText("Abbrechen").performClick()
        compose.waitForIdle()
        assertEquals(0, chatRequests.get())

        compose.onNodeWithText("Andere Stufe …").performClick()
        compose.onNode(hasText("Verschönern") and hasStateDescription("vorhanden")).performClick()
        compose.onNodeWithText("Ersetzen").performClick()

        ui.waitFor("Neu verschönert.")
        val version = History.get(ui.ctx, entry.id)!!.versions.getValue(Processing.BEAUTIFY)
        assertEquals("Neu verschönert.", version.text)
        assertTrue("ersetzt, nicht mehr bearbeitet", !version.edited)
    }

    /**
     * Waehrend "Andere Stufe …" eine vorhandene Fassung neu rechnet, ist ✎ fuer genau diese Fassung
     * gesperrt — sonst ersetzte das Ergebnis die Bearbeitung still. Kommt doch eine Bearbeitung
     * dazwischen, bleibt sie, und ein Hinweis sagt es.
     */
    @Test fun bearbeitenIstGesperrtSolangeDieFassungNeuGerechnetWird() {
        ownAccess()
        val entry = ui.record(refinement = Refinement(RefineMode.BEAUTIFY), text = "Schön.")
        eintrag(entry.id)
        ui.waitFor("Schön.")
        gate = CountDownLatch(1)

        compose.onNodeWithText("Andere Stufe …").performClick()
        compose.onNode(hasText("Verschönern") and hasStateDescription("vorhanden")).performClick()
        ui.waitUntil { compose.onAllNodes(hasStateDescription("wird verarbeitet")).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithContentDescription("Bearbeiten").assertIsNotEnabled()
        chip("Ursprung").performClick()
        compose.onNodeWithContentDescription("Bearbeiten").assertIsEnabled()

        // Eine Bearbeitung derselben Fassung aus der Rechenzeit (etwa ein zweites Fenster).
        History.edit(ui.ctx, entry.id, Processing.BEAUTIFY, "Von Hand schön.")
        gate!!.countDown()

        ui.waitFor("Fassung inzwischen bearbeitet · deine Änderungen bleiben")
        assertEquals("Von Hand schön.", History.get(ui.ctx, entry.id)!!.versions.getValue(Processing.BEAUTIFY).text)
    }

    /** Schmalstes Zielgeraet: alle Chips bleiben im Bild, sie brechen um. */
    @Test @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun chipsBrechenBei360dpUmStattUeberzulaufen() {
        val entry = ui.record(refinement = Refinement(RefineMode.POLISH))
        listOf(Processing.POLISH_CLEAN, Processing.POLISH_READABLE, Processing.BEAUTIFY, Processing.SUMMARIZE, Processing.PROMPT)
            .forEach { History.setVersion(ui.ctx, entry.id, it, HistoryVersion("Text $it", 0L, "Claude")) }
        History.edit(ui.ctx, entry.id, Processing.EDITED, "Von Hand.")
        eintrag(entry.id)
        ui.waitFor("Komme morgen später.")

        val breite = compose.onNode(isRoot()).fetchSemanticsNode().boundsInRoot.right
        val chips = listOf("Ursprung", "Glätten", "Glätten · Ohne Füllwörter", "Glätten · Lesbar", "Verschönern", "Zusammenfassen", "Prompt", "Bearbeitet")
        val rechts = chips.map { chip(it).fetchSemanticsNode().boundsInRoot }
        rechts.forEachIndexed { i, r -> assertTrue("${chips[i]} ragt hinaus: ${r.right} > $breite", r.right <= breite) }
        assertTrue("mehr als eine Zeile", rechts.map { it.top }.distinct().size > 1)
    }
}
