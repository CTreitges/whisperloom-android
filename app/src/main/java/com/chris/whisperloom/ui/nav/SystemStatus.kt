package com.chris.whisperloom.ui.nav

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.chris.whisperloom.a11y.TextInserterAccessibilityService
import com.chris.whisperloom.overlay.FloatingMicService
import com.chris.whisperloom.whisper.ModelStore
import com.chris.whisperloom.whisper.OfflineSupport
import com.chris.whisperloom.whisper.TextModelCatalog

/**
 * Momentaufnahme des Systemzustands, den die App nicht selbst kontrolliert (Berechtigungen,
 * Dienste, Tastatur, Modell-Dateien). Wird in MainActivity.onResume neu gelesen; Tests
 * bauen sich den Wert direkt (alle Felder haben Defaults).
 */
data class SystemStatus(
    val micGranted: Boolean = false,
    val canDrawOverlays: Boolean = false,
    val a11yRunning: Boolean = false,
    /** POST_NOTIFICATIONS gibt es erst ab API 33 — darunter ist Schritt 6 unsichtbar. */
    val notifNeeded: Boolean = false,
    val notifGranted: Boolean = true,
    val imeEnabled: Boolean = false,
    val imeSelected: Boolean = false,
    val bubbleRunning: Boolean = false,
    /** IDs der vollstaendig installierten whisper-Modelle (ModelCatalog). */
    val installedModels: Set<String> = emptySet(),
    /** IDs der vollstaendig installierten Textmodelle (TextModelCatalog). */
    val installedTextModels: Set<String> = emptySet(),
    /** Belegter Platz aller Offline-Modelle (beide Arten, Teildateien, Caches). */
    val modelsUsedBytes: Long = 0L,
    val offlineSupported: Boolean = true,
    val totalRamBytes: Long = 8L shl 30,
) {
    companion object {
        fun read(context: Context): SystemStatus {
            val app = context.applicationContext
            val imm = app.getSystemService(InputMethodManager::class.java)
            val imeEnabled = imm?.enabledInputMethodList?.any { it.packageName == app.packageName } ?: false
            val selected = Settings.Secure.getString(app.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            val notifNeeded = Build.VERSION.SDK_INT >= 33
            val store = ModelStore(app)
            val totalRam = OfflineSupport.totalRamBytes(app)
            return SystemStatus(
                micGranted = granted(app, Manifest.permission.RECORD_AUDIO),
                canDrawOverlays = Settings.canDrawOverlays(app),
                a11yRunning = TextInserterAccessibilityService.isRunning(),
                notifNeeded = notifNeeded,
                notifGranted = !notifNeeded || granted(app, Manifest.permission.POST_NOTIFICATIONS),
                imeEnabled = imeEnabled,
                imeSelected = selected?.startsWith(app.packageName + "/") == true,
                bubbleRunning = FloatingMicService.isRunning,
                installedModels = store.installed().map { it.id }.toSet(),
                installedTextModels = store.installed(TextModelCatalog.models).map { it.id }.toSet(),
                modelsUsedBytes = store.usedBytes(),
                offlineSupported = OfflineSupport.isSupported && OfflineSupport.deviceFits(totalRam),
                totalRamBytes = totalRam,
            )
        }

        private fun granted(ctx: Context, permission: String) =
            ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}
