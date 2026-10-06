package com.chris.whisperloom

import com.chris.whisperloom.OfflineRefineRule.LOCAL
import com.chris.whisperloom.OfflineRefineRule.ONLINE_LOCAL
import com.chris.whisperloom.OfflineRefineRule.SKIP
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Die komplette Entscheidungstabelle aus Spec §2 — reines JVM. Jede Zeile steht ausgeschrieben
 * da, damit eine Aenderung der Tabelle hier sichtbar wird und nicht nur im Code.
 */
class RefineDecisionTest {

    private val online = RefineRoute.Online(fallbackLocal = false)
    private val onlineThenLocal = RefineRoute.Online(fallbackLocal = true)
    private val local = RefineRoute.Local
    private val noNet = RefineRoute.Raw(RefineHint.NO_NET)
    private val missing = RefineRoute.Raw(RefineHint.LOCAL_MISSING)
    private val chosenRaw = RefineRoute.Raw(null)

    private data class Case(
        val rule: OfflineRefineRule,
        val ownOnline: Boolean,
        val network: Boolean,
        val localReady: Boolean,
        val expected: RefineRoute,
    )

    /** Offline-Erkennung, Stufe aktiv: alle 3 x 2 x 2 x 2 Kombinationen. */
    private val offlineCases = listOf(
        // Lokales Textmodell: nie online, egal ob Zugang und Netz da sind.
        Case(LOCAL, ownOnline = true, network = true, localReady = true, expected = local),
        Case(LOCAL, ownOnline = true, network = true, localReady = false, expected = missing),
        Case(LOCAL, ownOnline = true, network = false, localReady = true, expected = local),
        Case(LOCAL, ownOnline = true, network = false, localReady = false, expected = missing),
        Case(LOCAL, ownOnline = false, network = true, localReady = true, expected = local),
        Case(LOCAL, ownOnline = false, network = true, localReady = false, expected = missing),
        Case(LOCAL, ownOnline = false, network = false, localReady = true, expected = local),
        Case(LOCAL, ownOnline = false, network = false, localReady = false, expected = missing),
        // Online, ohne Netz lokal: online nur mit eigenem Zugang und Netz; Fehler -> lokal, wenn bereit.
        Case(ONLINE_LOCAL, ownOnline = true, network = true, localReady = true, expected = onlineThenLocal),
        Case(ONLINE_LOCAL, ownOnline = true, network = true, localReady = false, expected = online),
        Case(ONLINE_LOCAL, ownOnline = true, network = false, localReady = true, expected = local),
        Case(ONLINE_LOCAL, ownOnline = true, network = false, localReady = false, expected = missing),
        Case(ONLINE_LOCAL, ownOnline = false, network = true, localReady = true, expected = local),
        Case(ONLINE_LOCAL, ownOnline = false, network = true, localReady = false, expected = missing),
        Case(ONLINE_LOCAL, ownOnline = false, network = false, localReady = true, expected = local),
        Case(ONLINE_LOCAL, ownOnline = false, network = false, localReady = false, expected = missing),
        // Ueberspringen: online nur mit eigenem Zugang und Netz, sonst ohne KI; das lokale Modell zaehlt nie.
        Case(SKIP, ownOnline = true, network = true, localReady = true, expected = online),
        Case(SKIP, ownOnline = true, network = true, localReady = false, expected = online),
        Case(SKIP, ownOnline = true, network = false, localReady = true, expected = noNet),
        Case(SKIP, ownOnline = true, network = false, localReady = false, expected = noNet),
        Case(SKIP, ownOnline = false, network = true, localReady = true, expected = chosenRaw),
        Case(SKIP, ownOnline = false, network = true, localReady = false, expected = chosenRaw),
        Case(SKIP, ownOnline = false, network = false, localReady = true, expected = chosenRaw),
        Case(SKIP, ownOnline = false, network = false, localReady = false, expected = chosenRaw),
    )

    private val bools = listOf(true, false)

    @Test fun offlineErkennungNachDerTabelle() {
        // Die Liste deckt jede Kombination genau einmal ab.
        assertEquals(24, offlineCases.map { listOf(it.rule, it.ownOnline, it.network, it.localReady) }.toSet().size)
        for (c in offlineCases) {
            val actual = RefineDecision.route(Engine.OFFLINE, c.rule, true, c.ownOnline, c.network, c.localReady)
            assertEquals(c.toString(), c.expected, actual)
        }
    }

    @Test fun onlineErkennungWieBisherNurMitNetz() {
        // Regel, eigener Zugang und lokales Modell zaehlen bei Online-Erkennung nicht.
        for (engine in listOf(Engine.ONLINE, null)) {
            for (rule in OfflineRefineRule.entries) for (own in bools) for (ready in bools) {
                val case = "$engine $rule own=$own ready=$ready"
                assertEquals(case, online, RefineDecision.route(engine, rule, true, own, network = true, localReady = ready))
                assertEquals(case, noNet, RefineDecision.route(engine, rule, true, own, network = false, localReady = ready))
            }
        }
    }

    @Test fun stufeAusHeisstImmerOhneKiUndOhneHinweis() {
        for (engine in listOf(Engine.ONLINE, Engine.OFFLINE, null)) {
            for (rule in OfflineRefineRule.entries) for (own in bools) for (net in bools) for (ready in bools) {
                assertEquals(
                    "$engine $rule own=$own net=$net ready=$ready",
                    chosenRaw,
                    RefineDecision.route(engine, rule, false, own, net, ready),
                )
            }
        }
    }

    // --- Pflichtkarte / Home-Warnung ------------------------------------------------

    @Test fun modellFehltNurBeiOfflineMitStufeUndLokalerRegel() {
        val modes = listOf(RefineMode.OFF, RefineMode.POLISH)
        val expectedTrue = setOf(
            listOf(RefineMode.POLISH, RefineMode.OFF, LOCAL),
            listOf(RefineMode.OFF, RefineMode.POLISH, LOCAL),
            listOf(RefineMode.POLISH, RefineMode.POLISH, LOCAL),
            listOf(RefineMode.POLISH, RefineMode.OFF, ONLINE_LOCAL),
            listOf(RefineMode.OFF, RefineMode.POLISH, ONLINE_LOCAL),
            listOf(RefineMode.POLISH, RefineMode.POLISH, ONLINE_LOCAL),
        )
        var count = 0
        for (engine in listOf(Engine.ONLINE, Engine.OFFLINE, null)) {
            for (dictation in modes) for (share in modes) for (rule in OfflineRefineRule.entries) for (ready in bools) {
                val missing = RefineDecision.localModelMissing(engine, dictation, share, rule, ready)
                val want = engine == Engine.OFFLINE && !ready && listOf(dictation, share, rule) in expectedTrue
                assertEquals("$engine diktat=$dictation share=$share $rule ready=$ready", want, missing)
                if (missing) count++
            }
        }
        assertEquals(6, count)
    }

    @Test fun jedeKiStufeZaehltFuerDiePflichtkarte() {
        for (mode in RefineMode.entries - RefineMode.OFF) {
            assertEquals(mode.name, true, RefineDecision.localModelMissing(Engine.OFFLINE, mode, RefineMode.OFF, LOCAL, false))
        }
    }

    // --- Regel-Schluessel -----------------------------------------------------------

    @Test fun regelSchluesselUndDefault() {
        assertEquals(listOf("local", "online_local", "skip"), OfflineRefineRule.entries.map { it.key })
        for (r in OfflineRefineRule.entries) assertEquals(r, OfflineRefineRule.fromKey(r.key))
        assertEquals(LOCAL, OfflineRefineRule.fromKey(null))
        assertEquals(LOCAL, OfflineRefineRule.fromKey("no_net"))
    }
}
