package com.chris.whisperloom.simple

import android.content.Context
import android.content.SharedPreferences
import com.chris.whisperloom.Prefs

/**
 * Schalter je Person (Konzept §10): fest im Build ueber [com.chris.whisperloom.PresetSeed], fuer Person und Betreuer
 * unsichtbar. Rohe Schluessel in der Prefs-Datei [Prefs.FILE] mit Praefix `simple_`; fehlt ein Schluessel, gilt der
 * Standard aus dem Konzept. Nur lesen — die Simple-APK hat keine Einstellungen.
 */
class SimpleProfile(sp: SharedPreferences) {

    /** Wo der vorlaeufige Text erscheint. TalkBack erzwingt zur Laufzeit [LINE] (Konzept §6.3). */
    enum class Preview(val key: String) {
        FIELD("field"), LINE("line"), OFF("off");

        companion object {
            fun of(key: String?): Preview = entries.firstOrNull { it.key == key } ?: FIELD
        }
    }

    /** Festschreiben: an Pausen (VAD, Stille in Sekunden) oder nur beim Fertig-Tipp. */
    enum class Commit(val key: String, val vadSeconds: Double?) {
        PAUSE_1_5("pause_1_5", 1.5), PAUSE_3("pause_3", 3.0), MANUAL("manual", null);

        companion object {
            fun of(key: String?): Commit = entries.firstOrNull { it.key == key } ?: PAUSE_1_5
        }
    }

    val preview: Preview = Preview.of(sp.getString(KEY_PREVIEW, null))

    /** Knopf "sehr gross": ganze Flaeche ausser Loeschen (sonst "gross", ca. 3/4). */
    val buttonHuge: Boolean = sp.getBoolean(KEY_BUTTON_HUGE, false)

    /** Doppeltreffer-Sperre nach einem Zustandswechsel (0 / 500 / 1000 ms). */
    val lockMs: Long = sp.getInt(KEY_LOCK_MS, 500).coerceIn(0, 1000).toLong()

    /** Loeschen nach dem Diktat erst mit zweitem Tipp. */
    val deleteConfirm: Boolean = sp.getBoolean(KEY_DELETE_CONFIRM, true)

    val tones: Boolean = sp.getBoolean(KEY_TONES, true)

    val haptics: Boolean = sp.getBoolean(KEY_HAPTICS, true)

    val commit: Commit = Commit.of(sp.getString(KEY_COMMIT, null))

    /** Eigene Woerter (Realtime: hoechstens 50 à 20 Zeichen, Konzept §7.1). */
    val keyterms: List<String> = keyterms(sp.getString(KEY_KEYTERMS, null))

    /** Fuellwoerter und Stocken entfernen (`no_verbatim`). */
    val noVerbatim: Boolean = sp.getBoolean(KEY_NO_VERBATIM, false)

    /** Sicherheits-Stopp ohne erkannte Sprache, Minuten; 0 = aus (WCAG 2.2.1: abschaltbar). */
    val safetyStopMin: Int = sp.getInt(KEY_SAFETY_STOP_MIN, 10).coerceAtLeast(0)

    /** "Fertig per Stille" nach Sekunden ohne Textaenderung; 0 = aus (Standard). */
    val doneBySilenceSec: Int = sp.getInt(KEY_DONE_BY_SILENCE_SEC, 0).coerceAtLeast(0)

    companion object {
        const val KEY_PREVIEW = "simple_preview"
        const val KEY_BUTTON_HUGE = "simple_button_huge"
        const val KEY_LOCK_MS = "simple_lock_ms"
        const val KEY_DELETE_CONFIRM = "simple_delete_confirm"
        const val KEY_TONES = "simple_tones"
        const val KEY_HAPTICS = "simple_haptics"
        const val KEY_COMMIT = "simple_commit"
        const val KEY_KEYTERMS = "simple_keyterms"
        const val KEY_NO_VERBATIM = "simple_no_verbatim"
        const val KEY_SAFETY_STOP_MIN = "simple_safety_stop_min"
        const val KEY_DONE_BY_SILENCE_SEC = "simple_done_by_silence_sec"

        const val MAX_KEYTERMS = 50
        const val MAX_KEYTERM_CHARS = 20

        fun load(context: Context): SimpleProfile =
            SimpleProfile(context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE))

        /** Komma- oder zeilengetrennt; zu lange Begriffe fallen weg, mehr als 50 auch. */
        fun keyterms(raw: String?): List<String> =
            raw.orEmpty().split(',', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.length <= MAX_KEYTERM_CHARS }
                .distinct()
                .take(MAX_KEYTERMS)
    }
}
