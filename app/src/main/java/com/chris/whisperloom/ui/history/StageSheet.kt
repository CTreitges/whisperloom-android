package com.chris.whisperloom.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.RefineWay
import com.chris.whisperloom.history.HistoryEntry
import com.chris.whisperloom.history.Processing
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.LoomSheet
import com.chris.whisperloom.ui.components.SectionHeader
import com.chris.whisperloom.ui.components.levelLabel
import com.chris.whisperloom.ui.settings.cleanupLabel
import com.chris.whisperloom.ui.settings.cleanupSubtitle
import com.chris.whisperloom.ui.settings.levelSubtitle

/**
 * "Andere Stufe …": dieselben Stufen wie in den Einstellungen (ohne "Aus", Prompt nur mit Pro),
 * Glaetten mit der Wahl der Bereinigung. Vorhandene Fassungen tragen ein Haekchen ("vorhanden"),
 * was gerade gerechnet wird, ist gesperrt. Gerechnet wird aus dem Ursprung mit den aktuellen
 * Diktat-Einstellungen; die eingestellte Stufe bleibt.
 */
@Composable
fun StageSheet(entry: HistoryEntry, loading: List<Processing>, prompt: Boolean, onDismiss: () -> Unit, onPick: (Processing) -> Unit) {
    LoomSheet(title = stringResource(R.string.history_sheet_title), onDismiss = onDismiss) {
        Text(
            stringResource(R.string.history_sheet_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val stages = HistoryText.stages(prompt)
        Column {
            SectionHeader(levelLabel(RefineMode.POLISH), inset = 0.dp)
            stages.filter { it.stage == RefineMode.POLISH }.forEach { p ->
                StageOption(stringResource(cleanupLabel(p.cleanup!!)), stringResource(cleanupSubtitle(p.cleanup!!)), entry, p, loading, onPick)
            }
            SectionHeader(stringResource(R.string.history_sheet_more), inset = 0.dp)
            stages.filter { it.stage != RefineMode.POLISH }.forEach { p ->
                StageOption(levelLabel(p.stage!!), stringResource(levelSubtitle(p.stage, RefineWay.DICTATION)), entry, p, loading, onPick)
            }
        }
    }
}

@Composable
private fun StageOption(
    headline: String,
    supporting: String,
    entry: HistoryEntry,
    processing: Processing,
    loading: List<Processing>,
    onPick: (Processing) -> Unit,
) {
    val exists = processing in entry.versions
    val existing = stringResource(R.string.history_sheet_existing)
    LoomRow(
        headline = headline,
        supporting = supporting,
        onClick = { onPick(processing) },
        enabled = processing !in loading,
        stateDescription = if (exists) existing else null,
        trailing = if (exists) {
            { LoomIcon(R.drawable.ic_check_circle, null, Modifier.padding(start = 8.dp).size(24.dp), MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
    )
}
