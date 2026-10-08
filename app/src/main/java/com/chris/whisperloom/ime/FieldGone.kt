package com.chris.whisperloom.ime

import android.content.Context
import android.widget.Toast
import com.chris.whisperloom.R
import com.chris.whisperloom.ui.components.copyToClipboard
import com.chris.whisperloom.ui.components.needsOwnCopyConfirmation

/**
 * Der fertige Text kam in kein Feld mehr (App gewechselt, Tastatur zu — N2). Liegt er im Verlauf,
 * genuegt der Hinweis. Sonst (Verlauf aus) geht er in die Zwischenablage wie beim schwebenden Knopf,
 * die Bestaetigung nur bis Android 12 selbst (ab 13 zeigt sie das System, N4). Aus einem Passwort-
 * oder Inkognito-Feld nie: dort bleibt nur der Hinweis, dass nichts eingefuegt wurde.
 */
internal object FieldGone {

    /** @return Hinweis der Ruhe-Statuszeile bis zum naechsten Diktat */
    fun keep(context: Context, text: String, inHistory: Boolean, privateField: Boolean): Int = when {
        inHistory -> R.string.kb_only_in_history
        privateField -> R.string.kb_not_inserted
        else -> {
            copyToClipboard(context, text)
            if (needsOwnCopyConfirmation()) Toast.makeText(context, R.string.share_copied, Toast.LENGTH_SHORT).show()
            R.string.kb_only_in_clipboard
        }
    }
}
