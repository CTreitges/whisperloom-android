package com.chris.whisperloom.overlay

import android.app.Application
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.RefineMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.Proxy

/** Fenster des Modus-Kreuzes (docs/design/bubble-textfeld.md §3): Anzeige und Pillenbreite. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // echte Textbreiten fuer pillWidthDp
@Config(sdk = [35])
class ModeCrossWindowTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val modes = RefineMode.settings(promptEnabled = true)

    /** WindowManager, der jedes addView ablehnt (Overlay-Recht entzogen, BadTokenException). */
    private fun refusingWm(): WindowManager =
        Proxy.newProxyInstance(WindowManager::class.java.classLoader, arrayOf(WindowManager::class.java)) { _, method, _ ->
            if (method.name == "addView") throw WindowManager.BadTokenException("kein Overlay-Recht")
            null
        } as WindowManager

    @Test fun abgelehntesFensterGiltNichtAlsAngezeigt() {
        val window = ModeCrossWindow(app, refusingWm())
        val layout = ModeCross.layout(500f, 1000f, ModeCross.Area(0, 0, 1000, 2000), modes, density = 1f)
        window.show(layout, RefineMode.POLISH)
        assertFalse(window.isShown)
        assertNull("ohne Fenster kein Ziel waehlbar", window.layout)
    }

    @Test fun normaleSchriftBehaeltDiePillenbreite() {
        assertEquals(ModeCross.PILL_W_DP, ModeCrossWindow.pillWidthDp(app, modes), 0.01f)
    }

    @Test fun grosseSchriftVerbreitertDiePille() {
        RuntimeEnvironment.setFontScale(2f)
        val ctx: Application = ApplicationProvider.getApplicationContext()
        assertTrue(ModeCrossWindow.pillWidthDp(ctx, modes) > ModeCross.PILL_W_DP)
    }
}
