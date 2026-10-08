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
        // Review-Befund: auch Emojis, Sternchen und das deutsche ‚…‘ stehen zwischen Satzende und Leerraum.
        assertEquals("Super!😀 Danke", TextPolisher.polish("super!😀 danke", full))
        assertEquals("**Wichtig.** Danach", TextPolisher.polish("**wichtig.** danach", full))
        assertEquals("Er sagte ‚Stopp.‘ Dann", TextPolisher.polish("er sagte ‚Stopp.‘ dann", full))
    }

    @Test fun zeilenanfangMitZifferBleibtKlein() {
        val lines = full.copy(keepLineBreaks = true)
        assertEquals(
            "Make me a plan.\n- 12 people, 2 of them vegan",
            TextPolisher.polish("make me a plan.\n- 12 people, 2 of them vegan", lines),
        )
        assertEquals("Hallo.\nWelt", TextPolisher.polish("hallo.\nwelt", lines))
    }

    // --- Abkuerzungen beenden keinen Satz (Nebenbefund N1) ----------------------------------

    @Test fun nachAbkuerzungUndOrdnungszahlBleibtKlein() {
        assertEquals("Obst, z. B. ein Apfel.", TextPolisher.polish("obst, z. B. ein Apfel.", full))
        assertEquals("Das kostet ca. fünf Euro.", TextPolisher.polish("das kostet ca. fünf Euro.", full))
        assertEquals("Das heißt d.h. nichts.", TextPolisher.polish("das heißt d.h. nichts.", full))
        assertEquals("Vom 1. bis 5. Mai", TextPolisher.polish("vom 1. bis 5. Mai", full))
    }

    @Test fun nachDerKiBleibtKleinNachAbkuerzung() {
        val afterAi = PolishPlan.options(
            removeFillers = true, autoCapitalize = true, language = "de", refineMode = RefineMode.POLISH,
        )
        assertEquals("Bring z. B. ein Brot mit.", TextPolisher.polish("Bring z. B. ein Brot mit.", afterAi))
        assertEquals("Wir sind ca. zehn Leute.", TextPolisher.polish("Wir sind ca. zehn Leute.", afterAi))
    }

    @Test fun satzendeNachEchtemSatzBleibtGross() {
        assertEquals("Das ist gut. Das auch.", TextPolisher.polish("das ist gut. das auch.", full))
        assertEquals("Wir waren im Kino. Danach", TextPolisher.polish("wir waren im Kino. ähm danach", full))
        assertEquals("Er ging (z. B. heim). Dann", TextPolisher.polish("er ging (z. B. heim). dann", full))
    }

    // --- Ein entferntes Fuellwort am Satzanfang gibt die Gross-Schreibung weiter (Review 3.9.0) ----

    @Test fun nachZahlOderEinzelbuchstabeAmSatzendeUebernimmtDasFolgewortDieGrossSchreibung() {
        // Die Abkuerzungs-Regel (N1) haelt "2021." und "B." fuer kein Satzende — das gross
        // geschriebene "Ähm" stand aber am Satzanfang, also gilt das fuer das Wort danach.
        assertEquals(
            "Das Projekt begann 2021. Dann kam die Pandemie.",
            TextPolisher.polish("Das Projekt begann 2021. Ähm, dann kam die Pandemie.", full),
        )
        assertEquals("Plan B. Also los.", TextPolisher.polish("Plan B. Ähm, also los.", full))
        assertEquals(
            "We met in room 204. Then we left.",
            TextPolisher.polish("We met in room 204. Um, then we left.", PolishOptions(language = "en")),
        )
    }

    @Test fun nachDerKiUebernimmtDasFolgewortDieGrossSchreibung() {
        // Werkseinstellung: Glaetten · Nur Zeichensetzung laesst jedes "Ähm" stehen, die Liste raeumt danach auf.
        val afterAi = PolishPlan.options(
            removeFillers = true, autoCapitalize = true, language = "de", refineMode = RefineMode.POLISH,
        )
        assertEquals(
            "Das Projekt begann 2021. Dann kam die Pandemie.",
            TextPolisher.polish("Das Projekt begann 2021. Ähm, dann kam die Pandemie.", afterAi),
        )
        assertEquals("Zimmer 12. Bring Brot mit.", TextPolisher.polish("Zimmer 12. Äh, bring Brot mit.", afterAi))
    }

    @Test fun mehrereFuellwoerterAmSatzanfangGebenDieGrossSchreibungWeiter() {
        assertEquals("Das war 2021. Dann kam X.", TextPolisher.polish("Das war 2021. Ähm, äh, dann kam X.", full))
        assertEquals("Dann kam X.", TextPolisher.polish("Ähm, äh, dann kam X.", PolishOptions(language = "de", autoCapitalize = false)))
    }

    @Test fun grossesFuellwortMittenImSatzMachtNichtsGross() {
        assertEquals("Er sagte nein.", TextPolisher.polish("er sagte Ähm, nein.", full))
        assertEquals("Vom 1. bis 5. Mai", TextPolisher.polish("vom 1. ähm bis 5. Mai", full))
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
