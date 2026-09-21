package com.chris.whisperloom.agent

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Was das Widget in welchem Zustand zeigt — inklusive Bildschirmleser-Text. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceTaskWidgetViewTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before fun leeren() {
        ctx.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(VoiceTaskStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

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

    /** Baut die RemoteViews und haengt sie an eine echte View — nur so ist der Klick pruefbar. */
    private fun gezeichnet(state: VoiceTaskState, elapsed: Long = 0, message: String = ""): View {
        val parent = FrameLayout(ctx)
        return VoiceTaskWidgetView.build(ctx, state, elapsed, message).apply(ctx, parent)
    }

    @Test fun jederZustandHatEineTippflaecheAusserWaehrendGearbeitetWird() {
        VoiceTaskState.entries.forEach { state ->
            val wurzel = gezeichnet(state)
            assertTrue("$state ohne Tippflaeche", wurzel.hasOnClickListeners())
        }
    }

    @Test fun dieBeschreibungHaengtAnDerGanzenFlaeche() {
        val wurzel = gezeichnet(VoiceTaskState.RECORDING, 7_000)
        assertEquals(cd(VoiceTaskState.RECORDING, 7_000), wurzel.contentDescription)
        // Das Symbol darf nicht mitgelesen werden, sonst sagt TalkBack alles doppelt.
        val symbol = wurzel.findViewById<View>(R.id.widget_icon)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, symbol.importantForAccessibility)
    }

    @Test fun dieStatuszeileZeigtWasStatusSagt() {
        val wurzel = gezeichnet(VoiceTaskState.ERROR, message = "Server nicht erreichbar")
        val zeile = wurzel.findViewById<TextView>(R.id.widget_status)
        assertEquals(status(VoiceTaskState.ERROR, message = "Server nicht erreichbar"), zeile.text.toString())
    }

    // --- Ein echtes Widget, ueber onUpdate des Providers ---------------------

    private fun zeileEinesEchtenWidgets(): String {
        val manager = AppWidgetManager.getInstance(ctx)
        val id = shadowOf(manager).createWidget(VoiceTaskWidget::class.java, R.layout.widget_task)
        return shadowOf(manager).getViewFor(id).findViewById<TextView>(R.id.widget_status).text.toString()
    }

    @Test fun einNeuesWidgetSagtDassNochNichtsEingerichtetIst() {
        assertEquals(ctx.getString(R.string.widget_off), zeileEinesEchtenWidgets())
    }

    @Test fun einNeuesWidgetIstBereitSobaldAllesStimmt() {
        Prefs(ctx).apply {
            agentEnabled = true
            agentUrl = "https://bridge.example.de"
            agentToken = "geheim"
        }
        shadowOf(ctx as android.app.Application).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        assertEquals(ctx.getString(R.string.widget_ready), zeileEinesEchtenWidgets())
    }

    @Test fun ohneMikrofonSagtDasWidgetGenauDas() {
        Prefs(ctx).apply {
            agentEnabled = true
            agentUrl = "https://bridge.example.de"
            agentToken = "geheim"
        }
        shadowOf(ctx as android.app.Application).denyPermissions(android.Manifest.permission.RECORD_AUDIO)
        assertEquals(ctx.getString(R.string.widget_no_mic), zeileEinesEchtenWidgets())
    }
}
