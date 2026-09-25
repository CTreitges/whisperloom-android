package com.chris.whisperloom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-Unit-Tests fuer die reine Textveredelung. Laeuft ohne Android/Emulator
 * (Gradle `test`-Task), deckt Fuellwort-Entfernung, Gross-Schreibung,
 * Whitespace- und Satzzeichen-Normalisierung ab.
 */
class TextPolisherTest {

    private val full = PolishOptions(removeFillers = true, autoCapitalize = true, language = "auto")

    @Test fun leererInputBleibtLeer() {
        assertEquals("", TextPolisher.polish("", full))
        assertEquals("", TextPolisher.polish("   \n  ", full))
    }

    @Test fun whitespaceWirdNormalisiert() {
        // Nur Satzanfang gross (kein Title-Case) -> "welt" bleibt klein.
        assertEquals("Hallo welt", TextPolisher.polish("  hallo   welt  ", full))
        assertEquals("Hallo welt", TextPolisher.polish("hallo\n\twelt", full))
    }

    @Test fun ersterBuchstabeGross() {
        assertEquals("Das ist ein Test.", TextPolisher.polish("das ist ein Test.", full))
    }

    @Test fun grossNachSatzende() {
        assertEquals(
            "Hallo. Wie geht's? Gut!",
            TextPolisher.polish("hallo. wie geht's? gut!", full),
        )
    }

    // --- Satzende nur mit Leerraum danach (Regression, Stufe "Prompt") --------------------

    @Test fun punktInZahlenUndDateinamenBeendetKeinenSatz() {
        assertEquals(
            "Was ist neu in Python 3.13 gegenüber 3.12?",
            TextPolisher.polish("was ist neu in Python 3.13 gegenüber 3.12?", full),
        )
        assertEquals("Lohnt sich GPT 4.1 mini", TextPolisher.polish("lohnt sich GPT 4.1 mini", full))
        assertEquals("Öffne config.yaml und www.example.com", TextPolisher.polish("öffne config.yaml und www.example.com", full))
    }

    @Test fun satzendeMitAnfuehrungszeichenOderKlammerZaehltWeiter() {
        assertEquals("Er sagte „Stopp.“ Dann ging er.", TextPolisher.polish("er sagte „Stopp.“ dann ging er.", full))
        assertEquals("(Siehe oben.) Weiter geht's.", TextPolisher.polish("(siehe oben.) weiter geht's.", full))
    }

    @Test fun zeilenanfangMitZifferBleibtKlein() {
        val lines = full.copy(keepLineBreaks = true)
        assertEquals(
            "Make me a plan.\n- 12 people, 2 of them vegan",
            TextPolisher.polish("make me a plan.\n- 12 people, 2 of them vegan", lines),
        )
        assertEquals("Hallo.\nWelt", TextPolisher.polish("hallo.\nwelt", lines))
    }

    @Test fun punktVorWortBleibtVomVorwortGetrennt() {
        assertEquals(
            "Lösche alle .log-Dateien und die Datei .env.",
            TextPolisher.polish("lösche alle .log-Dateien und die Datei .env .", full),
        )
        assertEquals("Ende. Hallo, welt!", TextPolisher.polish("ende . hallo , welt !", full))
    }

    @Test fun deutscheFuellwoerterEntfernt() {
        assertEquals(
            "Ich denke das ist gut.",
            TextPolisher.polish("ich ähm denke äh das ist gut.", PolishOptions(language = "de")),
        )
    }

    @Test fun fuellwortGrossgeschriebenAmSatzanfangEntfernt() {
        // "Ähm" am Anfang muss weg, danach wird korrekt gross geschrieben.
        assertEquals(
            "Also gut.",
            TextPolisher.polish("Ähm also gut.", PolishOptions(language = "de")),
        )
    }

    @Test fun englischeFuellwoerterEntfernt() {
        assertEquals(
            "I think this works.",
            TextPolisher.polish("i um think uh this works.", PolishOptions(language = "en")),
        )
    }

    @Test fun echteWoerterBleibenErhalten() {
        // "um" ist hier Teilstring von "umsonst" / eigenstaendiges dt. Wort -> nicht anfassen bei de.
        assertEquals(
            "Das war umsonst.",
            TextPolisher.polish("das war umsonst.", PolishOptions(language = "de")),
        )
    }

    @Test fun fuellwortInWortGrenzeNichtEntfernt() {
        // "erm" darf nicht aus "Determinante" gerissen werden.
        assertEquals(
            "Determinante",
            TextPolisher.polish("Determinante", PolishOptions(language = "en")),
        )
    }

    @Test fun leerzeichenVorSatzzeichenEntfernt() {
        assertEquals(
            "Hallo, welt!",
            TextPolisher.polish("hallo , welt !", full),
        )
    }

    @Test fun fuellwortEntfernungAbschaltbar() {
        assertEquals(
            "Ich ähm denke.",
            TextPolisher.polish("ich ähm denke.", PolishOptions(removeFillers = false, language = "de")),
        )
    }

    @Test fun grossSchreibungAbschaltbar() {
        assertEquals(
            "das bleibt klein.",
            TextPolisher.polish("das bleibt klein.", PolishOptions(autoCapitalize = false)),
        )
    }

    @Test fun autoModusLoeschtKeineEchtenWoerter() {
        // "um" (dt.) und "este" (span.) sind echte Woerter -> im auto-Modus nicht entfernen.
        assertEquals(
            "Ich gehe um die Ecke.",
            TextPolisher.polish("ich gehe um die Ecke.", PolishOptions(language = "auto")),
        )
        assertEquals(
            "Compré este libro.",
            TextPolisher.polish("compré este libro.", PolishOptions(language = "auto")),
        )
    }

    @Test fun autoModusEntferntEindeutigeFueller() {
        assertEquals(
            "Ich denke ja.",
            TextPolisher.polish("ich ähm denke uh ja.", PolishOptions(language = "auto")),
        )
    }

    // --- eigene und abgewaehlte Fuellwoerter -----------------------------------

    @Test fun eigeneFuellwoerterWerdenEntfernt() {
        assertEquals(
            "Das ist gut.",
            TextPolisher.polish(
                "das ist halt gut, sozusagen.",
                PolishOptions(language = "de", customFillers = listOf("halt", "sozusagen")),
            ),
        )
    }

    @Test fun fuellwortVorSatzendeLaesstKeinKommaZurueck() {
        assertEquals("Das ist gut.", TextPolisher.polish("das ist gut, ähm.", PolishOptions(language = "de")))
        assertEquals("Echt, ja?", TextPolisher.polish("echt, ähm, ja?", PolishOptions(language = "de")))
        // Ohne Fuellwort-Entfernung bleibt der Wortlaut samt Kommas unangetastet.
        assertEquals("Gut, ähm.", TextPolisher.polish("gut, ähm.", PolishOptions(removeFillers = false, language = "de")))
    }

    @Test fun eigeneFuellwoerterNurAlsGanzeWoerterUndOhneGrossKlein() {
        val o = PolishOptions(language = "de", customFillers = listOf("halt"))
        assertEquals("Haltestelle bleibt.", TextPolisher.polish("Haltestelle bleibt.", o))
        assertEquals("Das bleibt.", TextPolisher.polish("das HALT bleibt.", o))
        // Unicode: Wortgrenze vor/nach Umlauten.
        assertEquals("Schön so.", TextPolisher.polish("schön äh so.", PolishOptions(language = "de", customFillers = listOf("äh"))))
    }

    @Test fun leereEigeneEintraegeWerdenIgnoriert() {
        assertEquals(
            "Das bleibt.",
            TextPolisher.polish("das bleibt.", PolishOptions(language = "de", customFillers = listOf("", "   "))),
        )
    }

    @Test fun mehrwortFuellerFunktionieren() {
        assertEquals(
            "I think so.",
            TextPolisher.polish("I think you know so.", PolishOptions(language = "en", customFillers = listOf("you know"))),
        )
    }

    @Test fun abgewaehlteStandardwoerterBleibenStehen() {
        assertEquals(
            "Ich hmm denke ja.",
            TextPolisher.polish("ich hmm denke ähm ja.", PolishOptions(language = "de", disabledFillers = setOf("hmm"))),
        )
    }

    @Test fun eingebauteListenSindOeffentlich() {
        assertTrue(TextPolisher.builtinFillers("de").contains("ähm"))
        assertTrue(TextPolisher.builtinFillers("en").contains("um"))
        assertTrue(TextPolisher.builtinFillers("auto").contains("ähm"))
        assertTrue(TextPolisher.builtinFillers("auto").none { it == "um" })
        assertEquals(emptyList<String>(), TextPolisher.builtinFillers("xx"))
        for (lang in listOf("de", "en", "es", "fr", "it", "auto")) {
            for (w in TextPolisher.builtinFillers(lang)) assertEquals(w, w.lowercase())
        }
    }

    // --- Zeilenumbrueche ----------------------------------------------------------

    @Test fun kiAbsaetzeBleibenErhalten() {
        val o = PolishOptions(language = "de", keepLineBreaks = true)
        assertEquals(
            "Erster Absatz.\n\nZweiter Absatz.",
            TextPolisher.polish("Erster   Absatz. \n\n\n\n Zweiter Absatz.  ", o),
        )
        assertEquals(
            "- Punkt eins\n- Punkt zwei",
            TextPolisher.polish("- Punkt eins\n- Punkt zwei", o),
        )
    }

    @Test fun fuellwortAmZeilenendeLaesstKeinenRest() {
        val o = PolishOptions(language = "de", keepLineBreaks = true)
        assertEquals("Ich denke.\nJa.", TextPolisher.polish("ich denke ähm.\näh ja.", o))
    }
}
