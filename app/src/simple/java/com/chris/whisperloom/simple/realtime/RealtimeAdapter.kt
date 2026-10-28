package com.chris.whisperloom.simple.realtime

/** Was der Anbieter ueber die WebSocket-Verbindung meldet — anbieterneutral (Konzept §11.2, Adapter je Anbieter). */
sealed class RealtimeEvent {
    object Started : RealtimeEvent()
    data class Partial(val text: String) : RealtimeEvent()
    data class Committed(val text: String) : RealtimeEvent()
    data class Error(val code: String, val message: String?) : RealtimeEvent()
    object Ignored : RealtimeEvent()
}

/** Sitzungsoptionen aus dem Profil (Konzept §10): Sprache, Festschreiben, eigene Woerter, Fuellwoerter. */
data class RealtimeOptions(
    val language: String = "de",
    /** Stille in Sekunden bis zum Festschreiben an Pausen; null = nur beim Fertig-Tipp (manual). */
    val vadSilenceSeconds: Double? = 1.5,
    val keyterms: List<String> = emptyList(),
    val noVerbatim: Boolean = false,
)

/** Anbieter-Schnittstelle: Verbindungs-URL, Audio-Nachricht, Deutung der Server-Nachrichten. */
interface RealtimeAdapter {
    fun url(wsBase: String, token: String, options: RealtimeOptions): String
    fun audioMessage(pcm: ByteArray, sampleRate: Int, commit: Boolean): String
    fun parse(text: String): RealtimeEvent
}

/** Die laufende Verbindung aus Sicht der Tastatur; [RealtimeSession] ist die echte, Tests nutzen einen Fake. */
interface RealtimeLink {
    /** Token holen und verbinden (blockiert fuer das Token — IO-Thread). false = kein Token, Fehler schon gemeldet. */
    fun start(): Boolean
    fun sendAudio(pcm: ByteArray)
    /** Fertig: letzten Abschnitt festschreiben lassen (`commit: true`), dann schliessen. */
    fun finish()
    /** Sofort schliessen, nichts festschreiben; kein Rueckruf mehr. */
    fun cancel()
}
