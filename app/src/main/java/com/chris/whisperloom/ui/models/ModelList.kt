package com.chris.whisperloom.ui.models

import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chris.whisperloom.R
import com.chris.whisperloom.llm.LocalTextEngine
import com.chris.whisperloom.ui.components.CardShape
import com.chris.whisperloom.ui.components.SnackController
import com.chris.whisperloom.ui.components.StatusChip
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.fileSize
import com.chris.whisperloom.ui.components.offlineModelDetails
import com.chris.whisperloom.ui.components.offlineModelLabel
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.theme.loom
import com.chris.whisperloom.whisper.DownloadState
import com.chris.whisperloom.whisper.ModelCatalog
import com.chris.whisperloom.whisper.ModelDownloadService
import com.chris.whisperloom.whisper.ModelDownloads
import com.chris.whisperloom.whisper.ModelStore
import com.chris.whisperloom.whisper.OfflineModel
import com.chris.whisperloom.whisper.OfflineSupport
import com.chris.whisperloom.whisper.TextModelCatalog
import com.chris.whisperloom.whisper.WhisperEngine

/**
 * Modell-Liste mit Download/Abbruch/Loeschen/Auswahl (E4 und Schritt 2b, Spec §2.7) inkl.
 * Dialoge D1 (Loeschen) und D2 (mobile Daten) — die whisper-Modelle oder, mit [text], die
 * Textmodelle (E4 Abschnitt Textverbesserung). Zustand je Modell: ModelStore (installiert)
 * vor ModelDownloads (laedt/fehlgeschlagen), siehe wp3-notes §4.
 */
@Composable
fun ModelListSection(snack: SnackController, showEmptyState: Boolean = true, text: Boolean = false) {
    val ctx = LocalContext.current
    val env = LocalAppEnv.current
    val prefs = env.prefs
    val store = remember { ModelStore(ctx) }
    val models = if (text) TextModelCatalog.models else ModelCatalog.models
    val selectedId = if (text) prefs.localLlmModel else prefs.offlineModel
    val states by ModelDownloads.states.collectAsStateWithLifecycle()
    var storeVersion by remember { mutableIntStateOf(0) }
    val installed = remember(states, storeVersion) {
        models.filter { store.isInstalled(it) }.map { it.id }.toSet()
    }
    // Home/Router/Hub lesen die installierten Modelle aus dem Systemstatus — nach Download/Loeschen nachziehen.
    LaunchedEffect(installed) {
        val known = if (text) env.status.installedTextModels else env.status.installedModels
        if (installed != known) env.refreshStatus()
    }
    val download = rememberModelDownload()
    var pendingDelete by remember { mutableStateOf<OfflineModel?>(null) }
    val anyRunning = states.values.any { it is DownloadState.Running }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (showEmptyState && installed.isEmpty() && !anyRunning) EmptyModelsState()
        models.forEach { model ->
            ModelRow(
                model = model,
                installed = model.id in installed,
                selected = selectedId == model.id,
                state = states[model.id] ?: DownloadState.Idle,
                fits = OfflineSupport.fitsDevice(env.status.totalRamBytes, model),
                busy = anyRunning,
                onSelect = { if (text) prefs.localLlmModel = model.id else prefs.offlineModel = model.id },
                onLoad = { download.start(model) },
                onCancel = { ModelDownloadService.cancel(ctx) },
                onDelete = { pendingDelete = model },
                onRetry = { download.start(model) },
            )
        }
    }

    pendingDelete?.let { model ->
        DeleteDialog(
            model = model,
            text = text,
            // Der Zwischenspeicher (XNNPACK-Cache, ~0,8 GB bei E2B) wird mitgeloescht — ehrlich mitzaehlen.
            cacheBytes = if (text) store.cacheBytes(model) else 0L,
            activeAndOnly = selectedId == model.id && installed.size == 1,
            onConfirm = {
                store.delete(model)
                ModelDownloads.clear(model.id)
                // geladenes Modell aus dem RAM (nie mitten in einer Rechnung)
                if (text) LocalTextEngine.release() else WhisperEngine.release()
                storeVersion++
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** Startet einen Modell-Download ([start]); ueber mobile Daten erst nach Dialog D2. */
class ModelDownload(val start: (OfflineModel) -> Unit)

/**
 * Download-Start fuer Modell-Liste, Pflichtkarte, Text-Karte und Assistent. Den Dialog D2 zeichnet
 * dieses Composable selbst (Muster rememberDisclosureGate). [onStarted] laeuft erst, wenn der
 * Download wirklich startet — nicht, wenn der Nutzer D2 abbricht.
 */
@Composable
fun rememberModelDownload(onStarted: (OfflineModel) -> Unit = {}): ModelDownload {
    val ctx = LocalContext.current
    val started by rememberUpdatedState(onStarted)
    var pending by remember { mutableStateOf<OfflineModel?>(null) }
    fun begin(model: OfflineModel) {
        ModelDownloadService.start(ctx, model.id)
        started(model)
    }
    pending?.let { model ->
        MeteredDialog(
            model = model,
            onConfirm = {
                begin(model)
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
    return remember { ModelDownload { model -> if (OfflineSupport.isMeteredNetwork(ctx)) pending = model else begin(model) } }
}

/** Eine Modell-Zeile: Radio (nur installiert), Label + Details, Trailing je Zustand, Fortschritt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelRow(
    model: OfflineModel,
    installed: Boolean,
    selected: Boolean,
    state: DownloadState,
    fits: Boolean,
    busy: Boolean,
    onSelect: () -> Unit,
    onLoad: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    val label = offlineModelLabel(model.id)
    val running = if (!installed) state as? DownloadState.Running else null
    val failed = if (!installed) state as? DownloadState.Failed else null
    val border = if (failed != null) Modifier.border(1.dp, MaterialTheme.colorScheme.error, CardShape) else Modifier

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (fits) 1f else DIMMED_ALPHA)
            .then(border),
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Radio + Titel + Details als EIN auswaehlbares Element (Spec §5.6): TalkBack liest
                // "Small, Optionsfeld, ausgewaehlt" statt viermal nur "Optionsfeld". Die Trailing-Knoepfe
                // (Laden/Abbrechen/Loeschen) bleiben eigene, beschriftete Knoten ausserhalb.
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(selected = selected, enabled = installed, role = Role.RadioButton, onClick = onSelect)
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(selected = selected, onClick = null, enabled = installed)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // FlowRow: neben "Gemma 4 E2B" und dem Laden-Knopf passt "Empfohlen" nicht immer —
                        // dann in die naechste Zeile statt zusammengedrueckt.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterVertically))
                            if (model.recommended) {
                                StatusChip(
                                    stringResource(R.string.models_recommended), R.drawable.ic_check,
                                    MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Text(
                            offlineModelDetails(model.id),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!fits) {
                            StatusChip(
                                stringResource(R.string.models_too_big), R.drawable.ic_warning,
                                MaterialTheme.loom.warningContainer, MaterialTheme.loom.onWarningContainer,
                            )
                        }
                    }
                }
                when {
                    installed -> Row(verticalAlignment = Alignment.CenterVertically) {
                        LoomIcon(R.drawable.ic_check_circle, stringResource(R.string.models_installed), Modifier.size(24.dp), MaterialTheme.loom.success)
                        IconButton(onClick = onDelete) {
                            LoomIcon(R.drawable.ic_delete, stringResource(R.string.models_delete_cd, label))
                        }
                    }
                    running != null -> IconButton(onClick = onCancel) {
                        LoomIcon(R.drawable.ic_close, stringResource(R.string.models_cancel_cd))
                    }
                    // Wie "Laden": waehrend ein anderer Download laeuft, wuerde der Dienst den Start still ignorieren.
                    failed != null -> FilledTonalButton(onClick = onRetry, enabled = !busy) { Text(stringResource(R.string.common_retry)) }
                    else -> {
                        val cd = stringResource(R.string.models_download_cd, label)
                        FilledTonalButton(
                            onClick = onLoad,
                            enabled = fits && !busy,
                            modifier = Modifier.semantics { contentDescription = cd },
                        ) {
                            LoomIcon(R.drawable.ic_download, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.models_load, fileSize(model.bytes)))
                        }
                    }
                }
            }
            if (running != null) DownloadProgress(running)
            if (failed != null) {
                Text(
                    stringResource(R.string.models_failed, failed.message),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
        }
    }
}

/** Fortschrittsbalken und -zeile eines laufenden Downloads (auch Pflichtkarte, Text-Karte, Assistent). */
@Composable
fun DownloadProgress(running: DownloadState.Running) {
    val known = running.total > 0
    if (known) {
        LinearProgressIndicator(
            progress = { running.bytes.toFloat() / running.total },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    } else {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
    val text = if (known) {
        stringResource(
            R.string.models_progress,
            running.percent, fileSize(running.bytes), fileSize(running.total), fileSize(running.bytesPerSec) + "/s",
        )
    } else {
        stringResource(R.string.models_progress_unknown, fileSize(running.bytes))
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Leerzustand ueber der Liste (nichts installiert, kein Download aktiv). */
@Composable
private fun EmptyModelsState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LoomIcon(R.drawable.ic_download_for_offline, null, Modifier.size(64.dp), MaterialTheme.colorScheme.outline)
        Text(stringResource(R.string.models_empty_title), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.models_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** D2: vor einem Download ueber gebuehrenpflichtiges Netz. */
@Composable
private fun MeteredDialog(model: OfflineModel, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(stringResource(R.string.models_metered_title)) },
        text = { Text(stringResource(R.string.models_metered_body, fileSize(model.bytes))) },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.models_metered_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** D1: Modell loeschen; beim aktiven und einzigen Modell mit Zusatzhinweis. [text] = Textmodell. */
@Composable
private fun DeleteDialog(
    model: OfflineModel,
    text: Boolean,
    cacheBytes: Long,
    activeAndOnly: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val label = offlineModelLabel(model.id)
    val freed = when {
        cacheBytes > 0 -> stringResource(R.string.models_llm_delete_body_cache, fileSize(model.bytes), fileSize(cacheBytes))
        text -> stringResource(R.string.models_llm_delete_body, fileSize(model.bytes))
        else -> stringResource(R.string.models_delete_body, fileSize(model.bytes))
    }
    val body = freed +
        if (activeAndOnly) "\n\n" + stringResource(if (text) R.string.models_llm_delete_active else R.string.models_delete_active) else ""
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(stringResource(R.string.models_delete_title, label)) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.common_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** Gedimmte Karte fuer "zu gross" (M3-Disabled-Alpha). */
private const val DIMMED_ALPHA = 0.38f
