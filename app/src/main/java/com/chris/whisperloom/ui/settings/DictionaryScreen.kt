package com.chris.whisperloom.ui.settings

import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.Engine
import com.chris.whisperloom.R
import com.chris.whisperloom.api.ApiStyle
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.SectionCard
import com.chris.whisperloom.ui.components.SwitchRow
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.state.LocalAppEnv

/**
 * Woerterbuch & Regeln (3.9.0): das Vokabular (vorher unter Erkennung › Sprache & Kontext) und die
 * festen Regeln ohne KI (vorher Text › Regeln ohne KI). "Leerzeichen nach Diktat" ist ein
 * Einfuege-Verhalten und steht jetzt bei Knopf & Tastatur.
 */
@Composable
fun DictionaryScreen(nav: NavState) {
    val prefs = LocalAppEnv.current.prefs
    val stt = prefs.sttAccess()
    var showVocabulary by rememberSaveable { mutableStateOf(false) }
    var showFillers by rememberSaveable { mutableStateOf(false) }

    SettingsPageScaffold(R.string.settings_group_dictionary, nav) { snack ->
        SectionCard(title = stringResource(R.string.dict_card_vocab)) {
            LoomRow(
                headline = stringResource(R.string.vocab_title),
                supporting = vocabValue(prefs),
                trailing = {
                    FilledTonalButton(onClick = { showVocabulary = true }) { Text(stringResource(R.string.vocab_open)) }
                },
            )
            // Mistral/OpenRouter kennen kein prompt-Feld; eine context_bias-Wortliste ist nicht
            // umgesetzt (Spec §2.4, offen) — also ehrlich sagen, dass der Kontext dort nicht ankommt.
            // ElevenLabs bekommt das Vokabular als keyterms, aber mit Aufpreis.
            val contextInfo = when {
                prefs.engine == Engine.OFFLINE -> R.string.pref_api_prompt_info
                !stt.provider.sttSendsPrompt -> R.string.rec_context_unsupported
                stt.provider.api == ApiStyle.ELEVENLABS -> R.string.rec_context_keyterms
                else -> R.string.pref_api_prompt_info
            }
            Text(
                stringResource(contextInfo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = stringResource(R.string.dict_card_rules), gap = 4.dp) {
            SwitchRow(
                headline = stringResource(R.string.pref_remove_fillers),
                supporting = stringResource(R.string.text_fillers_sub),
                checked = prefs.removeFillers,
                onCheckedChange = { prefs.removeFillers = it },
            )
            // Immer bedienbar: die Liste gilt auch fuers Ausblenden im Sprachnachrichten-Fenster.
            TextButton(onClick = { showFillers = true }) {
                Text(stringResource(R.string.text_fillers_edit))
            }
            SwitchRow(
                headline = stringResource(R.string.pref_auto_cap),
                checked = prefs.autoCapitalize,
                onCheckedChange = { prefs.autoCapitalize = it },
            )
        }

        if (showVocabulary) VocabularySheet(snack) { showVocabulary = false }
        if (showFillers) FillersSheet { showFillers = false }
    }
}
