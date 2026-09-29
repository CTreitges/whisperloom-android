package com.chris.whisperloom

import org.junit.Assert.assertEquals
import org.junit.Test

/** Absatzregel der Share-Ansicht (UX-Spec §2.9) als reine Funktion — ohne Android. */
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
}
