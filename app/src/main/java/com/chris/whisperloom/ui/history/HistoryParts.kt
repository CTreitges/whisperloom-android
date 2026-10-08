package com.chris.whisperloom.ui.history

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.history.HistoryEntry
import com.chris.whisperloom.history.HistorySource
import com.chris.whisperloom.history.Processing
import com.chris.whisperloom.ui.components.SnackController
import com.chris.whisperloom.ui.components.copyToClipboard
import com.chris.whisperloom.ui.components.levelLabel
import com.chris.whisperloom.ui.components.needsOwnCopyConfirmation
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.settings.cleanupLabel
import java.time.LocalDate

// Gemeinsames der Verlauf-Bildschirme: Bezeichnungen, Kopieren/Teilen, Rueckfrage, Rueckweg zur Liste.

/** Name einer Fassung (Chip, Hinweiszeile): Glaetten nennt die Bereinigung, wenn sie vom Standard abweicht. */
@Composable
fun processingLabel(processing: Processing): String = when (processing) {
    Processing.OFF -> stringResource(R.string.history_version_off)
    Processing.EDITED -> stringResource(R.string.history_version_edited)
    Processing.POLISH_CLEAN, Processing.POLISH_READABLE ->
        stringResource(R.string.history_version_polish, levelLabel(RefineMode.POLISH), stringResource(cleanupLabel(processing.cleanup!!)))
    else -> levelLabel(processing.stage!!)
}

@Composable
fun sourceLabel(source: HistorySource): String =
    stringResource(if (source == HistorySource.BUBBLE) R.string.history_source_bubble else R.string.history_source_keyboard)

fun sourceIcon(source: HistorySource): Int = if (source == HistorySource.BUBBLE) R.drawable.ic_mic else R.drawable.ic_keyboard

/** "Heute", "Gestern" oder "Mo., 6. Okt.". */
@Composable
fun dayLabel(day: LocalDate, today: LocalDate): String = when (HistoryText.kind(day, today)) {
    HistoryText.DayKind.TODAY -> stringResource(R.string.history_today)
    HistoryText.DayKind.YESTERDAY -> stringResource(R.string.history_yesterday)
    HistoryText.DayKind.DATE -> HistoryText.date(day, today)
}

/** Text in die Zwischenablage; die Bestaetigung nur bis Android 12 selbst (ab 13 zeigt sie das System, N4). */
fun copyText(ctx: Context, text: String, snack: SnackController) {
    copyToClipboard(ctx, text)
    if (needsOwnCopyConfirmation()) snack.show(ctx.getString(R.string.share_copied))
}

/** Systemdialog "Teilen" mit dem Text. */
fun shareText(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.share_forward)))
    } catch (_: ActivityNotFoundException) {
        // Ohne Ziel-App gibt es nichts zu teilen; der Chooser selbst fehlt praktisch nie.
    }
}

/** Ein im Eintrag geloeschter Eintrag, fuer "Rueckgaengig" in der Liste — einmal abholbar. */
internal object HistoryUndo {
    @Volatile private var pending: HistoryEntry? = null

    fun offer(entry: HistoryEntry) {
        pending = entry
    }

    fun take(): HistoryEntry? = pending.also { pending = null }
}

/** Zurueck zur Liste: liegt sie unter dem obersten Screen, dorthin; sonst ersetzt sie ihn. */
fun NavState.backToHistory() {
    val stack = snapshot()
    if (stack.size >= 2 && stack[stack.lastIndex - 1] == Screen.History) pop() else replaceTop(Screen.History)
}

/** Rueckfrage vor einer Aktion, die etwas loescht oder ersetzt; [destructive] faerbt die Bestaetigung rot. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    dismiss: String = stringResource(R.string.common_cancel),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            if (destructive) {
                TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(confirm)
                }
            } else {
                Button(onClick = onConfirm) { Text(confirm) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
    )
}
