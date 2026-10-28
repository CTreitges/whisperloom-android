package com.chris.whisperloom.simple.realtime

import java.io.IOException

/** Kurzzeit-Token des Anbieters samt Verbindungsdaten — kommt vom Token-Dienst (Konzept §8.2, `POST /v1/sitzung`). */
data class SessionToken(
    val token: String,
    /** z. B. `wss://api.elevenlabs.io`; Tests: der Fake-Server. */
    val wsBase: String,
    val expiresAtMs: Long,
)

/** Token-Weg (Konzept §8.2). In Etappe 1 nur Schnittstelle; der Token-Dienst folgt in Etappe 2 (U4). */
interface TokenSource {
    @Throws(IOException::class)
    fun fetch(): SessionToken
}

/** Etappe 1: es gibt noch keinen Token-Dienst — jeder Start endet mit "Gerade keine Verbindung". */
object NoTokenSource : TokenSource {
    override fun fetch(): SessionToken = throw IOException("Kein Token-Dienst (Etappe 1)")
}

/**
 * Haelt das vorab geholte Token (Konzept §6.1: beim Einblenden der Tastatur holen, 15 min gueltig). Abgelaufen
 * oder verbraucht → neu holen. ElevenLabs-Einmal-Tokens sind nach dem Verbinden verbraucht ([invalidate]).
 */
class TokenCache(
    private val source: TokenSource,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var cached: SessionToken? = null

    /** Anzahl der Abrufe beim Dienst (Tests: "Token abgelaufen → neues Token"). */
    var fetches = 0
        private set

    @Synchronized
    @Throws(IOException::class)
    fun current(): SessionToken {
        val c = cached
        if (c != null && c.expiresAtMs - MARGIN_MS > clock()) return c
        fetches++
        return source.fetch().also { cached = it }
    }

    /** Vorab holen; ein Fehler bleibt still — beim Start wird erneut versucht. */
    @Synchronized
    fun prefetch() {
        try {
            current()
        } catch (_: IOException) {
        }
    }

    @Synchronized
    fun invalidate() {
        cached = null
    }

    companion object {
        /** Ein Token, das in weniger als 30 s ablaeuft, gilt als abgelaufen. */
        const val MARGIN_MS = 30_000L
    }
}
