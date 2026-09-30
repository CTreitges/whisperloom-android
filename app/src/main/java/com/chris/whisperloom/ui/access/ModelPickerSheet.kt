package com.chris.whisperloom.ui.access

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.ModelCache
import com.chris.whisperloom.R
import com.chris.whisperloom.api.ModelOption
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.LoomSheet
import com.chris.whisperloom.ui.theme.loom
import java.text.DateFormat
import java.util.Date

/**
 * Modellwahl mit Pro "Modelle vom Server": Suche, "Empfohlen" (Katalog mit Notizen), "Vom Server"
 * (zuletzt geladene Liste) und "Eigenes Modell …". Eine LazyColumn, weil OpenRouter Hunderte
 * Modelle liefert.
 *
 * Das gewaehlte Modell bleibt gueltig, auch wenn der Server es nicht (mehr) listet — es steht dann
 * markiert ganz oben. Eine Empfehlung, die der Server nicht mehr fuehrt, heisst "nicht mehr
 * gelistet"; eine leere oder noch nicht geladene Liste sagt darueber nichts.
 *
 * @param checkListed aus bei ElevenLabs: /v1/models listet Scribe wohl gar nicht.
 */
@Composable
fun ModelPickerSheet(
    recommended: List<ModelOption>,
    server: ModelCache.Entry?,
    selected: String,
    onSelect: (String) -> Unit,
    onCustom: () -> Unit,
    onDismiss: () -> Unit,
    checkListed: Boolean = true,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    fun hit(vararg texts: String?) = q.isEmpty() || texts.any { it != null && it.contains(q, ignoreCase = true) }

    val serverIds = remember(server) { server?.models.orEmpty().mapTo(HashSet()) { it.id } }
    val canMark = checkListed && serverIds.isNotEmpty()
    val recs = remember(recommended, q) { recommended.filter { hit(it.id, it.label, it.note) } }
    val remote = remember(server, q) { server?.models.orEmpty().filter { hit(it.id, it.label, it.note) } }
    val orphan = selected.isNotBlank() && selected !in serverIds && recommended.none { it.id == selected } && hit(selected)
    val stand = remember(server?.fetchedAt) {
        server?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.fetchedAt)) }
    }
    val stale = stringResource(R.string.models_stale)

    LoomSheet(title = stringResource(R.string.models_pick), onDismiss = onDismiss, scroll = false) { dismiss ->
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.models_search)) },
            singleLine = true,
        )
        LazyColumn(Modifier.weight(1f, fill = false).selectableGroup()) {
            fun pick(id: String) {
                onSelect(id)
                dismiss()
            }
            if (orphan) {
                item(key = "selected") { ModelRow(selected, stale.takeIf { canMark }, selected = true, warn = canMark) { dismiss() } }
            }
            if (recs.isNotEmpty()) {
                item(key = "h:recommended") { PickerHeader(stringResource(R.string.models_recommended)) }
                items(recs, key = { "r:${it.id}" }) { m ->
                    val gone = canMark && m.id !in serverIds
                    val note = listOfNotNull(stale.takeIf { gone }, m.note.ifBlank { null }).joinToString(" · ")
                    ModelRow(m.label, note.ifEmpty { null }, m.id == selected, warn = gone) { pick(m.id) }
                }
            }
            if (server != null && stand != null) {
                item(key = "h:server") { PickerHeader(stringResource(R.string.models_server, stand)) }
                if (server.models.isEmpty()) {
                    item(key = "none") { Text(stringResource(R.string.models_none), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(remote, key = { "s:${it.id}" }) { m ->
                    val note = listOfNotNull(m.id.takeIf { m.label != null }, m.note).joinToString(" · ")
                    ModelRow(m.label ?: m.id, note.ifEmpty { null }, m.id == selected) { pick(m.id) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onCustom) { Text(stringResource(R.string.rec_model_custom)) }
            TextButton(onClick = dismiss) { Text(stringResource(R.string.common_close)) }
        }
    }
}

@Composable
private fun PickerHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
    )
}

/** Auswaehlbare Zeile wie die Stufen im Text-Screen: TalkBack liest Name, Notiz und "ausgewaehlt". */
@Composable
private fun ModelRow(headline: String, supporting: String?, selected: Boolean, warn: Boolean = false, onClick: () -> Unit) {
    LoomRow(
        headline = headline,
        supporting = supporting,
        supportingColor = if (warn) MaterialTheme.loom.warning else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        trailing = { RadioButton(selected = selected, onClick = null) },
    )
}
