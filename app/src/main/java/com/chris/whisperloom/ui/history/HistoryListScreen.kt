package com.chris.whisperloom.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chris.whisperloom.Formats
import com.chris.whisperloom.R
import com.chris.whisperloom.history.History
import com.chris.whisperloom.history.HistoryEntry
import com.chris.whisperloom.history.Processing
import com.chris.whisperloom.ui.components.DetailScaffold
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.PrimaryButton
import com.chris.whisperloom.ui.components.SectionHeader
import com.chris.whisperloom.ui.components.levelLabel
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.share.StatusCard
import com.chris.whisperloom.ui.state.LocalAppEnv
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Verlauf (3.9.0, Plan §6.2): Diktate nach Tagen, je Zeile der Ursprung, darunter Uhrzeit · Quelle ·
 * Dauer und rechts die damalige Stufe. Tippen oeffnet den Eintrag; Wischen (oder die TalkBack-Aktion
 * "Loeschen") loescht mit "Rueckgaengig". ⋮: Verlauf-Einstellungen, Alle loeschen.
 */
@Composable
fun HistoryListScreen(nav: NavState) {
    val ctx = LocalContext.current
    val prefs = LocalAppEnv.current.prefs
    val snack = rememberSnack()
    val scope = rememberCoroutineScope()
    val changes by History.changes.collectAsStateWithLifecycle()
    val entries by produceState<List<HistoryEntry>?>(null, changes) {
        value = withContext(Dispatchers.IO) { History.list(ctx) }
    }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val deleted = stringResource(R.string.history_deleted)
    val undo = stringResource(R.string.history_undo)
    val offerUndo: (HistoryEntry) -> Unit = { gone ->
        snack.show(deleted, undo) { scope.launch(Dispatchers.IO) { History.restore(ctx, gone) } }
    }
    val delete: (HistoryEntry) -> Unit = { entry ->
        scope.launch {
            withContext(Dispatchers.IO) { History.delete(ctx, entry.id) }?.let(offerUndo)
        }
    }
    // Im Eintrag ueber ⋮ geloescht: das "Rueckgaengig" gehoert hierher.
    LaunchedEffect(Unit) { HistoryUndo.take()?.let(offerUndo) }

    DetailScaffold(
        title = stringResource(R.string.history_title),
        onBack = { nav.pop() },
        snack = snack,
        actions = { ListMenu(hasEntries = !entries.isNullOrEmpty(), onSettings = { nav.push(Screen.HistorySettings) }) { confirmClear = true } },
    ) { padding ->
        val list = entries
        when {
            !prefs.historyEnabled -> StatusCard(
                icon = painterResource(R.drawable.ic_history),
                iconTint = MaterialTheme.colorScheme.outline,
                title = stringResource(R.string.history_off),
                body = stringResource(R.string.history_off_body),
                modifier = Modifier.padding(padding),
            ) {
                PrimaryButton(stringResource(R.string.history_turn_on), onClick = { scope.launch(Dispatchers.IO) { History.setEnabled(ctx, true) } })
            }
            list == null -> Unit
            list.isEmpty() -> StatusCard(
                icon = painterResource(R.drawable.ic_history),
                iconTint = MaterialTheme.colorScheme.outline,
                title = stringResource(R.string.history_empty),
                body = stringResource(R.string.history_empty_body),
                modifier = Modifier.padding(padding),
            ) {}
            else -> EntryList(list, padding, onOpen = { nav.push(Screen.HistoryDetail(it.id)) }, onDelete = delete)
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.history_clear_title),
            body = stringResource(R.string.history_clear_body),
            confirm = stringResource(R.string.history_clear),
            onConfirm = {
                confirmClear = false
                scope.launch(Dispatchers.IO) { History.clear(ctx) }
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun ListMenu(hasEntries: Boolean, onSettings: () -> Unit, onClear: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { LoomIcon(R.drawable.ic_more_vert, stringResource(R.string.cd_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_settings_title)) },
                onClick = {
                    open = false
                    onSettings()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_clear)) },
                enabled = hasEntries,
                onClick = {
                    open = false
                    onClear()
                },
            )
        }
    }
}

@Composable
private fun EntryList(entries: List<HistoryEntry>, padding: PaddingValues, onOpen: (HistoryEntry) -> Unit, onDelete: (HistoryEntry) -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
        HistoryText.groups(entries, zone).forEach { group ->
            item(key = "day-${group.day}") { SectionHeader(dayLabel(group.day, today)) }
            items(group.entries, key = { it.id }) { entry ->
                EntryRow(entry, zone, { onOpen(entry) }, { onDelete(entry) }, Modifier.animateItem())
            }
        }
    }
}

/**
 * Eine Zeile: Quelle als Symbol, Ursprung in hoechstens drei Zeilen, darunter Uhrzeit · Quelle ·
 * Dauer und die Stufe als Etikett (passt beides nicht nebeneinander, bricht das Etikett um).
 * Wischen nach links loescht; fuer TalkBack dieselbe Aktion als "Loeschen" (M3: nie nur Wischen).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryRow(entry: HistoryEntry, zone: ZoneId, onOpen: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val state = rememberSwipeToDismissBoxState()
    val deleteLabel = stringResource(R.string.common_delete)
    val meta = stringResource(
        R.string.history_meta,
        HistoryText.time(entry.createdAt, zone),
        sourceLabel(entry.source),
        Formats.duration(entry.durationMs),
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = { DeleteBackground() },
        enableDismissFromStartToEnd = false,
        onDismiss = { onDelete() },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .clickable(onClickLabel = stringResource(R.string.common_open), onClick = onOpen)
                .semantics { customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete(); true }) }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LoomIcon(sourceIcon(entry.source), null, Modifier.padding(top = 2.dp).size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.raw.trim(), style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                // Passt beides in eine Zeile, steht das Etikett rechts; sonst links in der naechsten
                // Zeile. Den Mindestabstand traegt die Uhrzeit-Zeile, damit das Etikett umgebrochen buendig steht.
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    StageTag(entry)
                }
            }
        }
    }
}

/** Etikett der damaligen Stufe; ohne KI entstanden "ohne KI" in Fehlerfarbe; bei "Aus" keins. */
@Composable
private fun StageTag(entry: HistoryEntry) {
    val processing = entry.processing
    val stage = processing.stage
    if (stage == null || processing == Processing.OFF) return
    val failed = HistoryText.failed(processing, entry.versions[processing])
    val cs = MaterialTheme.colorScheme
    Text(
        if (failed) stringResource(R.string.history_failed) else levelLabel(stage),
        style = MaterialTheme.typography.labelMedium,
        color = if (failed) cs.onErrorContainer else cs.onSecondaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(if (failed) cs.errorContainer else cs.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Hinter der wischenden Zeile: errorContainer mit Papierkorb am rechten Rand. */
@Composable
private fun DeleteBackground() {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        LoomIcon(R.drawable.ic_delete, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onErrorContainer)
    }
}
