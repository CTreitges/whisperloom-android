package com.chris.whisperloom.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chris.whisperloom.R
import com.chris.whisperloom.ui.components.DetailScaffold
import com.chris.whisperloom.ui.components.ScrollColumn
import com.chris.whisperloom.ui.components.SectionHeader
import com.chris.whisperloom.ui.components.fileSize
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.models.LocalModelRequiredCard
import com.chris.whisperloom.ui.models.ModelListSection
import com.chris.whisperloom.ui.models.localModelMissing
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.whisper.ModelDownloads
import com.chris.whisperloom.whisper.ModelStore

/**
 * E4 — Offline-Modelle (UX-Spec §2.7): Intro, Speicher (beide Arten), Abschnitt Spracherkennung
 * (Engine-Umschalter, whisper-Modelle, Quelle) und Abschnitt Textverbesserung (Pflichtkarte, wenn
 * offline das Textmodell fehlt; Textmodelle, Quelle).
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
        }
    }
}
