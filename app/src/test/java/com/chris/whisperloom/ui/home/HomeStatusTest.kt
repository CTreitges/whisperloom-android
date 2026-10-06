package com.chris.whisperloom.ui.home

import com.chris.whisperloom.Engine
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.RefineDecision
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.api.RefineBlock
import com.chris.whisperloom.ui.components.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home-Statuslogik (UX-Spec §2.1): Sperre, Banner-Prioritaet, Status-Farben, Tastatur-Zeile. */
class HomeStatusTest {

    @Test fun heroSperreMikrofonVorOverlay() {
        assertEquals(HomeStatus.Blocked.NONE, HomeStatus.blocked(micGranted = true, canDrawOverlays = true))
        assertEquals(HomeStatus.Blocked.MIC, HomeStatus.blocked(micGranted = false, canDrawOverlays = false))
        assertEquals(HomeStatus.Blocked.OVERLAY, HomeStatus.blocked(micGranted = true, canDrawOverlays = false))
    }

    @Test fun bannerZeigtNurDenErstenOffenenPunkt() {
        assertEquals(HomeStatus.Banner.NONE, HomeStatus.banner(true, Engine.ONLINE, false, true, true))
        assertEquals(HomeStatus.Banner.A11Y, HomeStatus.banner(false, Engine.OFFLINE, false, true, false))
        assertEquals(HomeStatus.Banner.MODEL, HomeStatus.banner(true, Engine.OFFLINE, false, true, false))
        assertEquals(HomeStatus.Banner.NOTIF, HomeStatus.banner(true, Engine.OFFLINE, true, true, false))
        assertEquals(HomeStatus.Banner.NONE, HomeStatus.banner(true, Engine.ONLINE, false, false, false))
    }

    @Test fun bannerTextmodellNachModellUndVorBenachrichtigung() {
        assertEquals(HomeStatus.Banner.TEXT_MODEL, HomeStatus.banner(true, Engine.OFFLINE, true, true, false, textModelMissing = true))
        assertEquals(HomeStatus.Banner.MODEL, HomeStatus.banner(true, Engine.OFFLINE, false, true, true, textModelMissing = true))
        assertEquals(HomeStatus.Banner.A11Y, HomeStatus.banner(false, Engine.OFFLINE, true, true, true, textModelMissing = true))
        assertEquals(HomeStatus.Banner.NONE, HomeStatus.banner(true, Engine.OFFLINE, true, false, false, textModelMissing = false))
    }

    @Test fun offlineZeileNachRegelZugangUndModell() {
        val r = OfflineRefineRule.entries
        fun zeile(rule: OfflineRefineRule, own: Boolean, local: Boolean) = HomeStatus.offlineRefine(rule, own, local)
        assertEquals(HomeStatus.OfflineRefine.LOCAL, zeile(OfflineRefineRule.LOCAL, own = true, local = true))
        assertEquals(HomeStatus.OfflineRefine.MISSING, zeile(OfflineRefineRule.LOCAL, own = true, local = false))
        assertEquals(HomeStatus.OfflineRefine.ONLINE_LOCAL, zeile(OfflineRefineRule.ONLINE_LOCAL, own = true, local = true))
        assertEquals(HomeStatus.OfflineRefine.LOCAL, zeile(OfflineRefineRule.ONLINE_LOCAL, own = false, local = true))
        // Ohne Modell fehlt es auch mit eigenem Zugang: ohne Netz gaebe es keinen Ausweg (Spec §0.4).
        assertEquals(HomeStatus.OfflineRefine.MISSING, zeile(OfflineRefineRule.ONLINE_LOCAL, own = true, local = false))
        assertEquals(HomeStatus.OfflineRefine.ONLINE, zeile(OfflineRefineRule.SKIP, own = true, local = false))
        assertEquals(HomeStatus.OfflineRefine.SKIPPED, zeile(OfflineRefineRule.SKIP, own = false, local = true))
        // Nur das fehlende Modell warnt.
        r.forEach { rule ->
            listOf(true, false).forEach { own ->
                listOf(true, false).forEach { local ->
                    val state = zeile(rule, own, local)
                    val tone = HomeStatus.offlineRefineTone(state)
                    assertEquals("$rule/$own/$local", if (state == HomeStatus.OfflineRefine.MISSING) Tone.WARNING else Tone.NEUTRAL, tone)
                    assertEquals(
                        "Warnung genau wie localModelMissing ($rule/$local)",
                        RefineDecision.localModelMissing(Engine.OFFLINE, RefineMode.POLISH, RefineMode.OFF, rule, local),
                        state == HomeStatus.OfflineRefine.MISSING,
                    )
                }
            }
        }
    }

    @Test fun statusFarben() {
        assertEquals(Tone.SUCCESS, HomeStatus.recognitionTone(Engine.ONLINE, false))
        assertEquals(Tone.ERROR, HomeStatus.recognitionTone(Engine.OFFLINE, false))
        assertEquals(Tone.SUCCESS, HomeStatus.recognitionTone(Engine.OFFLINE, true))
        assertEquals(Tone.ERROR, HomeStatus.recognitionTone(null, true))
        assertEquals(Tone.SUCCESS, HomeStatus.permissionsTone(true, true, true))
        assertEquals(Tone.WARNING, HomeStatus.permissionsTone(true, true, false))
        assertEquals(Tone.ERROR, HomeStatus.permissionsTone(true, false, true))
    }

    @Test fun tastaturZeileUndModelleZeile() {
        assertEquals(HomeStatus.Keyboard.ACTIVE, HomeStatus.keyboard(true, true))
        assertEquals(HomeStatus.Keyboard.ENABLED, HomeStatus.keyboard(true, false))
        assertEquals(HomeStatus.Keyboard.OFF, HomeStatus.keyboard(false, true))
        assertTrue(HomeStatus.showModelsRow(1, Engine.ONLINE))
        assertTrue(HomeStatus.showModelsRow(0, Engine.OFFLINE))
        assertFalse(HomeStatus.showModelsRow(0, Engine.ONLINE))
    }

    @Test fun textZeileWarntNurBeiGewaehlterStufeOhneMoeglichenChat() {
        assertEquals(Tone.WARNING, HomeStatus.refineTone(RefineMode.POLISH, RefineBlock.NO_CHAT))
        assertEquals(Tone.WARNING, HomeStatus.refineTone(RefineMode.SUMMARIZE, RefineBlock.NO_MODEL))
        assertEquals(Tone.WARNING, HomeStatus.refineTone(RefineMode.POLISH, RefineBlock.OFFLINE))
        assertEquals(Tone.NEUTRAL, HomeStatus.refineTone(RefineMode.POLISH, null))
        // Stufe "Aus": nichts zu verbessern, also auch nichts zu warnen.
        assertEquals(Tone.NEUTRAL, HomeStatus.refineTone(RefineMode.OFF, RefineBlock.NO_CHAT))
    }
}
