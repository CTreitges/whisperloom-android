package com.chris.whisperloom.ui.state

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Der Compose-Spiegel muss Schreibzugriffe von aussen mitbekommen: die Diktat-Tastatur ist
 * ein eigener Dienst mit eigener [Prefs]-Instanz und schreibt beim Schnellzugriff direkt.
 * Ohne den Horcher zeigte der Einstellungs-Screen danach weiter den alten Wert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrefsStateTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var state: PrefsState

    @Before fun aufbau() {
        state = PrefsState(Prefs(ctx))
    }

    @After fun abbau() {
        state.dispose()
    }

    @Test fun eineAenderungVonAussenKommtAn() {
        state.refineMode = RefineMode.OFF
        assertEquals(RefineMode.OFF, state.refineMode)

        // Das tut die Tastatur: eigene Prefs-Instanz, direkt geschrieben.
        Prefs(ctx).refineMode = RefineMode.BEAUTIFY

        assertEquals(RefineMode.BEAUTIFY, state.refineMode)
    }

    @Test fun auchDieUebrigenFelderZiehenNach() {
        state.trailingSpace = true
        Prefs(ctx).trailingSpace = false
        assertEquals(false, state.trailingSpace)
    }

    @Test fun eigeneSchreibzugriffeWirkenWeiterhinSofort() {
        state.refineMode = RefineMode.SUMMARIZE
        assertEquals(RefineMode.SUMMARIZE, state.refineMode)
        assertEquals(RefineMode.SUMMARIZE, Prefs(ctx).refineMode)
    }

    @Test fun nachDisposeKommtNichtsMehrAn() {
        state.refineMode = RefineMode.OFF
        state.dispose()
        Prefs(ctx).refineMode = RefineMode.POLISH
        assertEquals("Horcher haette abgemeldet sein muessen", RefineMode.OFF, state.refineMode)
    }
}
