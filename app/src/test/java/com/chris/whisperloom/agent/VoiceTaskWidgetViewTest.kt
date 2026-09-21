package com.chris.whisperloom.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Was das Widget in welchem Zustand zeigt — inklusive Bildschirmleser-Text. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceTaskWidgetViewTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun status(state: VoiceTaskState, elapsed: Long = 0, message: String = "") =
        VoiceTaskWidgetView.status(ctx, state, elapsed, message)

    private fun cd(state: VoiceTaskState, elapsed: Long = 0, message: String = "") =
        VoiceTaskWidgetView.contentDescription(ctx, state, elapsed, message)

    @Test fun bereitLaedtZumTippenEin() {
        assertEquals(ctx.getString(R.string.widget_ready), status(VoiceTaskState.READY))
    }

    @Test fun waehrendDerAufnahmeLaeuftDieZeit() {
        assertEquals("0:07", status(VoiceTaskState.RECORDING, 7_000))
        assertEquals("1:05", status(VoiceTaskState.RECORDING, 65_000))
    }

    @Test fun derFehlergrundStehtInDerZeile() {
        assertTrue(status(VoiceTaskState.ERROR, message = "Server nicht erreichbar").contains("Server nicht erreichbar"))
    }

    @Test fun ohneGrundBleibtEineAllgemeineMeldung() {
        // Sonst stuende dort "— tippen fuer erneuten Versuch" ohne Subjekt.
        assertTrue(status(VoiceTaskState.ERROR).contains(ctx.getString(R.string.kb_error)))
    }

    @Test fun ausgeschaltetFuehrtInDieEinstellungen() {
        assertEquals(ctx.getString(R.string.widget_off), status(VoiceTaskState.OFF))
    }

    @Test fun jederZustandHatEinenEigenenBildschirmleserText() {
        val texte = VoiceTaskState.entries.map { cd(it, 7_000, "Grund") }
        assertEquals("Kein Zustand darf klingen wie ein anderer", texte.size, texte.toSet().size)
        texte.forEach { assertTrue("leer", it.isNotBlank()) }
    }

    @Test fun derBildschirmleserNenntDieLaufendeZeit() {
        assertTrue(cd(VoiceTaskState.RECORDING, 7_000).contains("0:07"))
    }

    @Test fun dieGanzeFlaecheIstDieTippflaeche() {
        // RemoteViews laesst sich nicht auslesen; geprueft wird, dass der Bau jeden Zustand ueberlebt.
        VoiceTaskState.entries.forEach { VoiceTaskWidgetView.build(ctx, it, 1_000, "Grund") }
    }

    @Test fun zeichnenOhneWidgetAufDemStartbildschirmIstFolgenlos() {
        VoiceTaskWidgetView.push(ctx, VoiceTaskState.WORKING)
    }
}
