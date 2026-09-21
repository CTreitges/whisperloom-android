package com.chris.whisperloom.ui.state

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.api.AccessResolver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Der Compose-Spiegel schreibt sofort durch (Spec §1.3: kein Speichern-Button) — und er
 * bekommt mit, wenn jemand anders schreibt: die Diktat-Tastatur ist ein eigener Dienst mit
 * eigener [Prefs]-Instanz und aendert beim Schnellzugriff die Stufe direkt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrefsStateTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var state: PrefsState

    @Before fun aufbau() {
        // SharedPreferences leben prozessweit — ohne das traegt ein Test den Stand des
        // vorherigen mit sich herum.
        ctx.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        state = PrefsState(Prefs(ctx))
    }

    @After fun abbau() {
        state.dispose()
    }

    // --- Durchschreiben ------------------------------------------------------

    @Test fun schreibtSofortDurch() {
        state.engine = Engine.OFFLINE
        state.refineMode = RefineMode.BEAUTIFY
        state.customFillers = setOf("Sozusagen", "halt ")
        state.sttProviderId = "groq"
        state.tutorialSeen = true
        val fresh = Prefs(ctx)
        assertTrue(fresh.tutorialSeen)
        assertEquals(Engine.OFFLINE, fresh.engine)
        assertEquals(RefineMode.BEAUTIFY, fresh.refineMode)
        assertEquals(setOf("sozusagen", "halt"), fresh.customFillers)
        assertEquals("groq", fresh.sttProviderId)
        // Der Spiegel liefert die normalisierte Fassung nicht selbst — Anzeige ist klein geschrieben ueber den Setter-Aufrufer.
        assertEquals("https://api.groq.com/openai/v1", state.sttAccess().baseUrl)
    }

    @Test fun llmUseOwnFolgtDemProviderFeld() {
        assertFalse(state.llmUseOwn)
        state.llmProviderId = "openai"
        assertTrue(state.llmUseOwn)
        state.llmProviderId = AccessResolver.LLM_SAME
        assertFalse(state.llmUseOwn)
    }

    @Test fun positionZuruecksetzen() {
        state.prefs.floatX = 500
        state.prefs.floatY = 900
        state.resetBubblePosition()
        assertEquals(Prefs.DEFAULT_FLOAT_X, Prefs(ctx).floatX)
        assertEquals(Prefs.DEFAULT_FLOAT_Y, Prefs(ctx).floatY)
    }

    // --- Aenderungen von aussen ---------------------------------------------

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
