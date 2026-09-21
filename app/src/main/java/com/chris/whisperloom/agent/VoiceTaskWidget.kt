package com.chris.whisperloom.agent

import android.Manifest
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.pm.PackageManager
import com.chris.whisperloom.Prefs

/**
 * Das Widget selbst. Es rechnet nichts aus und merkt sich nichts: bei jedem Anlass wird der
 * aktuelle Zustand aus Einstellungen, Berechtigung und gespeichertem Auftrag neu bestimmt
 * ([resolve]) und die Flaeche neu gezeichnet.
 *
 * `updatePeriodMillis=0` — es gibt also keinen Takt. [onUpdate] laeuft beim Hinzufuegen,
 * nach einem Neustart und nach App-Updates; alles andere kommt von Dienst und Auftrag.
 */
class VoiceTaskWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        val (state, message) = resolve(ctx)
        manager.updateAppWidget(ids, VoiceTaskWidgetView.build(ctx, state, message = message))
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

        fun hasMicPermission(ctx: Context): Boolean =
            ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        /** Neu zeichnen mit dem aus den Quellen abgeleiteten Zustand (nach Einstellungs-Aenderungen). */
        fun refresh(ctx: Context) {
            val (state, message) = resolve(ctx)
            VoiceTaskWidgetView.push(ctx, state, message = message)
        }
    }
}
