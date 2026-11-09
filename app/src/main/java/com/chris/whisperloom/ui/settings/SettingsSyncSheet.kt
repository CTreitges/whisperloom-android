package com.chris.whisperloom.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R
import com.chris.whisperloom.agent.VoiceTaskWidget
import com.chris.whisperloom.sync.ImportPreview
import com.chris.whisperloom.sync.SettingsSync
import com.chris.whisperloom.ui.components.SnackController
import com.chris.whisperloom.ui.components.LoomSheet
import com.chris.whisperloom.ui.state.LocalAppEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Einstellungen uebertragen (WLD-P5c): Export und Import im Format v1 von WhisperLoom Desktop ueber das
 * Storage Access Framework. Der Import zeigt erst eine Vorschau ([ImportDialog]); uebernommen wird nur nach OK
 * und nur eine fehlerfreie Datei. API-Keys gehen nie in den Export; beim Import bleiben sie, ausser die Datei
 * schickte sie an ein neues Ziel (dann werden sie geloescht, siehe Hinweis in der Vorschau).
 */
@Composable
fun SettingsSyncSheet(snack: SnackController, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val res = LocalResources.current
    val prefs = LocalAppEnv.current.prefs.prefs
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<ImportPreview?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_JSON)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { writeText(ctx, uri, SettingsSync.export(prefs)) }
            snack.show(res.getString(if (ok) R.string.sync_exported else R.string.sync_export_failed))
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { readText(ctx, uri) }
            if (text == null) snack.show(res.getString(R.string.sync_read_failed))
            else preview = SettingsSync.preview(prefs, text)
        }
    }

    LoomSheet(title = stringResource(R.string.settings_group_sync), onDismiss = onDismiss) { dismiss ->
        Text(
            stringResource(R.string.sync_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { exporter.launch(FILE_NAME) }) { Text(stringResource(R.string.sync_export)) }
            FilledTonalButton(onClick = { importer.launch(IMPORT_TYPES) }) { Text(stringResource(R.string.sync_import)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = dismiss) { Text(stringResource(R.string.common_close)) }
        }
    }

    preview?.let { p ->
        ImportDialog(
            preview = p,
            onApply = {
                SettingsSync.apply(ctx, prefs, p)
                // Pro Widgets koennen mit dem Import an- oder ausgegangen sein.
                VoiceTaskWidget.refresh(ctx)
                preview = null
                snack.show(res.getString(R.string.sync_imported))
            },
            onDismiss = { preview = null },
        )
    }
}

/**
 * Vorschau eines Imports: Fehler (dann nur Schliessen), sonst die geaenderten Einstellungen und die Hinweise —
 * u. a., dass `secrets` und `policy` nur fuer WhisperLoom Desktop gelten.
 */
@Composable
fun ImportDialog(preview: ImportPreview, onApply: () -> Unit, onDismiss: () -> Unit) {
    val lines = buildList {
        if (!preview.canApply) {
            addAll(preview.errors)
        } else {
            if (preview.changes.isEmpty()) add(stringResource(R.string.sync_no_changes))
            else add(pluralStringResource(R.plurals.sync_changes, preview.changes.size, preview.changes.size))
            preview.changes.take(MAX_LINES).forEach { add("• $it") }
            val more = preview.changes.size - MAX_LINES
            if (more > 0) add(pluralStringResource(R.plurals.sync_more, more, more))
            addAll(preview.warnings)
            if (preview.droppedKeys.isEmpty()) add(stringResource(R.string.sync_keys_stay))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(stringResource(if (preview.canApply) R.string.sync_confirm_title else R.string.sync_invalid_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            if (preview.canApply) Button(onClick = onApply) { Text(stringResource(R.string.common_apply)) }
            else TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
        dismissButton = if (preview.canApply) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
        } else {
            null
        },
    )
}

/** Liest hoechstens [SettingsSync.MAX_FILE_BYTES]; null = nicht lesbar oder zu gross. */
private fun readText(ctx: Context, uri: Uri): String? = try {
    ctx.contentResolver.openInputStream(uri)?.use { input ->
        // readNBytes erst ab API 33 (minSdk 26): selbst begrenzt lesen.
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (out.size() <= SettingsSync.MAX_FILE_BYTES) {
            val n = input.read(chunk)
            if (n < 0) break
            out.write(chunk, 0, n)
        }
        if (out.size() > SettingsSync.MAX_FILE_BYTES) null else out.toString(Charsets.UTF_8.name())
    }
} catch (_: Exception) {
    // IOException, SecurityException, aber auch IllegalArgumentException/UnsupportedOperationException
    // mancher DocumentsProvider: Meldung statt Absturz.
    null
}

private fun writeText(ctx: Context, uri: Uri, text: String): Boolean = try {
    // "wt": eine vorhandene Datei wird ersetzt, nicht nur ueberschrieben (Rest der alten bliebe sonst stehen).
    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
} catch (_: Exception) {
    // wie readText: auch "wt" nicht unterstuetzt (IllegalArgumentException) wird zur Meldung.
    false
}

private const val MIME_JSON = "application/json"
private const val FILE_NAME = "whisperloom-einstellungen.json"

/** Manche Dateimanager melden .json als Text oder octet-stream. */
private val IMPORT_TYPES = arrayOf(MIME_JSON, "text/*", "application/octet-stream")

/** So viele geaenderte Pfade zeigt die Vorschau, der Rest als "… und N weitere". */
private const val MAX_LINES = 12
