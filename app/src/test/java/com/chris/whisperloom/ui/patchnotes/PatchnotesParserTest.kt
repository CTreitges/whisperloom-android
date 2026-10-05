package com.chris.whisperloom.ui.patchnotes

import com.chris.whisperloom.BuildConfig
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Patchnotes-Parser (reine JVM): erst Inline-Fixtures fuer jede Regel aus der Spec, dann die echten
 * Dateien — dort nur historische Fakten, die sich mit spaeteren Releases nicht mehr aendern.
 * Arbeitsverzeichnis der Unit-Tests ist app/.
 */
class PatchnotesParserTest {

    private fun parse(md: String) = PatchnotesParser.parseChangelog(md)
    private fun one(md: String) = parse(md).single()

    /** Ein einzelner Punkt unter "Hinzugefuegt". */
    private fun entry(text: String) = one("## [1.0.0]\n### Hinzugefügt\n- $text").sections.single().entries.single()

    // --- Versionskoepfe ----------------------------------------------------------

    @Test fun versionskopfLiefertVersionUndDatum() {
        val r = one("## [3.8.1] — 2026-10-05")
        assertEquals("3.8.1", r.version)
        assertEquals(LocalDate.of(2026, 10, 5), r.date)
        assertEquals(LocalDate.of(2026, 1, 2), one("## 3.1.0 - 2026-01-02").date)
        assertEquals(LocalDate.of(2026, 1, 2), one("## [3.1.0] – 2026-01-02").date)
    }

    @Test fun einsPunktNullBleibtEinsPunktNull() {
        assertEquals("1.0", one("## [1.0] — 2026-07-09").version)
    }

    @Test fun unreleasedUndUnbekannteAbschnitteWerdenUebersprungen() {
        val md = """
            # Changelog

            Vorspann mit [Link](https://example.com).

            ## [Unreleased]

            ### Hinzugefügt

            - **Kommt noch.** Steht nirgends.

            ## Notizen

            - auch nicht

            ## [3.0.0] — 2026-09-07

            ### Behoben

            - Echt.
        """.trimIndent()
        val releases = parse(md)
        assertEquals(listOf("3.0.0"), releases.map { it.version })
        assertNull(releases.single().intro)
        assertEquals(Entry(null, "Echt.", emptyList()), releases.single().sections.single().entries.single())
    }

    @Test fun ohneOderMitUngueltigemDatumIstDateNull() {
        assertNull(one("## [3.0.0]").date)
        assertNull(one("## [3.0.0] — 2026-13-40").date)
    }

    // --- Struktur ------------------------------------------------------------------

    @Test fun referenzDefinitionenLandenNirgends() {
        val r = one("## [3.0.0]\n### Behoben\n- Punkt\n\n[3.0.0]: #300--2026-09-07\n[1.0]: #10--2026-07-09")
        assertEquals(listOf(Entry(null, "Punkt", emptyList())), r.sections.single().entries)
    }

    @Test fun textVorDemErstenKategoriekopfIstDasIntro() {
        val r = one("## [3.0.0]\n\nKomplett neue Oberfläche,\nOffline zurück.\n\n### Hinzugefügt\n- x")
        assertEquals("Komplett neue Oberfläche, Offline zurück.", r.intro)
        assertEquals(Category.ADDED, r.sections.single().category)
    }

    @Test fun unbekannteUeberschriftWirdOtherMitIhremTitel() {
        val r = one("## [3.0.0]\n### Migration\n- a\n### Hinweise\n- b")
        assertEquals(listOf(Category.OTHER to "Migration", Category.OTHER to "Hinweise"), r.sections.map { it.category to it.title })
    }

    @Test fun kategoriekoepfeOhneRuecksichtAufGrossschreibung() {
        val r = one("## [3.0.0]\n### Bekannte Punkte\n- a\n###  SICHERHEIT \n- b\n### Technik\n- c\n### Entfernt\n- d")
        assertEquals(listOf(Category.KNOWN, Category.SECURITY, Category.TECH, Category.REMOVED), r.sections.map { it.category })
    }

    @Test fun doppelteKategorienWerdenZusammengefuehrt() {
        val r = one("## [3.4.0]\n### Hinzugefügt\n- a\n### Sicherheit\n- s\n### Hinzugefügt\n- b")
        assertEquals(listOf(Category.ADDED, Category.SECURITY), r.sections.map { it.category })
        assertEquals(listOf("a", "b"), r.sections.first().entries.map { it.body })
    }

    @Test fun codebloeckeWerdenUebersprungen() {
        val r = one("## [3.0.0]\n### Geändert\n- vorher\n```\n## [9.9.9]\n- kein Punkt\n```\n- nachher")
        assertEquals(listOf("vorher", "nachher"), r.sections.single().entries.map { it.body })
    }

    // --- Lead-Split (Spec §3, alle fuenf Zeilen) ------------------------------------

    @Test fun leadOhneRest() {
        assertEquals(Entry("Nur fett", null, emptyList()), entry("**Nur fett**"))
    }

    @Test fun leadMitSatzzeichenVerliertPunktUndDoppelpunkt() {
        assertEquals(Entry("„Lesbarer glätten“", "Neuer Schalter.", emptyList()), entry("**„Lesbarer glätten“.** Neuer Schalter."))
        assertEquals(Entry("Mit „Glätten“", "Die Anweisung.", emptyList()), entry("**Mit „Glätten“:** Die Anweisung."))
        assertEquals(Entry("Achtung!", "Text", emptyList()), entry("**Achtung!** Text"))
    }

    @Test fun leadMitGedankenstrichMachtDenRestGross() {
        assertEquals(Entry("Neuer Name", "Die App wird neu installiert.", emptyList()), entry("**Neuer Name** — die App wird neu installiert."))
        assertEquals(Entry("Neuer Name", "Die App.", emptyList()), entry("**Neuer Name** – die App."))
    }

    @Test fun leadMitKlammer() {
        val e = entry("**Widget hing** ([#10](https://github.com/x/y/issues/10)). Scheiterte …")
        assertEquals("Widget hing", e.lead)
        assertEquals("([#10](https://github.com/x/y/issues/10)). Scheiterte …", e.body)
    }

    @Test fun laeuftDerSatzWeiterGibtEsKeinenLead() {
        val text = "**Einrichtungs-Assistent** mit sieben Schritten."
        assertEquals(Entry(null, text, emptyList()), entry(text))
        assertEquals(Entry(null, "**Offener Fettdruck ohne Ende", emptyList()), entry("**Offener Fettdruck ohne Ende"))
    }

    // --- Kinder, Fortsetzungen, Leerzeilen ---------------------------------------------

    @Test fun kinderAufEbeneZweiTiefereEbenenWerdenAbgeflacht() {
        val md = "## [3.7.0]\n### Behoben\n- **Fehler.** Text\n  - Kind 1\n    - Enkel\n  * Kind 2\n  - **Fett.** ohne Split\n- Zweiter"
        val entries = one(md).sections.single().entries
        assertEquals(listOf("Kind 1", "Enkel", "Kind 2", "**Fett.** ohne Split"), entries.first().children)
        assertEquals(Entry(null, "Zweiter", emptyList()), entries[1])
    }

    @Test fun fortsetzungszeilenHaengenAmLetztenElement() {
        val md = "## [3.0.0]\n### Geändert\nOhne Punkt davor\n- **Lead.** Anfang\nweiter so\n- Mit Kind\n  - Kind\n    noch mehr"
        val entries = one(md).sections.single().entries
        assertEquals(Entry(null, "Ohne Punkt davor", emptyList()), entries[0])
        assertEquals(Entry("Lead", "Anfang weiter so", emptyList()), entries[1])
        assertEquals(listOf("Kind noch mehr"), entries[2].children)
    }

    @Test fun leerzeilenZwischenPunktenSindWirkungslos() {
        val dicht = one("## [3.3.0]\n### Hinzugefügt\n- a\n- b")
        val luftig = one("## [3.3.0]\n\n### Hinzugefügt\n\n- a\n\n\n- b\n")
        assertEquals(dicht, luftig)
    }

    @Test fun windowsZeilenendenStoerenNicht() {
        assertEquals(one("## [3.3.0] — 2026-09-21\n### Behoben\n- a"), one("## [3.3.0] — 2026-09-21\r\n### Behoben\r\n- a\r\n"))
    }

    // --- Technik -------------------------------------------------------------------------

    @Test fun technikStehtWederInDenSichtbarenSektionenNochInDerAnzahl() {
        val r = one("## [3.0.0]\n### Technik\n- t1\n- t2\n### Behoben\n- f\n### Hinzugefügt\n- a1\n- a2")
        assertEquals(listOf(Category.ADDED, Category.FIXED), r.visibleSections.map { it.category })
        assertEquals(3, r.changeCount)
        assertEquals(3, r.major)
    }

    // --- Inline ----------------------------------------------------------------------------

    @Test fun inlineFettUndCode() {
        assertEquals(
            listOf(Span.Bold("fett"), Span.Plain(" und "), Span.Code("code"), Span.Plain(".")),
            parseInline("**fett** und `code`."),
        )
    }

    @Test fun codeBleibtWoertlich() {
        assertEquals(listOf(Span.Code("<think>"), Span.Plain("-Blöcke")), parseInline("`<think>`-Blöcke"))
        assertEquals(listOf(Span.Code("a **b** c")), parseInline("`a **b** c`"))
    }

    @Test fun nurHttpsWirdEinLinkAnkerBleibenText() {
        assertEquals(
            listOf(Span.Plain("("), Span.Link("#10", "https://github.com/x/issues/10"), Span.Plain(")")),
            parseInline("([#10](https://github.com/x/issues/10))"),
        )
        assertEquals(listOf(Span.Plain("Siehe "), Span.Plain("Fragen")), parseInline("Siehe [Fragen](#14-häufige-fragen)"))
    }

    @Test fun offenesFettBleibtLiteral() {
        assertEquals(listOf(Span.Plain("offenes **fett")), parseInline("offenes **fett"))
    }

    @Test fun klartextOhneMarkdownZeichen() {
        assertEquals(
            "Lead mit code, Link und Anker",
            plainText("**Lead** mit `code`, [Link](https://a.b) und [Anker](#x)"),
        )
        // Code im Fettdruck (3.0.0 „Breaking: Standardmodell ist jetzt `gpt-transcribe`“).
        assertEquals("ist jetzt gpt-transcribe", plainText("**ist jetzt `gpt-transcribe`**"))
    }

    // --- Highlights --------------------------------------------------------------------------

    @Test fun highlightsMitVersionOhneTagline() {
        val f = PatchnotesParser.parseHighlights("WhisperLoom 3.8.1\n• Neu: „Lesbarer glätten“ – Glätten repariert\n• Neue Anweisungen")!!
        assertEquals("3.8.1", f.version)
        assertNull(f.tagline)
        assertEquals(listOf(Highlight("Neu", "„Lesbarer glätten“ – Glätten repariert"), Highlight(null, "Neue Anweisungen")), f.items)
    }

    @Test fun taglineNachDemGedankenstrichMitGrossemAnfang() {
        val f = PatchnotesParser.parseHighlights("Version 3.1.0 – neuer Name, neue Paket-ID.\n- Punkt")!!
        assertEquals("3.1.0", f.version)
        assertEquals("Neuer Name, neue Paket-ID.", f.tagline)
        assertEquals(listOf(Highlight(null, "Punkt")), f.items)
    }

    @Test fun kickerIstEinEinzelnesWort() {
        val f = PatchnotesParser.parseHighlights(
            "WhisperLoom 3.3.0 — Diktieren.\n• Wisch-Geste in der Tastatur: nach rechts ziehen\n* Behoben: die Taste\n• Pro: Modelle\n• Bedienungshilfen: zu lang",
        )!!
        assertEquals(listOf(null, "Behoben", "Pro", null), f.items.map { it.kicker })
        assertEquals("Wisch-Geste in der Tastatur: nach rechts ziehen", f.items.first().text)
    }

    @Test fun fortsetzungszeilenHaengenAmPunkt() {
        val f = PatchnotesParser.parseHighlights("WhisperLoom 3.4.1\n• Erster\n  weiter\n\n• Zweiter\r\n")!!
        assertEquals(listOf("Erster weiter", "Zweiter"), f.items.map { it.text })
    }

    @Test fun ohneVersionInZeileEinsKeineHighlights() {
        assertNull(PatchnotesParser.parseHighlights("Neuigkeiten\n• WhisperLoom 3.8.1"))
        assertNull(PatchnotesParser.parseHighlights(""))
    }

    @Test fun mergeDieSpaetereDateiGewinntUnbekannteVersionFaelltWeg() {
        val releases = listOf(Release("3.1.0", null, null, emptyList()), Release("3.0.0", null, null, emptyList()))
        val files = listOf(
            HighlightFile("3.1.0", "Alt", listOf(Highlight(null, "alt"))),
            HighlightFile("3.1.0", "Neu", listOf(Highlight(null, "neu"))),
            HighlightFile("9.9.9", null, listOf(Highlight(null, "fremd"))),
        )
        val merged = PatchnotesParser.merge(releases, files)
        assertEquals(Release("3.1.0", null, null, emptyList(), listOf(Highlight(null, "neu")), "Neu"), merged[0])
        assertEquals(releases[1], merged[1])
        assertEquals(2, merged.size)
    }

    // --- Teaser ------------------------------------------------------------------------------

    @Test fun teaserTaglineVorIntroVorHighlightVorErstemEintrag() {
        val sections = listOf(
            Section(Category.TECH, "Technik", listOf(Entry(null, "AGP", emptyList()))),
            Section(Category.FIXED, "Behoben", listOf(Entry("**Erster** Fix", "x", emptyList()))),
            Section(Category.ADDED, "Hinzugefügt", listOf(Entry("Neu", null, emptyList()))),
        )
        val ohneAlles = Release("3.0.0", null, null, sections)
        assertEquals("Erster Fix", PatchnotesParser.teaser(ohneAlles))
        val mitHighlight = ohneAlles.copy(highlights = listOf(Highlight("Neu", "ElevenLabs"), Highlight(null, "zwei")))
        assertEquals("Neu: ElevenLabs", PatchnotesParser.teaser(mitHighlight))
        assertEquals("Ohne Kicker", PatchnotesParser.teaser(ohneAlles.copy(highlights = listOf(Highlight(null, "Ohne Kicker")))))
        val mitIntro = mitHighlight.copy(intro = "Komplett `neu`")
        assertEquals("Komplett neu", PatchnotesParser.teaser(mitIntro))
        assertEquals("Diktieren.", PatchnotesParser.teaser(mitIntro.copy(tagline = "Diktieren.")))
        assertNull(PatchnotesParser.teaser(Release("1.0", null, null, emptyList())))
    }

    // --- Echte Dateien (nur historische Fakten) -------------------------------------------

    private val changelog = File("../CHANGELOG.md").readText(Charsets.UTF_8)
    private val highlightFiles = File("../fastlane/metadata/android/de-DE/changelogs")
        .listFiles { f -> f.name.endsWith(".txt") }!!
        .sortedBy { it.nameWithoutExtension.toInt() }
        .map { PatchnotesParser.parseHighlights(it.readText(Charsets.UTF_8)) }
    private val echte = PatchnotesParser.merge(PatchnotesParser.parseChangelog(changelog), highlightFiles.filterNotNull())

    private fun version(v: String) = echte.single { it.version == v }

    @Test fun echtesChangelogBeginntMitDerInstalliertenVersion() {
        assertTrue("${echte.size} Releases", echte.size >= 15)
        assertEquals(BuildConfig.VERSION_NAME, echte.first().version)
        assertTrue(echte.all { Regex("""\d+(\.\d+){1,3}""").matches(it.version) })
        assertEquals("ohne Datum", emptyList<String>(), echte.filter { it.date == null }.map { it.version })
    }

    @Test fun echte381() {
        val r = version("3.8.1")
        assertEquals(LocalDate.of(2026, 10, 5), r.date)
        assertEquals(
            listOf(Category.ADDED to 1, Category.CHANGED to 2, Category.FIXED to 2),
            r.visibleSections.map { it.category to it.entries.size },
        )
        assertEquals("„Lesbarer glätten“", r.sections.first().entries.first().lead)
        assertEquals(4, r.highlights.size)
        assertEquals("Neu", r.highlights.first().kicker)
        assertEquals(5, r.changeCount)
    }

    @Test fun echte370() {
        val behoben = version("3.7.0").sections.single { it.category == Category.FIXED }
        val widget = behoben.entries.single { it.children.size == 5 }
        assertEquals("Sprachauftrag-Widget blieb auf „Wird gesendet …“ hängen, nur ein Force-Stop half", widget.lead)
        val spans = (listOfNotNull(widget.body) + widget.children).flatMap(::parseInline)
        assertTrue(Span.Code("adb shell dumpsys jobscheduler com.chris.whisperloom") in spans)
        assertTrue(spans.any { it is Span.Link && it.url.endsWith("/issues/10") })
    }

    @Test fun echte300() {
        val r = version("3.0.0")
        assertTrue(r.intro!!.startsWith("Komplett neue Oberfläche"))
        assertEquals(8, r.sections.single { it.category == Category.TECH }.entries.size)
        assertEquals(37, r.changeCount)
        assertEquals(2, r.sections.single { it.category == Category.KNOWN }.entries.size)
        assertFalse(r.visibleSections.any { it.category == Category.TECH })
    }

    @Test fun echte200Und10() {
        assertEquals("Breaking: nur noch API-Betrieb", PatchnotesParser.teaser(version("2.0.0")))
        val erste = version("1.0").sections.flatMap { it.entries }
        assertEquals(5, erste.size)
        assertTrue(erste.all { it.lead == null })
    }

    @Test fun jedeHighlightDateiPasstZuEinerVersion() {
        val versionen = echte.map { it.version }.toSet()
        highlightFiles.forEach { f ->
            assertTrue("Highlight-Datei ohne Version", f != null)
            assertTrue("${f!!.version} fehlt im CHANGELOG", f.version in versionen)
        }
        assertEquals("Diktieren ohne Dauerhalten.", version("3.3.0").tagline)
    }

    @Test fun keinKlartextEnthaeltMarkdownReste() {
        val texte = echte.flatMap { r ->
            listOfNotNull(r.intro, r.tagline, PatchnotesParser.teaser(r)) + r.highlights.map { it.text } +
                r.sections.flatMap { s -> s.entries.flatMap { listOfNotNull(it.lead, it.body) + it.children } }
        }
        val reste = texte.map(::plainText).filter { "](#" in it || "**" in it }
        assertEquals(emptyList<String>(), reste)
    }
}
