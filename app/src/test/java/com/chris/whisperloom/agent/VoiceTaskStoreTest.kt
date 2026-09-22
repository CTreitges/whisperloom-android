package com.chris.whisperloom.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Der eine offene Auftrag, ueber Prozessgrenzen hinweg. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceTaskStoreTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: VoiceTaskStore

    @Before fun leeren() {
        ctx.getSharedPreferences(VoiceTaskStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        store = VoiceTaskStore(ctx)
        store.clear()
    }

    private fun ton(n: Int = 800) = FloatArray(n) { i -> if (i % 2 == 0) 0.4f else -0.4f }

    @Test fun frischIstNichtsZuTun() {
        assertFalse(store.hasWork)
        assertEquals(VoiceTaskState.READY, store.state)
        assertEquals("", store.requestId)
    }

    @Test fun einNeuerAuftragLegtAudioUndKennungAn() {
        val id = store.begin(ton(), 4200, "2026-09-21T20:00:00Z")
        assertTrue(store.hasWork)
        assertEquals(id, store.requestId)
        assertEquals(4200, store.durationMs)
        assertEquals("2026-09-21T20:00:00Z", store.recordedAt)
        assertTrue(store.audioFile.isFile)
    }

    @Test fun dieAufnahmeKommtUnveraendertZurueck() {
        store.begin(ton(200), 1000, "")
        val zurueck = store.loadSamples()
        assertEquals(200, zurueck.size)
        assertEquals(0.4f, zurueck[0], 1e-4f)
        assertEquals(-0.4f, zurueck[1], 1e-4f)
    }

    @Test fun dasAudioLiegtInFilesDirNichtImCache() {
        store.begin(ton(), 1000, "")
        // cacheDir raeumt das System bei Platzmangel weg — mitten im Wiederholungsversuch.
        assertEquals(ctx.filesDir, store.audioFile.parentFile)
    }

    @Test fun jederAuftragBekommtEineEigeneKennung() {
        val a = store.begin(ton(), 1000, "")
        val b = store.begin(ton(), 1000, "")
        assertNotEquals(a, b)
    }

    @Test fun einNeuerAuftragVerwirftDenAltenText() {
        store.begin(ton(), 1000, "")
        store.text = "alter Text"
        store.begin(ton(), 1000, "")
        assertEquals("", store.text)
    }

    @Test fun erkannterTextAlleinIstAuchArbeit() {
        store.begin(ton(), 1000, "")
        store.text = "Kauf Milch"
        store.audioFile.delete()
        assertTrue("Der Text reicht zum erneuten Senden — das Audio wird dann nicht mehr gebraucht", store.hasWork)
    }

    @Test fun aufraeumenLoeschtAudioUndKennung() {
        store.begin(ton(), 1000, "")
        store.text = "Kauf Milch"
        store.clear()
        assertFalse(store.hasWork)
        assertFalse(store.audioFile.isFile)
        assertEquals("", store.requestId)
        assertEquals("", store.text)
    }

    @Test fun zustandUndMeldungUeberlebenEineNeueInstanz() {
        store.state = VoiceTaskState.ERROR
        store.message = "Server nicht erreichbar"
        val zweite = VoiceTaskStore(ctx)
        assertEquals(VoiceTaskState.ERROR, zweite.state)
        assertEquals("Server nicht erreichbar", zweite.message)
    }

    @Test fun einUnbekannterGespeicherterZustandKipptNicht() {
        ctx.getSharedPreferences(VoiceTaskStore.FILE, Context.MODE_PRIVATE)
            .edit().putString("state", "QUATSCH").commit()
        assertEquals(VoiceTaskState.READY, VoiceTaskStore(ctx).state)
    }

    @Test fun ohneAudioDateiKommtEinLeeresFeldZurueck() {
        assertEquals(0, store.loadSamples().size)
    }

    @Test fun derHinweisZurTextverbesserungGehoertZumAuftrag() {
        store.begin(ton(), 1000, "")
        store.refineSkipped = "API-Fehler 429"
        assertEquals("API-Fehler 429", VoiceTaskStore(ctx).refineSkipped)
        store.clear()
        assertEquals("Mit dem Auftrag ist auch sein Hinweis erledigt", "", store.refineSkipped)
    }

    @Test fun einNeuerAuftragStartetOhneAltenHinweis() {
        store.begin(ton(), 1000, "")
        store.refineSkipped = "alt"
        store.begin(ton(), 1000, "")
        assertEquals("", store.refineSkipped)
    }
}
