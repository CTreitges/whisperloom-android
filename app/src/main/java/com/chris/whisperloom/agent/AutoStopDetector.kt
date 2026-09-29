package com.chris.whisperloom.agent

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Entscheidet aus dem Pegelverlauf einer Widget-Aufnahme, wann sie von selbst endet
 * (Auto-Stopp, pro Profil schaltbar). Rein und ohne eigene Uhr: der Dienst reicht je Puffer den
 * Peak (0..1) und die Zeit seit Aufnahmebeginn herein — so ist jeder Verlauf im Test exakt
 * nachstellbar.
 *
 * - Rauschboden als Minimum-Folger: faellt sofort auf einen leiseren Puffer, steigt hoechstens
 *   um [floorRiseDbPerS] dB/s. Er startet FEST bei [floorInit] und wird nicht aus den ersten
 *   Puffern gelernt — wer gleich losspricht, wuerde sonst selbst zum Boden und verloere das Diktat.
 * - Laut ist ein Puffer ueber `on = max(minThreshold, factor * Boden)`. Innerhalb eines lauten
 *   Abschnitts reicht `off = hysteresis * on`, damit leisere Silbenenden ihn nicht zerhacken.
 * - Sprache gilt als erkannt, sobald ein lauter Abschnitt [speechConfirmMs] durchhaelt — ein
 *   Klick oder Klopfen reicht dafuer nicht.
 * - [Decision.SPEECH_ENDED]: nach erkannter Sprache [pauseMs] lang leise, fruehestens nach
 *   [minRecordingMs].
 * - [Decision.NO_SPEECH]: bis [noSpeechMs] keine Sprache erkannt. Laeuft gerade ein lauter
 *   Abschnitt, wird er erst zu Ende bewertet — wer kurz vor Ablauf losspricht, wird nicht
 *   abgeschnitten.
 *
 * Die erste Entscheidung ausser [Decision.CONTINUE] rastet ein. Nicht threadsicher: ein Detektor
 * gehoert genau einer Aufnahme und wird nur von ihrem Aufnahme-Thread gefuettert.
 */
class AutoStopDetector(
    /** Stille nach erkannter Sprache bis zum Stopp; kommt aus dem Profil ([SpeechPause]). */
    val pauseMs: Long = SpeechPause.NORMAL.ms,
    private val floorInit: Float = FLOOR_INIT,
    private val floorRiseDbPerS: Float = FLOOR_RISE_DB_PER_S,
    private val minThreshold: Float = MIN_THRESHOLD,
    private val factor: Float = FACTOR,
    private val hysteresis: Float = HYSTERESIS,
    private val speechConfirmMs: Long = SPEECH_CONFIRM_MS,
    private val minRecordingMs: Long = MIN_RECORDING_MS,
    private val noSpeechMs: Long = NO_SPEECH_MS,
) {

    enum class Decision { CONTINUE, SPEECH_ENDED, NO_SPEECH }

    /**
     * Aktueller Rauschboden, fuer das Kalibrier-Log. Nie unter `minThreshold / factor`: darunter
     * aendert er die Schwellen ohnehin nicht mehr, und ohne Untergrenze bliebe er nach digitalen
     * Nullen (Mikrofon-Anlauf, stummgeschaltet) fuer immer 0 — er steigt multiplikativ.
     */
    var floor: Float = floorInit
        private set

    private val floorMin = minThreshold / factor
    private var lastAt = -1L
    private var loud = false
    private var loudSince = 0L
    private var quietSince = 0L
    private var speech = false
    private var decision = Decision.CONTINUE

    fun feed(peak: Float, atMs: Long): Decision {
        if (decision != Decision.CONTINUE) return decision
        val dt = if (lastAt < 0) 0L else max(0L, atMs - lastAt)
        lastAt = atMs
        val risen = floor * 10f.pow(floorRiseDbPerS * dt / 20_000f)
        floor = max(min(peak, risen), floorMin)

        val on = max(minThreshold, factor * floor)
        val nowLoud = if (loud) peak >= hysteresis * on else peak > on
        if (nowLoud && !loud) loudSince = atMs
        if (!nowLoud && loud) quietSince = atMs
        loud = nowLoud
        if (loud && atMs - loudSince >= speechConfirmMs) speech = true

        decision = when {
            speech && !loud && atMs - quietSince >= pauseMs && atMs >= minRecordingMs -> Decision.SPEECH_ENDED
            !speech && !loud && atMs >= noSpeechMs -> Decision.NO_SPEECH
            else -> Decision.CONTINUE
        }
        return decision
    }

    companion object {
        /** Start-Rauschboden, fest (nicht gelernt). */
        const val FLOOR_INIT = 0.015f
        const val FLOOR_RISE_DB_PER_S = 3f
        const val MIN_THRESHOLD = 0.04f
        const val FACTOR = 3f
        const val HYSTERESIS = 0.7f
        const val SPEECH_CONFIRM_MS = 250L
        const val MIN_RECORDING_MS = 1_500L
        const val NO_SPEECH_MS = 8_000L
    }
}
