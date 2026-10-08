package com.chris.whisperloom.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.R
import com.chris.whisperloom.ui.components.DetailScaffold
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.ScrollColumn
import com.chris.whisperloom.ui.components.SectionCard
import com.chris.whisperloom.ui.components.SectionHeader
import com.chris.whisperloom.ui.components.fileSize
import com.chris.whisperloom.ui.components.offlineRuleDetails
import com.chris.whisperloom.ui.components.offlineRuleLabel
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.models.LocalModelRequiredCard
import com.chris.whisperloom.ui.models.ModelListSection
import com.chris.whisperloom.ui.models.localModelMissing
import com.chris.whisperloom.ui.models.offlineRule
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.whisper.ModelDownloads
import com.chris.whisperloom.whisper.ModelStore

/**
 * E4 — Offline-Modelle (UX-Spec §2.7): Intro, Speicher (beide Arten), Abschnitt Spracherkennung
 * (Engine-Umschalter, whisper-Modelle, Quelle) und Abschnitt Textverbesserung (Pflichtkarte, wenn
 * offline das Textmodell fehlt; Textmodelle, Quelle; wo das Geraet offline erkennen kann, die Regel
 * "Textverbesserung bei Offline-Erkennung" — bis 3.8.6 eine eigene Seite unter Text).
 * Ziel des Home-Banners und des Tastatur-Hinweises "Offline ohne Textmodell".
 */
@Composable
fun ModelsScreen(nav: NavState) {
    val ctx = LocalContext.current
    val env = LocalAppEnv.current
    val snack = rememberSnack()
    val store = remember { ModelStore(ctx) }
    val states by ModelDownloads.states.collectAsStateWithLifecycle()
    // Nach Download/Loeschen aendert sich der Systemstatus (installierte Modelle) -> Speicherzeile neu lesen.
    val used = remember(states, env.status) { store.usedBytes() }
    val free = remember(states, env.status) { store.freeBytes() }

    DetailScaffold(title = stringResource(R.string.models_title), onBack = { nav.pop() }, snack = snack) { padding ->
        ScrollColumn(padding) {
            Text(
                stringResource(R.string.models_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.models_storage, fileSize(used), fileSize(free)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader(stringResource(R.string.models_section_stt), inset = 0.dp)
            EngineSwitch(snack, requireModelForOffline = true)
            ModelListSection(snack)
            Text(
                stringResource(R.string.models_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            SectionHeader(stringResource(R.string.models_section_llm), inset = 0.dp)
            Text(
                stringResource(R.string.models_llm_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (localModelMissing(env.prefs, env.status)) LocalModelRequiredCard(compact = true)
            ModelListSection(snack, showEmptyState = false, text = true)
            Text(
                stringResource(R.string.models_llm_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            if (env.status.offlineSupported) OfflineRuleCard()
        }
    }
}

/** Die Regel "Textverbesserung bei Offline-Erkennung" mit drei Optionen. */
@Composable
private fun OfflineRuleCard() {
    val env = LocalAppEnv.current
    val prefs = env.prefs
    SectionCard(title = stringResource(R.string.text_rule_title), gap = 4.dp) {
        // Passt kein Textmodell ins Geraet, bleibt nur "Ueberspringen" — mit Grund.
        if (!env.status.textModelFits) {
            Text(
                stringResource(R.string.text_local_needs_ram),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.selectableGroup()) {
            OfflineRefineRule.entries.forEach { rule ->
                val enabled = env.status.textModelFits || rule == OfflineRefineRule.SKIP
                val selected = offlineRule(prefs, env.status) == rule
                LoomRow(
                    headline = offlineRuleLabel(rule),
                    supporting = offlineRuleDetails(rule),
                    modifier = Modifier
                        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) { prefs.offlineRefine = rule }
                        .alpha(if (enabled) 1f else 0.38f),
                    trailing = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
                )
            }
        }
        // Beide Stufen "Aus": die Regel wirkt dann nicht.
        if (!anyAiStage(prefs)) {
            Text(
                stringResource(R.string.text_smart_needs_level),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
