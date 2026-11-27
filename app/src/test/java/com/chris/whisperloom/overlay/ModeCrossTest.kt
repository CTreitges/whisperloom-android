package com.chris.whisperloom.overlay

import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.overlay.ModeCross.CrossTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Modus-Kreuz am schwebenden Knopf (docs/design/bubble-textfeld.md §2/§3), ohne Android. Dichte 1: dp = px. */
class ModeCrossTest {

    private val four = RefineMode.settings(promptEnabled = false)
    private val five = RefineMode.settings(promptEnabled = true)

    private fun layout(cx: Float, cy: Float, modes: List<RefineMode> = four, w: Int = 1000, h: Int = 2000, top: Int = 0) =
        ModeCross.layout(cx, cy, ModeCross.Area(0, top, w, h), modes, density = 1f)

    private fun ModeCross.Layout.at(target: CrossTarget) = requireNotNull(pointOf(target))

    private fun assertNear(x: Float, y: Float, p: ModeCross.Point) {
        assertEquals("x", x, p.x, 0.5f)
        assertEquals("y", y, p.y, 0.5f)
    }

    private fun half(t: CrossTarget) = if (t == CrossTarget.Discard) 26f to 26f else 36f to 22f

    /** Pillen 72 x 44, ✕ 52, 8 Rand: alles im sicheren Bereich. */
    private fun assertInside(l: ModeCross.Layout, w: Int, h: Int, top: Int = 0) {
        for ((target, p) in l.targets) {
            val (hw, hh) = half(target)
            assertTrue("$target links $p", p.x - hw >= 8f - 0.5f)
            assertTrue("$target rechts $p", p.x + hw <= w - 8f + 0.5f)
            assertTrue("$target oben $p", p.y - hh >= top + 8f - 0.5f)
            assertTrue("$target unten $p", p.y + hh <= h - 8f + 0.5f)
        }
    }

    private fun assertNoOverlap(l: ModeCross.Layout) {
        for (i in l.targets.indices) for (j in i + 1 until l.targets.size) {
            val (a, pa) = l.targets[i]
            val (b, pb) = l.targets[j]
            val (aw, ah) = half(a)
            val (bw, bh) = half(b)
            val apart = abs(pa.x - pb.x) >= aw + bw - 0.5f || abs(pa.y - pb.y) >= ah + bh - 0.5f
            assertTrue("$a und $b ueberlappen ($pa, $pb)", apart)
        }
    }

    private fun direction(cx: Float, cy: Float, p: ModeCross.Point) =
        Math.toDegrees(atan2((cy - p.y).toDouble(), (p.x - cx).toDouble())).toFloat()

    private fun angleBetween(a: Float, b: Float): Float {
        val d = abs((a - b) % 360f)
        return if (d > 180f) 360f - d else d
    }

    private fun modeOrder(l: ModeCross.Layout) = l.targets.mapNotNull { (it.target as? CrossTarget.Mode)?.mode }

    @Test fun vierModiAlsBogenNachObenUndKreuzUnten() {
        val l = layout(500f, 1000f)
        assertEquals(listOf(RefineMode.OFF, RefineMode.POLISH, RefineMode.BEAUTIFY, RefineMode.SUMMARIZE), modeOrder(l))
        assertNear(388f, 1000f, l.at(CrossTarget.Mode(RefineMode.OFF)))
        assertNear(444f, 903f, l.at(CrossTarget.Mode(RefineMode.POLISH)))
        assertNear(556f, 903f, l.at(CrossTarget.Mode(RefineMode.BEAUTIFY)))
        assertNear(612f, 1000f, l.at(CrossTarget.Mode(RefineMode.SUMMARIZE)))
        assertNear(500f, 1096f, l.at(CrossTarget.Discard))
    }

    @Test fun promptAlsFuenftesZielUndBeautifyObenInDerMitte() {
        val l = layout(500f, 1000f, five)
        assertEquals(6, l.targets.size)
        assertNear(500f, 888f, l.at(CrossTarget.Mode(RefineMode.BEAUTIFY)))
        assertNear(612f, 1000f, l.at(CrossTarget.Mode(RefineMode.PROMPT)))
    }

    @Test fun inDerMitteKeineUeberlappung() {
        assertNoOverlap(layout(500f, 1000f, four))
        assertNoOverlap(layout(500f, 1000f, five))
    }

    /** Typische Telefone (360 und 411 dp breit), Knopfmitte am Rand (48 dp) und in den Ecken. */
    @Test fun amRandLiegenAlleZieleImBildschirmUndBleibenWaehlbar() {
        for ((w, h) in listOf(360 to 780, 411 to 891)) {
            val xs = listOf(48f, w / 2f, w - 48f)
            val ys = listOf(48f + 24f, h / 2f, h - 48f - 48f)
            for (cx in xs) for (cy in ys) for (modes in listOf(four, five)) {
                val l = layout(cx, cy, modes, w, h - 48, top = 24)
                val where = "($cx,$cy) auf ${w}x$h mit ${modes.size} Modi"
                assertInside(l, w, h - 48, top = 24)
                assertEquals("Reihenfolge $where", modes, modeOrder(l))
                assertNoOverlap(l)
                for ((target, p) in l.targets) {
                    assertTrue("$target zu nah $where", hypot(p.x - cx, p.y - cy) >= ModeCross.MIN_REACH_DP - 0.5f)
                }
                // Am Rand klar getrennte Richtungen; in der Ecke mindestens doppelte Winkel-Hysterese (waehlbar).
                val corner = cx != w / 2f && cy != h / 2f
                val needed = if (corner) 2 * ModeCross.ANGLE_HYSTERESIS_DEG else ModeCross.MIN_SEPARATION_DEG
                val angles = l.targets.map { direction(cx, cy, it.point) }
                for (i in angles.indices) for (j in i + 1 until angles.size) {
                    assertTrue(
                        "Richtungen $i/$j zu eng $where: ${angleBetween(angles[i], angles[j])}",
                        angleBetween(angles[i], angles[j]) >= needed - 0.5f,
                    )
                }
            }
        }
    }

    @Test fun winzigerBildschirmKlemmtAlles() {
        val l = layout(60f, 60f, five, 120, 120)
        assertInside(l, 120, 120)
    }

    @Test fun unterDerSchwelleKeinZiel() {
        val l = layout(500f, 1000f)
        assertNull(ModeCross.pick(540f, 1000f, 500f, 1000f, l, current = null, density = 1f))
    }

    @Test fun abDerSchwelleDasZielInFingerrichtung() {
        val l = layout(500f, 1000f)
        assertEquals(CrossTarget.Mode(RefineMode.SUMMARIZE), ModeCross.pick(560f, 1000f, 500f, 1000f, l, null, 1f))
        assertEquals(CrossTarget.Mode(RefineMode.OFF), ModeCross.pick(440f, 1000f, 500f, 1000f, l, null, 1f))
        assertEquals(CrossTarget.Mode(RefineMode.POLISH), ModeCross.pick(470f, 940f, 500f, 1000f, l, null, 1f))
        assertEquals(CrossTarget.Discard, ModeCross.pick(500f, 1060f, 500f, 1000f, l, null, 1f))
    }

    @Test fun kreuzGewinntNichtWegenSeinesKuerzerenRadius() {
        // Schraeg nach unten links, naeher an "Aus" (180°) als am ✕ (270°): Richtung entscheidet, nicht Abstand.
        val l = layout(500f, 1000f)
        assertEquals(CrossTarget.Mode(RefineMode.OFF), ModeCross.pick(440f, 1030f, 500f, 1000f, l, null, 1f))
    }

    @Test fun hystereseHaeltDasZielBeimZurueckzittern() {
        val l = layout(500f, 1000f)
        val right = CrossTarget.Mode(RefineMode.SUMMARIZE)
        assertEquals("unter 56, aber ueber 44: bleibt", right, ModeCross.pick(548f, 1000f, 500f, 1000f, l, right, 1f))
        assertNull("unter 44: loest", ModeCross.pick(542f, 1000f, 500f, 1000f, l, right, 1f))
        assertNull("frisch unter 56: kein Ziel", ModeCross.pick(548f, 1000f, 500f, 1000f, l, null, 1f))
    }

    @Test fun wechselErstWennDieRichtungDeutlichZumAnderenZielZeigt() {
        val l = layout(500f, 1000f)
        val polish = CrossTarget.Mode(RefineMode.POLISH)
        val beautify = CrossTarget.Mode(RefineMode.BEAUTIFY)
        // Genau nach oben, zwischen Glaetten (120°) und Schoener (60°): bleibt beim eingerasteten.
        assertEquals(polish, ModeCross.pick(500f, 900f, 500f, 1000f, l, polish, 1f))
        assertEquals(beautify, ModeCross.pick(500f, 900f, 500f, 1000f, l, beautify, 1f))
        assertEquals(beautify, ModeCross.pick(540f, 910f, 500f, 1000f, l, polish, 1f))
    }

    @Test fun deckkraftKaumSichtbarAufDemKnopfDeutlichVorDemEinrasten() {
        assertEquals(ModeCross.FAINT_ALPHA, ModeCross.alpha(0f, slopPx = 8f, density = 1f), 0.001f)
        assertEquals(ModeCross.FAINT_ALPHA, ModeCross.alpha(8f, 8f, 1f), 0.001f)
        assertEquals("voll schon vor der Einrast-Schwelle", 1f, ModeCross.alpha(ModeCross.FULL_ALPHA_DP, 8f, 1f), 0.001f)
        assertTrue(ModeCross.FULL_ALPHA_DP < ModeCross.ARM_DP)
        assertEquals(1f, ModeCross.alpha(300f, 8f, 1f), 0.001f)
        val mid = ModeCross.alpha(20f, 8f, 1f)
        assertTrue(mid > ModeCross.FAINT_ALPHA && mid < 1f)
        assertTrue("kaum sichtbar", ModeCross.FAINT_ALPHA <= 0.2f)
    }

    private fun tone(ms: Int, amplitude: Float, silenceMs: Int = 0): FloatArray {
        val n = ms * 16
        val s = FloatArray(n + silenceMs * 16)
        for (i in 0 until n) s[i] = if (i % 2 == 0) amplitude else -amplitude
        return s
    }

    @Test fun spracheBrauchtPegelUeberMindestdauer() {
        assertFalse("leer", ModeCross.hasSpeech(FloatArray(0)))
        assertFalse("Raumrauschen", ModeCross.hasSpeech(tone(2000, 0.005f)))
        assertTrue("leises Sprechen", ModeCross.hasSpeech(tone(400, 0.03f, silenceMs = 1000)))
        assertTrue("normale Sprache", ModeCross.hasSpeech(tone(1000, 0.25f)))
    }

    @Test fun hapticKnacksIstKeineSprache() {
        // 80 ms laut (Vibrationsmotor im Mikrofon), sonst still: unter der Mindestdauer.
        assertFalse(ModeCross.hasSpeech(tone(80, 0.6f, silenceMs = 1500)))
    }
}
