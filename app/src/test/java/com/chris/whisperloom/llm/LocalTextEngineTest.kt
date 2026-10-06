package com.chris.whisperloom.llm

import android.content.ComponentCallbacks2
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.WhisperLoomApplication
import com.chris.whisperloom.whisper.ModelStore
import com.chris.whisperloom.whisper.TextModelCatalog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Der prozessweite Halter des lokalen Textmodells mit einem Fake statt LiteRT-LM: lazy laden,
 * wiederverwenden, freigeben — und nie mitten in einer Rechnung schliessen (SIGSEGV #3771).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalTextEngineTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val original = LocalTextEngine.factory
    private val e2b = TextModelCatalog.GEMMA4_E2B
    private val e4b = TextModelCatalog.GEMMA4_E4B
    private lateinit var made: MutableList<FakeTextModel>

    @Before fun aufbau() {
        LocalTextEngine.init(ctx)
        made = fakeTextModels { _, user -> "fertig: $user" }
    }

    @After fun abbau() {
        resetTextEngine(original)
        ModelStore(ctx).dir.deleteRecursively()
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > until) fail(what)
            Thread.sleep(10)
        }
    }

    // --- Bereit ---------------------------------------------------------------------------

    @Test fun bereitHeisstInstalliertUndPasstInDenRam() {
        deviceRam(ctx, 8)
        assertFalse("nicht geladen", LocalTextEngine.isReady(ctx, e2b.id))
        installSparse(ctx, e2b)
        assertTrue(LocalTextEngine.isReady(ctx, e2b.id))
        assertFalse("unbekannte ID", LocalTextEngine.isReady(ctx, "gibtsnicht"))
        assertFalse("whisper-Modell ist kein Textmodell", LocalTextEngine.isReady(ctx, "small"))

        ModelStore(ctx).partFile(e2b).writeBytes(byteArrayOf(1))
        assertFalse("Teildatei daneben = Download laeuft", LocalTextEngine.isReady(ctx, e2b.id))
    }

    @Test fun zuWenigRamIstNichtBereit() {
        installSparse(ctx, e2b)
        deviceRam(ctx, 4)
        assertFalse("E2B braucht 6 GB", LocalTextEngine.isReady(ctx, e2b.id))
        // "6-GB-Geraet" meldet ~5,6 GiB — die 10-%-Toleranz aus fitsDevice gilt auch hier.
        deviceRam(ctx, 6)
        assertTrue(LocalTextEngine.isReady(ctx, e2b.id))
    }

    // --- Laden, wiederverwenden, wechseln ---------------------------------------------------

    @Test fun laedtBeimErstenAuftragUndNimmtDasModellDanachWieder() {
        installSparse(ctx, e2b)
        assertFalse(LocalTextEngine.isLoaded)

        assertEquals("fertig: eins", LocalTextEngine.generate(e2b.id, "sys", "eins"))
        assertEquals("fertig: zwei", LocalTextEngine.generate(e2b.id, "sys", "zwei"))

        assertEquals("einmal geladen", 1, made.size)
        assertEquals(listOf("sys" to "eins", "sys" to "zwei"), made[0].calls)
        val store = ModelStore(ctx)
        assertEquals(store.file(e2b), made[0].file)
        assertEquals("Cache je Modell unter models/llm-cache/<id>", File(store.dir, "llm-cache/gemma4_e2b"), made[0].cacheDir)
        assertTrue("Cache-Ordner angelegt", made[0].cacheDir.isDirectory)
    }

    @Test fun modellwechselGibtDasAlteErstFrei() {
        installSparse(ctx, e2b)
        installSparse(ctx, e4b)
        LocalTextEngine.generate(e2b.id, "sys", "eins")
        LocalTextEngine.generate(e4b.id, "sys", "zwei")

        assertEquals(2, made.size)
        assertEquals("E2B geschlossen", 1, made[0].closes)
        assertEquals(0, made[1].closes)
    }

    @Test fun fehlendesModellWirft() {
        try {
            LocalTextEngine.generate(e2b.id, "sys", "eins")
            fail("IllegalStateException erwartet")
        } catch (e: IllegalStateException) {
            assertEquals(0, made.size)
        }
    }

    // --- Freigeben -------------------------------------------------------------------------

    @Test fun freigebenSchliesstUndDerNaechsteAuftragLaedtNeu() {
        installSparse(ctx, e2b)
        LocalTextEngine.generate(e2b.id, "sys", "eins")
        LocalTextEngine.release()

        assertFalse(LocalTextEngine.isLoaded)
        assertEquals(1, made[0].closes)
        LocalTextEngine.generate(e2b.id, "sys", "zwei")
        assertEquals(2, made.size)
    }

    @Test fun nieMittenInDerRechnungSchliessen() {
        installSparse(ctx, e2b)
        LocalTextEngine.generate(e2b.id, "sys", "laden")
        val model = made[0]
        model.holdNext()
        val worker = thread { LocalTextEngine.generate(e2b.id, "sys", "lang") }
        assertTrue(model.started.await(5, TimeUnit.SECONDS))

        LocalTextEngine.release() // Speicherdruck waehrend der Rechnung
        assertEquals("close waehrend generate = SIGSEGV", 0, model.closes)
        assertTrue(LocalTextEngine.isLoaded)

        model.proceed()
        worker.join(5_000)
        LocalTextEngine.release()
        assertEquals(1, model.closes)
    }

    @Test fun nachLeerlaufWirdFreigegeben() {
        installSparse(ctx, e2b)
        LocalTextEngine.idleReleaseMs = 50
        LocalTextEngine.generate(e2b.id, "sys", "eins")
        waitUntil("nach Leerlauf nicht freigegeben") { !LocalTextEngine.isLoaded }
        assertEquals(1, made[0].closes)
    }

    @Test fun speicherdruckGibtFrei() {
        installSparse(ctx, e2b)
        LocalTextEngine.generate(e2b.id, "sys", "eins")
        (ctx as WhisperLoomApplication).onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertFalse(LocalTextEngine.isLoaded)
        assertEquals(1, made[0].closes)
    }

    // --- Vorwaermen ------------------------------------------------------------------------

    @Test fun vorwaermenLaedtImHintergrundOhneZuWarten() {
        installSparse(ctx, e2b)
        val ladenDarf = CountDownLatch(1)
        val fakes = fakeTextModels()
        val fabrik = LocalTextEngine.factory
        LocalTextEngine.factory = { file, cache ->
            check(ladenDarf.await(5, TimeUnit.SECONDS))
            fabrik(file, cache)
        }

        LocalTextEngine.warmUp(e2b.id) // kehrt zurueck, obwohl das Laden noch blockiert
        assertFalse(LocalTextEngine.isLoaded)
        ladenDarf.countDown()
        waitUntil("Vorwaermen hat nicht geladen") { LocalTextEngine.isLoaded }

        LocalTextEngine.generate(e2b.id, "sys", "eins")
        assertEquals("die Rechnung nimmt das vorgewaermte Modell", 1, fakes.size)
        assertEquals(listOf("sys" to "eins"), fakes[0].calls)
    }

    @Test fun vorwaermenOhneModellIstStill() {
        LocalTextEngine.warmUp(e2b.id)
        Thread.sleep(100)
        assertFalse(LocalTextEngine.isLoaded)
        assertEquals(0, made.size)
    }

    // --- Zeitgrenzen (Review c1) -------------------------------------------------------------

    @Test fun zeitbudgetWaechstMitDerLaengeUndIstGedeckelt() {
        assertEquals(45_000L, LocalTextEngine.budgetMs(0))
        assertEquals(105_000L, LocalTextEngine.budgetMs(100))
        assertEquals(10 * 60_000L, LocalTextEngine.budgetMs(5_000))
    }

    @Test fun nachDemZeitbudgetBrichtDerWaechterAb() {
        installSparse(ctx, e2b)
        LocalTextEngine.generate(e2b.id, "sys", "laden")
        val model = made[0]
        model.holdNext() // rechnet, bis abgebrochen wird
        LocalTextEngine.budget = { 100 }
        LocalTextEngine.watchTickMs = 20

        val start = System.currentTimeMillis()
        try {
            LocalTextEngine.generate(e2b.id, "sys", "lang")
            fail("Abbruch erwartet")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!, e.message!!.contains("CANCELLED"))
        }
        assertTrue("cancelProcess nach dem Budget", model.cancels >= 1)
        assertTrue("nicht erst nach dem Fake-Timeout", System.currentTimeMillis() - start < 3_000)
        assertEquals("kein Haenger: das Modell rechnet weiter", "fertig: weiter", LocalTextEngine.generate(e2b.id, "sys", "weiter"))
        assertEquals(1, made.size)
    }

    @Test fun einHaengendesModellHaeltDieFolgendenAuftraegeNichtAuf() {
        installSparse(ctx, e2b)
        LocalTextEngine.generate(e2b.id, "sys", "laden")
        val model = made[0]
        model.ignoresCancel = true // cancelProcess wirkt nicht (LiteRT-LM #2202)
        model.holdNext()
        LocalTextEngine.budget = { 100 }
        LocalTextEngine.hungGraceMs = 100
        LocalTextEngine.watchTickMs = 20
        LocalTextEngine.lockWaitMs = 4_000
        val haengt = thread { runCatching { LocalTextEngine.generate(e2b.id, "sys", "haengt") } }
        assertTrue(model.started.await(5, TimeUnit.SECONDS))

        // Der naechste Auftrag wartet nicht bis zum Ende der Lock-Wartezeit, sondern gibt auf, sobald das Modell haengt.
        val start = System.currentTimeMillis()
        try {
            LocalTextEngine.generate(e2b.id, "sys", "danach")
            fail("IllegalStateException erwartet")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!, e.message!!.contains("haengt"))
        }
        assertTrue("gab erst nach der Lock-Wartezeit auf", System.currentTimeMillis() - start < 3_000)
        assertTrue(model.cancels >= 1)
        // ... und weitere sofort, ohne am Lock zu warten.
        val sofort = System.currentTimeMillis()
        assertTrue(runCatching { LocalTextEngine.generate(e2b.id, "sys", "noch eins") }.exceptionOrNull() is IllegalStateException)
        assertTrue(System.currentTimeMillis() - sofort < 500)

        // Kehrt die Rechnung doch zurueck, wird das Modell neu aufgebaut (nie close mitten in der Rechnung).
        assertEquals(0, model.closes)
        model.proceed()
        haengt.join(5_000)
        assertEquals(1, model.closes)
        assertEquals("fertig: wieder da", LocalTextEngine.generate(e2b.id, "sys", "wieder da"))
        assertEquals("neu geladen", 2, made.size)
    }

    @Test fun aufDenLockWirdNichtEwigGewartet() {
        installSparse(ctx, e2b)
        LocalTextEngine.generate(e2b.id, "sys", "laden")
        val model = made[0]
        model.holdNext()
        LocalTextEngine.lockWaitMs = 100
        LocalTextEngine.watchTickMs = 20
        val lang = thread { LocalTextEngine.generate(e2b.id, "sys", "lang") }
        assertTrue(model.started.await(5, TimeUnit.SECONDS))

        try {
            LocalTextEngine.generate(e2b.id, "sys", "wartet")
            fail("IllegalStateException erwartet")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!, e.message!!.contains("belegt"))
        }

        // Ein abgebrochener Auftrag gibt das Warten sofort auf.
        LocalTextEngine.lockWaitMs = 4_000
        var weg = false
        var aufgegeben = false
        val wartet = thread {
            try {
                LocalTextEngine.generate(e2b.id, "sys", "abgeloest") { weg }
            } catch (e: CancellationException) {
                aufgegeben = true
            }
        }
        Thread.sleep(100)
        weg = true
        wartet.join(2_000)
        assertTrue(aufgegeben)

        model.proceed()
        lang.join(5_000)
        assertEquals(listOf("sys" to "laden", "sys" to "lang"), model.calls)
    }

    // --- Abbrechen -------------------------------------------------------------------------

    @Test fun abbruchTrifftNurDenAbgebrochenenAuftrag() {
        installSparse(ctx, e2b)
        LocalTextEngine.generate(e2b.id, "sys", "laden")
        val model = made[0]
        model.holdNext()
        var abgebrochen = false
        val worker = thread { LocalTextEngine.generate(e2b.id, "sys", "lang") { abgebrochen } }
        assertTrue(model.started.await(5, TimeUnit.SECONDS))

        LocalTextEngine.cancel() // fremder Tipp: dieser Auftrag ist nicht abgebrochen
        assertEquals(0, model.cancels)

        abgebrochen = true
        LocalTextEngine.cancel()
        assertEquals(1, model.cancels)
        worker.join(5_000)
        assertFalse(worker.isAlive)
        assertEquals("cancel ist kein close", 0, model.closes)
    }

    /** Review c4: der Tipp faellt zwischen die Pruefung des Halters und den Rechenstart des Modells. */
    @Test fun abbruchKurzVorDemRechenstartGehtNichtVerloren() {
        installSparse(ctx, e2b)
        LocalTextEngine.generate(e2b.id, "sys", "laden")
        val model = made[0]
        var abgebrochen = false
        model.beforeEnter = {
            abgebrochen = true
            LocalTextEngine.cancel()
        }

        try {
            LocalTextEngine.generate(e2b.id, "sys", "lang") { abgebrochen }
            fail("CancellationException erwartet")
        } catch (e: CancellationException) {
            assertEquals("die verworfene Rechnung laeuft nicht", listOf("sys" to "laden"), model.calls)
        }
    }

    @Test fun abgebrochenVorDerRechnungRechnetGarNicht() {
        installSparse(ctx, e2b)
        try {
            LocalTextEngine.generate(e2b.id, "sys", "eins") { true }
            fail("CancellationException erwartet")
        } catch (e: CancellationException) {
            assertEquals(emptyList<Pair<String, String>>(), made[0].calls)
        }
    }
}
