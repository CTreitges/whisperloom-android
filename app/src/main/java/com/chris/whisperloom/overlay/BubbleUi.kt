package com.chris.whisperloom.overlay

import com.chris.whisperloom.Formats

/**
 * Zustaende des schwebenden Knopfs und der IME-Mikro-Taste. Im API-Betrieb dauert die
 * Uebertragung spuerbar — ohne sichtbaren Unterschied zwischen "nimmt auf" und
 * "sendet gerade" tippt man mitten in die laufende Anfrage.
 */
enum class BubbleState {
    /** Bereit. Tippen startet die Aufnahme. */
    IDLE,

    /** Nimmt auf. Tippen beendet und sendet, Ziehen auf das Abbrechen-Ziel verwirft. */
    RECORDING,

    /** Anfrage laeuft. Tippen tut nichts — ausser in der Textverbesserung: dann Text ohne KI. */
    SENDING,

    /** Fehlgeschlagen, Audio ist gepuffert. Tippen versucht es erneut. */
    ERROR,
}

/** Reine (Android-freie) Anzeige-Helfer — JVM-unit-testbar. */
object BubbleUi {

    /** Aufnahme-Punkt vor dem Timer. */
    const val DOT = "●"

    /** Blink-Takt des Punkts: 1 Hz = 500 ms an, 500 ms aus. */
    const val DOT_PERIOD_MS = 500L

    /** Aufnahmedauer als m:ss, z. B. 7000 ms -> "0:07". */
    fun formatDuration(millis: Long): String = Formats.duration(millis)

    /** Timer-Label "● m:ss" (UX-Spec §5.1). */
    fun timerText(millis: Long): String = "$DOT ${formatDuration(millis)}"

    /** Ob der Punkt in dieser Blink-Phase sichtbar ist (an in den ersten 500 ms jeder Sekunde). */
    fun dotVisible(millis: Long): Boolean = (millis.coerceAtLeast(0) / DOT_PERIOD_MS) % 2 == 0L
}
