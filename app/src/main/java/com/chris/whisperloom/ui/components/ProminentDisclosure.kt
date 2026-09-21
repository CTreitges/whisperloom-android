package com.chris.whisperloom.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.chris.whisperloom.R

/**
 * Prominent Disclosure — Google-Play-Pflicht vor dem Zugriff auf sensible Daten (Mikrofon) bzw.
 * vor der Aktivierung der Bedienungshilfe. Ein EIGENER, von anderen Dialogen getrennter Hinweis
 * erscheint VOR dem System-Permission-Dialog bzw. vor dem Öffnen der Bedienungshilfe-Einstellungen,
 * beschreibt die Datennutzung (Aufnahme, optionaler Versand an den gewählten Anbieter) und lässt
 * nur nach AKTIVER Zustimmung ("Fortfahren") fortfahren.
 *
 * Doku: support.google.com/googleplay/android-developer/answer/11150561 (Best practices) und
 * .../answer/10964491 (AccessibilityService). Der Dialog erscheint nur, solange die Berechtigung
 * fehlt (der Aufrufer schaltet ihn im "noch nicht erteilt"-Zweig davor) — also immer vor dem
 * ersten Zugriff, ohne persistentes Flag.
 */
enum class DisclosureKind(val titleRes: Int, val bodyRes: Int) {
    MICROPHONE(R.string.disclosure_mic_title, R.string.disclosure_mic_body),
    ACCESSIBILITY(R.string.disclosure_a11y_title, R.string.disclosure_a11y_body),
}

/** [request] öffnet den Disclosure-Dialog; erst "Fortfahren" ruft das übergebene onAccept. */
class DisclosureGate(val request: () -> Unit)

/**
 * Schaltet den Prominent-Disclosure-Dialog vor [onAccept] (Permission-Launcher oder Settings-Intent).
 * Der Dialog wird von diesem Composable selbst gerendert — im Composition-Scope des Aufrufers.
 */
@Composable
fun rememberDisclosureGate(kind: DisclosureKind, onAccept: () -> Unit): DisclosureGate {
    var show by remember { mutableStateOf(false) }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(stringResource(kind.titleRes)) },
            text = { Text(stringResource(kind.bodyRes)) },
            confirmButton = {
                TextButton(onClick = {
                    show = false
                    onAccept()
                }) { Text(stringResource(R.string.disclosure_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { show = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
    return remember { DisclosureGate { show = true } }
}
