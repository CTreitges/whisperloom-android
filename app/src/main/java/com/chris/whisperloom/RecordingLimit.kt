package com.chris.whisperloom

import com.chris.whisperloom.ime.DictationSession

/**
 * Hoechstdauer einer Aufnahme (Plan CTR-272 B2, UX-Spec §6.21) — rein und JVM-testbar wie
 * [DictationSession]. Die Grenze kommt von den Anbietern: OpenAI nimmt hoechstens 25 MB, bei 16 kHz
 * Mono PCM16 gut 13 min, und ein 413 ist nicht wiederholbar — das Audio waere weg. Darum gilt sie
 * nur fuer die Online-Erkennung; whisper.cpp rechnet beliebig lange.
 *
 * Kein neuer Zustand und kein "Weiter": Ab [DictationSession.LONG_MS] ein Hinweis, [WARN_BEFORE_MS]
 * vor dem Ende eine Warnung (Text, Ansage, Haptik), am Ende wird gesendet.
 */
object RecordingLimit {

    enum class Stage { OK, HINT, WARN, STOP }

    /** So lange vor dem Ende kommt die Warnung (WCAG 2.2.1 verlangt mindestens 20 s). */
    const val WARN_BEFORE_MS = 20_000L

    /**
     * Stufe nach [recordedMs]. [limited] = Online-Erkennung. Knopf und gehaltene Tastatur-Aufnahme
     * enden bei [DictationSession.MAX_MS]; [hintMs] null = kein Hinweis vorab.
     */
    fun stage(
        recordedMs: Long,
        limited: Boolean,
        stopMs: Long = DictationSession.MAX_MS,
        hintMs: Long? = DictationSession.LONG_MS,
    ): Stage = when {
        !limited -> Stage.OK
        recordedMs >= stopMs -> Stage.STOP
        recordedMs >= stopMs - WARN_BEFORE_MS -> Stage.WARN
        hintMs != null && recordedMs >= hintMs -> Stage.HINT
        else -> Stage.OK
    }
}
