package com.chris.whisperloom.overlay

import android.view.HapticFeedbackConstants
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Motion-Werte und Haptik-Zuordnung nach UX-Spec §5.4 — reine Rechnung. */
class BubbleMotionTest {

    @Test fun dauernNachSpec() {
        assertEquals(1200L, BubbleMotion.PULSE_DURATION_MS)
        assertEquals(1000L, BubbleMotion.ARC_ROTATION_MS)
        assertEquals(300L, BubbleMotion.SHAKE_DURATION_MS)
        assertEquals(300L, BubbleMotion.SUCCESS_RING_MS)
        assertEquals(2000L, BubbleMotion.COPIED_HINT_MS)
        assertEquals(200L, BubbleMotion.CANCEL_APPEAR_MS)
        assertEquals(1.12f, BubbleMotion.CANCEL_HIT_SCALE)
        assertEquals(0.8f, BubbleMotion.CANCEL_SCALE_START)
    }

    @Test fun pulsAlphaFaelltVon045AufNull() {
        assertEquals(0.45f, BubbleMotion.pulseAlpha(0f), 1e-6f)
        assertEquals(0.225f, BubbleMotion.pulseAlpha(0.5f), 1e-6f)
        assertEquals(0f, BubbleMotion.pulseAlpha(1f), 1e-6f)
        assertEquals(0f, BubbleMotion.pulseAlpha(2f), 1e-6f) // ueber 1 geklemmt
    }

    @Test fun pulsSkaliertVonEinsAuf135() {
        assertEquals(1f, BubbleMotion.pulseScale(0f, 0f), 1e-6f)
        assertEquals(1.35f, BubbleMotion.pulseScale(1f, 0f), 1e-6f)
        assertEquals(1.175f, BubbleMotion.pulseScale(0.5f, 0f), 1e-6f)
    }

    @Test fun pegelHebtDenStartradiusUmBisZu15Prozent() {
        assertEquals(1.15f, BubbleMotion.pulseScale(0f, 1f), 1e-6f)
        assertEquals(1.075f, BubbleMotion.pulseScale(0f, 0.5f), 1e-6f)
        assertEquals(1.15f, BubbleMotion.pulseScale(0f, 5f), 1e-6f) // Pegel geklemmt
        // Das Ende bleibt 1,35 — der Puls wird bei lautem Pegel nur "voller", nicht groesser.
        assertEquals(1.35f, BubbleMotion.pulseScale(1f, 1f), 1e-6f)
    }

    @Test fun shakeHatDreiHalbschwingungenMitSechsDp() {
        assertArrayEquals(floatArrayOf(0f, 6f, -6f, 6f, 0f), BubbleMotion.shakeOffsetsDp(), 1e-6f)
    }

    @Test fun reduceMotionBeiDauerSkalierungNull() {
        assertTrue(BubbleMotion.isReduceMotion(0f))
        assertFalse(BubbleMotion.isReduceMotion(1f))
        assertFalse(BubbleMotion.isReduceMotion(0.5f))
    }

    @Test fun haptikAbApi30() {
        assertEquals(HapticFeedbackConstants.CONFIRM, BubbleMotion.hapticConstant(BubbleMotion.Haptic.CONFIRM, 30))
        assertEquals(HapticFeedbackConstants.REJECT, BubbleMotion.hapticConstant(BubbleMotion.Haptic.REJECT, 35))
        assertEquals(
            HapticFeedbackConstants.CONTEXT_CLICK,
            BubbleMotion.hapticConstant(BubbleMotion.Haptic.CONTEXT_CLICK, 30),
        )
    }

    @Test fun erfolgAbApi34IstToggleOffEinmal() {
        assertEquals(HapticFeedbackConstants.TOGGLE_OFF, BubbleMotion.hapticConstant(BubbleMotion.Haptic.SUCCESS, 34))
        assertEquals(HapticFeedbackConstants.TOGGLE_OFF, BubbleMotion.hapticConstant(BubbleMotion.Haptic.SUCCESS, 36))
        assertEquals(1, BubbleMotion.hapticPulses(BubbleMotion.Haptic.SUCCESS, 34))
    }

    @Test fun erfolgDarunterIstEinDoppelimpulsMit80Ms() {
        assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, BubbleMotion.hapticConstant(BubbleMotion.Haptic.SUCCESS, 33))
        assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, BubbleMotion.hapticConstant(BubbleMotion.Haptic.SUCCESS, 26))
        assertEquals(2, BubbleMotion.hapticPulses(BubbleMotion.Haptic.SUCCESS, 33))
        assertEquals(80L, BubbleMotion.SUCCESS_PULSE_GAP_MS)
    }

    @Test fun nurDerErfolgWirdGedoppelt() {
        for (kind in listOf(BubbleMotion.Haptic.CONFIRM, BubbleMotion.Haptic.CONTEXT_CLICK, BubbleMotion.Haptic.REJECT)) {
            for (sdk in listOf(26, 29, 30, 33, 34, 36)) assertEquals("$kind/$sdk", 1, BubbleMotion.hapticPulses(kind, sdk))
        }
    }

    /** Der Erfolg muss sich von Start und Senden unterscheiden — als Konstante oder als Doppelimpuls. */
    @Test fun erfolgFuehltSichAndersAnAlsStartUndSenden() {
        for (sdk in listOf(26, 29, 30, 33, 34, 36)) {
            val erfolg = BubbleMotion.hapticConstant(BubbleMotion.Haptic.SUCCESS, sdk) to
                BubbleMotion.hapticPulses(BubbleMotion.Haptic.SUCCESS, sdk)
            for (andere in listOf(BubbleMotion.Haptic.CONFIRM, BubbleMotion.Haptic.CONTEXT_CLICK)) {
                val v = BubbleMotion.hapticConstant(andere, sdk) to BubbleMotion.hapticPulses(andere, sdk)
                assertNotEquals("SUCCESS wie $andere bei API $sdk", v, erfolg)
            }
        }
    }

    @Test fun haptikFallbackVorApi30() {
        assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, BubbleMotion.hapticConstant(BubbleMotion.Haptic.CONFIRM, 29))
        assertEquals(HapticFeedbackConstants.LONG_PRESS, BubbleMotion.hapticConstant(BubbleMotion.Haptic.REJECT, 26))
        assertEquals(
            HapticFeedbackConstants.CONTEXT_CLICK,
            BubbleMotion.hapticConstant(BubbleMotion.Haptic.CONTEXT_CLICK, 26),
        )
    }
}
