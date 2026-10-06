package com.chris.whisperloom.llm

import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.api.TextRefiner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Laengengrenzen des lokalen Textmodells (Review c3), ohne LiteRT-LM. */
class LocalTextModelTest {

    private fun messages(words: Int, mode: RefineMode = RefineMode.BEAUTIFY) =
        TextRefiner.messages(List(words) { "Termin" }.joinToString(" "), "de", mode, smartFillers = false, paragraphs = true)

    @Test fun diktatUndAntwortMuessenInDieKvTabellePassen() {
        val (system, kurz) = messages(500)
        assertTrue("500 Woerter passen, auch mit dem laengsten System-Prompt", LocalTextModel.fits(system, kurz))
        val (system2, lang) = messages(1_000)
        assertFalse("1000 Woerter passen nicht mehr", LocalTextModel.fits(system2, lang))
    }

    @Test fun volleKvTabelleHeisstAbgeschnitten() {
        // Smoketest 0.16.1: an der Grenze steht die Conversation genau auf maxNumTokens.
        assertTrue(LocalTextModel.truncated(LocalTextModel.MAX_NUM_TOKENS))
        assertFalse(LocalTextModel.truncated(LocalTextModel.MAX_NUM_TOKENS - 1))
        assertFalse(LocalTextModel.truncated(577))
    }
}
