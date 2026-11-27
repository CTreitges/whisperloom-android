package com.chris.whisperloom.a11y

import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private val ime = AccessibilityWindowInfo.TYPE_INPUT_METHOD
    private val appFenster = AccessibilityWindowInfo.TYPE_APPLICATION

    @Test fun meldungFeldMitTastatur() {
        val node = knoten(editable = true)
        val focus = TextInserterAccessibilityService.focusOf(listOf(ime, appFenster), node)
        assertEquals(FieldFocus(FieldKey(node.windowId, node.hashCode()), password = false, keyboardOpen = true), focus)
    }

    @Test fun meldungOhneTastaturfenster() {
        val focus = TextInserterAccessibilityService.focusOf(listOf(appFenster), knoten(editable = true))
        assertFalse(focus.keyboardOpen)
    }

    @Test fun meldungPasswortNachKnotenOderEingabetyp() {
        val perKnoten = knoten(editable = true).apply { isPassword = true }
        assertTrue(TextInserterAccessibilityService.focusOf(listOf(ime), perKnoten).password)
        val perTyp = knoten(editable = true).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        assertTrue(TextInserterAccessibilityService.focusOf(listOf(ime), perTyp).password)
    }

    @Test fun n6_zahlenfeldIstKeinPasswort() {
        val zahl = knoten(editable = true).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        assertFalse(TextInserterAccessibilityService.focusOf(listOf(ime), zahl).password)
        val telefon = knoten(editable = true).apply { inputType = InputType.TYPE_CLASS_PHONE }
        assertFalse(TextInserterAccessibilityService.focusOf(listOf(ime), telefon).password)
    }

    @Test fun meldungOhneEditierbarenFokus() {
        assertEquals(FieldFocus(null, false, true), TextInserterAccessibilityService.focusOf(listOf(ime), knoten(editable = false)))
        assertEquals(FieldFocus(null, false, false), TextInserterAccessibilityService.focusOf(emptyList(), null))
    }

    @Test fun nichtEditierbarOderOhneKnotenKeineKennung() {
        assertNull(TextInserterAccessibilityService.fieldKeyOf(3, knoten(editable = false)))
        assertNull(TextInserterAccessibilityService.fieldKeyOf(3, null))
    }
}
