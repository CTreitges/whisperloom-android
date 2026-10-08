package com.chris.whisperloom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM-Unit-Tests fuer die Absatz-Heuristik geteilter Sprachnachrichten. */
class ParagrapherTest {

    private fun normalized(s: String) = s.trim().replace(Regex("\\s+"), " ")

    private fun sentence(i: Int) = "Das ist der ganz normale Satz Nummer $i mit ein paar Woertern drin."

    @Test fun leerBleibtLeer() {
        assertEquals(emptyList<String>(), Paragrapher.split(""))
        assertEquals(emptyList<String>(), Paragrapher.split("   \n "))
    }

    @Test fun kurzerTextIstEinAbsatz() {
        val t = "Hallo. Wie geht es dir? Mir geht es gut."
        assertEquals(listOf(t), Paragrapher.split(t))
    }

    @Test fun langerTextWirdGeteiltUndWoerterBleibenUnveraendert() {
        val text = (1..14).joinToString("  ") { sentence(it) }
        val paragraphs = Paragrapher.split(text)
        assertTrue("erwartet mehrere Absaetze, war ${paragraphs.size}", paragraphs.size >= 2)
        assertEquals(normalized(text), normalized(paragraphs.joinToString(" ")))
        for (p in paragraphs) {
            assertTrue("Absatz endet mitten im Satz: $p", p.endsWith("."))
            assertTrue("Absatz zu lang: ${p.length}", p.length <= 600)
        }
    }

    @Test fun absatzwechselBevorzugtVorDiskursmarker() {
        // Nach ~300 Zeichen kommt "Ausserdem …" — dort soll der Absatz beginnen.
        val text = (1..5).joinToString(" ") { sentence(it) } +
            " Außerdem wollte ich noch etwas sagen. " + (6..9).joinToString(" ") { sentence(it) }
        val paragraphs = Paragrapher.split(text)
        assertTrue(paragraphs.any { it.startsWith("Außerdem") })
        assertEquals(normalized(text), normalized(paragraphs.joinToString(" ")))
    }

    @Test fun abkuerzungenUndOrdnungszahlenTrennenKeinenSatz() {
        val s = Paragrapher.sentences("Wir treffen uns am 3. Oktober, z. B. um 10 Uhr bzw. spaeter. Danach essen wir.")
        assertEquals(
            listOf("Wir treffen uns am 3. Oktober, z. B. um 10 Uhr bzw. spaeter.", "Danach essen wir."),
            s,
        )
        assertEquals(
            listOf("Das gilt z.B. hier, d.h. ueberall.", "Danach essen wir."),
            Paragrapher.sentences("Das gilt z.B. hier, d.h. ueberall. Danach essen wir."),
        )
    }

    @Test fun satzgrenzenMitVerschiedenenZeichen() {
        assertEquals(
            listOf("Echt?", "Ja!", "Okay…", "Gut."),
            Paragrapher.sentences("Echt? Ja! Okay… Gut."),
        )
    }

    @Test fun kurzerSchlusssatzHaengtSichAn() {
        val text = (1..8).joinToString(" ") { sentence(it) } + " Tschüss."
        val paragraphs = Paragrapher.split(text)
        assertTrue(paragraphs.last().endsWith("Tschüss."))
        assertTrue(paragraphs.last().length > "Tschüss.".length)
    }

    // UX-Spec §2.9: nach 3 Saetzen beginnt ein neuer Absatz …
    @Test fun hoechstensDreiSaetzeProAbsatz() {
        val text = (1..7).joinToString(" ") { sentence(it) }
        val paragraphs = Paragrapher.split(text)
        assertEquals(listOf(3, 3, 1), paragraphs.map { Paragrapher.sentences(it).size })
        assertEquals(normalized(text), normalized(paragraphs.joinToString(" ")))
    }

    // … oder sobald 350 Zeichen erreicht sind: zwei 200-Zeichen-Saetze reichen.
    @Test fun absatzEndetSobald350ZeichenErreichtSind() {
        val long = "Wort ".repeat(40).trim() + "."
        assertEquals(200, long.length)
        val paragraphs = Paragrapher.split(List(4) { long }.joinToString(" "))
        assertEquals(listOf(2, 2), paragraphs.map { Paragrapher.sentences(it).size })
    }

    // Bewusste Ausnahme: ein einzelner kurzer Schlusssatz bleibt nicht allein stehen.
    @Test fun einzelnerKurzerSchlusssatzWirdAngehaengt() {
        val text = (1..9).joinToString(" ") { sentence(it) } + " Tschüss."
        val paragraphs = Paragrapher.split(text)
        assertEquals(3, paragraphs.size)
        assertTrue(paragraphs.last().endsWith("Tschüss."))
        assertEquals(normalized(text), normalized(paragraphs.joinToString(" ")))
    }

    @Test fun ohneSatzzeichenBleibtEinBlock() {
        val words = (1..200).joinToString(" ") { "wort$it" }
        assertEquals(listOf(words), Paragrapher.split(words))
    }
}
