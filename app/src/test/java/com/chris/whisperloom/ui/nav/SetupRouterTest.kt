package com.chris.whisperloom.ui.nav

import com.chris.whisperloom.Engine
import com.chris.whisperloom.OfflineRefineRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Startlogik-Formel und Schritt-Reihenfolge (UX-Spec §1.2, §2.2) — rein, ohne Android. */
class SetupRouterTest {

    private val ready = SetupFacts(
        engine = Engine.ONLINE, sttComplete = true, urlValid = true,
        micGranted = true, overlayGranted = true, welcomeSeen = true,
    )

    @Test fun ohneEngineNichtEingerichtet() {
        assertFalse(SetupRouter.isSetUp(ready.copy(engine = null)))
        assertFalse(SetupRouter.recognitionReady(SetupFacts()))
    }

    @Test fun onlineBrauchtZugangUndGueltigeUrl() {
        assertTrue(SetupRouter.isSetUp(ready))
        assertFalse(SetupRouter.isSetUp(ready.copy(sttComplete = false)))
        assertFalse(SetupRouter.isSetUp(ready.copy(urlValid = false)))
    }

    @Test fun offlineBrauchtInstalliertesModell() {
        val offline = ready.copy(engine = Engine.OFFLINE, sttComplete = false)
        assertFalse(SetupRouter.isSetUp(offline))
        assertTrue(SetupRouter.isSetUp(offline.copy(modelInstalled = true)))
    }

    @Test fun mikrofonIstPflicht() {
        assertFalse(SetupRouter.isSetUp(ready.copy(micGranted = false)))
    }

    @Test fun overlayOderBewussterTastaturPfad() {
        val noOverlay = ready.copy(overlayGranted = false)
        assertFalse(SetupRouter.isSetUp(noOverlay))
        assertFalse(SetupRouter.isSetUp(noOverlay.copy(overlaySkipped = true)))
        assertTrue(SetupRouter.isSetUp(noOverlay.copy(overlaySkipped = true, imeEnabled = true)))
    }

    @Test fun bedienungshilfeUndBenachrichtigungBlockierenNicht() {
        assertTrue(SetupRouter.isSetUp(ready.copy(a11yRunning = false, notifNeeded = true, notifGranted = false)))
    }

    @Test fun ersterOffenerSchrittFolgtDerReihenfolge() {
        assertEquals(1, SetupRouter.firstOpenStep(SetupFacts()))
        assertEquals(2, SetupRouter.firstOpenStep(SetupFacts(engine = Engine.ONLINE)))
        assertEquals(3, SetupRouter.firstOpenStep(ready.copy(micGranted = false, overlayGranted = false)))
        assertEquals(4, SetupRouter.firstOpenStep(ready.copy(overlayGranted = false)))
        // Overlay bewusst uebersprungen, Tastatur (Pflicht) fehlt: Bedienungshilfe ist der erste offene Schritt.
        assertEquals(5, SetupRouter.firstOpenStep(ready.copy(overlayGranted = false, overlaySkipped = true)))
        assertEquals(7, SetupRouter.firstOpenStep(ready.copy(overlayGranted = false, overlaySkipped = true, a11ySkipped = true)))
    }

    @Test fun startZeigtWillkommenNurBeimErstenMal() {
        assertEquals(Start.Welcome, SetupRouter.start(SetupFacts()))
        assertEquals(Start.Step(1), SetupRouter.start(SetupFacts(welcomeSeen = true)))
        assertEquals(Start.Home, SetupRouter.start(ready))
        assertEquals(Start.Home, SetupRouter.start(ready.copy(welcomeSeen = false)))
    }

    @Test fun benachrichtigungenNurAbApi33() {
        assertEquals(listOf(1, 2, 3, 4, 5, 7), SetupRouter.visibleSteps(SetupFacts(notifNeeded = false)))
        assertEquals((1..7).toList(), SetupRouter.visibleSteps(SetupFacts(notifNeeded = true)))
        assertEquals(6, SetupRouter.position(7, SetupFacts(notifNeeded = false)))
        assertEquals(7, SetupRouter.position(7, SetupFacts(notifNeeded = true)))
        assertEquals(7, SetupRouter.next(5, SetupFacts(notifNeeded = false)))
        assertEquals(null, SetupRouter.next(7, SetupFacts()))
        assertEquals(null, SetupRouter.previous(1, SetupFacts()))
    }

    @Test fun schrittZustaende() {
        assertEquals(StepState.OPEN, SetupRouter.stepState(1, SetupFacts()))
        assertEquals(StepState.DONE, SetupRouter.stepState(1, SetupFacts(engine = Engine.ONLINE)))
        assertEquals(StepState.SKIPPED, SetupRouter.stepState(4, SetupFacts(overlaySkipped = true)))
        assertEquals(StepState.DONE, SetupRouter.stepState(4, SetupFacts(overlaySkipped = true, overlayGranted = true)))
        assertEquals(StepState.OPTIONAL, SetupRouter.stepState(5, SetupFacts()))
        assertEquals(StepState.SKIPPED, SetupRouter.stepState(5, SetupFacts(a11ySkipped = true)))
        assertEquals(StepState.DONE, SetupRouter.stepState(5, SetupFacts(a11yRunning = true, a11ySkipped = true)))
        assertEquals(StepState.OPTIONAL, SetupRouter.stepState(6, SetupFacts(notifNeeded = true, notifGranted = false)))
        assertEquals(StepState.OPTIONAL, SetupRouter.stepState(7, SetupFacts()))
        assertEquals(StepState.SKIPPED, SetupRouter.stepState(7, SetupFacts(keyboardSkipped = true)))
        // "Nur Tastatur": Schritt 7 wird Pflicht, ein altes Skip-Flag zaehlt nicht mehr.
        assertEquals(StepState.OPEN, SetupRouter.stepState(7, SetupFacts(overlaySkipped = true, keyboardSkipped = true)))
        assertTrue(SetupRouter.isMandatory(7, SetupFacts(overlaySkipped = true)))
        assertFalse(SetupRouter.isMandatory(7, SetupFacts()))
    }

    @Test fun fortschrittZaehltErledigteUndUebersprungene() {
        assertEquals(0, SetupRouter.doneCount(SetupFacts()))
        // 1–3 erledigt, 4 + 5 uebersprungen, 7 (Pflicht im Tastatur-Pfad) offen -> 5 von 6.
        assertEquals(5, SetupRouter.doneCount(ready.copy(a11ySkipped = true, overlayGranted = false, overlaySkipped = true)))
        assertEquals(6, SetupRouter.visibleSteps(ready).size)
    }

    @Test fun schritt2bWahlTextmodellOderUeberspringen() {
        assertEquals(TextChoice.SKIP, SetupRouter.textChoice(OfflineRefineRule.SKIP, localReady = true, localLoading = false))
        assertEquals(TextChoice.LOCAL, SetupRouter.textChoice(OfflineRefineRule.LOCAL, localReady = true, localLoading = false))
        assertEquals("Weiter schon waehrend des Downloads", TextChoice.LOCAL, SetupRouter.textChoice(OfflineRefineRule.LOCAL, localReady = false, localLoading = true))
        assertEquals(TextChoice.LOCAL, SetupRouter.textChoice(OfflineRefineRule.ONLINE_LOCAL, localReady = true, localLoading = false))
        // Der Standard "lokal" allein ist noch keine Wahl: ohne Modell bleibt Weiter gesperrt.
        assertEquals(null, SetupRouter.textChoice(OfflineRefineRule.LOCAL, localReady = false, localLoading = false))
    }

    @Test fun dieWahlIn2bAendertNichtsAmEingerichtetSein() {
        val f = SetupFacts(engine = Engine.OFFLINE, modelInstalled = true, micGranted = true, overlayGranted = true)
        assertTrue(SetupRouter.isSetUp(f))
    }
}
