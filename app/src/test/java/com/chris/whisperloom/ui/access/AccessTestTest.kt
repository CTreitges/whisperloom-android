package com.chris.whisperloom.ui.access

import com.chris.whisperloom.api.ApiHttpException
import com.chris.whisperloom.api.ApiNetworkException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Fehlerklassifikation fuer den Ergebnis-Chip von "Zugang pruefen" (UX-Spec §6.1 err_*). */
class AccessTestTest {

    @Test fun httpStatusWirdZugeordnet() {
        assertEquals(AccessTest.Kind.UNAUTHORIZED, AccessTest.classify(ApiHttpException(401, "bad key")).kind)
        assertEquals(AccessTest.Kind.UNAUTHORIZED, AccessTest.classify(ApiHttpException(403, "forbidden")).kind)
        assertEquals(AccessTest.Kind.RATE_LIMIT, AccessTest.classify(ApiHttpException(429, "slow down")).kind)
        // 402 = Guthaben aufgebraucht (ElevenLabs, OpenRouter) — eigener Text statt "Unbekannter Fehler".
        assertEquals(AccessTest.Kind.NO_CREDIT, AccessTest.classify(ApiHttpException(402, "insufficient credits")).kind)
        val server = AccessTest.classify(ApiHttpException(503, "down"))
        assertEquals(AccessTest.Kind.SERVER, server.kind)
        assertEquals(503, server.code)
        assertEquals(AccessTest.Kind.UNKNOWN, AccessTest.classify(ApiHttpException(404, "nope")).kind)
    }

    @Test fun netzfehlerUndTimeout() {
        assertEquals(AccessTest.Kind.TIMEOUT, AccessTest.classify(ApiNetworkException(SocketTimeoutException("Read timed out"))).kind)
        assertEquals(AccessTest.Kind.NETWORK, AccessTest.classify(ApiNetworkException(UnknownHostException("api"))).kind)
        assertEquals(AccessTest.Kind.NETWORK, AccessTest.classify(ApiNetworkException(IOException("x"))).kind)
    }

    @Test fun sonstigesIstUnbekanntMitMeldung() {
        val f = AccessTest.classify(IllegalStateException("kaputt"))
        assertEquals(AccessTest.Kind.UNKNOWN, f.kind)
        assertEquals("kaputt", f.message)
    }

    @Test fun stilleIstEineSekunde16kHz() {
        val wav = AccessTest.silence()
        assertEquals(16_000 * 2, wav.pcmByteCount)
        assertEquals(16_000, wav.sampleRate)
    }

    @Test fun sekundenMitEinerNachkommastelle() {
        assertEquals("0,8", AccessTest.seconds(812))
        assertEquals("12,0", AccessTest.seconds(12_000))
    }
}
