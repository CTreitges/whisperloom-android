package com.chris.whisperloom.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.PolishCleanup
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.RefineWay
import com.chris.whisperloom.Refinement
import com.chris.whisperloom.SummarizeForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verarbeitung im Verlauf: aus dem Auftrag (Stufe samt Bereinigung) und zurueck mit den aktuellen Einstellungen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProcessingTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs

    @Before fun aufbau() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
    }

    @Test fun jedeDiktatVerarbeitungHatIhreFassung() {
        val way = RefineWay.DICTATION
        val faelle = mapOf(
            Refinement.OFF to Processing.OFF,
            Refinement.of(way, RefineMode.POLISH, PolishCleanup.PLAIN, true, SummarizeForm.AUTO) to Processing.POLISH_PLAIN,
            Refinement.of(way, RefineMode.POLISH, PolishCleanup.CLEAN, false, SummarizeForm.AUTO) to Processing.POLISH_CLEAN,
            Refinement.of(way, RefineMode.POLISH, PolishCleanup.READABLE, true, SummarizeForm.AUTO) to Processing.POLISH_READABLE,
            Refinement.of(way, RefineMode.BEAUTIFY, PolishCleanup.CLEAN, true, SummarizeForm.AUTO) to Processing.BEAUTIFY,
            Refinement.of(way, RefineMode.SUMMARIZE, PolishCleanup.PLAIN, true, SummarizeForm.PROSE) to Processing.SUMMARIZE,
            Refinement.of(way, RefineMode.PROMPT, PolishCleanup.PLAIN, true, SummarizeForm.AUTO) to Processing.PROMPT,
        )
        for ((refinement, processing) in faelle) assertEquals(refinement.toString(), processing, Processing.of(refinement))
        assertEquals("ohne Bearbeitet hat jede Verarbeitung ihren Auftrag", Processing.entries - Processing.EDITED, faelle.values.toList())
    }

    @Test fun neuVerarbeitenNimmtDieAktuellenDiktatEinstellungen() {
        prefs.setParagraphsFor(RefineMode.POLISH, false)
        prefs.setSummarizeFormFor(RefineWay.DICTATION, SummarizeForm.PROSE)
        // Die Bereinigung kommt aus der Verarbeitung, nicht aus den Einstellungen.
        prefs.setPolishCleanupFor(RefineWay.DICTATION, PolishCleanup.READABLE)

        assertEquals(Refinement(RefineMode.POLISH, paragraphs = false), Processing.POLISH_PLAIN.refinement(prefs))
        assertEquals(Refinement(RefineMode.POLISH, smartFillers = true, paragraphs = false), Processing.POLISH_CLEAN.refinement(prefs))
        assertEquals(Refinement(RefineMode.READABLE, paragraphs = false), Processing.POLISH_READABLE.refinement(prefs))
        assertEquals(Refinement(RefineMode.BEAUTIFY), Processing.BEAUTIFY.refinement(prefs))
        assertEquals(Refinement(RefineMode.SUMMARIZE, paragraphs = false), Processing.SUMMARIZE.refinement(prefs))
        assertEquals(Refinement.OFF, Processing.OFF.refinement(prefs))
        assertNull(Processing.EDITED.refinement(prefs))
        for (p in Processing.entries - Processing.EDITED) assertEquals(p, Processing.of(p.refinement(prefs)!!))
    }

    @Test fun schluesselSindStabil() {
        assertEquals(
            listOf("off", "polish", "polish_clean", "polish_readable", "beautify", "summarize", "prompt", "edited"),
            Processing.entries.map { it.key },
        )
        assertEquals(Processing.POLISH_READABLE, Processing.fromKey("polish_readable"))
        assertNull(Processing.fromKey("paragraphs"))
    }
}
