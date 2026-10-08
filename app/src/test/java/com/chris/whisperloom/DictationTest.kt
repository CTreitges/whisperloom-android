package com.chris.whisperloom

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.api.NetworkCheck
import com.chris.whisperloom.api.ProviderCatalog
import com.chris.whisperloom.api.WavUpload
import com.chris.whisperloom.llm.LocalTextEngine
import com.chris.whisperloom.llm.deviceRam
import com.chris.whisperloom.llm.fakeTextModels
import com.chris.whisperloom.llm.installSparse
import com.chris.whisperloom.llm.resetTextEngine
import com.chris.whisperloom.whisper.ModelCatalog
import com.chris.whisperloom.whisper.ModelStore
import com.chris.whisperloom.whisper.TextModelCatalog
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * Das Ergebnisobjekt eines Diktats ([Dictation], [Refined]) und die oeffentliche Funktion
 * [TranscriptionEngine.refine], die Diktat und Verlauf gemeinsam nutzen: gleiche Eingabe, gleiche
 * Ausgabe. Erkennung und Textmodell als Fake, der Online-Zugang als lokaler HttpServer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DictationTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs
    private lateinit var server: HttpServer

    private val originalBackend = TranscriptionEngine.backendFactory
    private val originalNetwork = RefinePlan.networkCheck
    private val originalModel = LocalTextEngine.factory

    private val chatRequests = AtomicInteger()
    @Volatile private var chatStatus = 200
    @Volatile private var net = true

    /** Eine Sekunde Ton. */
    private val speech = FloatArray(AudioUtils.SAMPLE_RATE) { 0.3f }

    @Before fun aufbau() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { ex ->
            chatRequests.incrementAndGet()
            ex.requestBody.readBytes()
            val out = (if (chatStatus == 200) """{"choices":[{"message":{"content":"Online verbessert."}}]}""" else "{}").toByteArray()
            ex.sendResponseHeaders(chatStatus, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.start()
        TranscriptionEngine.backendFactory = { _, _ ->
            object : TranscriptionBackend {
                override val label = "Offline · Small"
                override fun transcribe(upload: WavUpload, language: String) = TranscriptResult("also ähm hallo welt", "de")
            }
        }
        RefinePlan.networkCheck = { NetworkCheck { _, _ -> net } }
        LocalTextEngine.init(ctx)
        fakeTextModels { _, _ -> "Lokal verbessert." }
        deviceRam(ctx, 8)

        installSparse(ctx, ModelCatalog.SMALL)
        prefs.engine = Engine.OFFLINE
        prefs.language = "auto"
        prefs.refineMode = RefineMode.POLISH
    }

    @After fun abbau() {
        server.stop(0)
        TranscriptionEngine.backendFactory = originalBackend
        RefinePlan.networkCheck = originalNetwork
        resetTextEngine(originalModel)
        ModelStore(ctx).dir.deleteRecursively()
    }

    /** Eigener Online-Zugang: der lokale HttpServer, Modell ohne Katalog-Eintrag. */
    private fun ownAccess(rule: OfflineRefineRule) {
        prefs.offlineRefine = rule
        prefs.llmProviderId = "custom"
        prefs.llmUrl = "http://127.0.0.1:${server.address.port}/v1"
        prefs.llmModel = "qwen3:8b"
    }

    @Test fun mitKiNenntRohtextSpracheVerarbeitungDauerUndModell() {
        ownAccess(OfflineRefineRule.SKIP)
        prefs.setPolishCleanupFor(RefineWay.DICTATION, PolishCleanup.CLEAN)

        val d = TranscriptionEngine.transcribe(ctx, speech)

        assertEquals("also ähm hallo welt", d.raw)
        assertEquals("bei auto die erkannte", "de", d.language)
        assertEquals(1000L, d.durationMs)
        assertEquals("Glaetten samt Bereinigung", Refinement(RefineMode.POLISH, smartFillers = true), d.result.refinement)
        assertEquals("Online verbessert.", d.text)
        assertTrue(d.result.refined)
        assertEquals("freie ID ohne Katalog-Eintrag", "qwen3:8b", d.result.model)
        assertNull(d.result.skipped)
        assertNull(d.result.note)
    }

    @Test fun ohneKiNenntDenGrundUndKeinModell() {
        ownAccess(OfflineRefineRule.SKIP)
        net = false

        val d = TranscriptionEngine.transcribe(ctx, speech)

        assertEquals("Also hallo welt", d.text)
        assertFalse(d.result.refined)
        assertNull(d.result.model)
        assertEquals(RefinePlan.MSG_NO_NET, d.result.skipped)
        assertEquals(0, chatRequests.get())
    }

    @Test fun stufeAusIstOhneKiOhneGrund() {
        prefs.refineMode = RefineMode.OFF

        val d = TranscriptionEngine.transcribe(ctx, speech)

        assertEquals(Refined(Refinement.OFF, "Also hallo welt"), d.result)
    }

    @Test fun onlineGescheitertLokalVerbessertNenntDasLokaleModell() {
        ownAccess(OfflineRefineRule.ONLINE_LOCAL)
        installSparse(ctx, TextModelCatalog.GEMMA4_E2B)
        chatStatus = 500

        val d = TranscriptionEngine.transcribe(ctx, speech)

        assertEquals("Lokal verbessert.", d.text)
        assertEquals(TextModelCatalog.GEMMA4_E2B.label, d.result.model)
        assertEquals(RefinePlan.MSG_ONLINE_FAILED_LOCAL, d.result.note)
        assertNull(d.result.skipped)
    }

    @Test fun nichtsErkanntIstLeerOhneKi() {
        TranscriptionEngine.backendFactory = { _, _ ->
            object : TranscriptionBackend {
                override val label = "Offline · Small"
                override fun transcribe(upload: WavUpload, language: String) = TranscriptResult("  ")
            }
        }
        ownAccess(OfflineRefineRule.SKIP)

        val d = TranscriptionEngine.transcribe(ctx, speech)

        assertEquals("", d.raw)
        assertEquals("", d.text)
        assertFalse(d.result.refined)
        assertEquals("keine Anfrage fuer nichts", 0, chatRequests.get())
    }

    /** Der Verlauf rechnet aus dem gespeicherten Rohtext dasselbe wie das Diktat — keine zweite Logik. */
    @Test fun refineAusDemRohtextGleichtDemDiktat() {
        ownAccess(OfflineRefineRule.SKIP)
        val faelle = listOf(RefineMode.OFF, RefineMode.POLISH, RefineMode.SUMMARIZE)
        for (stage in faelle) {
            prefs.refineMode = stage
            val d = TranscriptionEngine.transcribe(ctx, speech)
            val r = TranscriptionEngine.refine(ctx, d.raw, d.language, d.result.refinement, networkProven = false)
            assertEquals(stage.name, d.result, r)
        }
        net = false
        val d = TranscriptionEngine.transcribe(ctx, speech)
        val r = TranscriptionEngine.refine(ctx, d.raw, d.language, d.result.refinement, networkProven = false)
        assertEquals("auch ohne KI mit Grund", d.result, r)
    }

    /**
     * Captive-Portal: aktives, aber nicht validiertes Netz. Nach einer Online-Erkennung reicht es
     * (sie hat es gerade getragen); beim Neu-Verarbeiten aus dem Verlauf geht keine Anfrage raus.
     * Mit der echten Netzpruefung, nicht dem Fake.
     */
    @Test fun ohneNetzNachweisVerlangtDasNeuVerarbeitenEinValidiertesNetz() {
        RefinePlan.networkCheck = originalNetwork
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        shadowOf(cm).setNetworkCapabilities(
            cm.activeNetwork,
            ShadowNetworkCapabilities.newInstance().also { shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) },
        )
        prefs.engine = Engine.ONLINE
        prefs.llmProviderId = ProviderCatalog.OPENAI_ID // api.openai.com: nicht im eigenen Netz
        prefs.llmKey = "sk-test"
        val polish = Refinement(RefineMode.POLISH)

        val r = TranscriptionEngine.refine(ctx, "also hallo welt", "de", polish, networkProven = false)

        assertEquals("Also hallo welt", r.text)
        assertEquals(RefinePlan.MSG_NO_NET, r.skipped)
        assertEquals(RefineRoute.Online(fallbackLocal = false), RefinePlan.of(ctx, prefs, RefineMode.POLISH, networkProven = true).route)
    }

    @Test fun toStringEnthaeltKeinenText() {
        ownAccess(OfflineRefineRule.SKIP)
        val s = TranscriptionEngine.transcribe(ctx, speech).toString()
        assertFalse(s, s.contains("hallo", ignoreCase = true))
        assertFalse(s, s.contains("verbessert"))
    }
}
