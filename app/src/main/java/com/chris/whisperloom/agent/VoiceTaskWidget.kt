package com.chris.whisperloom.agent

import android.Manifest
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.chris.whisperloom.Prefs

/**
 * Das Widget selbst. Es rechnet nichts aus und merkt sich nichts: bei jedem Anlass wird der
 * aktuelle Zustand aus Einstellungen, Berechtigung, gespeichertem Auftrag und dem Server des
 * Profils neu bestimmt ([resolve], [idle]) und die Flaeche neu gezeichnet.
 *
 * `updatePeriodMillis=0` — es gibt also keinen Takt. [onUpdate] laeuft beim Hinzufuegen,
 * nach einem Neustart und nach App-Updates; alles andere kommt von Dienst und Auftrag.
 *
 * Welches Profil eine Instanz zeigt, steht im [WidgetProfileStore]; hier wird die Zuordnung
 * nur mitgefuehrt, wenn Instanzen verschwinden, nach einer Wiederherstellung neue Ids haben oder
 * nach einem App-Update als Bestand uebernommen werden.
 */
class VoiceTaskWidget : AppWidgetProvider() {

    /**
     * Zusaetzlich zu den Widget-Broadcasts: `MY_PACKAGE_REPLACED` nach einem App-Update. Widgets,
     * die schon vor den Profilen lagen, haben keine Bindung — "Neu konfigurieren" hielte sie fuer
     * eine Erstplatzierung, baende still und schloesse sich. Der Broadcast kommt nie waehrend einer
     * Platzierung, also ist jede ungebundene Instanz hier Bestand. Idempotent, laeuft bei jedem Update.
     */
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            WidgetProfileStore(ctx).adopt(placedIds(ctx))
            return
        }
        super.onReceive(ctx, intent)
    }

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        val (state, message) = resolve(ctx)
        VoiceTaskWidgetView.pushTo(ctx, manager, ids, state, message = message)
    }

    /**
     * Groesse geaendert. Ab Android 12 waehlt das System selbst aus der Groessen-Map — nichts zu
     * tun. Darunter haengt die Variante an den gemeldeten Spannen, also diese Instanz neu zeichnen.
     */
    override fun onAppWidgetOptionsChanged(ctx: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        val (state, message) = resolve(ctx, WidgetProfileStore(ctx).forWidget(id))
        VoiceTaskWidgetView.pushTo(ctx, manager, intArrayOf(id), state, message = message)
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        WidgetProfileStore(ctx).unbind(ids)
    }

    /**
     * Wiederhergestellte Widgets bekommen neue Ids; die Profil-Zuordnung zieht mit. Danach laut
     * Doku `OPTION_APPWIDGET_RESTORE_COMPLETED` setzen (erst ab Android 11 bekannt) und neu
     * zeichnen — Letzteres uebernimmt [onUpdate], das `AppWidgetProvider.onReceive` direkt im
     * Anschluss mit den neuen Ids aufruft. Mit `allowBackup=false` ist das Vorsorge fuer
     * Launcher, die selbst sichern. Ohne mitgesicherte Bindung (die Profil-Datei ist vom Backup
     * ausgeschlossen) wird das Widget wie ein Bestands-Widget uebernommen ([WidgetProfileStore.adopt]).
     */
    override fun onRestored(ctx: Context, oldIds: IntArray, newIds: IntArray) {
        val store = WidgetProfileStore(ctx)
        store.remap(oldIds, newIds)
        store.adopt(newIds)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = AppWidgetManager.getInstance(ctx)
            val done = Bundle().apply { putBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, true) }
            newIds.forEach { manager.updateAppWidgetOptions(it, done) }
        }
    }

    companion object {

        /**
         * Der Zustand, der fuer alle Widgets gleich ist, aus drei unabhaengigen Quellen: sind Pro
         * Widgets ueberhaupt an, darf die App das Mikrofon, und liegt ein Auftrag herum. Liefert
         * es "bereit", entscheidet je Instanz noch das Profil ([idle]) — das setzt
         * [VoiceTaskWidgetView.pushTo] ein.
         */
        fun resolve(ctx: Context): Pair<VoiceTaskState, String> {
            if (!Prefs(ctx).proWidgetsEnabled) return VoiceTaskState.OFF to ""
            if (!hasMicPermission(ctx)) return VoiceTaskState.NO_MIC to ""
            val store = VoiceTaskStore(ctx)
            return VoiceTaskUi.afterRestart(store.state, store.hasWork) to store.message
        }

        /**
         * Der wahre Zustand eines Widgets mit [profile], in dieser Reihenfolge: Pro Widgets aus,
         * kein Mikrofon, laufender Auftrag (zeigen alle), Profil ohne Server, bereit.
         */
        fun resolve(ctx: Context, profile: WidgetProfile): Pair<VoiceTaskState, String> {
            val (state, message) = resolve(ctx)
            return idle(state, profile) to message
        }

        /**
         * Ruhezustand je Widget: "bereit" heisst nur mit gueltigem Server im eigenen Profil
         * bereit, sonst [VoiceTaskState.NO_SERVER]. Alle anderen Zustaende gelten fuer alle
         * Widgets gleich und bleiben — auch "aus", denn der Schalter ist global.
         */
        fun idle(state: VoiceTaskState, profile: WidgetProfile): VoiceTaskState = when (state) {
            VoiceTaskState.READY, VoiceTaskState.NO_SERVER ->
                if (profile.serverReady) VoiceTaskState.READY else VoiceTaskState.NO_SERVER
            else -> state
        }

        /**
         * Alle Instanzen auf dem Startbildschirm. Auf Geraeten ohne `android.software.app_widgets`
         * (TV, Auto, abgespeckte Images) gibt es den Widget-Dienst nicht, und `getInstance`
         * liefert null — dann eben keine.
         */
        fun placedIds(ctx: Context): IntArray =
            AppWidgetManager.getInstance(ctx)?.getAppWidgetIds(ComponentName(ctx, VoiceTaskWidget::class.java))
                ?: IntArray(0)

        fun hasMicPermission(ctx: Context): Boolean =
            ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        /** Neu zeichnen mit dem aus den Quellen abgeleiteten Zustand (nach Einstellungs-Aenderungen). */
        fun refresh(ctx: Context) {
            val (state, message) = resolve(ctx)
            VoiceTaskWidgetView.push(ctx, state, message = message)
        }
    }
}
