package com.chris.whisperloom.overlay

import com.chris.whisperloom.RefineMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** TalkBack-Aktionen am schwebenden Knopf (N11), ohne Android. */
class BubbleActionsTest {

    private val modes = RefineMode.settings(promptEnabled = true)

    @Test fun ruheJeModusEineAktionUndAusblenden() {
        val a = BubbleActions.of(BubbleState.IDLE, modes, RefineMode.BEAUTIFY)
        assertEquals(modes.map { BubbleAction.SetMode(it, it == RefineMode.BEAUTIFY) } + BubbleAction.Hide, a)
    }

    @Test fun aufnahmeUndFehlerKoennenVerwerfen() {
        for (state in listOf(BubbleState.RECORDING, BubbleState.ERROR)) {
            val a = BubbleActions.of(state, modes, RefineMode.OFF)
            assertEquals(BubbleAction.Discard, a.last())
            assertTrue(BubbleAction.Hide !in a)
        }
    }

    @Test fun waehrendDerUebertragungKeineAktionen() {
        assertEquals(emptyList<BubbleAction>(), BubbleActions.of(BubbleState.SENDING, modes, RefineMode.OFF))
    }

    @Test fun promptNurWennEingeschaltet() {
        val a = BubbleActions.of(BubbleState.IDLE, RefineMode.settings(promptEnabled = false), RefineMode.OFF)
        assertTrue(a.none { it is BubbleAction.SetMode && it.mode == RefineMode.PROMPT })
    }
}
