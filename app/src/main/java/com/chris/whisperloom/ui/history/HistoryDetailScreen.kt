package com.chris.whisperloom.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chris.whisperloom.Formats
import com.chris.whisperloom.PolishPlan
import com.chris.whisperloom.R
import com.chris.whisperloom.TextPolisher
import com.chris.whisperloom.history.History
import com.chris.whisperloom.history.HistoryEntry
import com.chris.whisperloom.history.Processing
import com.chris.whisperloom.ui.components.ActionBarButton
import com.chris.whisperloom.ui.components.FillerToggleBar
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.TranscriptActionBar
import com.chris.whisperloom.ui.components.TranscriptHeadCard
import com.chris.whisperloom.ui.components.TranscriptNote
import com.chris.whisperloom.ui.components.TranscriptText
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.components.transcriptParagraphs
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.share.SkeletonLines
import com.chris.whisperloom.ui.state.LocalAppEnv
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ein Eintrag des Verlaufs (Plan §6.3) im Aufbau des Sprachnachrichten-Fensters: Kopfkarte, Chips
 * fuer Ursprung und Fassungen, markierbarer Text, Hinweiszeile, unten Kopieren und "Andere Stufe …".
 * Welche Fassung sichtbar ist, steht im Screen ([Screen.HistoryDetail.show]) — so uebersteht sie
 * Rotation, Prozesstod und den Weg ins Bearbeiten-Fenster. Ist der Eintrag weg, geht es zur Liste.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(screen: Screen.HistoryDetail, nav: NavState) {
    val ctx = LocalContext.current
    val id = screen.id
    val snack = rememberSnack()
    val scope = rememberCoroutineScope()
    val changes by History.changes.collectAsStateWithLifecycle()
    var gone by remember(id) { mutableStateOf(false) }
    val entry by produceState<HistoryEntry?>(null, id, changes) {
        val loaded = withContext(Dispatchers.IO) { History.get(ctx, id) }
        if (loaded == null) gone = true else value = loaded
    }
    // Nur solange dieser Eintrag oben liegt: beim Ausblenden nach "Loeschen" ist er schon weg.
    LaunchedEffect(gone) { if (gone && (nav.current as? Screen.HistoryDetail)?.id == id) nav.backToHistory() }

    val running by HistoryJobs.running.collectAsStateWithLifecycle()
    val finished by HistoryJobs.finished.collectAsStateWithLifecycle()
    val res = LocalResources.current
    LaunchedEffect(finished) {
        finished.filter { it.job.id == id }.forEach { done ->
            HistoryJobs.consume(done)
            val message = when {
                done.saved -> done.note
                done.gone -> null
                done.kept -> res.getString(R.string.history_reprocess_kept)
                done.reason != null -> res.getString(R.string.history_reprocess_failed, done.reason)
                else -> res.getString(R.string.history_reprocess_failed_plain)
            }
            if (message != null) snack.show(message)
        }
    }

    var showSheet by rememberSaveable { mutableStateOf(false) }
    var replaceKey by rememberSaveable { mutableStateOf<String?>(null) }
    var hideFillers by rememberSaveable(id) { mutableStateOf(false) }

    val e = entry
    val loading = running.filter { it.id == id }.map { it.processing }
    // Sichtbar: die gewaehlte Fassung, solange es sie gibt oder sie gerechnet wird; sonst die damalige.
    val chosen: Processing? = when (screen.show) {
        null -> e?.let { HistoryText.initial(it) }
        Screen.HistoryDetail.ORIGIN -> null
        else -> Processing.fromKey(screen.show)
    }
    val shown: Processing? = if (e == null || chosen == null || chosen in e.versions || chosen in loading) chosen else HistoryText.initial(e)
    val text: String? = when {
        e == null -> null
        shown == null -> if (hideFillers) withoutFillers(e) else e.raw
        else -> e.versions[shown]?.text
    }
    val show: (Processing?) -> Unit = { p -> nav.replaceTop(Screen.HistoryDetail(id, p?.key ?: Screen.HistoryDetail.ORIGIN)) }
    val start: (Processing) -> Unit = { p ->
        HistoryJobs.start(ctx, id, p)
        show(p)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_entry_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.pop() }) { LoomIcon(R.drawable.ic_arrow_back, stringResource(R.string.cd_back)) }
                },
                actions = {
                    // Nicht waehrend diese Fassung neu gerechnet wird: das Ergebnis ersetzte die Bearbeitung.
                    IconButton(onClick = { nav.push(Screen.HistoryEdit(id, shown ?: Processing.EDITED)) }, enabled = text != null && shown !in loading) {
                        LoomIcon(R.drawable.ic_edit, stringResource(R.string.history_cd_edit))
                    }
                    EntryMenu(
                        enabled = e != null,
                        onShare = { text?.let { shareText(ctx, it) } },
                        onDelete = {
                            scope.launch {
                                val deleted = withContext(Dispatchers.IO) { History.delete(ctx, id) }
                                if (deleted != null) HistoryUndo.offer(deleted)
                                nav.backToHistory()
                            }
                        },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snack.host) },
        bottomBar = {
            TranscriptActionBar {
                ActionBarButton(R.drawable.ic_content_copy, stringResource(R.string.share_copy), { text?.let { copyText(ctx, it, snack) } }, enabled = text != null)
                ActionBarButton(R.drawable.ic_refresh, stringResource(R.string.history_other_stage), { showSheet = true }, primary = true, enabled = e != null)
            }
        },
    ) { padding ->
        if (e == null) return@Scaffold
        val nothing = stringResource(R.string.share_nothing_recognised)
        Column(
            Modifier.fillMaxSize().padding(padding).padding(start = 20.dp, end = 20.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TranscriptText(Modifier.weight(1f)) {
                item(key = "head") { DisableSelection { HeadCard(e) } }
                item(key = "chips") { DisableSelection { VersionChips(e, shown, loading, show) } }
                if (text == null) item(key = "loading") { DisableSelection { SkeletonLines() } }
                else transcriptParagraphs("text", HistoryText.paragraphs(text), nothing)
            }
            if (shown != null) VersionNote(e, shown)
            else FillerToggleBar(hideFillers) { hideFillers = it }
        }
    }

    if (showSheet && e != null) {
        StageSheet(e, loading, LocalAppEnv.current.prefs.promptLevelEnabled, onDismiss = { showSheet = false }) { p ->
            showSheet = false
            if (e.versions[p]?.edited == true) replaceKey = p.key else start(p)
        }
    }
    val replace = replaceKey?.let(Processing::fromKey)
    if (replace != null) {
        ConfirmDialog(
            title = stringResource(R.string.history_replace_title),
            body = stringResource(R.string.history_replace_body, processingLabel(replace)),
            confirm = stringResource(R.string.history_replace),
            onConfirm = {
                replaceKey = null
                start(replace)
            },
            onDismiss = { replaceKey = null },
        )
    }
}

/** Ursprung ohne Fuellwoerter — dieselbe Regel wie "Fuellwoerter ausblenden" im Sprachnachrichten-Fenster. */
@Composable
private fun withoutFillers(entry: HistoryEntry): String {
    val prefs = LocalAppEnv.current.prefs
    return remember(entry.raw, entry.language, prefs.customFillers, prefs.disabledFillers) {
        val options = PolishPlan.cleaned(entry.language, prefs.customFillers, prefs.disabledFillers).copy(keepLineBreaks = true)
        TextPolisher.polish(entry.raw, options)
    }
}

/** Quelle als Symbol, "Heute, 14:32", "Tastatur · 0:41 · 86 Woerter" (Woerter des Ursprungs). */
@Composable
private fun HeadCard(entry: HistoryEntry) {
    val zone = remember { ZoneId.systemDefault() }
    val title = stringResource(
        R.string.history_when,
        dayLabel(HistoryText.day(entry.createdAt, zone), LocalDate.now(zone)),
        HistoryText.time(entry.createdAt, zone),
    )
    val words = HistoryText.words(entry.raw)
    val subtitle = stringResource(
        R.string.history_head,
        sourceLabel(entry.source),
        Formats.duration(entry.durationMs),
        pluralStringResource(R.plurals.history_words, words, words),
    )
    TranscriptHeadCard(sourceIcon(entry.source), title, subtitle)
}

/**
 * Ursprung und die Fassungen in der Reihenfolge ihres Entstehens, dahinter, was gerade gerechnet
 * wird (mit Ladeanzeige). Bricht bei 360 dp in weitere Zeilen um.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VersionChips(entry: HistoryEntry, shown: Processing?, loading: List<Processing>, onShow: (Processing?) -> Unit) {
    val busy = stringResource(R.string.history_busy)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VersionChip(stringResource(R.string.history_origin), selected = shown == null, busy = false, busyLabel = busy) { onShow(null) }
        (entry.versions.keys + loading.filter { it !in entry.versions }).forEach { p ->
            VersionChip(processingLabel(p), selected = shown == p, busy = p in loading, busyLabel = busy) { onShow(p) }
        }
    }
}

@Composable
private fun VersionChip(label: String, selected: Boolean, busy: Boolean, busyLabel: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = when {
            busy -> { { CircularProgressIndicator(Modifier.size(FilterChipDefaults.IconSize), strokeWidth = 2.dp) } }
            selected -> { { LoomIcon(R.drawable.ic_check, null, Modifier.size(FilterChipDefaults.IconSize)) } }
            else -> null
        },
        modifier = if (busy) Modifier.semantics { stateDescription = busyLabel } else Modifier,
    )
}

/** ⓘ Verarbeitung · Modell (· bearbeitet) bzw. in Fehlerfarbe, warum ohne KI. */
@Composable
private fun VersionNote(entry: HistoryEntry, processing: Processing) {
    val version = entry.versions[processing] ?: return
    val label = processingLabel(processing)
    val failed = HistoryText.failed(processing, version)
    val base = when {
        processing == Processing.EDITED -> stringResource(R.string.history_note_edited_origin)
        processing == Processing.OFF -> stringResource(R.string.history_note_off)
        failed && version.failed != null -> stringResource(R.string.history_note_failed, label, version.failed)
        failed -> stringResource(R.string.history_note_failed_plain, label)
        else -> stringResource(R.string.history_note, label, version.model.orEmpty())
    }
    val text = if (version.edited && processing != Processing.EDITED) stringResource(R.string.history_note_edited, base) else base
    TranscriptNote(text, error = failed, icon = if (failed) R.drawable.ic_error else R.drawable.ic_info)
}

@Composable
private fun EntryMenu(enabled: Boolean, onShare: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = enabled) { LoomIcon(R.drawable.ic_more_vert, stringResource(R.string.cd_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share_forward)) },
                leadingIcon = { LoomIcon(R.drawable.ic_share, null) },
                onClick = {
                    open = false
                    onShare()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_delete)) },
                leadingIcon = { LoomIcon(R.drawable.ic_delete, null) },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}
