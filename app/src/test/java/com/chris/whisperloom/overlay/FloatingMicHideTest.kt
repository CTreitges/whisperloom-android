package com.chris.whisperloom.overlay

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import com.chris.whisperloom.a11y.FieldKey
import com.chris.whisperloom.llm.OfflineRefineFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.time.Duration
import kotlin.math.roundToInt

/**
 * Ausblenden bis zum naechsten Feld (UX-Spec §6.22): Ablegen aufs Ziel unten in Ruhe nimmt den
 * Knopf weg, bis die Bedienungshilfe ein anderes Feld meldet. Der Dienst laeuft weiter; es gibt
 * keine Frist und keine Notification-Aktion "Einblenden".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingMicHideTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val fixture = OfflineRefineFixture(app)
    private var controller: ServiceController<FloatingMicService>? = null
    private lateinit var shadowWm: ShadowWindowManagerImpl
    private lateinit var root: View
    private lateinit var bubble: View

    private val feldA = FieldKey(windowId = 7, nodeId = 101)
    private val feldB = FieldKey(windowId = 7, nodeId = 202)

    @Before fun aufbau() {
        fixture.setUp()
        ShadowSettings.setCanDrawOverlays(true)
        val c = Robolectric.buildService(FloatingMicService::class.java).create()
        controller = c
        shadowWm = Shadow.extract(c.get().getSystemService(Context.WINDOW_SERVICE))
        root = shadowWm.views.single()
        bubble = root.findViewById(R.id.bubble)
    }

    @After fun abbau() {
        controller?.destroy()
        fixture.tearDown()
    }

    private fun warten(ms: Long = 0) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun fokus(feld: FieldKey) {
        FloatingMicService.fieldFocused(feld)
        warten()
    }

    private var downAt = 0L

    private fun touch(action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downAt = now
        val e = MotionEvent.obtain(downAt, now, action, x, y, 0)
        bubble.dispatchTouchEvent(e)
        e.recycle()
    }

    /** Zieht den Knopf so, dass seine Mitte bei ([toX], [toY]) liegt, ohne loszulassen. */
    private fun ziehenNach(toX: Int, toY: Int) {
        val lp = root.layoutParams as WindowManager.LayoutParams
        val dx = toX - root.width / 2 - lp.x
        val dy = toY - root.height / 2 - lp.y
        touch(MotionEvent.ACTION_DOWN, 10f, 10f)
        touch(MotionEvent.ACTION_MOVE, 10f + dx, 10f + dy)
    }

    /** Mitte des Ziels unten (CancelTarget: 96 dp ueber dem Rand, 72 dp gross). */
    private fun zielMitte(): Pair<Int, Int> {
        val dm = app.resources.displayMetrics
        val size = (BubblePosition.CANCEL_SIZE_DP * dm.density).roundToInt()
        val top = dm.heightPixels - (BubblePosition.CANCEL_MARGIN_DP * dm.density).roundToInt() - size
        return dm.widthPixels / 2 to top + size / 2
    }

    private fun aufsZielAblegen() {
        val (x, y) = zielMitte()
        ziehenNach(x, y)
        touch(MotionEvent.ACTION_UP, 0f, 0f)
        warten()
    }

    private fun zielLabel(): String? = shadowWm.views
        .mapNotNull { it.findViewById<TextView>(R.id.cancel_label) }
        .singleOrNull()?.text?.toString()

    private fun notification(): Notification =
        shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(BubbleNotification.ID)

    /** Ein Punkt im oberen Viertel, weit weg vom Ziel unten. */
    private fun abseits(): Pair<Int, Int> {
        val dm = app.resources.displayMetrics
        return dm.widthPixels / 4 to dm.heightPixels / 4
    }

    @Test fun zielInRuheIstAusblenden() {
        val (x, y) = abseits()
        ziehenNach(x, y)
        assertEquals(app.getString(R.string.float_hide_label), zielLabel())
    }

    @Test fun zielInDerAufnahmeBleibtVerwerfen() {
        bubble.performClick()
        fixture.awaitRecorded()
        val (x, y) = abseits()
        ziehenNach(x, y)
        assertEquals(app.getString(R.string.float_cancel_label), zielLabel())
    }

    @Test fun gleichesFeldBleibtVersteckt_anderesFeldZeigtWieder() {
        fokus(feldA)
        aufsZielAblegen()
        assertEquals(View.GONE, root.visibility)
        assertTrue("Dienst laeuft weiter", FloatingMicService.isRunning)

        fokus(feldA)
        assertEquals("gleiches Feld: weiter versteckt", View.GONE, root.visibility)

        fokus(feldB)
        assertEquals("anderes Feld: wieder da", View.VISIBLE, root.visibility)
    }

    /** Kommt der Fokus nach dem Wechsel zurueck ins erste Feld, ist das ein neuer Fokus. */
    @Test fun nachDemWiederauftauchenGiltDasFeldNeu() {
        fokus(feldA)
        aufsZielAblegen()
        fokus(feldB)
        fokus(feldA)
        assertEquals(View.VISIBLE, root.visibility)
    }

    @Test fun ohneBekanntesFeldZeigtDasNaechsteFeld() {
        aufsZielAblegen()
        assertEquals(View.GONE, root.visibility)
        fokus(feldA)
        assertEquals(View.VISIBLE, root.visibility)
    }

    @Test fun keineFristKeinEinblenden() {
        fokus(feldA)
        aufsZielAblegen()
        warten(60 * 60_000L)
        assertEquals("ohne neues Feld bleibt er weg, auch nach einer Stunde", View.GONE, root.visibility)
        assertEquals(
            listOf(app.getString(R.string.float_stop)),
            notification().actions.orEmpty().map { it.title.toString() },
        )
    }

    @Test fun ablegenNebenDemZielVerschiebtNur() {
        fokus(feldA)
        val (x, y) = abseits()
        ziehenNach(x, y)
        touch(MotionEvent.ACTION_UP, 0f, 0f)
        warten()
        assertEquals(View.VISIBLE, root.visibility)
    }
}
