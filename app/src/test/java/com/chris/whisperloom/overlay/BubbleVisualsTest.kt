package com.chris.whisperloom.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Akzeptanz V1.1: die vier Zustaende unterscheiden sich gleichzeitig in Fuellung, Ring,
 * Icon und Label — reine Beschreibung, ohne Android.
 */
class BubbleVisualsTest {

    private val all = BubbleState.values().map { it to BubbleVisuals.visualFor(it) }

    @Test fun jederZustandUnterscheidetSichInFuellungRingIconUndLabel() {
        for ((a, va) in all) for ((b, vb) in all) {
            if (a == b) continue
            assertNotEquals("Fuellung $a/$b", va.fill, vb.fill)
            assertNotEquals("Ring $a/$b", va.ring, vb.ring)
            assertNotEquals("Label $a/$b", va.label, vb.label)
            assertNotEquals("Beschreibung $a/$b", va.description, vb.description)
        }
        // Icon: IDLE und SENDING zeigen beide das Mikrofon — dort trennt die Deckkraft (50 %) und die Farbe.
        assertEquals(BubbleVisual.Icon.STOP, BubbleVisuals.visualFor(BubbleState.RECORDING).icon)
        assertEquals(BubbleVisual.Icon.REPLAY, BubbleVisuals.visualFor(BubbleState.ERROR).icon)
        val idle = BubbleVisuals.visualFor(BubbleState.IDLE)
        val sending = BubbleVisuals.visualFor(BubbleState.SENDING)
        assertEquals(idle.icon, sending.icon)
        assertNotEquals(idle.iconTint, sending.iconTint)
        assertNotEquals(idle.iconAlpha, sending.iconAlpha)
    }

    @Test fun idleNachSpec() {
        val v = BubbleVisuals.visualFor(BubbleState.IDLE)
        assertEquals(BubbleVisual.Fill.SURFACE, v.fill)
        assertEquals(BubbleVisual.Ring.PRIMARY, v.ring)
        assertEquals(BubbleVisual.Icon.MIC, v.icon)
        assertEquals(BubbleVisual.IconTint.PRIMARY, v.iconTint)
        assertEquals(1f, v.iconAlpha)
        assertEquals(BubbleVisual.Label.NONE, v.label)
        assertEquals(BubbleVisual.Description.IDLE, v.description)
    }

    @Test fun recordingNachSpec() {
        val v = BubbleVisuals.visualFor(BubbleState.RECORDING)
        assertEquals(BubbleVisual.Fill.RECORDING, v.fill)
        assertEquals(BubbleVisual.Ring.PULSE, v.ring)
        assertEquals(BubbleVisual.Icon.STOP, v.icon)
        assertEquals(BubbleVisual.IconTint.ON_RECORDING, v.iconTint)
        assertEquals(BubbleVisual.Label.TIMER, v.label)
        assertEquals(BubbleVisual.LabelStyle.RECORDING, v.labelStyle)
    }

    @Test fun sendingNachSpec() {
        val v = BubbleVisuals.visualFor(BubbleState.SENDING)
        assertEquals(BubbleVisual.Fill.PRIMARY_CONTAINER, v.fill)
        assertEquals(BubbleVisual.Ring.ARC, v.ring)
        assertEquals(BubbleVisual.Icon.MIC, v.icon)
        assertEquals(BubbleVisual.IconTint.ON_PRIMARY_CONTAINER, v.iconTint)
        assertEquals(0.5f, v.iconAlpha)
        assertEquals(BubbleVisual.Label.SENDING, v.label)
        assertEquals(BubbleVisual.LabelStyle.NEUTRAL, v.labelStyle)
    }

    @Test fun errorNachSpec() {
        val v = BubbleVisuals.visualFor(BubbleState.ERROR)
        assertEquals(BubbleVisual.Fill.ERROR_CONTAINER, v.fill)
        assertEquals(BubbleVisual.Ring.ERROR, v.ring)
        assertEquals(BubbleVisual.Icon.REPLAY, v.icon)
        assertEquals(BubbleVisual.IconTint.ON_ERROR_CONTAINER, v.iconTint)
        assertEquals(BubbleVisual.Label.RETRY_HINT, v.label)
        assertEquals(BubbleVisual.LabelStyle.ERROR, v.labelStyle)
    }

    @Test fun kopiertHinweisAendertNurIconUndLabelVonIdle() {
        val plain = BubbleVisuals.visualFor(BubbleState.IDLE)
        val copied = BubbleVisuals.visualFor(BubbleState.IDLE, copiedHint = true)
        assertEquals(BubbleVisual.Icon.CONTENT_COPY, copied.icon)
        assertEquals(BubbleVisual.Label.COPIED, copied.label)
        assertEquals(plain.copy(icon = copied.icon, label = copied.label), copied)
        // In den anderen Zustaenden wirkt der Hinweis nicht.
        for (s in listOf(BubbleState.RECORDING, BubbleState.SENDING, BubbleState.ERROR)) {
            assertEquals(BubbleVisuals.visualFor(s), BubbleVisuals.visualFor(s, copiedHint = true))
        }
    }

    @Test fun reduceMotionErsetztNurDenPulsDurchStatischenRing() {
        val v = BubbleVisuals.visualFor(BubbleState.RECORDING, reduceMotion = true)
        assertEquals(BubbleVisual.Ring.RECORDING_STATIC, v.ring)
        assertEquals(BubbleVisuals.visualFor(BubbleState.RECORDING).copy(ring = v.ring), v)
        for (s in listOf(BubbleState.IDLE, BubbleState.SENDING, BubbleState.ERROR)) {
            assertEquals(BubbleVisuals.visualFor(s), BubbleVisuals.visualFor(s, reduceMotion = true))
        }
    }

    @Test fun textverbesserungAendertNurLabelUndBeschreibungVonSending() {
        val v = BubbleVisuals.visualFor(BubbleState.SENDING, refining = true)
        assertEquals(BubbleVisual.Label.REFINING, v.label)
        assertEquals(BubbleVisual.Description.REFINING, v.description)
        assertEquals(
            BubbleVisuals.visualFor(BubbleState.SENDING).copy(label = v.label, description = v.description),
            v,
        )
        for (s in listOf(BubbleState.IDLE, BubbleState.RECORDING, BubbleState.ERROR)) {
            assertEquals(BubbleVisuals.visualFor(s), BubbleVisuals.visualFor(s, refining = true))
        }
    }

    @Test fun nurSendingDimmtDasIcon() {
        for ((s, v) in all) {
            if (s == BubbleState.SENDING) assertEquals(0.5f, v.iconAlpha) else assertEquals(1f, v.iconAlpha)
        }
        assertTrue(BubbleVisuals.IDLE_FILL_ALPHA in 0.95f..0.97f)
    }
}
