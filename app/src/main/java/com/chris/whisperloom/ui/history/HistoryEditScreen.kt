package com.chris.whisperloom.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R
import com.chris.whisperloom.history.History
import com.chris.whisperloom.history.HistoryEntry
import com.chris.whisperloom.history.Processing
import com.chris.whisperloom.ui.components.ActionBarButton
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.TranscriptActionBar
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bearbeiten-Fenster (Plan §6.4) als Vollbild-Dialog: ✕, "Bearbeiten", "Speichern". Speichern
 * ersetzt die Fassung [processing] und markiert sie "bearbeitet"; [Processing.EDITED] ist der
 * bearbeitete Ursprung (E8) — gibt es ihn schon, geht es dort weiter, sonst beginnt er beim
 * Ursprung. Schliessen mit Aenderungen fragt nach. Rueckgaengig/Wiederholen: [TextFieldState.undoState].
 */
@Composable
fun HistoryEditScreen(id: String, processing: Processing, nav: NavState) {
    val ctx = LocalContext.current
    var gone by remember(id) { mutableStateOf(false) }
    // Bewusst nicht an History.changes gebunden: der Ausgangstext aendert sich nicht unter den Fingern.
    val entry by produceState<HistoryEntry?>(null, id) {
        val loaded = withContext(Dispatchers.IO) { History.get(ctx, id) }
        if (loaded == null) gone = true else value = loaded
    }
    LaunchedEffect(gone) { if (gone && nav.current is Screen.HistoryEdit) nav.pop() }
    val e = entry ?: return
    Editor(e, processing, nav)
}

// foundation 1.12 (lokales AAR geprueft): TextFieldState.undoState mit undo/redo und canUndo/canRedo ist noch experimentell.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun Editor(entry: HistoryEntry, processing: Processing, nav: NavState) {
    val ctx = LocalContext.current
    val snack = rememberSnack()
    val scope = rememberCoroutineScope()
    val original = entry.versions[processing]?.text ?: entry.raw
    val state = rememberTextFieldState(original)
    val text = state.text.toString()
    val dirty = text != original
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    // Ein zweiter Tipp auf "Speichern" darf nicht ein zweites Mal zurueckspringen.
    var saving by remember { mutableStateOf(false) }
    val close = { if (dirty) confirmDiscard = true else nav.pop() }
    BackHandler(enabled = dirty) { confirmDiscard = true }

    val save: () -> Unit = {
        saving = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) { History.edit(ctx, entry.id, processing, text) }
            nav.pop()
            // Zurueck im Eintrag: die gespeicherte Fassung ist sichtbar (beim Ursprung "Bearbeitet").
            if (saved != null && (nav.current as? Screen.HistoryDetail)?.id == entry.id) {
                nav.replaceTop(Screen.HistoryDetail(entry.id, processing.key))
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_edit_title)) },
                navigationIcon = { IconButton(onClick = close) { LoomIcon(R.drawable.ic_close, stringResource(R.string.cd_close)) } },
                actions = {
                    TextButton(onClick = save, enabled = dirty && text.isNotBlank() && !saving) { Text(stringResource(R.string.history_save)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snack.host) },
        bottomBar = {
            // Ueber der Tastatur: imePadding hier, die Leiste selbst traegt nur den Nav-Inset.
            TranscriptActionBar(Modifier.imePadding()) {
                ActionBarButton(R.drawable.ic_content_copy, stringResource(R.string.share_copy), { copyText(ctx, text, snack) }, enabled = text.isNotBlank())
                ActionBarButton(R.drawable.ic_share, stringResource(R.string.share_forward), { shareText(ctx, text) }, primary = true, enabled = text.isNotBlank())
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val of = if (processing == Processing.EDITED && processing !in entry.versions) stringResource(R.string.history_origin)
                else processingLabel(processing)
                Text(
                    stringResource(R.string.history_edit_of, of),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { state.undoState.undo() }, enabled = state.undoState.canUndo) {
                    LoomIcon(R.drawable.ic_undo, stringResource(R.string.history_cd_undo))
                }
                IconButton(onClick = { state.undoState.redo() }, enabled = state.undoState.canRedo) {
                    LoomIcon(R.drawable.ic_redo, stringResource(R.string.history_cd_redo))
                }
            }
            TextField(
                state = state,
                modifier = Modifier.fillMaxWidth().weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge,
                lineLimits = TextFieldLineLimits.MultiLine(),
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.history_discard_title),
            body = stringResource(R.string.history_discard_body),
            confirm = stringResource(R.string.history_discard),
            dismiss = stringResource(R.string.history_keep_editing),
            onConfirm = {
                confirmDiscard = false
                nav.pop()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}
