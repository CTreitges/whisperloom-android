package com.chris.whisperloom.agent

import android.Manifest
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.chris.whisperloom.Prefs

/**
 * Das Widget selbst. Es rechnet nichts aus und merkt sich nichts: bei jedem Anlass wird der
 * aktuelle Zustand aus Einstellungen, Berechtigung und gespeichertem Auftrag neu bestimmt
 * ([resolve]) und die Flaeche neu gezeichnet.
 *
 * `updatePeriodMillis=0` — es gibt also keinen Takt. [onUpdate] laeuft beim Hinzufuegen,
 * nach einem Neustart und nach App-Updates; alles andere kommt von Dienst und Auftrag.
 *
 * Welches Profil eine Instanz zeigt, steht im [WidgetProfileStore]; hier wird die Zuordnung
 * nur mitgefuehrt, wenn Instanzen verschwinden oder nach einer Wiederherstellung neue Ids haben.
 */
class VoiceTaskWidget : AppWidgetProvider() {

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
        val (state, message) = resolve(ctx)
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
     * Launcher, die selbst sichern.
     */
    override fun onRestored(ctx: Context, oldIds: IntArray, newIds: IntArray) {
        WidgetProfileStore(ctx).remap(oldIds, newIds)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = AppWidgetManager.getInstance(ctx)
            val done = Bundle().apply { putBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, true) }
            newIds.forEach { manager.updateAppWidgetOptions(it, done) }
        }
    }

    companion object {

        /**
         * Der wahre Zustand, aus drei unabhaengigen Quellen: ist das Feature ueberhaupt
         * eingerichtet, darf die App das Mikrofon, und liegt ein Auftrag herum.
         */
        fun resolve(ctx: Context): Pair<VoiceTaskState, String> {
            if (!Prefs(ctx).agentReady) return VoiceTaskState.OFF to ""
            if (!hasMicPermission(ctx)) return VoiceTaskState.NO_MIC to ""
            val store = VoiceTaskStore(ctx)
            return VoiceTaskUi.afterRestart(store.state, store.hasWork) to store.message
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
