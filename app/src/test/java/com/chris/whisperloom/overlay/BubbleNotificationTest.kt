package com.chris.whisperloom.overlay

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Akzeptanz N1: Silhouette als Small-Icon, Farbe je Zustand, Beenden-Aktion, Tipp -> Home. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BubbleNotificationTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun smallIconIstDieSilhouette() {
        BubbleNotification.ensureChannel(ctx)
        val n = BubbleNotification.build(ctx, BubbleState.IDLE)
        assertEquals(R.drawable.ic_stat_whisperloom, n.smallIcon.resId)
    }

    @Test fun contentIntentUndBeendenAktionSindGesetzt() {
        val n = BubbleNotification.build(ctx, BubbleState.IDLE)
        assertNotNull("contentIntent (Tipp -> Home) fehlt", n.contentIntent)
        assertEquals(1, n.actions.size)
        assertEquals(ctx.getString(R.string.float_stop), n.actions[0].title.toString())
        assertEquals(R.drawable.ic_stop, n.actions[0].getIcon().resId)
        assertNotNull(n.actions[0].actionIntent)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(BubbleNotification.CHANNEL_ID, n.channelId)
        assertEquals(ctx.getString(R.string.float_running), n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    /** UX-Spec §6.22: ausgeblendet nennt die Uhrzeit und bietet "Einblenden" vor "Beenden". */
    @Test fun ausgeblendetMitEinblendenAktion() {
        val until = 1_700_000_000_000L
        val n = BubbleNotification.build(ctx, BubbleState.IDLE, snoozedUntil = until)
        val time = android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date(until))
        assertEquals(ctx.getString(R.string.float_snoozed_text, time), n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(2, n.actions.size)
        assertEquals(ctx.getString(R.string.float_snooze_end), n.actions[0].title.toString())
        assertEquals(R.drawable.ic_visibility, n.actions[0].getIcon().resId)
        val intent = shadowOf(n.actions[0].actionIntent).savedIntent
        assertEquals(FloatingMicService.ACTION_SNOOZE_END, intent.action)
        assertEquals(ctx.getString(R.string.float_stop), n.actions[1].title.toString())
    }

    @Test fun ohneAusblendenWieBisher() {
        val n = BubbleNotification.build(ctx, BubbleState.IDLE)
        assertEquals(ctx.getString(R.string.float_running_text), n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test fun farbeFolgtDerAufnahme() {
        assertEquals(ctx.getColor(R.color.loom_primary), BubbleNotification.build(ctx, BubbleState.IDLE).color)
        assertEquals(ctx.getColor(R.color.loom_primary), BubbleNotification.build(ctx, BubbleState.SENDING).color)
        assertEquals(ctx.getColor(R.color.loom_recording), BubbleNotification.build(ctx, BubbleState.RECORDING).color)
    }
}
