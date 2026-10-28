package com.chris.whisperloom.a11y

import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Die Bedienungshilfe fuegt nie in ein Passwortfeld ein (UX-Spec §6.21) — gleich, ob das Feld
 * sich per Knoten oder per Eingabetyp als Passwortfeld meldet. Zahlen- und Telefonfelder bleiben.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InsertPolicyTest {

    private fun feld(password: Boolean = false, inputType: Int = InputType.TYPE_CLASS_TEXT) =
        AccessibilityNodeInfo().apply {
            isEditable = true
            isPassword = password
            this.inputType = inputType
        }

    @Test fun passwortLautKnotenGesperrt() {
        assertEquals(InsertResult.BLOCKED, TextInserterAccessibilityService.insertInto(feld(password = true), "geheim"))
    }

    @Test fun passwortLautEingabetypGesperrt() {
        val typ = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        assertEquals(InsertResult.BLOCKED, TextInserterAccessibilityService.insertInto(feld(inputType = typ), "geheim"))
        val pin = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        assertEquals(InsertResult.BLOCKED, TextInserterAccessibilityService.insertInto(feld(inputType = pin), "1234"))
    }

    @Test fun zahlenUndTelefonfelderNichtGesperrt() {
        for (typ in listOf(InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_TEXT)) {
            assertEquals("Typ $typ", InsertResult.INSERTED, TextInserterAccessibilityService.insertInto(feld(inputType = typ), "0221 12345"))
        }
    }

    @Test fun ohneFeldScheitertEs() {
        assertEquals(InsertResult.FAILED, TextInserterAccessibilityService.insertInto(null, "Hallo"))
    }

    @Test fun ohneDienstScheitertEs() {
        assertEquals(InsertResult.FAILED, TextInserterAccessibilityService.tryInsert("Hallo"))
    }
}
