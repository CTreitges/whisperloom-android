package com.chris.whisperloom.agent

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.chris.whisperloom.R

/**
 * Zeichnet das Widget. Das Widget haelt keinen Zustand — hier wird aus [VoiceTaskState] und
 * ein paar Zahlen eine komplette [RemoteViews] gebaut und ueber den AppWidgetManager an ALLE
 * Instanzen geschickt.
 *
 * Die Entscheidung, was ein Tipp bedeutet, faellt in [VoiceTaskUi.tap]; hier wird sie nur in
 * den passenden PendingIntent uebersetzt.
 */
object VoiceTaskWidgetView {

    fun build(ctx: Context, state: VoiceTaskState, elapsedMs: Long = 0, message: String = ""): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_task)
        v.setInt(R.id.widget_root, "setBackgroundResource", background(state))
        v.setImageViewResource(R.id.widget_icon, icon(state))
        v.setInt(R.id.widget_icon, "setColorFilter", ctx.getColor(iconColor(state)))
        v.setTextColor(R.id.widget_status, ctx.getColor(textColor(state)))
        v.setTextViewText(R.id.widget_status, status(ctx, state, elapsedMs, message))
        v.setContentDescription(R.id.widget_root, contentDescription(ctx, state, elapsedMs, message))
        val intent = pendingIntent(ctx, VoiceTaskUi.tap(state))
        if (intent != null) v.setOnClickPendingIntent(R.id.widget_root, intent)
        return v
    }

    /** Alle Instanzen des Widgets neu zeichnen. Ohne Widget auf dem Startbildschirm folgenlos. */
    fun push(ctx: Context, state: VoiceTaskState, elapsedMs: Long = 0, message: String = "") {
        AppWidgetManager.getInstance(ctx).updateAppWidget(
            ComponentName(ctx, VoiceTaskWidget::class.java),
            build(ctx, state, elapsedMs, message),
        )
    }

    fun status(ctx: Context, state: VoiceTaskState, elapsedMs: Long, message: String): String = when (state) {
        VoiceTaskState.READY -> ctx.getString(R.string.widget_ready)
        VoiceTaskState.RECORDING -> VoiceTaskUi.timerText(elapsedMs)
        VoiceTaskState.WORKING -> ctx.getString(R.string.widget_working)
        VoiceTaskState.SENT -> ctx.getString(R.string.widget_sent)
        VoiceTaskState.NO_MIC -> ctx.getString(R.string.widget_no_mic)
        VoiceTaskState.OFF -> ctx.getString(R.string.widget_off)
        VoiceTaskState.ERROR -> ctx.getString(R.string.widget_error_retry, reason(ctx, message))
    }

    fun contentDescription(ctx: Context, state: VoiceTaskState, elapsedMs: Long, message: String): String = when (state) {
        VoiceTaskState.READY -> ctx.getString(R.string.cd_widget_ready)
        VoiceTaskState.RECORDING -> ctx.getString(R.string.cd_widget_recording, VoiceTaskUi.timerText(elapsedMs))
        VoiceTaskState.WORKING -> ctx.getString(R.string.cd_widget_working)
        VoiceTaskState.SENT -> ctx.getString(R.string.cd_widget_sent)
        VoiceTaskState.NO_MIC -> ctx.getString(R.string.cd_widget_no_mic)
        VoiceTaskState.OFF -> ctx.getString(R.string.widget_off)
        VoiceTaskState.ERROR -> ctx.getString(R.string.cd_widget_error, reason(ctx, message))
    }

    /** Ohne eigene Meldung bleibt es bei einem allgemeinen Hinweis statt einer leeren Zeile. */
    private fun reason(ctx: Context, message: String): String =
        message.ifBlank { ctx.getString(R.string.kb_error) }

    private fun background(state: VoiceTaskState): Int = when (state) {
        VoiceTaskState.RECORDING -> R.drawable.widget_bg_recording
        VoiceTaskState.WORKING -> R.drawable.widget_bg_working
        VoiceTaskState.SENT -> R.drawable.widget_bg_sent
        VoiceTaskState.ERROR, VoiceTaskState.NO_MIC -> R.drawable.widget_bg_error
        VoiceTaskState.READY, VoiceTaskState.OFF -> R.drawable.widget_bg_ready
    }

    private fun icon(state: VoiceTaskState): Int = when (state) {
        VoiceTaskState.RECORDING -> R.drawable.ic_stop
        VoiceTaskState.WORKING, VoiceTaskState.SENT -> R.drawable.ic_send
        VoiceTaskState.NO_MIC, VoiceTaskState.OFF -> R.drawable.ic_mic_off
        VoiceTaskState.READY, VoiceTaskState.ERROR -> R.drawable.ic_mic
    }

    private fun iconColor(state: VoiceTaskState): Int = when (state) {
        VoiceTaskState.RECORDING -> R.color.loom_recording
        VoiceTaskState.WORKING -> R.color.loom_secondary
        VoiceTaskState.SENT -> R.color.loom_success
        VoiceTaskState.ERROR, VoiceTaskState.NO_MIC -> R.color.loom_error
        VoiceTaskState.OFF -> R.color.loom_outline
        VoiceTaskState.READY -> R.color.loom_primary
    }

    private fun textColor(state: VoiceTaskState): Int = when (state) {
        VoiceTaskState.RECORDING -> R.color.loom_recordingText
        VoiceTaskState.WORKING -> R.color.loom_onSecondaryContainer
        VoiceTaskState.SENT -> R.color.loom_onSuccessContainer
        VoiceTaskState.ERROR, VoiceTaskState.NO_MIC -> R.color.loom_onErrorContainer
        VoiceTaskState.READY, VoiceTaskState.OFF -> R.color.loom_onSurfaceVariant
    }

    /**
     * Start und Wiederholung laufen ueber die Trampolin-Activity: damit ist die App beim
     * Start des Mikrofon-Dienstes real im Vordergrund, und die Hintergrund-Beschraenkungen
     * ab Android 14 koennen gar nicht erst greifen (die scheitern sonst STILL — ohne
     * Exception, nur ohne Ton). Das Beenden darf direkt an den Dienst gehen: der laeuft
     * zu dem Zeitpunkt bereits im Vordergrund.
     *
     * [TapIntent.NONE] ist der einzige Zustand ohne Tippflaeche — den vergibt [VoiceTaskUi.tap]
     * nicht, er bleibt als Rueckfallwert fuer eine Intent ohne Angabe.
     */
    private fun pendingIntent(ctx: Context, intent: TapIntent): PendingIntent? {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return when (intent) {
            TapIntent.NONE -> null
            TapIntent.STOP -> PendingIntent.getForegroundService(
                ctx, REQ_STOP,
                Intent(ctx, VoiceTaskService::class.java).setAction(VoiceTaskService.ACTION_STOP),
                flags,
            )
            else -> PendingIntent.getActivity(
                ctx, REQ_TRAMPOLINE + intent.ordinal,
                VoiceTaskTrampolineActivity.intent(ctx, intent),
                flags,
            )
        }
    }

    private const val REQ_STOP = 71
    private const val REQ_TRAMPOLINE = 80
}
