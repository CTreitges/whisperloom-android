package com.chris.whisperloom.ui.nav

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.whisper.ModelCatalog
import com.chris.whisperloom.whisper.ModelStore
import com.chris.whisperloom.whisper.TextModelCatalog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile

/** Robolectric: der Systemstatus trennt whisper- und Textmodelle, der Platz zaehlt beide. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemStatusTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val store = ModelStore(ctx)

    private fun sparse(f: File, bytes: Long) {
        f.parentFile!!.mkdirs()
        RandomAccessFile(f, "rw").use { it.setLength(bytes) }
    }

    @After fun aufraeumen() {
        store.dir.deleteRecursively()
    }

    @Test fun whisperUndTextmodelleGetrennt() {
        val e2b = TextModelCatalog.GEMMA4_E2B
        sparse(store.file(ModelCatalog.BASE), ModelCatalog.BASE.bytes)
        sparse(store.file(e2b), e2b.bytes)
        sparse(File(store.cacheDir(e2b), "a.xnnpack_cache"), 1_000)

        val status = SystemStatus.read(ctx)

        assertEquals(setOf("base"), status.installedModels)
        assertEquals(setOf("gemma4_e2b"), status.installedTextModels)
        assertEquals(ModelCatalog.BASE.bytes + e2b.bytes + 1_000, status.modelsUsedBytes)
    }
}
