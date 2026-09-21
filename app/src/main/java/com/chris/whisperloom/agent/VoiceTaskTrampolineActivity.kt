package com.chris.whisperloom.agent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
            TapIntent.REFRESH -> VoiceTaskWidget.refresh(this)
            else -> openApp()
        }
        finish()
    }

    private fun start() {
        val prefs = Prefs(this)
        if (!prefs.agentReady) {
            openApp()
            return
        }
        if (!VoiceTaskWidget.hasMicPermission(this)) {
            VoiceTaskWidgetView.push(this, VoiceTaskState.NO_MIC)
            openApp()
            return
        }
        startForegroundService(Intent(this, VoiceTaskService::class.java).setAction(VoiceTaskService.ACTION_START))
    }

    /**
     * Erneut senden braucht kein Mikrofon — der Auftrag liegt schon auf der Platte. Deshalb
     * kein Dienst, sondern direkt der Auftrag; das spart einen Foreground-Service, der nur
     * Warten wuerde.
     */
    private fun retry() {
        // Nichts zu wiederholen (z. B. nach "Kein Ton aufgenommen") — dann ist der Tipp
        // als neuer Anlauf gemeint, nicht als Wiederholung.
        if (!VoiceTaskStore(this).hasWork) {
            start()
            return
        }
        VoiceTaskWidgetView.push(this, VoiceTaskState.WORKING)
        VoiceTaskWork.enqueue(this)
    }

    /** Einstellungen der App — dort wird das Mikrofon erlaubt bzw. der Server eingetragen. */
    private fun openApp() {
        startActivity(AppNav.agent(this))
    }

    companion object {
        const val EXTRA_INTENT = "voice_task_intent"

        fun intent(ctx: Context, tap: TapIntent): Intent =
            Intent(ctx, VoiceTaskTrampolineActivity::class.java)
                .setAction(ACTION_PREFIX + tap.name)
                .putExtra(EXTRA_INTENT, tap.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun intentOf(intent: Intent?): TapIntent =
            runCatching { TapIntent.valueOf(intent?.getStringExtra(EXTRA_INTENT).orEmpty()) }
                .getOrDefault(TapIntent.NONE)

        /**
         * Eigene Action je Absicht: sonst haelt das System zwei PendingIntents, die sich nur
         * in den Extras unterscheiden, fuer denselben — "erneut senden" wuerde dann eine
         * neue Aufnahme starten.
         */
        private const val ACTION_PREFIX = "com.chris.whisperloom.agent.TAP_"
    }
}
