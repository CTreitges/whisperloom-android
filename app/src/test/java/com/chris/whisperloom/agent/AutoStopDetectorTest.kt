package com.chris.whisperloom.agent

import com.chris.whisperloom.agent.AutoStopDetector.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Der Auto-Stopp-Detektor, reine JVM. Ein Verlauf ist eine Folge von Puffern (Zeit, Peak); die
 * Zeit kommt von aussen, deshalb ist jeder Fall exakt und ohne Warten pruefbar.
 */
class AutoStopDetectorTest {

    private val stille = 0.01f

    /** Erste Entscheidung ausser CONTINUE mit ihrem Zeitpunkt, oder CONTINUE mit -1. */
    private fun ersteEntscheidung(d: AutoStopDetector, frames: List<Pair<Long, Float>>): Pair<Decision, Long> {
        for ((at, peak) in frames) {
            val e = d.feed(peak, at)
            if (e != Decision.CONTINUE) return e to at
        }
        return Decision.CONTINUE to -1L
    }

    /** 40-ms-Puffer von 0 bis [bisMs] (ausschliesslich), Pegel je Zeitpunkt. */
    private fun verlauf(bisMs: Long, schritt: Long = 40, pegel: (Long) -> Float): List<Pair<Long, Float>> =
        (0 until bisMs step schritt).map { it to pegel(it) }

    /** Silben: laut und leiser im Wechsel, aber ohne Luecke bis zur Stille. */
    private fun silbe(t: Long): Float = if ((t / 120) % 2 == 0L) 0.4f else 0.12f

    // --- Sprechpause ---------------------------------------------------------

    @Test fun stopptGenauEineSprechpauseNachDemEnde() {
        // Sprache von 0,5 s bis 3 s, danach Stille: Stopp bei 3000 + 2000, keinen Puffer frueher.
        val frames = verlauf(10_000) { t -> if (t in 500 until 3_000) silbe(t) else stille }
        assertEquals(Decision.SPEECH_ENDED to 5_000L, ersteEntscheidung(AutoStopDetector(), frames))
    }

    @Test fun eineAtempauseStopptNicht() {
        // 1,2 s Luft holen mitten im Diktat: die Aufnahme laeuft weiter bis zum echten Ende.
        val frames = verlauf(12_000) { t ->
            if (t in 500 until 2_500 || t in 3_700 until 5_000) silbe(t) else stille
        }
        assertEquals(Decision.SPEECH_ENDED to 7_000L, ersteEntscheidung(AutoStopDetector(), frames))
    }

    @Test fun diePauseKommtAusDemProfil() {
        // Sprache 0,5-3 s: Stopp je nach gewaehlter Sprechpause (20-ms-Puffer treffen 6,5 s genau).
        val frames = verlauf(12_000, schritt = 20) { t -> if (t in 500 until 3_000) silbe(t) else stille }
        mapOf(SpeechPause.SHORT to 4_200L, SpeechPause.NORMAL to 5_000L, SpeechPause.LONG to 6_500L)
            .forEach { (pause, stopp) ->
                assertEquals(pause.name, Decision.SPEECH_ENDED to stopp, ersteEntscheidung(AutoStopDetector(pause.ms), frames))
            }
    }

    @Test fun vorDerMindestdauerKommtKeinStopp() {
        // Kurzer Satz mit sehr kurzer Pause: frei waere bei 600 ms, gestoppt wird erst bei 1500.
        val frames = verlauf(4_000, schritt = 50) { t -> if (t < 400) 0.4f else stille }
        assertEquals(
            Decision.SPEECH_ENDED to AutoStopDetector.MIN_RECORDING_MS,
            ersteEntscheidung(AutoStopDetector(pauseMs = 200), frames),
        )
    }

    @Test fun unregelmaessigePufferWerdenVertragen() {
        // Puffer mal 20, mal 120 ms: gestoppt wird am ersten Puffer, der 2 s nach dem Ende liegt.
        val abstaende = longArrayOf(20, 90, 45, 120, 60)
        val frames = mutableListOf<Pair<Long, Float>>()
        var t = 0L
        var i = 0
        while (t < 10_000) {
            frames += t to (if (t in 500 until 3_000) 0.4f else stille)
            t += abstaende[i++ % abstaende.size]
        }
        val endeDerSprache = frames.first { it.first >= 3_000 }.first
        val erwartet = frames.first { it.first >= endeDerSprache + 2_000 }.first
        assertEquals(Decision.SPEECH_ENDED to erwartet, ersteEntscheidung(AutoStopDetector(), frames))
    }

    @Test fun dieEntscheidungRastetEin() {
        val stopp = AutoStopDetector()
        ersteEntscheidung(stopp, verlauf(6_000) { t -> if (t < 1_000) 0.4f else stille })
        assertEquals("Wer danach spricht, aendert nichts", Decision.SPEECH_ENDED, stopp.feed(0.5f, 6_000))

        val nichts = AutoStopDetector()
        ersteEntscheidung(nichts, verlauf(9_000) { stille })
        assertEquals(Decision.NO_SPEECH, nichts.feed(0.5f, 9_000))
        assertEquals(Decision.NO_SPEECH, nichts.feed(0.5f, 9_500))
    }

    // --- Keine Sprache -------------------------------------------------------

    @Test fun nurStilleIstNachAchtSekundenNichtsGehoert() {
        val frames = verlauf(10_000) { stille }
        assertEquals(Decision.NO_SPEECH to AutoStopDetector.NO_SPEECH_MS, ersteEntscheidung(AutoStopDetector(), frames))
    }

    @Test fun einKlickIstKeineSprache() {
        // 100 ms mit 0,8 (Klopfen ans Telefon): danach bleibt es bei "nichts gehoert".
        val frames = verlauf(10_000) { t -> if (t in 2_000 until 2_100) 0.8f else stille }
        assertEquals(Decision.NO_SPEECH to AutoStopDetector.NO_SPEECH_MS, ersteEntscheidung(AutoStopDetector(), frames))
    }

    @Test fun werKurzVorAblaufLossprichtWirdNichtAbgeschnitten() {
        // Sprache ab 7,9 s: bei 8 s ist der laute Abschnitt noch nicht bestaetigt, aber im Gange.
        val frames = verlauf(14_000) { t -> if (t in 7_900 until 9_500) 0.4f else stille }
        assertEquals(Decision.SPEECH_ENDED to 11_520L, ersteEntscheidung(AutoStopDetector(), frames))
    }

    // --- Regression: Rauschboden (fatal flaw eines Entwurfs) ------------------

    @Test fun werAbNullSprichtVerliertNichts() {
        // Dauerton 0,4 fuer 3 s ab dem ersten Puffer: ein aus den ersten Puffern gelernter Boden
        // haette die Stimme selbst zum Hintergrund erklaert — und nach 8 s "nichts gehoert".
        val frames = verlauf(10_000) { t -> if (t < 3_000) 0.4f else stille }
        assertEquals(Decision.SPEECH_ENDED to 5_000L, ersteEntscheidung(AutoStopDetector(), frames))
    }

    @Test fun fuenfSekundenDurchgehendeSpracheHaltenDurch() {
        val d = AutoStopDetector()
        verlauf(5_000) { 0.3f }.forEach { (at, peak) ->
            assertEquals("bei $at ms", Decision.CONTINUE, d.feed(peak, at))
        }
    }

    @Test fun rauschenPlusSpracheStopptErstNachDemEnde() {
        // Grundrauschen 0,08 durchgehend, Sprache 0,4 von 0,5 bis 4 s: Stopp bei 6 s, nicht vorher.
        val frames = verlauf(12_000) { t -> if (t in 500 until 4_000) 0.4f else 0.08f }
        assertEquals(Decision.SPEECH_ENDED to 6_000L, ersteEntscheidung(AutoStopDetector(), frames))
    }

    @Test fun digitaleNullenFrierenDenBodenNichtEin() {
        // Viele Mikrofone liefern beim Anlaufen reine Nullen. Ohne Untergrenze bliebe der Boden
        // danach 0 (er steigt multiplikativ) — ein Dauerbrummen wuerde nie zum Hintergrund und
        // die Aufnahme endete nur per Tipp oder Notbremse (hier: CONTINUE bis zum Schluss).
        val frames = verlauf(20_000) { t -> if (t < 500) 0f else 0.08f }
        assertEquals(Decision.NO_SPEECH to AutoStopDetector.NO_SPEECH_MS, ersteEntscheidung(AutoStopDetector(), frames))
    }

    // --- Regression: Dauerlaerm ist keine Sprache ------------------------------

    @Test fun dauerrauschenOhneSpracheIstNichtsGehoert() {
        // Auto, Strasse, Luefter: gleich ab dem ersten Puffer ueber der Schwelle und lange laut —
        // der Abschnitt endet aber nur, weil der Boden ihn einholt. Gesendet werden darf das nie.
        listOf(0.05f, 0.08f, 0.12f, 0.2f).forEach { pegel ->
            val frames = verlauf(12_000) { pegel }
            assertEquals("Pegel $pegel", Decision.NO_SPEECH to AutoStopDetector.NO_SPEECH_MS, ersteEntscheidung(AutoStopDetector(), frames))
        }
    }

    @Test fun schwankendesRauschenOhneSpracheIstNichtsGehoert() {
        val zufall = Random(42)
        val frames = verlauf(12_000) { 0.06f + 0.04f * zufall.nextFloat() }
        assertEquals(Decision.NO_SPEECH to AutoStopDetector.NO_SPEECH_MS, ersteEntscheidung(AutoStopDetector(), frames))
    }

    @Test fun lautesBrummenOhneSpracheIstNichtsGehoert() {
        // 0,4 bleibt bis ~7,4 s laut, 1,0 bis ~10 s: bewertet wird erst am Ende des Abschnitts.
        assertEquals(
            Decision.NO_SPEECH to AutoStopDetector.NO_SPEECH_MS,
            ersteEntscheidung(AutoStopDetector(), verlauf(12_000) { 0.4f }),
        )
        val (e, at) = ersteEntscheidung(AutoStopDetector(), verlauf(15_000) { 1f })
        assertEquals(Decision.NO_SPEECH, e)
        assertTrue("erst nach dem lauten Abschnitt: $at", at > AutoStopDetector.NO_SPEECH_MS)
    }

    @Test fun spracheNachDemDauerrauschenWirdErkannt() {
        // Dauerrauschen 0,08 ab dem Start, gesprochen wird erst ab 4 s: drei Worte mit kurzen
        // Luecken bis 5,4 s. Der Rauschabschnitt davor zaehlt nicht, die Worte schon.
        val worte = listOf(4_000L until 4_400L, 4_500L until 4_900L, 5_000L until 5_400L)
        val frames = verlauf(14_000) { t -> if (worte.any { t in it }) 0.4f else 0.08f }
        assertEquals(Decision.SPEECH_ENDED to 7_400L, ersteEntscheidung(AutoStopDetector(), frames))
    }

    @Test fun derBodenFaelltSofortUndSteigtGebremst() {
        val d = AutoStopDetector()
        d.feed(0.5f, 0)
        assertEquals("Start fest, nicht aus dem ersten Puffer gelernt", AutoStopDetector.FLOOR_INIT, d.floor, 1e-6f)
        d.feed(0.5f, 1_000)
        // 3 dB/s: nach einer Sekunde Faktor 10^(3/20) = 1,4125
        assertEquals(AutoStopDetector.FLOOR_INIT * 1.4125f, d.floor, 1e-4f)
        d.feed(0.014f, 1_040)
        assertEquals("Minimum-Folger: faellt sofort", 0.014f, d.floor, 1e-6f)
    }
}
