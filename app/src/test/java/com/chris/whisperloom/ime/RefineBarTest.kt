package com.chris.whisperloom.ime

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Der Schnellzugriff auf die vier Textverbesserungs-Stufen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RefineBarTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private class Fixture(val root: View) {
        val row: ViewGroup = root.findViewById(R.id.refine_row)
        val bar = RefineBar(row) { true }
        fun key(id: Int): Button = root.findViewById(id)
        val alle get() = listOf(R.id.refine_off, R.id.refine_polish, R.id.refine_beautify, R.id.refine_summarize)
    }

    private fun fixture() = Fixture(LayoutInflater.from(ctx).inflate(R.layout.keyboard_view, null))

    @Test fun eingeklapptIstDieZeileWeg() {
        val f = fixture()
        assertFalse(f.bar.isShown)
        assertEquals(View.GONE, f.row.visibility)
    }

    @Test fun ausklappenZeigtDieVierStufen() {
        val f = fixture()
        f.bar.show(RefineMode.OFF, llmReady = true)
        assertTrue(f.bar.isShown)
        assertEquals(5, f.row.childCount)
        for (id in f.alle) assertEquals(View.VISIBLE, f.key(id).visibility)
        assertEquals("Prompt ohne Schalter sichtbar", View.GONE, f.key(R.id.refine_prompt).visibility)
    }

    // --- Stufe "Prompt" (nur mit Schalter in den erweiterten Optionen) --------

    @Test fun promptErscheintNurMitSchalter() {
        val f = fixture()
        assertEquals("Default im Layout", View.GONE, f.key(R.id.refine_prompt).visibility)
        f.bar.show(RefineMode.OFF, llmReady = true, promptEnabled = true)
        assertEquals(View.VISIBLE, f.key(R.id.refine_prompt).visibility)

        // Schalter in der App ausgeschaltet, Leiste erneut geoeffnet: wieder weg.
        f.bar.hide()
        f.bar.show(RefineMode.OFF, llmReady = true, promptEnabled = false)
        assertEquals(View.GONE, f.key(R.id.refine_prompt).visibility)
    }

    @Test fun promptVerhaeltSichWieDieAnderenKiStufen() {
        val f = fixture()
        val gewaehlt = mutableListOf<RefineMode>()
        f.bar.bind { gewaehlt += it }
        f.bar.show(RefineMode.PROMPT, llmReady = true, promptEnabled = true)
        val prompt = f.key(R.id.refine_prompt)
        assertTrue(prompt.isSelected)
        assertEquals(1, (f.alle + R.id.refine_prompt).count { f.key(it).isSelected })
        assertTrue(prompt.contentDescription.contains("Textverbesserung"))
        prompt.performClick()
        assertEquals(listOf(RefineMode.PROMPT), gewaehlt)

        f.bar.show(RefineMode.OFF, llmReady = false, promptEnabled = true)
        assertFalse("KI-Stufe ohne Zugang bedienbar: Prompt", prompt.isEnabled)
    }

    @Test fun genauDieGewaehlteStufeIstMarkiert() {
        val f = fixture()
        f.bar.show(RefineMode.BEAUTIFY, llmReady = true)
        assertTrue("Verschoenern muss markiert sein", f.key(R.id.refine_beautify).isSelected)
        assertEquals(1, f.alle.count { f.key(it).isSelected })

        f.bar.select(RefineMode.OFF)
        assertTrue(f.key(R.id.refine_off).isSelected)
        assertEquals(1, f.alle.count { f.key(it).isSelected })
    }

    @Test fun ohneKiZugangBleibtNurAusWaehlbar() {
        val f = fixture()
        f.bar.show(RefineMode.OFF, llmReady = false)
        assertTrue("Aus muss immer waehlbar bleiben", f.key(R.id.refine_off).isEnabled)
        for (id in listOf(R.id.refine_polish, R.id.refine_beautify, R.id.refine_summarize)) {
            assertFalse("KI-Stufe ohne Zugang bedienbar: ${ctx.resources.getResourceEntryName(id)}",
                f.key(id).isEnabled)
        }
    }

    @Test fun mitKiZugangSindAlleWaehlbar() {
        val f = fixture()
        f.bar.show(RefineMode.OFF, llmReady = true)
        for (id in f.alle) assertTrue(f.key(id).isEnabled)
    }

    @Test fun jedeStufeMeldetIhreWahl() {
        val f = fixture()
        val gewaehlt = mutableListOf<RefineMode>()
        f.bar.bind { gewaehlt += it }
        f.bar.show(RefineMode.OFF, llmReady = true)
        for (id in f.alle) f.key(id).performClick()
        assertEquals(RefineMode.SETTINGS, gewaehlt)
    }

    @Test fun jedeStufeSagtWozuSieGehoert() {
        val f = fixture()
        f.bar.bind {}
        for (id in f.alle) {
            val key = f.key(id)
            // Der blosse Tastentext ("Glaetten") sagt nicht, worum es geht.
            assertTrue(
                "Beschreibung nennt die Stufe nicht: ${key.contentDescription}",
                key.contentDescription.contains(key.text),
            )
            assertTrue(key.contentDescription.contains("Textverbesserung"))
        }
    }

    @Test fun einklappenVerbirgtDieZeileWieder() {
        val f = fixture()
        f.bar.show(RefineMode.POLISH, llmReady = true)
        f.bar.hide()
        assertFalse(f.bar.isShown)
        assertEquals(View.GONE, f.row.visibility)
    }

    @Test fun dasTotePARAGRAPHSStehtNichtInDerLeiste() {
        // Es ist nirgends zuweisbar und mappt im Einstellungs-Screen auf "Glaetten".
        assertFalse(RefineMode.SETTINGS.contains(RefineMode.PARAGRAPHS))
        assertEquals(4, RefineMode.SETTINGS.size)
    }
}
