package com.chris.whisperloom.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R
import com.chris.whisperloom.Vocabulary
import com.chris.whisperloom.VocabularySource
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.LoomSheet
import com.chris.whisperloom.ui.components.SnackController
import com.chris.whisperloom.ui.state.LocalAppEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Vokabular bearbeiten: eigene Begriffe als Liste (einzeln loeschbar) und eine dauerhaft
 * verknuepfte .md/.txt-Datei, die bei jedem Diktat neu gelesen wird.
 */
@Composable
fun VocabularySheet(snack: SnackController, onDismiss: () -> Unit) {
    val prefs = LocalAppEnv.current.prefs
    val ctx = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    val entries = Vocabulary.entries(prefs.apiPrompt)

    // Datei-Begriffe: null = nicht lesbar; neu gelesen bei Wechsel der Datei oder nach "Neu laden".
    var reloads by remember { mutableIntStateOf(0) }
    var fileTerms by remember { mutableStateOf<List<String>?>(emptyList()) }
    LaunchedEffect(prefs.vocabFileUri, reloads) {
        fileTerms = withContext(Dispatchers.IO) { VocabularySource.fileTerms(ctx, prefs.vocabFileUri) }
    }
    val prompt = Vocabulary.prompt(entries, fileTerms.orEmpty())

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = VocabularySource.displayName(ctx, uri)
        when {
            !VocabularySource.isTextFile(name) -> snack.show(ctx.getString(R.string.vocab_file_wrong_type))
            !VocabularySource.link(ctx, prefs.prefs, uri, name) -> snack.show(ctx.getString(R.string.vocab_file_no_permission))
            else -> {
                // PrefsState-Spiegel nachziehen: link() schreibt ueber die rohen Prefs.
                prefs.vocabFileUri = prefs.prefs.vocabFileUri
                prefs.vocabFileName = prefs.prefs.vocabFileName
                reloads++
            }
        }
    }

    fun addInput() {
        if (input.isBlank()) return
        prefs.apiPrompt = Vocabulary.serialize(Vocabulary.add(entries, input))
        input = ""
    }

    LoomSheet(title = stringResource(R.string.vocab_title), onDismiss = onDismiss) { dismiss ->
        Text(
            stringResource(R.string.vocab_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.vocab_add_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addInput() }),
            trailingIcon = {
                IconButton(onClick = { addInput() }) {
                    LoomIcon(R.drawable.ic_add, stringResource(R.string.cd_add_term))
                }
            },
            supportingText = { Text(stringResource(R.string.vocab_add_info)) },
        )

        Text(stringResource(R.string.vocab_own, entries.size), style = MaterialTheme.typography.labelLarge)
        if (entries.isEmpty()) {
            Text(
                stringResource(R.string.vocab_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column {
                entries.forEachIndexed { i, entry ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entry, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { prefs.apiPrompt = Vocabulary.serialize(Vocabulary.remove(entries, entry)) }) {
                            LoomIcon(R.drawable.ic_close, stringResource(R.string.cd_remove_word, entry), Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        Text(stringResource(R.string.vocab_file), style = MaterialTheme.typography.labelLarge)
        if (prefs.vocabFileUri.isBlank()) {
            Text(
                stringResource(R.string.vocab_file_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = { picker.launch(FILE_TYPES) }) {
                LoomIcon(R.drawable.ic_add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.vocab_file_link))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(prefs.vocabFileName.ifBlank { stringResource(R.string.vocab_file_unnamed) }, style = MaterialTheme.typography.titleMedium)
                val terms = fileTerms
                Text(
                    if (terms == null) stringResource(R.string.vocab_file_unreadable)
                    else pluralStringResource(R.plurals.vocab_file_terms, terms.size, terms.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (terms == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { reloads++ }) { Text(stringResource(R.string.vocab_file_reload)) }
                TextButton(onClick = { picker.launch(FILE_TYPES) }) { Text(stringResource(R.string.vocab_file_change)) }
                TextButton(onClick = {
                    VocabularySource.unlink(ctx, prefs.prefs)
                    prefs.vocabFileUri = ""
                    prefs.vocabFileName = ""
                }) { Text(stringResource(R.string.vocab_file_unlink)) }
            }
        }

        if (prompt.total > 0) {
            Text(
                if (prompt.truncated) stringResource(R.string.vocab_usage_truncated, prompt.used, prompt.total)
                else pluralStringResource(R.plurals.vocab_usage, prompt.total, prompt.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { prefs.apiPrompt = "" }, enabled = entries.isNotEmpty()) {
                Text(stringResource(R.string.vocab_clear))
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = dismiss) { Text(stringResource(R.string.common_done)) }
        }
    }
}

/** .md meldet so mancher Dateimanager als octet-stream — der Name wird danach geprueft. */
private val FILE_TYPES = arrayOf("text/*", "application/octet-stream")
