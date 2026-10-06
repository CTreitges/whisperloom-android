package com.chris.whisperloom.whisper

import com.chris.whisperloom.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM-Tests fuer den Modell-Katalog: Werte sind fest verdrahtet, hier wird ihre Form abgesichert. */
class ModelCatalogTest {

    private val hex64 = Regex("[0-9a-f]{64}")

    @Test fun vierModelleInAnzeigeReihenfolgeMitEindeutigenIds() {
        assertEquals(listOf("tiny", "base", "small", "large-v3-turbo"), ModelCatalog.models.map { it.id })
        assertEquals(ModelCatalog.models.size, ModelCatalog.models.map { it.id }.toSet().size)
        assertEquals(ModelCatalog.models.size, ModelCatalog.models.map { it.fileName }.toSet().size)
        assertEquals(ModelCatalog.models.size, ModelCatalog.models.map { it.sha256 }.toSet().size)
    }

    @Test fun pruefsummenGroessenUndUrls() {
        for (m in ModelCatalog.models) {
            assertTrue(m.id, hex64.matches(m.sha256))
            assertTrue(m.id, m.bytes > 0)
            assertTrue(m.id, m.approxRamBytes > m.bytes)
            assertTrue(m.id, m.fileName.startsWith("ggml-") && m.fileName.endsWith(".bin"))
            assertEquals("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/" + m.fileName, m.url)
            assertTrue(m.id, m.label.isNotBlank())
        }
    }

    /** research/whisper-cpp.md §5 — per HEAD auf huggingface.co bestaetigt (x-linked-size/-etag, 2026-09-06). */
    @Test fun werteAusDerHuggingFaceApi() {
        assertEquals(32_152_673L, ModelCatalog.TINY.bytes)
        assertEquals(59_707_625L, ModelCatalog.BASE.bytes)
        assertEquals(190_085_487L, ModelCatalog.SMALL.bytes)
        assertEquals(574_041_195L, ModelCatalog.LARGE_V3_TURBO.bytes)
        assertEquals("ggml-small-q5_1.bin", ModelCatalog.SMALL.fileName)
        assertEquals("ggml-large-v3-turbo-q5_0.bin", ModelCatalog.LARGE_V3_TURBO.fileName)
        assertEquals("ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb", ModelCatalog.SMALL.sha256)
    }

    @Test fun smallIstEmpfehlungUndDefault() {
        assertEquals(listOf(ModelCatalog.SMALL), ModelCatalog.models.filter { it.recommended })
        assertSame(ModelCatalog.SMALL, ModelCatalog.DEFAULT)
        assertEquals(Prefs.DEFAULT_OFFLINE_MODEL, ModelCatalog.DEFAULT.id)
        assertSame(ModelCatalog.SMALL, ModelCatalog.byId("gibt-es-nicht"))
        assertSame(ModelCatalog.SMALL, ModelCatalog.byId(null))
        assertSame(ModelCatalog.BASE, ModelCatalog.find("base"))
        assertNull(ModelCatalog.find("medium"))
    }

    /** Download-Dienst, ModelDownloads und ModelStore kennen nur die ID — sie muss ueber beide Kataloge eindeutig sein. */
    @Test fun idsUndDateienUeberBeideKatalogeEindeutig() {
        val all = ModelCatalog.models + TextModelCatalog.models
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(all.size, all.map { it.fileName }.toSet().size)
        assertEquals(all.size, all.map { it.sha256 }.toSet().size)
        for (m in all) assertSame(m.id, m, findOfflineModel(m.id))
        assertNull(findOfflineModel("medium"))
        assertNull(findOfflineModel(null))
    }

    @Test fun whisperModelleBrauchenKeinenZusatzplatz() {
        for (m in ModelCatalog.models) assertEquals(m.id, 0L, m.extraDiskBytes)
    }

    @Test fun nurLargeBrauchtMindestRam() {
        assertEquals(6L * 1024 * 1024 * 1024, ModelCatalog.LARGE_V3_TURBO.minDeviceRamBytes)
        for (m in ModelCatalog.models - ModelCatalog.LARGE_V3_TURBO) assertEquals(m.id, 0L, m.minDeviceRamBytes)
    }
}
