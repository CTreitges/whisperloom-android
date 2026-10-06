package com.chris.whisperloom.ui.models

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineDecision
import com.chris.whisperloom.SetupState
import com.chris.whisperloom.ui.components.CardShape
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.fileSize
import com.chris.whisperloom.ui.components.offlineModelLabel
import com.chris.whisperloom.ui.components.textModelSize
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.loom
import com.chris.whisperloom.whisper.DownloadState
import com.chris.whisperloom.whisper.ModelDownloads
import com.chris.whisperloom.whisper.OfflineModel
import com.chris.whisperloom.whisper.OfflineSupport
import com.chris.whisperloom.whisper.TextModelCatalog

/** Die Pflichtkarte — fuer Tests, die zaehlen, dass sie genau einmal da ist. */
const val LOCAL_MODEL_REQUIRED_TAG = "local-model-required"

/**
 * Die Regel, die wirkt ([OfflineRefineRule.effective]): passt kein Textmodell ins Geraet
 * ([SystemStatus.textModelFits]), "Ueberspringen" — fuer alles, was die Regel anzeigt.
 */
fun offlineRule(prefs: PrefsState, status: SystemStatus): OfflineRefineRule = prefs.offlineRefine.effective(status.textModelFits)

/**
 * "Offline ohne Textmodell" fuer die UI: [RefineDecision.localModelMissing] mit dem Systemstatus
 * ([SystemStatus.textModelReady]) statt Dateizugriff — dieselbe Wahrheit fuer Pflichtkarte, Home und Assistent.
 */
fun localModelMissing(prefs: PrefsState, status: SystemStatus): Boolean = RefineDecision.localModelMissing(
    engine = prefs.engine,
    dictationMode = prefs.refineMode,
    shareMode = prefs.shareRefineMode,
    rule = offlineRule(prefs, status),
    localReady = status.textModelReady(prefs.localLlmModel),
)

/** Das Textmodell, das "Textmodell laden" holt: das gewaehlte; passt es nicht in den RAM, das empfohlene. */
fun textModelToLoad(selectedId: String, totalRamBytes: Long): OfflineModel {
    val selected = TextModelCatalog.byId(selectedId)
    return if (OfflineSupport.fitsDevice(totalRamBytes, selected)) selected else TextModelCatalog.DEFAULT
}

/**
 * "Textmodell laden" (Pflichtkarte, Text-Karte, Assistent): Download wie in der Modell-Liste, und
 * sobald er startet, ist das Modell gewaehlt — steht die Regel auf "Ueberspringen", gilt danach
 * "Lokales Textmodell" (der Klick IST diese Wahl).
 */
@Composable
fun rememberTextModelLoad(): ModelDownload {
    val prefs = LocalAppEnv.current.prefs
    return rememberModelDownload { model ->
        prefs.localLlmModel = model.id
        if (prefs.offlineRefine == OfflineRefineRule.SKIP) prefs.offlineRefine = OfflineRefineRule.LOCAL
    }
}

/**
 * Pflichtkarte "Offline ohne Textmodell" (Spec §4) — ueberall dieselbe: Erkennung bei Offline,
 * Offline-Modelle (Textverbesserung), Text (Karte Offline-Erkennung), Assistent 2b. Der Aufrufer
 * zeigt sie genau dann, wenn [localModelMissing] gilt. Warnfarbe wie das Home-Banner; waehrend
 * der Download laeuft, Fortschritt statt Knoepfe.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalModelRequiredCard(modifier: Modifier = Modifier) {
    val env = LocalAppEnv.current
    val prefs = env.prefs
    val loom = MaterialTheme.loom
    val model = textModelToLoad(prefs.localLlmModel, env.status.totalRamBytes)
    val states by ModelDownloads.states.collectAsStateWithLifecycle()
    val state = states[model.id] ?: DownloadState.Idle
    // Der Dienst laedt immer nur eins: laeuft ein anderer Download, wuerde der Start still ignoriert.
    val busy = states.values.any { it is DownloadState.Running }
    val load = rememberTextModelLoad()
    // Mit eigenem Online-Zugang ist "ohne KI" nur die halbe Wahrheit: mit Netz wird online verbessert.
    val body = when {
        !SetupState.llmReady(prefs.llmAccess()) -> R.string.local_missing_body
        offlineRule(prefs, env.status) == OfflineRefineRule.ONLINE_LOCAL -> R.string.local_missing_body_online
        else -> R.string.local_missing_body_own
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(loom.warningContainer, CardShape)
            .padding(16.dp)
            .testTag(LOCAL_MODEL_REQUIRED_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LoomIcon(R.drawable.ic_warning, null, Modifier.size(24.dp), loom.onWarningContainer)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.local_missing_title), style = MaterialTheme.typography.titleSmall, color = loom.onWarningContainer)
                Text(
                    stringResource(body, offlineModelLabel(model.id), textModelSize(model)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = loom.onWarningContainer,
                )
            }
        }
        if (state is DownloadState.Running) {
            DownloadProgress(state)
        } else {
            if (state is DownloadState.Failed) {
                Text(
                    stringResource(R.string.models_failed, state.message),
                    style = MaterialTheme.typography.labelSmall,
                    color = loom.onWarningContainer,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
            // FlowRow: bei 360 dp oder grosser Schrift passen beide Knoepfe nicht nebeneinander.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(
                    onClick = { prefs.offlineRefine = OfflineRefineRule.SKIP },
                    colors = ButtonDefaults.textButtonColors(contentColor = loom.onWarningContainer),
                ) { Text(stringResource(R.string.local_missing_skip)) }
                FilledTonalButton(
                    onClick = { load.start(model) },
                    enabled = !busy && OfflineSupport.fitsDevice(env.status.totalRamBytes, model),
                ) { Text(stringResource(R.string.local_missing_load, fileSize(model.bytes))) }
            }
        }
    }
}
