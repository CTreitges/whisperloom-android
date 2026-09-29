package com.chris.whisperloom

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** Absatzregel und KI-Fassung der Share-Ansicht (UX-Spec §2.9) als reine Funktionen — ohne Android. */
class SharedAudioTranscriberTest {

    private fun normalized(s: String) = s.trim().replace(Regex("\\s+"), " ")

    private fun sentence(i: Int) = "Das ist der ganz normale Satz Nummer $i mit ein paar Woertern drin."

    @Test fun jedeStueckGrenzeIstEinAbsatz() {
        val chunks = listOf("Erstes Stück.", "Zweites Stück.", "Drittes Stück.")
        assertEquals(chunks, SharedAudioTranscriber.paragraphsForChunks(chunks))
    }

    @Test fun leerzeilenImStueckBleibenAbsatzgrenzen() {
        assertEquals(
            listOf("Oben.", "Unten."),
            SharedAudioTranscriber.paragraphsForChunks(listOf("Oben.\n\nUnten.")),
        )
        assertEquals(
            listOf("Oben.", "Unten."),
            SharedAudioTranscriber.paragraphsForChunks(listOf("Oben.\n  \n\nUnten.")),
        )
    }

    @Test fun leereStueckeFallenWeg() {
        assertEquals(
            listOf("Text."),
            SharedAudioTranscriber.paragraphsForChunks(listOf("", "   ", "Text.", "\n\n")),
        )
        assertEquals(emptyList<String>(), SharedAudioTranscriber.paragraphsForChunks(emptyList()))
    }

    @Test fun innerhalbEinesStuecksTeiltDerParagrapher() {
        val chunk = (1..7).joinToString(" ") { sentence(it) }
        val paragraphs = SharedAudioTranscriber.paragraphsForChunks(listOf(chunk, "Kurzes zweites Stück."))
        assertEquals(listOf(3, 3, 1, 1), paragraphs.map { Paragrapher.sentences(it).size })
        assertEquals(
            normalized("$chunk Kurzes zweites Stück."),
            normalized(paragraphs.joinToString(" ")),
        )
    }

    @Test fun woerterBleibenUnveraendert() {
        val chunks = listOf("Hallo   Welt, wie\ngeht es?", "Gut, ähm, danke.")
        val paragraphs = SharedAudioTranscriber.paragraphsForChunks(chunks)
        assertEquals(listOf("Hallo Welt, wie geht es?", "Gut, ähm, danke."), paragraphs)
    }

    // --- KI-Fassung (Einstellungen › Text › Geteilte Sprachnachrichten) ---------

    private val keineRegeln = PolishOptions(removeFillers = false, autoCapitalize = false, keepLineBreaks = true)

    @Test fun alleStueckeGehenAlsEinTextAnDieKi() {
        val gesehen = mutableListOf<String>()
        val absaetze = SharedAudioTranscriber.refinedParagraphs(listOf(" Erstes Stück. ", "", "Zweites Stück."), keineRegeln) {
            gesehen += it
            "Zusammenfassung."
        }
        assertEquals(listOf("Erstes Stück.\n\nZweites Stück."), gesehen)
        assertEquals(listOf("Zusammenfassung."), absaetze)
    }

    @Test fun leerzeilenDerKiWerdenAbsaetzeEinfacheUmbruecheBleiben() {
        val absaetze = SharedAudioTranscriber.refinedParagraphs(listOf("roh"), keineRegeln) {
            "Erster Absatz.\n\n\nKernpunkte:\n- eins\n- zwei\n\n"
        }
        assertEquals(listOf("Erster Absatz.", "Kernpunkte:\n- eins\n- zwei"), absaetze)
    }

    @Test fun nichtsErkanntKostetKeineAnfrage() {
        var aufrufe = 0
        val absaetze = SharedAudioTranscriber.refinedParagraphs(listOf("", "  "), keineRegeln) {
            aufrufe++
            "erfunden"
        }
        assertEquals(emptyList<String>(), absaetze)
        assertEquals(0, aufrufe)
    }

    @Test fun nachbearbeitungLaeuftWieBeimDiktat() {
        val options = PolishPlan.options(
            removeFillers = true,
            autoCapitalize = true,
            language = "de",
            refineMode = RefineMode.POLISH,
            smartFillers = false,
            paragraphs = true,
        )
        val absaetze = SharedAudioTranscriber.refinedParagraphs(listOf("roh"), options) {
            "Also, ähm, das passt.\n\nbis morgen."
        }
        assertEquals(listOf("Also, das passt.", "Bis morgen."), absaetze)
    }

    @Test fun fehlerDerKiWerdenDurchgereicht() {
        try {
            SharedAudioTranscriber.refinedParagraphs(listOf("roh"), keineRegeln) { throw IllegalStateException("API-Fehler 401") }
            fail("Fehler muss beim Aufrufer ankommen, der auf die wortgetreue Fassung zurueckfaellt")
        } catch (e: IllegalStateException) {
            assertEquals("API-Fehler 401", e.message)
        }
    }
}
