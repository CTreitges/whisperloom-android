package com.chris.whisperloom.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Dictation
import com.chris.whisperloom.Engine
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.RefinePlan
import com.chris.whisperloom.Refined
import com.chris.whisperloom.Refinement
import com.chris.whisperloom.api.NetworkCheck
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Der Verlauf-Speicher am echten Dateisystem (Robolectric): Rundreise, Groesse und Kuerzen, Reste
 * und kaputte Dateien, gleichzeitige Schreiber, Neu-Verarbeiten gegen einen lokalen HttpServer als
 * Online-Zugang — auch wenn der Eintrag waehrenddessen geloescht wird.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs
    private lateinit var server: HttpServer
    private val originalClock = History.clock
    private val originalNetwork = RefinePlan.networkCheck

    private var now = 1_791_456_000_000L
    private val chatRequests = AtomicInteger()

    /** Was der Server tut, bevor er antwortet (etwa den Eintrag loeschen). */
    @Volatile private var beforeAnswer: () -> Unit = {}
    @Volatile private var net = true
    @Volatile private var provenSeen: Boolean? = null

    @Before fun aufbau() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
        History.clear(ctx)
        History.clock = { now++ }
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { ex ->
            chatRequests.incrementAndGet()
            ex.requestBody.readBytes()
            beforeAnswer()
            val out = """{"choices":[{"message":{"content":"Neu geglättet."}}]}""".toByteArray()
            ex.sendResponseHeaders(200, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.start()
        RefinePlan.networkCheck = {
            NetworkCheck { _, proven ->
                provenSeen = proven
                net
            }
        }
    }

    @After fun abbau() {
        server.stop(0)
        History.clock = originalClock
        RefinePlan.networkCheck = originalNetwork
        History.clear(ctx)
    }

    private fun dictation(
        raw: String = "ähm also ich komme morgen später",
        text: String = "Komme morgen später.",
        refinement: Refinement = Refinement(RefineMode.SUMMARIZE),
        model: String? = "Claude Sonnet 5.5",
        skipped: String? = null,
    ) = Dictation(raw, "de", 41_000L, Refined(refinement, text, model, skipped))

    private fun record(d: Dictation = dictation(), source: HistorySource = HistorySource.KEYBOARD) =
        History.record(ctx, source, d)

    /** Offline erkannt, eigener Online-Zugang = der lokale HttpServer, Regel "Ueberspringen". */
    private fun ownAccess() {
        prefs.engine = Engine.OFFLINE
        prefs.offlineRefine = OfflineRefineRule.SKIP
        prefs.llmProviderId = "custom"
        prefs.llmUrl = "http://127.0.0.1:${server.address.port}/v1"
        prefs.llmModel = "qwen3:8b"
    }

    // --- Rundreise --------------------------------------------------------------------------

    @Test fun rundreiseMitUmlautenEmojiUndZwanzigtausendZeichen() {
        val lang = "Grüße 😀 „Zitat“ \"roh\" \\ ß\nZeile zwei. " + "x".repeat(20_000)
        assertTrue(record(dictation(raw = lang, text = "Fertig: $lang", skipped = "API-Fehler 401")))

        val entry = History.list(ctx).single()

        assertEquals(entry, History.get(ctx, entry.id))
        assertEquals(lang, entry.raw)
        assertEquals("de", entry.language)
        assertEquals(41_000L, entry.durationMs)
        assertEquals(HistorySource.KEYBOARD, entry.source)
        assertEquals(1_791_456_000_000L, entry.createdAt)
        assertTrue(entry.id, entry.id.startsWith("1791456000000-"))
        assertEquals(Processing.SUMMARIZE, entry.processing)
        assertEquals(
            mapOf(Processing.SUMMARIZE to HistoryVersion("Fertig: $lang", 1_791_456_000_000L, "Claude Sonnet 5.5", "API-Fehler 401")),
            entry.versions,
        )
    }

    @Test fun glaettenSpeichertDieBereinigungMit() {
        record(dictation(refinement = Refinement(RefineMode.POLISH, smartFillers = true)))
        record(dictation(refinement = Refinement(RefineMode.READABLE)))
        record(dictation(refinement = Refinement.OFF, model = null), HistorySource.BUBBLE)

        val list = History.list(ctx)

        assertEquals(listOf(Processing.OFF, Processing.POLISH_READABLE, Processing.POLISH_CLEAN), list.map { it.processing })
        assertEquals("neueste zuerst", HistorySource.BUBBLE, list.first().source)
    }

    @Test fun leereErkennungWirdNichtGespeichert() {
        assertFalse(record(dictation(raw = " \n", text = "")))
        assertEquals(0, History.count(ctx))
    }

    @Test fun liegtInNoBackupFilesDir() {
        record()
        val id = History.list(ctx).single().id

        assertEquals(File(ctx.noBackupFilesDir, "history"), History.dir(ctx))
        assertTrue(File(ctx.noBackupFilesDir, "history/$id.json").isFile)
        assertFalse(File(ctx.filesDir, "history").exists())
        assertFalse(File(ctx.cacheDir, "history").exists())
    }

    @Test fun toStringEnthaeltKeinenText() {
        record(dictation(raw = "geheimer Rohtext", text = "Geheime Fassung."))
        val entry = History.list(ctx).single()
        val s = entry.toString() + entry.versions.values.joinToString()
        assertFalse(s, s.contains("geheim", ignoreCase = true))
    }

    @Test fun signalSteigtBeiJederAenderung() {
        val before = History.changes.value
        record()
        assertTrue(History.changes.value > before)
    }

    // --- Groesse, aus, kuerzen ----------------------------------------------------------------

    @Test fun ausgeschaltetSchreibtNichts() {
        History.setEnabled(ctx, false)
        assertFalse(record())
        assertFalse(History.dir(ctx).exists())
    }

    @Test fun ausschaltenLoeschtAllesEinschaltenSchreibtWieder() {
        record()
        record()
        History.setEnabled(ctx, false)
        assertFalse(prefs.historyEnabled)
        assertEquals(0, History.count(ctx))

        History.setEnabled(ctx, true)
        assertTrue(record())
        assertEquals(1, History.count(ctx))
    }

    @Test fun einfuegenKuerztAufDieNeuestenN() {
        History.setSize(ctx, 10)
        repeat(12) { record(dictation(raw = "diktat $it")) }

        val list = History.list(ctx)

        assertEquals(10, list.size)
        assertEquals((11 downTo 2).map { "diktat $it" }, list.map { it.raw })
        assertEquals("keine Datei der zwei aeltesten mehr", 10, History.dir(ctx).list()!!.size)
    }

    /**
     * Uhr zurueckgestellt (von Hand, RTC vor dem NTP-Abgleich): die neue Id ist die aelteste, das
     * Kuerzen loescht sie gleich wieder. Dann liegt der Text nicht im Verlauf — die Tastatur darf
     * nicht "Text liegt im Verlauf" sagen.
     */
    @Test fun einEintragDenDasKuerzenGleichWiederLoeschtGiltNichtAlsGespeichert() {
        History.setSize(ctx, 10)
        repeat(10) { record(dictation(raw = "diktat $it")) }
        now -= 3_600_000

        assertFalse(record(dictation(raw = "mit alter Uhr")))

        assertEquals(10, History.count(ctx))
        assertTrue(History.list(ctx).none { it.raw == "mit alter Uhr" })
    }

    @Test fun verkleinernKuerztSofortVergroessernLoeschtNichts() {
        History.setSize(ctx, 50)
        repeat(30) { record(dictation(raw = "diktat $it")) }

        assertEquals("Rueckfrage: so viele wuerden geloescht", 20, History.excess(ctx, 10))
        assertEquals(0, History.excess(ctx, 50))
        assertEquals(20, History.setSize(ctx, 10))
        assertEquals(10, History.count(ctx))
        assertEquals("diktat 29", History.list(ctx).first().raw)

        assertEquals(0, History.setSize(ctx, 100))
        assertEquals(10, History.count(ctx))
        assertEquals(100, prefs.historySize)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nichtWaehlbareGroesseWirdAbgelehnt() {
        History.setSize(ctx, 7)
    }

    // --- Reste, kaputte Dateien, IO-Fehler ------------------------------------------------------

    @Test fun resteAbgebrochenerSchreibvorgaengeVerschwindenKaputteWerdenUebersprungen() {
        record(dictation(raw = "gut eins"))
        record(dictation(raw = "gut zwei"))
        val dir = History.dir(ctx)
        val waise = File(dir, "1791456000500-0000abcd.json.new").apply { writeText("""{"raw":"halber Text""") }
        val alt = File(dir, "1791456000501-0000abcd.json.bak").apply { writeText("Text") }
        File(dir, "1791456000502-0000abcd.json").writeText("kein JSON")
        File(dir, "1791456000503-0000abcd.json").writeText("""{"v":1,"raw":""}""")

        val list = History.list(ctx)

        assertEquals(listOf("gut zwei", "gut eins"), list.map { it.raw })
        assertFalse("verwaiste .new enthaelt Text", waise.exists())
        assertFalse(alt.exists())
    }

    @Test fun fehlendeUndUnbekannteFelderSindTolerant() {
        val dir = History.dir(ctx).apply { mkdirs() }
        File(dir, "1791456000123-3fa9c1d2.json").writeText(
            """{"v":9,"raw":"also hallo","neu":true,"processing":"zaubern",
               "versions":[{"processing":"zaubern","text":"x"},{"processing":"polish","text":"Also hallo."}]}""",
        )

        val entry = History.list(ctx).single()

        assertEquals(1_791_456_000_123L, entry.createdAt)
        assertEquals(HistorySource.KEYBOARD, entry.source)
        assertEquals(0L, entry.durationMs)
        assertEquals("unbekannte Stufe gilt als aus", Processing.OFF, entry.processing)
        assertEquals(mapOf(Processing.POLISH_PLAIN to HistoryVersion("Also hallo.", 0L)), entry.versions)
    }

    @Test fun ioFehlerWirftNicht() {
        History.dir(ctx).parentFile!!.mkdirs()
        History.dir(ctx).writeText("ich bin eine Datei, kein Ordner")
        assertFalse(record())
    }

    @Test fun fremdeIdsWerdenNichtGelesen() {
        record()
        assertNull(History.get(ctx, "../../shared_prefs/whisperloom"))
        assertNull(History.delete(ctx, "1791456000000-zzzzzzzz"))
    }

    // --- Gleichzeitig -----------------------------------------------------------------------

    @Test fun gleichzeitigeSchreiberVerlierenNichts() {
        History.clock = originalClock // echte Zeit: viele Diktate in derselben Millisekunde
        History.setSize(ctx, 500)

        (0 until 8).map { t -> thread { repeat(10) { record(dictation(raw = "faden $t diktat $it")) } } }.forEach { it.join() }

        val list = History.list(ctx)
        assertEquals(80, list.size)
        assertEquals(80, list.map { it.raw }.toSet().size)
    }

    // --- Fassungen: bearbeiten, neu verarbeiten -------------------------------------------------

    @Test fun bearbeitenDesUrsprungsErzeugtDieFassungBearbeitet() {
        record()
        val id = History.list(ctx).single().id

        val entry = History.edit(ctx, id, Processing.EDITED, "Mein Text.")!!

        assertEquals("Ursprung bleibt", "ähm also ich komme morgen später", entry.raw)
        assertEquals(listOf(Processing.SUMMARIZE, Processing.EDITED), entry.versions.keys.toList())
        assertEquals(HistoryVersion("Mein Text.", entry.versions.getValue(Processing.EDITED).createdAt, edited = true), entry.versions[Processing.EDITED])
        assertEquals(entry, History.get(ctx, id))
    }

    @Test fun bearbeitenEinerFassungErsetztSieUndBehaeltDasModell() {
        record()
        val id = History.list(ctx).single().id

        val version = History.edit(ctx, id, Processing.SUMMARIZE, "Komme später.")!!.versions.getValue(Processing.SUMMARIZE)

        assertEquals("Komme später.", version.text)
        assertTrue(version.edited)
        assertEquals("Claude Sonnet 5.5", version.model)
    }

    @Test fun neuVerarbeitenHaengtAnGleicheVerarbeitungErsetztAnIhremPlatz() {
        ownAccess()
        record(dictation(text = "Komme morgen später.", refinement = Refinement(RefineMode.SUMMARIZE)))
        val id = History.list(ctx).single().id

        val result = History.reprocess(ctx, id, Processing.POLISH_PLAIN)!!.result
        History.reprocess(ctx, id, Processing.SUMMARIZE)

        assertEquals("Neu geglättet.", result.text)
        assertEquals("qwen3:8b", result.model)
        assertEquals("das Netz gilt nicht als bewiesen", false, provenSeen)
        val entry = History.get(ctx, id)!!
        assertEquals("damalige Verarbeitung bleibt", Processing.SUMMARIZE, entry.processing)
        assertEquals(listOf(Processing.SUMMARIZE, Processing.POLISH_PLAIN), entry.versions.keys.toList())
        assertEquals("Neu geglättet.", entry.versions.getValue(Processing.SUMMARIZE).text)
        assertEquals("qwen3:8b", entry.versions.getValue(Processing.POLISH_PLAIN).model)
        assertEquals("ähm also ich komme morgen später", entry.raw)
    }

    @Test fun neuVerarbeitenOhneNetzLaesstDenEintragUnveraendert() {
        ownAccess()
        net = false
        record()
        val before = History.list(ctx).single()

        val result = History.reprocess(ctx, before.id, Processing.POLISH_PLAIN)!!.result

        assertEquals(RefinePlan.MSG_NO_NET, result.skipped)
        assertFalse(result.refined)
        assertEquals(before, History.get(ctx, before.id))
        assertEquals(0, chatRequests.get())
    }

    @Test fun neuVerarbeitenAusSpeichertDieRegelnFassung() {
        record()
        val id = History.list(ctx).single().id

        History.reprocess(ctx, id, Processing.OFF)

        assertEquals(HistoryVersion("Also ich komme morgen später", now - 1), History.get(ctx, id)!!.versions[Processing.OFF])
    }

    @Test fun neuVerarbeitenBelebtGeloeschteEintraegeNichtWieder() {
        ownAccess()
        record()
        val id = History.list(ctx).single().id
        // Der Eintrag verschwindet, waehrend die KI rechnet (Wischen in der Liste).
        beforeAnswer = { assertNotNull(History.delete(ctx, id)) }

        assertNull(History.reprocess(ctx, id, Processing.POLISH_PLAIN))

        assertEquals(1, chatRequests.get())
        assertNull(History.get(ctx, id))
        assertEquals(0, History.count(ctx))
        assertNull("auch direkt gesetzt nicht", History.setVersion(ctx, id, Processing.OFF, HistoryVersion("x", 0L)))
        assertEquals(0, History.count(ctx))
    }

    /** Waehrend die KI rechnet, speichert der Nutzer eine Bearbeitung derselben Fassung: sie bleibt. */
    @Test fun neuVerarbeitenUeberschreibtKeineBearbeitungAusDerRechenzeit() {
        ownAccess()
        record(dictation(text = "Geglättet.", refinement = Refinement(RefineMode.POLISH)))
        val id = History.list(ctx).single().id
        beforeAnswer = { assertNotNull(History.edit(ctx, id, Processing.POLISH_PLAIN, "Von Hand.")) }

        val reprocessed = History.reprocess(ctx, id, Processing.POLISH_PLAIN)!!

        assertTrue("nicht gespeichert, die Bearbeitung bleibt", reprocessed.kept)
        val version = History.get(ctx, id)!!.versions.getValue(Processing.POLISH_PLAIN)
        assertEquals("Von Hand.", version.text)
        assertTrue(version.edited)
        assertEquals(1, chatRequests.get())
    }

    /** Vor dem Start bearbeitet und die Rueckfrage "Ersetzen" bestaetigt: dann wird ersetzt. */
    @Test fun neuVerarbeitenErsetztEineVorDemStartBearbeiteteFassung() {
        ownAccess()
        record(dictation(text = "Geglättet.", refinement = Refinement(RefineMode.POLISH)))
        val id = History.list(ctx).single().id
        History.edit(ctx, id, Processing.POLISH_PLAIN, "Von Hand.")

        assertFalse(History.reprocess(ctx, id, Processing.POLISH_PLAIN)!!.kept)

        val version = History.get(ctx, id)!!.versions.getValue(Processing.POLISH_PLAIN)
        assertEquals("Neu geglättet.", version.text)
        assertFalse(version.edited)
    }

    // --- Loeschen und Rueckgaengig --------------------------------------------------------------

    @Test fun loeschenUndRueckgaengig() {
        record(dictation(raw = "eins"))
        record(dictation(raw = "zwei"))
        val zwei = History.list(ctx).first()

        val gone = History.delete(ctx, zwei.id)!!
        assertEquals(listOf("eins"), History.list(ctx).map { it.raw })

        assertTrue(History.restore(ctx, gone))
        assertEquals(listOf("zwei", "eins"), History.list(ctx).map { it.raw })
        assertEquals(zwei, History.get(ctx, zwei.id))
    }

    @Test fun alleLoeschenLeertDenOrdner() {
        repeat(3) { record() }
        History.clear(ctx)
        assertEquals(0, History.count(ctx))
        assertFalse(History.dir(ctx).exists())
    }
}
