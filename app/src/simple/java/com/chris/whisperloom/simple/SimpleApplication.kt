package com.chris.whisperloom.simple

import android.app.Application
import android.util.Log
import com.chris.whisperloom.PresetSeed

/**
 * Prozessweiter Einstieg der Simple-APK: nur die Vorbelegung ([PresetSeed] — Profil je Person, Konzept §10).
 * Keine Offline-Engines, kein Widget: die Simple-APK hat nichts davon (Konzept §11.1).
 *
 * Vor dem ersten Entsperren (directBootAware-Tastatur) ist der geschuetzte Speicher noch zu — dann wirft
 * [PresetSeed] eine IllegalStateException; [SimpleInputMethodService] holt die Vorbelegung nach dem Entsperren nach.
 */
class SimpleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            PresetSeed.applyFromAssets(this)
        } catch (e: Exception) {
            // Nur die Art des Fehlers: eine JSONException truege den Inhalt des Presets (ab Etappe 2 den Anmelde-Code) ins Log.
            Log.e(TAG, "Vorbelegung nicht uebernommen: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "SimpleApp"
    }
}
