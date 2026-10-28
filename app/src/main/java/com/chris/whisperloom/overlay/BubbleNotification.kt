package com.chris.whisperloom.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.text.format.DateFormat
import com.chris.whisperloom.AppNav
import com.chris.whisperloom.R
import java.util.Date

/**
 * Foreground-Notification des schwebenden Knopfs (UX-Spec §5.5, N1): weisse Silhouette als
 * Small-Icon, Akzentfarbe loom_primary (waehrend RECORDING loom_recording), Aktion "Beenden",
 * Tipp oeffnet Home. Ausgeblendet zusaetzlich "Einblenden" (§6.21). Als eigenes Objekt, damit der Builder ohne laufenden Service testbar ist.
 */
object BubbleNotification {

    const val ID = 42
    const val CHANNEL_ID = "whisperloom_float"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, ctx.getString(R.string.float_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    /**
     * [snoozedUntil] > 0: der Knopf ist bis zu dieser Uhrzeit (Wanduhr, ms) ausgeblendet — Text mit
     * Uhrzeit und Aktion "Einblenden" vor "Beenden" (UX-Spec §6.21).
     */
    fun build(ctx: Context, state: BubbleState, snoozedUntil: Long = 0L): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val stop = PendingIntent.getService(
            ctx, 1,
            Intent(ctx, FloatingMicService::class.java).setAction(FloatingMicService.ACTION_STOP),
            flags,
        )
        val open = PendingIntent.getActivity(ctx, 2, AppNav.home(ctx), flags)
        val color = ctx.getColor(if (state == BubbleState.RECORDING) R.color.loom_recording else R.color.loom_primary)
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(ctx, R.drawable.ic_stop), ctx.getString(R.string.float_stop), stop,
        ).build()
        val snoozed = snoozedUntil > 0L
        val text = if (snoozed) {
            ctx.getString(R.string.float_snoozed_text, DateFormat.getTimeFormat(ctx).format(Date(snoozedUntil)))
        } else {
            ctx.getString(R.string.float_running_text)
        }
        val builder = Notification.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_whisperloom)
            .setColor(color)
            .setContentTitle(ctx.getString(R.string.float_running))
            .setContentText(text)
            .setContentIntent(open)
        if (snoozed) {
            // Ein Tipp auf eine Notification-Aktion ist ein zulaessiger Ausloeser fuer den Mikrofon-FGS.
            val show = PendingIntent.getService(
                ctx, 3,
                Intent(ctx, FloatingMicService::class.java).setAction(FloatingMicService.ACTION_SNOOZE_END),
                flags,
            )
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(ctx, R.drawable.ic_visibility), ctx.getString(R.string.float_snooze_end), show,
                ).build(),
            )
        }
        return builder
            .addAction(stopAction)
            .setOngoing(true)
            .build()
    }
}
