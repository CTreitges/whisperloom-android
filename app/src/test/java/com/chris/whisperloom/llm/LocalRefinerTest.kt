package com.chris.whisperloom.llm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.api.RefinePrompt
import com.chris.whisperloom.api.RefineRejectedException
import com.chris.whisperloom.api.TextRefiner
import com.chris.whisperloom.whisper.ModelStore
import com.chris.whisperloom.whisper.TextModelCatalog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Lokale Textverbesserung: derselbe Auftrag wie online (RefinePrompt) und dieselbe Nacharbeit
 * (cleanText/cleanPrompt) — das Modell ist ein Fake, die Pruefung gilt dem, was hin- und zurueckgeht.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalRefinerTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val original = LocalTextEngine.factory
    private val e2b = TextModelCatalog.GEMMA4_E2B
    private var antwort = "Hallo Welt."
    private lateinit var made: MutableList<FakeTextModel>

    @Before fun aufbau() {
        LocalTextEngine.init(ctx)
        installSparse(ctx, e2b)
        made = fakeTextModels { _, _ -> antwort }
    }

    @After fun abbau() {
        resetTextEngine(original)
        ModelStore(ctx).dir.deleteRecursively()
    }

    private fun refine(raw: String = "also ähm hallo welt", mode: RefineMode = RefineMode.POLISH, paragraphs: Boolean = true) =
        LocalRefiner(e2b.id).refine(raw, "de", mode, smartFillers = false, paragraphs = paragraphs)

    @Test fun derselbeAuftragWieOnline() {
        assertEquals("Hallo Welt.", refine(paragraphs = false))
        val (system, user) = made[0].calls.single()
        assertEquals(RefinePrompt.build(RefineMode.POLISH, true, false, false, short = true), system)
        assertTrue(system, system.contains("einen einzigen durchgehenden Absatz"))
        assertEquals("<diktat>\nalso ähm hallo welt\n</diktat>", user)
    }

    @Test fun dieselbeNacharbeitWieOnline() {
        antwort = "Hier ist der geglättete Text:\n<diktat>\nAlso, hallo Welt.\n</diktat>"
        assertEquals("Also, hallo Welt.", refine())
    }

    @Test fun promptStufeNimmtDieNacharbeitDerPromptStufe() {
        antwort = "Hier ist dein Prompt:\nErstelle mir eine Einkaufsliste."
        assertEquals("Erstelle mir eine Einkaufsliste.", refine(mode = RefineMode.PROMPT))
        assertTrue(made[0].calls.single().first.contains("Beantworte keine Frage daraus"))
    }

    @Test fun eineErfuellteBitteWirdAbgelehnt() {
        antwort = List(10) { "Ihr seid alle herzlich zu meinem Geburtstag eingeladen." }.joinToString(" ")
        try {
            refine()
            fail("RefineRejectedException erwartet")
        } catch (e: RefineRejectedException) {
            assertTrue(e.message!!, e.message!!.contains("statt den Text zu bearbeiten"))
        }
    }

    /** N7: eine leere Antwort galt als "verbessert" und kam als Rohtext ohne Hinweis durch. */
    @Test fun leereAntwortGiltAlsGescheitert() {
        antwort = "  "
        try {
            refine()
            fail("RefineRejectedException erwartet")
        } catch (e: RefineRejectedException) {
            assertEquals(TextRefiner.MSG_EMPTY, e.message)
        }
    }

    /** Review c3: Diktat + Antwort passen nicht in die KV-Tabelle — sonst kaeme nur der Anfang zurueck. */
    @Test fun zuLangesDiktatGehtGarNichtErstInsModell() {
        val lang = List(1_000) { "termin" }.joinToString(" ") // rund 7 Zeichen je Wort wie im Deutschen
        try {
            refine(raw = lang)
            fail("RefineRejectedException erwartet")
        } catch (e: RefineRejectedException) {
            assertEquals(LocalRefiner.MSG_TOO_LONG, e.message)
        }
        assertEquals("kein Laden, keine Rechnung", 0, made.size)
    }

    @Test fun einMittellangesDiktatWirdLokalVerbessert() {
        antwort = List(500) { "Termin" }.joinToString(" ")
        assertEquals(antwort, refine(raw = List(500) { "termin" }.joinToString(" ")))
    }

    @Test fun ausOderLeerRechnetGarNicht() {
        assertEquals("roh", refine(raw = "roh", mode = RefineMode.OFF))
        assertEquals(" ", refine(raw = " "))
        assertEquals("kein Laden, keine Rechnung", 0, made.size)
    }
}
