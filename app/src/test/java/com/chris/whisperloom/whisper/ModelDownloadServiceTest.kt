package com.chris.whisperloom.whisper

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.llm.installSparse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric-Tests fuer das Dienst-Geruest (Intents, Sofort-Stopp, Fehlertexte). Der eigentliche
 * Download ist in ModelDownloaderTest abgedeckt; ein echter Katalog-Download braucht Netz und Geraet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelDownloadServiceTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun startIntentTraegtAktionUndModellId() {
        val i = ModelDownloadService.startIntent(ctx, "small")
        assertEquals(ModelDownloadService.ACTION_START, i.action)
        assertEquals("small", i.getStringExtra(ModelDownloadService.EXTRA_MODEL_ID))
        assertEquals(ModelDownloadService::class.java.name, i.component?.className)
        assertEquals(ModelDownloadService.ACTION_CANCEL, ModelDownloadService.cancelIntent(ctx).action)
    }

    @Test fun unbekanntesModellStopptDenDienstSofort() {
        val intent = ModelDownloadService.startIntent(ctx, "medium")
        val service = Robolectric.buildService(ModelDownloadService::class.java, intent).create().startCommand(0, 1).get()
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertEquals(DownloadState.Idle, ModelDownloads.stateOf("medium"))
    }

    @Test fun abbruchOhneLaufendenDownloadStopptDenDienst() {
        val intent = ModelDownloadService.cancelIntent(ctx)
        val service = Robolectric.buildService(ModelDownloadService::class.java, intent).create().startCommand(0, 1).get()
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    /** Der Nutzer hat es gezielt geladen: E4B fertig, waehrend E2B geladen und gewaehlt ist -> E4B. */
    @Test fun einFertigesTextmodellWirdImmerGewaehlt() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = Prefs(ctx)
        val e2b = TextModelCatalog.GEMMA4_E2B
        val e4b = TextModelCatalog.GEMMA4_E4B
        try {
            assertEquals("ab Werk E2B", e2b.id, prefs.localLlmModel)
            installSparse(ctx, e2b)
            installSparse(ctx, e4b)
            ModelDownloadService.selectTextModel(ctx, e4b)
            assertEquals("auch wenn das gewaehlte E2B geladen ist", e4b.id, prefs.localLlmModel)

            ModelDownloadService.selectTextModel(ctx, e2b)
            assertEquals(e2b.id, prefs.localLlmModel)

            ModelDownloadService.selectTextModel(ctx, ModelCatalog.SMALL)
            assertEquals("ein whisper-Modell waehlt kein Textmodell", e2b.id, prefs.localLlmModel)
        } finally {
            ModelStore(ctx).dir.deleteRecursively()
        }
    }

    @Test fun fehlertexteAusDenRessourcen() {
        fun text(kind: DownloadException.Kind, msg: String = "x") =
            ModelDownloadService.messageFor(ctx, DownloadException(msg, retryable = false, kind = kind))
        assertEquals(ctx.getString(R.string.err_download_net), text(DownloadException.Kind.NETWORK))
        assertEquals(ctx.getString(R.string.err_model_checksum), text(DownloadException.Kind.CHECKSUM))
        assertEquals(ctx.getString(R.string.err_storage_full), text(DownloadException.Kind.STORAGE))
        assertEquals("HTTP 404", text(DownloadException.Kind.OTHER, "HTTP 404"))
    }
}
