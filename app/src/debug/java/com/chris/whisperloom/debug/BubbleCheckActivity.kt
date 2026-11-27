package com.chris.whisperloom.debug

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import com.chris.whisperloom.R
import com.chris.whisperloom.overlay.FloatingMicService

/**
 * Nur im Debug-Build: feste Felder fuer `tools/bubble_emulator_check.py` (UX-Spec §6.22) — Feld A,
 * Feld B, ein Passwortfeld und ein Knopf ohne Feld. Mit `--ez start_bubble true` startet sie den
 * schwebenden Knopf aus dem Vordergrund (Mikrofon-Dienst darf nicht aus dem Hintergrund starten).
 * Klassische Views statt Compose: reine Pruefhilfe, keine App-Oberflaeche.
 */
class BubbleCheckActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        val plain = Button(this).apply {
            id = R.id.check_plain
            text = "ohne Feld"
            isFocusableInTouchMode = true
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 160, 48, 48)
                addView(plain)
                addView(field(R.id.check_field_a, "Feld A", InputType.TYPE_CLASS_TEXT))
                addView(field(R.id.check_field_b, "Feld B", InputType.TYPE_CLASS_TEXT))
                addView(
                    field(
                        R.id.check_field_pw, "Passwort",
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    ),
                )
            },
        )
        plain.requestFocus() // kein Feld fokussiert, bis die Pruefung eins antippt
        if (intent.getBooleanExtra(EXTRA_START_BUBBLE, false)) FloatingMicService.start(this)
    }

    private fun field(id: Int, hint: String, type: Int) = EditText(this).apply {
        this.id = id
        this.hint = hint
        inputType = type
    }

    companion object {
        const val EXTRA_START_BUBBLE = "start_bubble"
    }
}
