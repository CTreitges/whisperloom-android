package com.chris.whisperloom.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

/** JVM-Tests im Temp-Ordner; grosse Dateien als Sparse-Files (nur die Laenge zaehlt). */
class ModelStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private val base = ModelCatalog.BASE
    private lateinit var store: ModelStore

    @Before fun setUp() {
        store = ModelStore(File(tmp.root, "models"))
    }

    private fun sparse(f: File, bytes: Long) {
        f.parentFile!!.mkdirs()
        RandomAccessFile(f, "rw").use { it.setLength(bytes) }
    }

    @Test fun nurVollstaendigeDateiOhneTeildateiZaehlt() {
        assertFalse(store.isInstalled(base))
        sparse(store.file(base), base.bytes - 1)
        assertFalse("falsche Groesse", store.isInstalled(base))
        sparse(store.file(base), base.bytes)
        assertTrue(store.isInstalled(base))
        assertTrue(store.isInstalled("base"))
        store.partFile(base).writeBytes(byteArrayOf(1, 2, 3))
        assertFalse("Teildatei daneben", store.isInstalled(base))
    }

    @Test fun unbekannteIdIstNieInstalliert() {
        assertFalse(store.isInstalled("medium"))
        assertFalse(store.isInstalled(null))
    }

    @Test fun installedListetNurVollstaendige() {
        sparse(store.file(ModelCatalog.TINY), ModelCatalog.TINY.bytes)
        sparse(store.file(ModelCatalog.SMALL), 1234)
        assertEquals(listOf(ModelCatalog.TINY), store.installed())
    }

    @Test fun deleteEntferntModellUndTeildatei() {
        sparse(store.file(base), base.bytes)
        store.partFile(base).writeBytes(byteArrayOf(1))
        assertTrue(store.delete(base))
        assertFalse(store.file(base).exists())
        assertFalse(store.partFile(base).exists())
        assertTrue("idempotent", store.delete(base))
    }

    @Test fun usedBytesSummiertModelleUndTeildateien() {
        assertEquals(0L, store.usedBytes()) // Ordner existiert noch nicht
        sparse(store.file(ModelCatalog.TINY), 1000)
        store.partFile(base).writeBytes(ByteArray(50))
        assertEquals(1050L, store.usedBytes())
    }

    @Test fun textmodellWirdWieEinWhisperModellErkannt() {
        val e2b = TextModelCatalog.GEMMA4_E2B
        assertFalse(store.isInstalled("gemma4_e2b"))
        sparse(store.file(e2b), e2b.bytes)
        assertTrue(store.isInstalled(e2b))
        assertTrue("IDs gelten katalogweit", store.isInstalled("gemma4_e2b"))
        assertEquals(listOf(e2b), store.installed(TextModelCatalog.models))
        assertTrue("whisper-Liste bleibt unter sich", store.installed().isEmpty())
    }

    @Test fun deleteEinesTextmodellsLoeschtAuchDenCache() {
        val e2b = TextModelCatalog.GEMMA4_E2B
        sparse(store.file(e2b), e2b.bytes)
        sparse(File(store.cacheDir(e2b), "gemma-4-E2B-it.litertlm_1_2.xnnpack_cache"), 788)
        val otherCache = File(store.cacheDir(TextModelCatalog.GEMMA4_E4B), "x.xnnpack_cache")
        sparse(otherCache, 10)
        assertTrue(store.delete(e2b))
        assertFalse(store.file(e2b).exists())
        assertFalse(store.cacheDir(e2b).exists())
        assertTrue("Cache des anderen Modells bleibt", otherCache.exists())
        assertTrue("idempotent", store.delete(e2b))
    }

    @Test fun usedBytesZaehltDenCacheMit() {
        val e2b = TextModelCatalog.GEMMA4_E2B
        sparse(store.file(ModelCatalog.TINY), 1000)
        sparse(File(store.cacheDir(e2b), "a.xnnpack_cache"), 700)
        assertEquals(1700L, store.usedBytes())
    }

    @Test fun cacheOrdnerJeModellUnterModels() {
        // Unter models/ = Backup-Ausschluss (backup_rules.xml, data_extraction_rules.xml).
        assertEquals(File(store.dir, "llm-cache/gemma4_e2b"), store.cacheDir(TextModelCatalog.GEMMA4_E2B))
        assertEquals(File(store.dir, "llm-cache/gemma4_e4b"), store.cacheDir(TextModelCatalog.GEMMA4_E4B))
    }

    @Test fun pfadeUndOrdner() {
        assertEquals("ggml-base-q5_1.bin.part", store.partFile(base).name)
        assertEquals(File(store.dir, "ggml-base-q5_1.bin"), store.file(base))
        assertFalse(store.dir.exists())
        assertTrue("ohne Ordner zaehlt der Elternordner", store.freeBytes() > 0)
        assertTrue(store.ensureDir())
        assertTrue(store.dir.isDirectory)
        assertTrue(store.ensureDir())
    }
}
