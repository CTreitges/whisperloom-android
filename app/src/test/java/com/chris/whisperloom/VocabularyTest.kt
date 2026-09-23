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
        assertEquals(listOf("Anna", "Bernd"), Vocabulary.parseFile("﻿Anna\r\nBernd\r\n"))
    }

    @Test fun dateiParserEntferntDuplikateUnabhaengigVonGrossschreibung() {
        assertEquals(listOf("Ollama"), Vocabulary.parseFile("Ollama\n- ollama\nOLLAMA"))
    }

    @Test fun eigeneBegriffeKommenVorDerDatei() {
        val p = Vocabulary.prompt(listOf("Anna", "Bernd"), listOf("bernd", "Carla"))
        assertEquals("Anna, Bernd, Carla", p.text)
        assertEquals(3, p.used)
        assertEquals(3, p.total)
        assertFalse(p.truncated)
    }

    @Test fun kappungNurZwischenGanzenBegriffen() {
        val file = (1..400).map { "Begriff$it" }
        val p = Vocabulary.prompt(listOf("Anna"), file)
        assertTrue(p.text.length <= Vocabulary.MAX_PROMPT_CHARS)
        assertTrue(p.truncated)
        assertEquals(401, p.total)
        assertTrue(p.text.startsWith("Anna, Begriff1, "))
        // Letzter Begriff ist vollstaendig — nie mitten im Wort abgeschnitten.
        assertTrue(p.text.substringAfterLast(", ").matches(Regex("Begriff\\d+")))
        assertEquals(p.used, p.text.split(", ").size)
    }

    @Test fun langerEinzelnerFreitextWirdAmWortendeGekuerzt() {
        val legacy = List(300) { "wort" }.joinToString(" ")
        val p = Vocabulary.prompt(listOf(legacy))
        assertTrue(p.text.length <= Vocabulary.MAX_PROMPT_CHARS)
        assertTrue(p.text.endsWith("wort"))
        assertEquals(1, p.used)
    }

    @Test fun leerIstLeer() {
        val p = Vocabulary.prompt(emptyList())
        assertEquals("", p.text)
        assertEquals(0, p.total)
    }
}
