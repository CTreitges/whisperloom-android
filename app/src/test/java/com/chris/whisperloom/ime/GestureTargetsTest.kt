package com.chris.whisperloom.ime

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Die beiden Wisch-Ziele in ihren zwei Betriebsarten: Anzeige waehrend des Ziehens,
 * bedienbare Tasten nach dem Feststellen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GestureTargetsTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private class Fixture(root: View, reduceMotion: Boolean) {
        val discard: ImageButton = root.findViewById(R.id.gesture_discard)
        val lock: ImageButton = root.findViewById(R.id.gesture_lock)
        val targets = GestureTargets(discard, lock) { reduceMotion }
    }

    private fun fixture(reduceMotion: Boolean = true) =
        Fixture(LayoutInflater.from(ctx).inflate(R.layout.keyboard_view, null), reduceMotion)

    @Test fun vorDerGesteIstNichtsZuSehen() {
        val f = fixture()
        assertFalse(f.targets.isShown)
        assertEquals(View.GONE, f.discard.visibility)
        assertEquals(View.GONE, f.lock.visibility)
    }

    @Test fun beimZiehenSindEsNurAnzeigen() {
        val f = fixture()
        f.targets.showDragging(DictationGesture.Phase.RECORDING)
        assertTrue(f.targets.isShown)
        for (t in listOf(f.discard, f.lock)) {
            assertEquals(View.VISIBLE, t.visibility)
            // Der Finger liegt auf dem Mikrofon — hier ist nichts anzutippen.
            assertFalse("Ziel waehrend des Ziehens bedienbar", t.isClickable)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, t.importantForAccessibility)
            assertFalse(t.isActivated)
        }
    }

    @Test fun dasGetroffeneZielWirdHervorgehoben() {
        val f = fixture()
        f.targets.showDragging(DictationGesture.Phase.LOCK_ARMED)
        assertTrue("Feststellen nicht hervorgehoben", f.lock.isActivated)
        assertFalse(f.discard.isActivated)

        f.targets.showDragging(DictationGesture.Phase.CANCEL_ARMED)
        assertTrue("Verwerfen nicht hervorgehoben", f.discard.isActivated)
        assertFalse(f.lock.isActivated)

        f.targets.showDragging(DictationGesture.Phase.RECORDING)
        assertFalse(f.discard.isActivated)
        assertFalse(f.lock.isActivated)
    }

    @Test fun festgestelltWerdenEsBedienbareTasten() {
        val f = fixture()
        f.targets.showLocked()
        for (t in listOf(f.discard, f.lock)) {
            assertEquals(View.VISIBLE, t.visibility)
            assertTrue("Taste im festgestellten Zustand nicht bedienbar", t.isClickable)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, t.importantForAccessibility)
            assertFalse("Hervorhebung gehoert zum Ziehen, nicht zum Feststellen", t.isActivated)
        }
        // Rechts steht jetzt Senden statt Feststellen — die Aufnahme steht ja schon.
        assertEquals(ctx.getString(R.string.cd_kb_send), f.lock.contentDescription)
        assertEquals(ctx.getString(R.string.cd_kb_discard), f.discard.contentDescription)
    }

    @Test fun verbergenSetztDasFeststellenSymbolZurueck() {
        val f = fixture()
        f.targets.showLocked()
        f.targets.hide()
        assertFalse(f.targets.isShown)
        for (t in listOf(f.discard, f.lock)) {
            assertEquals(View.GONE, t.visibility)
            assertFalse(t.isClickable)
            assertFalse(t.isActivated)
        }
        assertEquals(ctx.getString(R.string.cd_kb_lock), f.lock.contentDescription)
    }

    @Test fun ausDemZiehenDirektInsFeststellen() {
        val f = fixture()
        f.targets.showDragging(DictationGesture.Phase.LOCK_ARMED)
        f.targets.showLocked()
        assertTrue(f.lock.isClickable)
        assertFalse("Hervorhebung muss beim Feststellen verschwinden", f.lock.isActivated)
    }

    @Test fun beiReduziertenAnimationenSindDieZieleSofortVollSichtbar() {
        val f = fixture(reduceMotion = true)
        f.targets.showDragging(DictationGesture.Phase.RECORDING)
        for (t in listOf(f.discard, f.lock)) {
            assertEquals(1f, t.alpha, 0f)
            assertEquals(1f, t.scaleX, 0f)
            assertEquals(1f, t.scaleY, 0f)
        }
    }

    @Test fun wiederholtesZeigenBlendetNichtErneutEin() {
        val f = fixture(reduceMotion = false)
        f.targets.showDragging(DictationGesture.Phase.RECORDING)
        f.discard.alpha = 1f
        f.targets.showDragging(DictationGesture.Phase.LOCK_ARMED)
        // Waere die Einblendung erneut gelaufen, stuende alpha wieder auf 0.
        assertEquals(1f, f.discard.alpha, 0f)
    }
}
