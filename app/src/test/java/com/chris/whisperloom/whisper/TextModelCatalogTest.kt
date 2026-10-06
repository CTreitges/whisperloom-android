package com.chris.whisperloom.whisper

import com.chris.whisperloom.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM-Tests fuer die lokalen Textmodelle: gepinnte Werte (Spec §1) und die RAM-Grenzen. */
class TextModelCatalogTest {

    private val gib = 1024L * 1024 * 1024
    private val e2b = TextModelCatalog.GEMMA4_E2B
    private val e4b = TextModelCatalog.GEMMA4_E4B

    @Test fun zweiModelleInAnzeigeReihenfolge() {
        assertEquals(listOf("gemma4_e2b", "gemma4_e4b"), TextModelCatalog.models.map { it.id })
        assertEquals("Gemma 4 E2B", e2b.label)
        assertEquals("Gemma 4 E4B", e4b.label)
    }

    /** Per HEAD gegen die gepinnte Revision bestaetigt (x-linked-size/-etag, 2026-10-06). */
    @Test fun gepinnteUrlsGroessenUndPruefsummen() {
        assertEquals(
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/" +
                "b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/gemma-4-E2B-it.litertlm",
            e2b.url,
        )
        assertEquals(
            "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/" +
                "2eee7ac325f20eb8c9ac1d0e972f7c84663062da/gemma-4-E4B-it.litertlm",
            e4b.url,
        )
        assertEquals("gemma-4-E2B-it.litertlm", e2b.fileName)
        assertEquals("gemma-4-E4B-it.litertlm", e4b.fileName)
        assertEquals(2_588_147_712L, e2b.bytes)
        assertEquals(3_659_530_240L, e4b.bytes)
        assertEquals("181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c", e2b.sha256)
        assertEquals("0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0", e4b.sha256)
        for (m in TextModelCatalog.models) {
            // Nie resolve/main: dort werden die Dateien ersetzt, der feste SHA-256 schluege dann fehl.
            assertFalse(m.id, m.url.contains("/resolve/main/"))
            assertTrue(m.id, m.url.endsWith("/" + m.fileName))
        }
    }

    @Test fun ramUndPlatzFuerDenCache() {
        assertEquals(6 * gib, e2b.minDeviceRamBytes)
        assertEquals(8 * gib, e4b.minDeviceRamBytes)
        assertEquals(1_800_000_000L, e2b.approxRamBytes)
        assertEquals(3_300_000_000L, e4b.approxRamBytes)
        assertEquals(800_000_000L, e2b.extraDiskBytes)
        assertEquals(1_100_000_000L, e4b.extraDiskBytes)
    }

    @Test fun e2bAbSechsGbMitToleranz() {
        assertTrue(OfflineSupport.fitsDevice(6 * gib, e2b))
        assertTrue("5,6 GiB gemeldet auf einem 6-GB-Geraet", OfflineSupport.fitsDevice(56 * gib / 10, e2b))
        assertTrue("Grenze: 90 % von 6 GiB", OfflineSupport.fitsDevice(6 * gib / 10 * 9, e2b))
        assertFalse(OfflineSupport.fitsDevice(6 * gib / 10 * 9 - 1, e2b))
        assertFalse(OfflineSupport.fitsDevice(4 * gib, e2b))
    }

    @Test fun e4bAbAchtGbMitToleranz() {
        assertTrue(OfflineSupport.fitsDevice(8 * gib, e4b))
        assertTrue("7,5 GiB gemeldet auf einem 8-GB-Geraet", OfflineSupport.fitsDevice(75 * gib / 10, e4b))
        assertTrue("Grenze: 90 % von 8 GiB", OfflineSupport.fitsDevice(8 * gib / 10 * 9, e4b))
        assertFalse(OfflineSupport.fitsDevice(8 * gib / 10 * 9 - 1, e4b))
        assertFalse("6-GB-Geraet: nur E2B", OfflineSupport.fitsDevice(6 * gib, e4b))
    }

    @Test fun e2bIstEmpfehlungUndDefault() {
        assertEquals(listOf(e2b), TextModelCatalog.models.filter { it.recommended })
        assertSame(e2b, TextModelCatalog.DEFAULT)
        assertEquals(Prefs.DEFAULT_LOCAL_LLM_MODEL, TextModelCatalog.DEFAULT.id)
        assertSame(e4b, TextModelCatalog.find("gemma4_e4b"))
        assertNull(TextModelCatalog.find("small"))
        assertSame(e2b, TextModelCatalog.byId("gibt-es-nicht"))
        assertSame(e2b, TextModelCatalog.byId(null))
    }
}
