package com.chris.whisperloom.agent

import com.chris.whisperloom.agent.WidgetLayout.ICON
import com.chris.whisperloom.agent.WidgetLayout.ROW
import com.chris.whisperloom.agent.WidgetLayout.STACK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Welche Variante bei welcher Groesse — die ganze Zellmass-Tabelle, reine JVM. */
class WidgetLayoutsTest {

    /** Zellmasse laut Doku (dp): Hochformat (73n-16) x (118m-16), Querformat (142n-15) x (66m-15). */
    private fun hoch(spalten: Int, zeilen: Int) = WidgetLayouts.pick(73f * spalten - 16, 118f * zeilen - 16)
    private fun quer(spalten: Int, zeilen: Int) = WidgetLayouts.pick(142f * spalten - 15, 66f * zeilen - 15)

    @Test fun dieZellmassTabelle() {
        // Spalten x Zeilen -> Hochformat, Querformat
        val erwartet = mapOf(
            (1 to 1) to (ICON to ROW),
            (2 to 1) to (ROW to ROW),
            (3 to 1) to (ROW to ROW),
            (4 to 1) to (ROW to ROW),
            (1 to 2) to (ICON to STACK),
            (2 to 2) to (STACK to STACK),
            (3 to 2) to (STACK to STACK),
            (4 to 2) to (STACK to STACK),
        )
        erwartet.forEach { (zellen, varianten) ->
            val (n, m) = zellen
            assertEquals("${n}x$m hochkant", varianten.first, hoch(n, m))
            assertEquals("${n}x$m quer", varianten.second, quer(n, m))
        }
    }

    @Test fun dieGrenzen() {
        // ceil(w) + 1 > 120: 119 reicht nicht, alles darueber schon.
        assertEquals(ICON, WidgetLayouts.pick(119f, 110f))
        assertEquals(STACK, WidgetLayouts.pick(119.5f, 110f))
        assertEquals(ROW, WidgetLayouts.pick(120f, 109f))
        assertEquals(STACK, WidgetLayouts.pick(120f, 110f))
        assertEquals(ICON, WidgetLayouts.pick(120f, 47f))
        assertEquals(ROW, WidgetLayouts.pick(120f, 48f))
    }

    @Test fun kleinerAlsJedesLayoutErgibtDasSymbol() {
        assertEquals(ICON, WidgetLayouts.pick(30f, 30f))
        assertEquals(ICON, WidgetLayouts.pick(0f, 0f))
    }

    @Test fun bestFitNimmtDieKleinsteDistanzNichtDieGroessteFlaeche() {
        // Wie der AOSP-Code, nicht wie sein Javadoc: 190x100 liegt naeher an 300x205 als 100x200,
        // obwohl 100x200 die groessere Flaeche hat.
        val hoch = 100f to 200f
        val breit = 190f to 100f
        val sieger = WidgetLayouts.bestFit(listOf(hoch, breit), 300f, 205f, { it.first }, { it.second })
        assertEquals(breit, sieger)
    }

    @Test fun bestFitNimmtOhnePassendesLayoutDasKleinste() {
        val gross = 200f to 200f
        val klein = 150f to 100f
        assertEquals(klein, WidgetLayouts.bestFit(listOf(gross, klein), 10f, 10f, { it.first }, { it.second }))
    }

    @Test fun abstandsregelUndFlaechenregelEntscheidenUeberallGleich() {
        // Das Designziel der Idealgroessen: egal, welche Regel ein kuenftiges Android umsetzt.
        var w = 40f
        while (w <= 600f) {
            var h = 40f
            while (h <= 300f) {
                assertEquals("bei ${w}x$h", flaechenregel(w, h), WidgetLayouts.pick(w, h))
                h += 0.5f
            }
            w += 0.5f
        }
    }

    @Test fun mitDemNamenIstDasSymbolHoeherAlsDieZeileGewinntAberNieNachAbstand() {
        // Der Name unter der Kachel macht ICON hoeher als ROW — "in beiden Achsen am kleinsten"
        // gilt nicht mehr. Am schmalsten und mit der kleinsten Flaeche bleibt ICON trotzdem: ohne
        // gemeldete Groesse nimmt das System weiter ICON.
        assertTrue(ICON.h > ROW.h)
        assertTrue(ICON.w < ROW.w && ROW.w == STACK.w)
        assertEquals(ICON, WidgetLayout.entries.minBy { it.w * it.h })
        // Nach Abstand laege ICON erst bei h > 8w - 587 vor einer passenden ROW (w > 119), dort
        // passt laengst STACK. Auch weit ueber die Ziehgrenzen hinaus entscheiden beide Regeln gleich.
        var w = 40f
        while (w <= 1200f) {
            var h = 40f
            while (h <= 1200f) {
                assertEquals("bei ${w}x$h", flaechenregel(w, h), WidgetLayouts.pick(w, h))
                h += 4f
            }
            w += 4f
        }
    }

    /** Die Regel aus dem Javadoc: das passende Layout mit der groessten Flaeche, sonst das kleinste. */
    private fun flaechenregel(w: Float, h: Float): WidgetLayout {
        val passend = WidgetLayout.entries.filter { kotlin.math.ceil(w) + 1 > it.w && kotlin.math.ceil(h) + 1 > it.h }
        return passend.maxByOrNull { it.w * it.h } ?: WidgetLayout.entries.minBy { it.w * it.h }
    }

    @Test fun unterAndroid12HochformatAusSchmalUndHochQuerformatAusBreitUndFlach() {
        // 1x1: der Launcher meldet 57..127 breit und 51..102 hoch.
        assertEquals(LegacyLayouts(portrait = ICON, landscape = ROW), WidgetLayouts.legacy(57, 127, 51, 102))
        // 2x2: 130..269 breit, 117..220 hoch.
        assertEquals(LegacyLayouts(portrait = STACK, landscape = STACK), WidgetLayouts.legacy(130, 269, 117, 220))
        // 4x1: 276..553 breit, 51..102 hoch.
        assertEquals(LegacyLayouts(portrait = ROW, landscape = ROW), WidgetLayouts.legacy(276, 553, 51, 102))
    }

    @Test fun ohneGemeldeteGroesseBleibtEsBeimGewohntenStapel() {
        assertEquals(LegacyLayouts(STACK, STACK), WidgetLayouts.legacy(0, 0, 0, 0))
    }

    @Test fun nurBereitUndFehlerZeigenDasProfilSymbol() {
        VoiceTaskState.entries.forEach {
            val erwartet = it == VoiceTaskState.READY || it == VoiceTaskState.ERROR
            assertEquals(it.name, erwartet, WidgetLayouts.showsProfileIcon(it))
        }
        assertTrue(WidgetLayouts.showsProfileIcon(VoiceTaskState.READY))
        assertFalse("Der Sekunden-Takt der Aufnahme darf kein Foto tragen", WidgetLayouts.showsProfileIcon(VoiceTaskState.RECORDING))
    }

    @Test fun jedeVarianteHatEinEigenesLayout() {
        assertEquals(WidgetLayout.entries.size, WidgetLayout.entries.map { it.layoutRes }.toSet().size)
    }
}
