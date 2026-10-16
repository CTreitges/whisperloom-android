package com.chris.whisperloom

import com.chris.whisperloom.api.ApiHttpException
import com.chris.whisperloom.api.ApiNetworkException
import com.chris.whisperloom.api.WavUpload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Fallback der Erkennung (3.9.1) als reine Huelle um zwei Backends: wann der zweite einspringt, dass
 * er danach bleibt (Stuecke einer Sprachnachricht) und welcher Fehler rausgeht, wenn beide scheitern.
 * Robolectric nur fuer android.util.Log.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FallbackBackendTest {

    private class Fake(override val label: String, var fail: Exception? = null, val text: String = label) : TranscriptionBackend {
        var calls = 0
        override fun transcribe(upload: WavUpload, language: String): TranscriptResult {
            calls++
            fail?.let { throw it }
            return TranscriptResult(text)
        }
    }

    private val upload = WavUpload.fromSamples(FloatArray(AudioUtils.SAMPLE_RATE))

    @Test fun derErsteErkenntDerFallbackSchweigt() {
        val first = Fake("OpenAI")
        val second = Fake("Groq")
        val backend = FallbackBackend(first, second)
        assertEquals("OpenAI", backend.transcribe(upload, "de").text)
        assertEquals(0, second.calls)
        assertFalse(backend.usedFallback)
        assertEquals("OpenAI", backend.label)
        assertNull(TranscriptionEngine.sttFallbackNote(backend))
    }

    @Test fun scheitertDerErsteErkenntDerFallbackDieselbeAufnahme() {
        val first = Fake("OpenAI", fail = ApiHttpException(429, "Rate limit"))
        val second = Fake("Groq")
        val backend = FallbackBackend(first, second)
        assertEquals("Groq", backend.transcribe(upload, "de").text)
        assertTrue(backend.usedFallback)
        assertEquals("Groq", backend.label)
        assertEquals("Spracherkennung fehlgeschlagen — mit Fallback Groq erkannt", TranscriptionEngine.sttFallbackNote(backend))
    }

    @Test fun nachDemAusfallBleibtEsBeimFallback() {
        // Geteilte Sprachnachricht in Stuecken: Stueck 2 und 3 warten nicht wieder auf den ersten.
        val first = Fake("OpenAI", fail = ApiNetworkException(SocketTimeoutException("Read timed out")))
        val second = Fake("Groq")
        val backend = FallbackBackend(first, second)
        repeat(3) { backend.transcribe(upload, "de") }
        assertEquals(1, first.calls)
        assertEquals(3, second.calls)
    }

    @Test fun scheiternBeideGehtDerFehlerDesErstenRaus() {
        val firstError = ApiHttpException(401, "Invalid key")
        val secondError = ApiNetworkException(UnknownHostException("api.groq.com"))
        val backend = FallbackBackend(Fake("OpenAI", fail = firstError), Fake("Groq", fail = secondError))
        try {
            backend.transcribe(upload, "de")
            fail("beide gescheitert")
        } catch (e: Exception) {
            assertSame(firstError, e)
            assertSame(secondError, e.suppressed.single())
        }
        assertFalse(backend.usedFallback)
    }

    @Test fun nachAbbruchKeinZweiterUpload() {
        val first = Fake("OpenAI", fail = ApiNetworkException(InterruptedIOException("interrupted")))
        val second = Fake("Groq")
        val backend = FallbackBackend(first, second)
        Thread.currentThread().interrupt()
        try {
            backend.transcribe(upload, "de")
            fail("abgebrochen")
        } catch (e: ApiNetworkException) {
            assertEquals(0, second.calls)
        } finally {
            Thread.interrupted() // Flag fuer die naechsten Tests zuruecksetzen
        }
    }

    @Test fun hinfaelligerAuftragSchicktNichtsAnDenFallback() {
        // Review 3.9.1: Widget-Auftrag abgeloest, waehrend der erste im Timeout hing — kein zweiter Upload.
        val second = Fake("Groq")
        val backend = FallbackBackend(Fake("OpenAI", fail = ApiNetworkException(SocketTimeoutException("Read timed out"))), second)
        backend.cancelled = { true }
        assertTrue(runCatching { backend.transcribe(upload, "de") }.exceptionOrNull() is ApiNetworkException)
        assertEquals(0, second.calls)
    }

    @Test fun ohneFallbackKeinHinweis() {
        assertNull(TranscriptionEngine.sttFallbackNote(Fake("OpenAI")))
    }
}
