package com.chris.whisperloom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.api.ApiNotConfiguredException
import com.chris.whisperloom.api.RefineRejectedException
import com.chris.whisperloom.api.TextRefiner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * KI-Stufe fuer geteilte Sprachnachrichten (Einstellungen › Text › Geteilte Sprachnachrichten):
 * welche Stufe gilt, was je Stueck an das Modell geht und dass kein KI-Fehler die erkannte
 * Nachricht kostet. Das Modell ist hier ein Lambda — kein Netz, kein Audio.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedRefineTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs

    @Before fun setUp() {
        ctx.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
    }

    private val calls = mutableListOf<Pair<String, RefineMode>>()

    private fun run(parts: List<String>, answer: (String) -> String = { "$it (KI)" }): SharedRefine.Result {
        var started = 0
        val result = SharedRefine.run(prefs, parts, "de", onStart = { started++ }) { raw, mode ->
            calls += raw to mode
            answer(raw)
        }
        assertEquals("onStart genau dann, wenn eine Stufe gilt", if (result.mode == RefineMode.OFF) 0 else 1, started)
        return result
    }

    // --- Welche Stufe gilt ---------------------------------------------------------------

    @Test fun ausFragtDasModellNieAuchWennDasDiktatEineStufeHat() {
        prefs.refineMode = RefineMode.BEAUTIFY
        val result = run(listOf("Hallo."))
        assertEquals(SharedRefine.Result(RefineMode.OFF, null, null), result)
        assertEquals(emptyList<Pair<String, RefineMode>>(), calls)
    }

    @Test fun dieStufeKommtAusDenShareEinstellungenNichtVomDiktat() {
        prefs.refineMode = RefineMode.POLISH
        prefs.shareRefineMode = RefineMode.SUMMARIZE
        val result = run(listOf("Hallo."))
        assertEquals(RefineMode.SUMMARIZE, result.mode)
        assertEquals(listOf("Hallo." to RefineMode.SUMMARIZE), calls)
        assertEquals(listOf("Hallo. (KI)"), result.paragraphs)
        assertNull(result.skipped)
    }

    @Test fun lesbarerGlaettenHatEinenEigenenSchalter() {
        prefs.shareRefineMode = RefineMode.POLISH
        prefs.sharePolishReadable = true
        val result = run(listOf("Hallo."))
        assertEquals(listOf("Hallo." to RefineMode.READABLE), calls)
        assertEquals(RefineMode.READABLE, result.mode)
    }

    /** 3.8.6: bis 3.8.5 wirkte der eine Schalter auf beides. */
    @Test fun derSchalterDesDiktatsWirktNichtAufGeteilteAudios() {
        prefs.refineMode = RefineMode.POLISH
        prefs.shareRefineMode = RefineMode.POLISH
        prefs.polishReadable = true
        val result = run(listOf("Hallo."))
        assertEquals(listOf("Hallo." to RefineMode.POLISH), calls)
        assertEquals(RefineMode.POLISH, result.mode)
        // Und andersherum: der Schalter der Sprachnachrichten aendert das Diktat nicht.
        prefs.polishReadable = false
        prefs.sharePolishReadable = true
        assertEquals(RefineMode.POLISH, prefs.dictationStage)
    }

    // --- Was an das Modell geht -------------------------------------------------------------

    /** Review HOCH: am Stueck waere eine lange Nachricht an der Laengengrenze abgeschnitten worden. */
    @Test fun jedesStueckGehtEinzelnAnDasModell() {
        prefs.shareRefineMode = RefineMode.POLISH
        val result = run(listOf(" Erstes Stück. ", "", "  ", "Zweites Stück."))
        assertEquals(listOf("Erstes Stück." to RefineMode.POLISH, "Zweites Stück." to RefineMode.POLISH), calls)
        assertEquals(listOf("Erstes Stück. (KI)", "Zweites Stück. (KI)"), result.paragraphs)
    }

    @Test fun nichtsErkanntKostetKeineAnfrage() {
        prefs.shareRefineMode = RefineMode.POLISH
        val result = run(listOf("", "  "))
        assertEquals(emptyList<String>(), result.paragraphs)
        assertEquals(emptyList<Pair<String, RefineMode>>(), calls)
    }

    @Test fun leerzeilenDesModellsWerdenAbsaetzeStichpunkteBleibenZusammen() {
        prefs.shareRefineMode = RefineMode.SUMMARIZE
        val result = run(listOf("roh")) { "Erster Absatz.\n\n\nKernpunkte:\n- eins\n- zwei\n\n" }
        assertEquals(listOf("Erster Absatz.", "Kernpunkte:\n- eins\n- zwei"), result.paragraphs)
    }

    // --- Nachbearbeitung wie beim Diktat, Absaetze immer ------------------------------------

    @Test fun nachbearbeitungNimmtDieRegelnDesDiktats() {
        prefs.shareRefineMode = RefineMode.POLISH
        prefs.customFillers = setOf("sozusagen")
        val result = run(listOf("roh")) { "Also, ähm, das passt sozusagen.\n\nbis morgen." }
        assertEquals(listOf("Also, das passt.", "Bis morgen."), result.paragraphs)
    }

    @Test fun absaetzeBleibenAuchWennDasDiktatSieAusHat() {
        prefs.refineParagraphs = false
        prefs.customFillers = setOf("sozusagen")
        prefs.disabledFillers = setOf("hmm")
        val options = SharedRefine.options(prefs, "de", RefineMode.POLISH)
        assertTrue("Absaetze der KI bleiben", options.keepLineBreaks)
        assertEquals(setOf("sozusagen"), options.customFillers.toSet())
        assertEquals(setOf("hmm"), options.disabledFillers)
        assertTrue(options.removeFillers)
        assertTrue(options.autoCapitalize)
    }

    @Test fun intelligenteFuellwoerterPausierenDieWortlisteWieBeimDiktat() {
        prefs.smartFillers = true
        assertEquals(false, SharedRefine.options(prefs, "de", RefineMode.POLISH).removeFillers)
    }

    // --- Kein KI-Fehler kostet die Nachricht -------------------------------------------------

    /** Die leere LLM-Adresse darf die Share-Ansicht nicht auf "Kein Zugang eingerichtet" stellen. */
    @Test fun fehlenderLlmZugangWirdZumHinweis() {
        prefs.shareRefineMode = RefineMode.BEAUTIFY
        val result = run(listOf("Hallo.")) { throw ApiNotConfiguredException() }
        assertEquals(RefineMode.BEAUTIFY, result.mode)
        assertNull(result.paragraphs)
        assertEquals(ApiNotConfiguredException().message, result.skipped)
    }

    @Test fun abgeschnitteneAntwortWirdZumHinweis() {
        prefs.shareRefineMode = RefineMode.POLISH
        val result = run(listOf("Hallo.")) { throw RefineRejectedException(TextRefiner.MSG_TRUNCATED) }
        assertNull(result.paragraphs)
        assertEquals(TextRefiner.MSG_TRUNCATED, result.skipped)
    }

    @Test fun einGescheitertesStueckVerwirftDieGanzeKiFassung() {
        prefs.shareRefineMode = RefineMode.POLISH
        val result = run(listOf("Eins.", "Zwei.")) { if (it == "Zwei.") throw IllegalStateException("API-Fehler 429") else "$it (KI)" }
        assertNull("halb KI, halb Rohtext waere schlimmer", result.paragraphs)
        assertEquals("API-Fehler 429", result.skipped)
    }

    @Test fun abbruchGehtDurchStattZumHinweisZuWerden() {
        prefs.shareRefineMode = RefineMode.POLISH
        try {
            SharedRefine.run(prefs, listOf("Eins.", "Zwei."), "de", isCancelled = { calls.isNotEmpty() }) { raw, mode ->
                calls += raw to mode
                raw
            }
            fail("Abbruch muss durchgehen")
        } catch (e: UnsupportedAudioException) {
            assertEquals("nach dem ersten Stueck kein weiteres", 1, calls.size)
        }
    }
}
