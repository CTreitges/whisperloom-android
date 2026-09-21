package com.chris.whisperloom.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.chris.whisperloom.AppNav
import com.chris.whisperloom.R

/**
 * Pflicht-Notification des Aufnahme-Dienstes. Als eigenes Objekt, damit der Builder ohne
 * laufenden Dienst pruefbar ist — dasselbe Muster wie beim schwebenden Knopf.
 */
object VoiceTaskNotification {

    const val ID = 43
    const val CHANNEL_ID = "whisperloom_agent"

    fun ensureChannel(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, ctx.getString(R.string.agent_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    fun build(ctx: Context): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val stop = PendingIntent.getService(
            ctx, 3,
            Intent(ctx, VoiceTaskService::class.java).setAction(VoiceTaskService.ACTION_STOP),
            flags,
        )
        return Notification.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_whisperloom)
            .setColor(ctx.getColor(R.color.loom_recording))
            .setContentTitle(ctx.getString(R.string.agent_notif_title))
            .setContentText(ctx.getString(R.string.agent_notif_recording))
            .setContentIntent(PendingIntent.getActivity(ctx, 4, AppNav.agent(ctx), flags))
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(ctx, R.drawable.ic_send), ctx.getString(R.string.agent_notif_stop), stop,
                ).build(),
            )
            .setOngoing(true)
            .build()
    }
}
