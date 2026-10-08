package com.chris.whisperloom.history

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** Was nie in den Verlauf kommt: Text aus Passwort- und Inkognito-Feldern (Plan §6.1). */
object HistoryPolicy {

    /**
     * Privates Feld: eine der vier Passwort-Varianten oder [EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING]
     * (Inkognito-Tab, privater Modus; laut Android "should not update any personalized data such as
     * typing history"). Die Variante zaehlt nur zusammen mit ihrer Klasse: 0x80 ist bei Text ein
     * Passwort, bei Zahlen nicht. Rein, ohne Geraet testbar.
     *
     * @param imeOptions beim schwebenden Knopf 0 — die Bedienungshilfe kennt keine imeOptions.
     */
    fun isPrivateField(inputType: Int, imeOptions: Int): Boolean {
        if (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return true
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    /** Das Feld der Tastatur; ohne Feld (null) nicht privat. */
    fun isPrivateField(info: EditorInfo?): Boolean = info != null && isPrivateField(info.inputType, info.imeOptions)
}
