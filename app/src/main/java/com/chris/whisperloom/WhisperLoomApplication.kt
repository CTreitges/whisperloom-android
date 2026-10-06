package com.chris.whisperloom

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import com.chris.whisperloom.agent.WidgetProfileStore
import com.chris.whisperloom.llm.LocalTextEngine
import com.chris.whisperloom.whisper.WhisperEngine

/**
 * Prozessweiter Einstieg: initialisiert die Offline-Engines (Modellordner) und gibt das geladene
 * whisper-Modell und das lokale Textmodell bei Speicherdruck frei — IME-Prozesse sind LMK-Kandidaten,
 * small belegt ~430 MB, large-v3-turbo ~1 GB, Gemma 4 E2B ~1,7 GB. Beim naechsten Diktat wird neu
 * geladen (Sekunden; das Textmodell waermt schon beim Aufnahmestart vor).
 *
 * Ausserdem der frueheste Punkt fuer die Widget-Migration: [onCreate] laeuft in jedem Prozess vor
 * Widget-Broadcast, Worker und Activity — ein Retry direkt nach dem Update findet den Server also
 * schon im Profil.
 */
class WhisperLoomApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        migrateWidgetServer()
        WhisperEngine.init(this)
        LocalTextEngine.init(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // RUNNING_CRITICAL (15), UI_HIDDEN (20) und alle Hintergrund-Stufen darueber.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            WhisperEngine.release()
            // Gibt nie mitten in einer Rechnung frei (close waehrend der Generierung = SIGSEGV).
            LocalTextEngine.release()
        }
    }

    /** Ein kaputter Profil-Speicher darf den Start nie verhindern; der naechste Start versucht es erneut. */
    private fun migrateWidgetServer() {
        try {
            WidgetProfileStore(this).migrateLegacyServer(
                getSharedPreferences(Prefs.FILE, MODE_PRIVATE),
                getString(R.string.widget_label),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Widget-Server nicht uebernommen", e)
        }
    }

    private companion object {
        const val TAG = "WhisperLoomApp"
    }
}
