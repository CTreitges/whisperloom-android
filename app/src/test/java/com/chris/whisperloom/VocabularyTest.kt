package com.chris.whisperloom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM-Tests fuer das Vokabular: Liste, Datei-Parser und der gekappte Prompt fuer den Erkenner. */
class VocabularyTest {

    @Test fun freitextAusAlterVersionBleibtEinEintragUndKommtUnveraendertAn() {
        val legacy = "Christof Treitges, WhisperLoom, Ollama"
        assertEquals(listOf(legacy), Vocabulary.entries(legacy))
        assertEquals(legacy, Vocabulary.prompt(Vocabulary.entries(legacy)).text)
    }

    @Test fun eintraegeSindZeilenOhneLeeres() {
        assertEquals(listOf("Anna", "Bernd"), Vocabulary.entries("  Anna \n\n Bernd\n"))
        assertEquals(emptyList<String>(), Vocabulary.entries(""))
        assertEquals("Anna\nBernd", Vocabulary.serialize(listOf("Anna", "Bernd")))
    }

    @Test fun hinzufuegenTrenntAnKommaUndSemikolonOhneDuplikate() {
        val list = Vocabulary.add(listOf("Anna"), "anna, Bernd; Carla ,, ")
        assertEquals(listOf("Anna", "Bernd", "Carla"), list)
    }

    @Test fun entfernenNimmtNurDenEintrag() {
        assertEquals(listOf("Anna", "Carla"), Vocabulary.remove(listOf("Anna", "Bernd", "Carla"), "Bernd"))
    }

    @Test fun dateiParserEntferntMarkdown() {
        val md = """
            # Vokabular Firma

            - Treitges
            - **WhisperLoom**
            * Oracle-VPS
            1. Caddy
            2) `systemd`
            - [ ] Tailscale
            - [x] Hermes
            > Zitat-Begriff

            Glm, Gemma; Ollama
            ---
            ```
            code wird ignoriert
            ```
            ## Zweite Ueberschrift
        """.trimIndent()
        assertEquals(
            listOf(
                "Treitges", "WhisperLoom", "Oracle-VPS", "Caddy", "systemd", "Tailscale", "Hermes",
                "Zitat-Begriff", "Glm", "Gemma", "Ollama",
            ),
            Vocabulary.parseFile(md),
        )
    }

    @Test fun dateiParserMitBomUndWindowsZeilenenden() {
        assertEquals(listOf("Anna", "Bernd"), Vocabulary.parseFile("\uFEFFAnna\r\nBernd\r\n"))
    }

    @Test fun dateiParserEntferntDuplikateUnabhaengigVonGrossschreibung() {
        assertEquals(listOf("Ollama"), Vocabulary.parseFile("Ollama\n- ollama\nOLLAMA"))
    }

    @Test fun eigeneBegriffeStehenAmEndeUndGewinnenBeiDuplikaten() {
        // Whisper beachtet nur das ENDE des Prompts — die eigenen Begriffe gehoeren dorthin.
        val p = Vocabulary.prompt(listOf("Anna", "Bernd"), listOf("bernd", "Carla"))
        assertEquals("Carla, Anna, Bernd", p.text)
        assertEquals(3, p.used)
        assertEquals(3, p.total)
        assertFalse(p.truncated)
    }

    @Test fun kappungWirftZuerstVordereDateiBegriffe() {
        val file = (1..400).map { "Begriff$it" }
        val p = Vocabulary.prompt(listOf("Anna"), file)
        assertTrue(p.text.length <= Vocabulary.MAX_PROMPT_CHARS)
        assertTrue(p.truncated)
        assertEquals(401, p.total)
        // Eigener Begriff bleibt, und zwar am Ende; davor die letzten Datei-Begriffe.
        assertTrue(p.text.endsWith("Begriff399, Begriff400, Anna"))
        assertFalse(p.text.contains("Begriff1,"))
        // Erster Begriff ist vollstaendig — nie mitten im Wort abgeschnitten.
        assertTrue(p.text.substringBefore(", ").matches(Regex("Begriff\\d+")))
        assertEquals(p.used, p.text.split(", ").size)
    }

    @Test fun langerEinzelnerFreitextWirdAmWortendeGekuerztUndAlsGekuerztGemeldet() {
        val legacy = List(300) { "wort" }.joinToString(" ")
        val p = Vocabulary.prompt(listOf(legacy))
        assertTrue(p.text.length <= Vocabulary.MAX_PROMPT_CHARS)
        assertTrue(p.text.endsWith("wort"))
        assertEquals(1, p.used)
        assertEquals(1, p.total)
        assertTrue(p.truncated)
    }

    @Test fun mehrzeiligerFreitextAusAlterVersionWirdZuEintraegen() {
        // Das alte Feld war mehrzeilig: jede Zeile wird ein Eintrag, verbunden mit ", ".
        val legacy = "Projekt X.\nTeilnehmer: Anna, Bernd"
        assertEquals(listOf("Projekt X.", "Teilnehmer: Anna, Bernd"), Vocabulary.entries(legacy))
        assertEquals("Projekt X., Teilnehmer: Anna, Bernd", Vocabulary.prompt(Vocabulary.entries(legacy)).text)
    }

    @Test fun dateiParserVerwirftZeichenOhneInhaltTabellenUndLeereAufgaben() {
        val md = "-\n+\n- [ ]\n***\n| Name | Rolle |\n|---|---|\n| Anna | Chefin |\n- Echt"
        assertEquals(listOf("Echt"), Vocabulary.parseFile(md))
    }

    @Test fun windows1252DateiBehaeltIhreUmlaute() {
        val cp1252 = "M\u00fcller\nG\u00f6rlitz".toByteArray(charset("windows-1252"))
        assertEquals("M\u00fcller\nG\u00f6rlitz", VocabularySource.decode(cp1252))
        val utf8 = "M\u00fcller".toByteArray(Charsets.UTF_8)
        assertEquals("M\u00fcller", VocabularySource.decode(utf8))
    }

    @Test fun textdateiNachEndungOderTyp() {
        assertTrue(VocabularySource.isTextFile("namen.md"))
        assertTrue(VocabularySource.isTextFile("Namen.TXT"))
        assertTrue(VocabularySource.isTextFile("Namen", "text/plain"))
        assertTrue(VocabularySource.isTextFile(null))
        assertFalse(VocabularySource.isTextFile("foto.jpg", "image/jpeg"))
        assertFalse(VocabularySource.isTextFile("archiv.zip", "application/octet-stream"))
    }

    @Test fun leerIstLeer() {
        val p = Vocabulary.prompt(emptyList())
        assertEquals("", p.text)
        assertEquals(0, p.total)
    }
}
