package com.chris.whisperloom.a11y

import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Feld-Kennung fuers Ausblenden bis zum naechsten Feld (UX-Spec §6.22): nur editierbare Felder, kein Inhalt. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FieldKeyTest {

    private fun knoten(editable: Boolean) = AccessibilityNodeInfo().apply {
        isEditable = editable
        text = "geheimer Inhalt"
    }

    @Test fun editierbaresFeldHatKennung() {
        val node = knoten(editable = true)
        val key = TextInserterAccessibilityService.fieldKeyOf(windowId = 3, node = node)
        assertEquals(FieldKey(3, node.hashCode()), key)
        assertEquals("gleicher Knoten = gleiche Kennung", key, TextInserterAccessibilityService.fieldKeyOf(3, node))
    }

    @Test fun nichtEditierbarOderOhneKnotenKeineKennung() {
        assertNull(TextInserterAccessibilityService.fieldKeyOf(3, knoten(editable = false)))
        assertNull(TextInserterAccessibilityService.fieldKeyOf(3, null))
    }
}
