package com.chris.whisperloom.agent

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import com.chris.whisperloom.AppNav
import com.chris.whisperloom.Prefs

/**
 * Unsichtbare Zwischenstation zwischen Widget-Tipp und Mikrofon-Dienst.
 *
 * Warum ueberhaupt: ab Android 14 darf ein Foreground-Service vom Typ `microphone` nicht
 * aus dem Hintergrund starten. Ob ein Widget-PendingIntent als Hintergrund zaehlt, steht in
 * der Doku nur als Fliesstext — und der Fehlschlag waere der schlimmste ueberhaupt: keine
 * Exception, nur Stille. Ueber eine Activity ist die App beim Start des Dienstes real im
 * Vordergrund, damit ist die Frage erledigt.
 *
 * Bewusst OHNE `android:noHistory="true"`: das wuerde onActivityResult killen und damit
 * jeden spaeteren Permission-Dialog stillschweigend unbrauchbar machen.
 */
class VoiceTaskTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intentOf(intent)) {
            TapIntent.START -> start()
            TapIntent.RETRY -> retry()
            TapIntent.REFRESH -> anstossen()
            else -> openApp()
        }
        finish()
    }

    private fun start() {
        // Welches Widget gestartet hat — dessen Profil bestimmt Server und wie die Aufnahme endet.
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // Erst aufnehmen, wenn das Widget auch senden kann — sonst waere die Transkription umsonst bezahlt.
        if (!Prefs(this).proWidgetsEnabled || !WidgetProfileStore(this).forWidget(widgetId).serverReady) {
            openApp()
            return
        }
        if (!VoiceTaskWidget.hasMicPermission(this)) {
            VoiceTaskWidgetView.push(this, VoiceTaskState.NO_MIC)
            openApp()
            return
        }
        val service = Intent(this, VoiceTaskService::class.java).setAction(VoiceTaskService.ACTION_START)
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) service.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        startForegroundService(service)
    }

    /**
     * Erneut senden braucht kein Mikrofon — der Auftrag liegt schon auf der Platte. Deshalb
     * kein Dienst, sondern direkt der Auftrag; das spart einen Foreground-Service, der nur
     * Warten wuerde. Ueber [anstossen], damit ein Doppeltipp einen gerade gestarteten Job
     * nicht wieder abbricht.
     *
     * Angestossen wird nur, was das Widget DES AUFTRAGS auch senden kann — egal welches Widget
     * getippt wurde. Sonst liefe jeder Tipp in denselben Fehler, und ein unsendbarer Auftrag von
     * Widget A blockierte auch B. Reihenfolge wie im Worker: Widget geloescht → verwerfen (wie
     * dort) und neu anfangen; Pro Widgets aus → "Erweitert"; kein gueltiger Server → Editor
     * genau dieses Widgets. Der Auftrag bleibt in den beiden letzten Faellen liegen.
     */
    private fun retry() {
        val store = VoiceTaskStore(this)
        // Nichts zu wiederholen (z. B. nach "Kein Ton aufgenommen") — dann ist der Tipp
        // als neuer Anlauf gemeint, nicht als Wiederholung.
        if (!store.hasWork) {
            start()
            return
        }
        // Ein Auftrag von vor 3.7.1 kennt kein Profil — wie im Worker das Standardprofil.
        val profile = WidgetProfileStore(this).get(store.profileId.ifEmpty { WidgetProfile.DEFAULT_ID })
        when {
            profile == null -> {
                VoiceTaskWork.cancel(this)
                store.clear()
                VoiceTaskWidget.refresh(this)
                start()
            }
            !Prefs(this).proWidgetsEnabled -> startActivity(AppNav.advanced(this))
            !profile.serverReady -> startActivity(AppNav.widgetProfile(this, profile.id))
            else -> anstossen()
        }
    }

    /**
     * Tipp auf "Wird gesendet …" (und erneut senden mit Auftrag): sendet jetzt, ausser ein
     * Worker arbeitet gerade wirklich ([VoiceTaskUi.nudge]). Frueher reihte der Tipp nur ein,
     * wenn gar kein Job mehr bestand — ein Job im Backoff blieb liegen, und das Widget hing (#10).
     *
     * Die Job-Phase wird ZUERST gelesen, der Store danach: erledigt der Worker den Auftrag
     * genau waehrend der Abfrage, sieht der Tipp keinen Auftrag mehr, statt "Gesendet" mit
     * "Wird gesendet …" zu uebermalen. Ohne Auftrag wird nur ein stehengebliebenes "Wird
     * gesendet …" richtiggestellt (dieselbe Regel wie im leeren Worker-Lauf): "Gesendet" oder
     * "Nichts verstanden — nichts gesendet" hat der Worker gerade selbst gezeichnet, resolve()
     * machte daraus "bereit" — und der Hinweis, dass nichts ankam, waere weg.
     *
     * SEND_NOW zeichnet VOR dem Einreihen: so ist der Push des Workers immer der letzte, und
     * ein Doppeltipp trifft auf RUNNING mit frischem Stempel. Der Store kommt dabei mit auf
     * WORKING — sonst zeichnete ein spaeteres onUpdate waehrend des Laufs noch den alten Fehler.
     * WAIT zeichnet gar nichts: die Flaeche gehoert dem laufenden Worker.
     */
    private fun anstossen() {
        val phase = VoiceTaskWork.phase(this)
        val store = VoiceTaskStore(this)
        val laeuftSeit = VoiceTaskUi.runningFor(store.attemptStartedAt, SystemClock.elapsedRealtime())
        when (VoiceTaskUi.nudge(store.hasWork, phase, laeuftSeit, store.offlineRecognition)) {
            Nudge.REDRAW -> if (store.state == VoiceTaskState.WORKING) VoiceTaskWidget.refresh(this)
            Nudge.SEND_NOW -> {
                store.state = VoiceTaskState.WORKING
                store.message = ""
                store.attemptStartedAt = SystemClock.elapsedRealtime()
                VoiceTaskWidgetView.push(this, VoiceTaskState.WORKING)
                VoiceTaskWork.sendNow(this)
            }
            Nudge.WAIT -> Unit
        }
    }

    /**
     * Die App dort oeffnen, wo sich das Hindernis dieses Widgets beheben laesst: Pro Widgets aus
     * → "Erweitert"; kein Mikrofon → Tab "Pro Widgets" (Mikrofon-Karte); Profil ohne Server →
     * dessen Editor; sonst der Tab "Pro Widgets".
     */
    private fun openApp() {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val profile = WidgetProfileStore(this).forWidget(widgetId)
        val target = when {
            !Prefs(this).proWidgetsEnabled -> AppNav.advanced(this)
            !VoiceTaskWidget.hasMicPermission(this) -> AppNav.proWidgets(this)
            !profile.serverReady -> AppNav.widgetProfile(this, profile.id)
            else -> AppNav.proWidgets(this)
        }
        startActivity(target)
    }

    companion object {
        const val EXTRA_INTENT = "voice_task_intent"

        /**
         * Mit gueltiger [appWidgetId] steht die Instanz in `data` und als
         * [AppWidgetManager.EXTRA_APPWIDGET_ID]: `data` macht die PendingIntents zweier Widgets
         * verschieden (Extras zaehlen beim Vergleich nicht), das Extra liest [start].
         */
        fun intent(ctx: Context, tap: TapIntent, appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID): Intent =
            Intent(ctx, VoiceTaskTrampolineActivity::class.java)
                .setAction(ACTION_PREFIX + tap.name)
                .putExtra(EXTRA_INTENT, tap.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .apply {
                    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        data = Uri.parse("$WIDGET_URI$appWidgetId")
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                }

        fun intentOf(intent: Intent?): TapIntent =
            runCatching { TapIntent.valueOf(intent?.getStringExtra(EXTRA_INTENT).orEmpty()) }
                .getOrDefault(TapIntent.NONE)

        /**
         * Eigene Action je Absicht: sonst haelt das System zwei PendingIntents, die sich nur
         * in den Extras unterscheiden, fuer denselben — "erneut senden" wuerde dann eine
         * neue Aufnahme starten.
         */
        private const val ACTION_PREFIX = "com.chris.whisperloom.agent.TAP_"

        /** Nur Unterscheidungsmerkmal je Instanz; nichts im System loest diese Adresse auf. */
        private const val WIDGET_URI = "whisperloom://widget/"
    }
}
