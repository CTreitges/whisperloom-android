package com.chris.whisperloom.agent

import com.chris.whisperloom.api.ApiHttpException
import com.chris.whisperloom.api.ApiNetworkException
import com.chris.whisperloom.api.ApiNotConfiguredException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/** Der Weg vom Ton zum abgeschickten Auftrag — ohne Android, ohne Netz, ohne WorkManager. */
class VoiceTaskPipelineTest {

    private var transkribiert = 0
    private var gesendet = mutableListOf<String>()

    private fun pipeline(
        samples: () -> FloatArray = { FloatArray(100) },
        transcribe: (FloatArray) -> String = { "Kauf Milch" },
        send: (String) -> Unit = { gesendet += it },
    ) = VoiceTaskPipeline(
        samples = samples,
        transcribe = { s -> transkribiert++; transcribe(s) },
        send = send,
    )

    @Test fun glatterDurchlaufSendetDenErkanntenText() {
        val outcome = pipeline().run()
        assertEquals(TaskOutcome.Sent("Kauf Milch"), outcome)
        assertEquals(listOf("Kauf Milch"), gesendet)
        assertEquals(1, transkribiert)
    }

    @Test fun bekannterTextWirdNichtNochmalTranskribiert() {
        // Das ist der teure Schritt: ein Wiederholungsversuch darf ihn nicht erneut bezahlen.
        val outcome = pipeline().run(cachedText = "Schon erkannt")
        assertEquals(TaskOutcome.Sent("Schon erkannt"), outcome)
        assertEquals(0, transkribiert)
        assertEquals(listOf("Schon erkannt"), gesendet)
    }

    @Test fun leerErkanntGiltAlsEndgueltigerFehlschlag() {
        val outcome = pipeline(transcribe = { "   " }).run()
        assertEquals(TaskOutcome.Failed(VoiceTaskPipeline.MSG_EMPTY, null), outcome)
        assertTrue("Ohne Text darf nichts rausgehen", gesendet.isEmpty())
    }

    @Test fun netzproblemBeimErkennenIstWiederholbar() {
        val outcome = pipeline(transcribe = { throw ApiNetworkException(SocketTimeoutException("read")) }).run()
        assertTrue(outcome is TaskOutcome.Retry)
        assertEquals(null, (outcome as TaskOutcome.Retry).text)
    }

    @Test fun fehlenderZugangIstNichtWiederholbar() {
        val outcome = pipeline(transcribe = { throw ApiNotConfiguredException() }).run()
        assertTrue("Ein fehlender Zugang bleibt beim zehnten Versuch fehlend", outcome is TaskOutcome.Failed)
    }

    @Test fun kaputtePfadeSindNichtWiederholbar() {
        val outcome = pipeline(samples = { throw IOException("Datei weg") }).run()
        assertTrue(outcome is TaskOutcome.Failed)
    }

    @Test fun serverfehlerBeimSendenIstWiederholbarUndHaeltDenText() {
        val outcome = pipeline(send = { throw ApiHttpException(503, "Hermes nicht erreichbar") }).run()
        assertTrue(outcome is TaskOutcome.Retry)
        // Genau darum geht es: der Text ueberlebt, damit der naechste Versuch billig ist.
        assertEquals("Kauf Milch", (outcome as TaskOutcome.Retry).text)
        assertEquals(1, transkribiert)
    }

    @Test fun falschesTokenIstEndgueltigUndHaeltTrotzdemDenText() {
        val outcome = pipeline(send = { throw ApiHttpException(401, "Token stimmt nicht") }).run()
        assertTrue(outcome is TaskOutcome.Failed)
        assertEquals("Kauf Milch", (outcome as TaskOutcome.Failed).text)
    }

    @Test fun rateLimitUndServerfehlerGeltenAlsVoruebergehend() {
        listOf(408, 429, 500, 503).forEach { code ->
            val outcome = pipeline(send = { throw ApiHttpException(code, "x") }).run()
            assertTrue("HTTP $code sollte wiederholbar sein", outcome is TaskOutcome.Retry)
        }
    }

    @Test fun vierhundertIstEndgueltig() {
        val outcome = pipeline(send = { throw ApiHttpException(400, "Das Transkript ist leer") }).run()
        assertTrue(outcome is TaskOutcome.Failed)
        assertFalse(outcome is TaskOutcome.Retry)
    }

    @Test fun derGrundStehtInDerMeldung() {
        val outcome = pipeline(send = { throw ApiHttpException(503, "Hermes nicht erreichbar") }).run()
        assertTrue((outcome as TaskOutcome.Retry).reason.contains("503"))
    }
}
