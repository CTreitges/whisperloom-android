package com.chris.whisperloom.history

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Welche Felder nie in den Verlauf kommen — reine Funktion, ohne Geraet. */
class HistoryPolicyTest {

    private fun privat(inputType: Int, imeOptions: Int = 0) = HistoryPolicy.isPrivateField(inputType, imeOptions)

    private val text = InputType.TYPE_CLASS_TEXT
    private val number = InputType.TYPE_CLASS_NUMBER

    @Test fun allePasswortVariantenSindPrivat() {
        assertTrue(privat(text or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertTrue(privat(text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertTrue(privat(text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertTrue(privat(number or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
        assertTrue("PIN mit Ziffern-Flag", privat(number or InputType.TYPE_NUMBER_VARIATION_PASSWORD or InputType.TYPE_NUMBER_FLAG_DECIMAL))
    }

    @Test fun inkognitoFlagIstPrivat() {
        assertTrue(privat(text, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
        assertTrue("mit Enter-Aktion", privat(text or InputType.TYPE_TEXT_VARIATION_URI, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_ACTION_GO))
    }

    @Test fun normaleFelderSindNichtPrivat() {
        assertFalse(privat(text))
        assertFalse(privat(text or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
        assertFalse(privat(text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertFalse(privat(text or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS))
        assertFalse(privat(text or InputType.TYPE_TEXT_VARIATION_URI))
        assertFalse("Adressleiste ohne Vorschlaege ist kein Inkognito", privat(text or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
        assertFalse(privat(number))
        assertFalse(privat(InputType.TYPE_CLASS_PHONE))
        assertFalse(privat(InputType.TYPE_NULL))
        assertFalse("andere imeOptions zaehlen nicht", privat(text, EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI))
    }

    /** 0x80 ist nur bei Text ein Passwort: bei Zahlen (und Datum) ist die Variante etwas anderes. */
    @Test fun varianteZaehltNurMitIhrerKlasse() {
        assertFalse(privat(number or 0x80))
        assertFalse(privat(number or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertFalse(privat(InputType.TYPE_CLASS_DATETIME or 0x80))
        assertFalse("Zahlen-Passwort-Variante bei Text", privat(text or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
    }
}
