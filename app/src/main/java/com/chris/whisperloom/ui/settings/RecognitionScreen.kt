package com.chris.whisperloom.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.Vocabulary
import com.chris.whisperloom.ui.access.PrivacyLine
import com.chris.whisperloom.ui.access.SttAccessSection
import com.chris.whisperloom.ui.components.DetailScaffold
import com.chris.whisperloom.ui.components.OutlinedSection
import com.chris.whisperloom.ui.components.ScrollColumn
import com.chris.whisperloom.ui.components.SectionCard
import com.chris.whisperloom.ui.components.LoomDropdown
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.fileSize
import com.chris.whisperloom.ui.components.languageLabel
import com.chris.whisperloom.ui.components.offlineModelDetails
import com.chris.whisperloom.ui.components.offlineModelLabel
import com.chris.whisperloom.ui.components.providerShortName
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.theme.loom
import com.chris.whisperloom.whisper.ModelCatalog

/** E1 — Erkennung (UX-Spec §2.4): Engine, Transkriptions-Zugang bzw. Offline-Modell, Sprache & Kontext. */
@Composable
fun RecognitionScreen(nav: NavState) {
    val env = LocalAppEnv.current
    val prefs = env.prefs
    val status = env.status
    val snack = rememberSnack()
    val offline = prefs.engine == Engine.OFFLINE
    val modelInstalled = prefs.offlineModel in status.installedModels
    val stt = prefs.sttAccess()
    var showVocabulary by rememberSaveable { mutableStateOf(false) }

    DetailScaffold(title = stringResource(R.string.rec_title), onBack = { nav.pop() }, snack = snack) { padding ->
        ScrollColumn(padding) {
            EngineSwitch(snack, requireModelForOffline = false)

            if (offline && !modelInstalled) {
                OutlinedSection(borderColor = MaterialTheme.loom.warningContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LoomIcon(R.drawable.ic_warning, null, Modifier.size(24.dp), MaterialTheme.loom.warning)
                        Text(stringResource(R.string.rec_no_model), style = MaterialTheme.typography.titleMedium)
                    }
                    FilledTonalButton(onClick = { nav.push(Screen.Models) }) { Text(stringResource(R.string.rec_load_model)) }
                }
            }

            if (!offline) {
                SectionCard(title = stringResource(R.string.rec_card_transcription)) {
                    SttAccessSection(snack, showPrivacy = false)
                }
            } else {
                SectionCard(title = stringResource(R.string.rec_card_offline)) {
                    val model = ModelCatalog.byId(prefs.offlineModel)
                    LoomRow(
                        headline = offlineModelLabel(model.id),
                        supporting = "${fileSize(model.bytes)} · ${offlineModelDetails(model.id).substringAfterLast(" · ")}",
                        trailing = { TextButton(onClick = { nav.push(Screen.Models) }) { Text(stringResource(R.string.common_change)) } },
                    )
                    Text(
                        stringResource(R.string.rec_offline_first_use),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(title = stringResource(R.string.rec_card_language)) {
                LoomDropdown(
                    label = stringResource(R.string.rec_language),
                    value = languageLabel(prefs.language),
                    options = Prefs.LANGUAGES,
                    optionLabel = { it.second },
                    onSelect = { prefs.language = it.first },
                )
                // Mistral/OpenRouter kennen kein prompt-Feld; eine context_bias-Wortliste ist nicht
                // umgesetzt (Spec §2.4, offen) — also ehrlich sagen, dass der Kontext dort nicht ankommt.
                val unsupported = !offline && !stt.provider.sttSendsPrompt
                val count = Vocabulary.entries(prefs.apiPrompt).size
                LoomRow(
                    headline = stringResource(R.string.vocab_title),
                    supporting = listOfNotNull(
                        if (count == 0) stringResource(R.string.vocab_none) else pluralStringResource(R.plurals.vocab_count, count, count),
                        prefs.vocabFileName.takeIf { prefs.vocabFileUri.isNotBlank() }
                            ?.let { stringResource(R.string.vocab_row_file, it.ifBlank { "…" }) },
                    ).joinToString(" · "),
                    trailing = {
                        FilledTonalButton(onClick = { showVocabulary = true }) { Text(stringResource(R.string.vocab_open)) }
                    },
                )
                Text(
                    stringResource(if (unsupported) R.string.rec_context_unsupported else R.string.pref_api_prompt_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PrivacyLine(
                if (offline) stringResource(R.string.rec_privacy_offline)
                else stringResource(R.string.rec_privacy_online, providerShortName(stt.provider)),
            )
        }
    }

    if (showVocabulary) VocabularySheet(snack) { showVocabulary = false }
}
