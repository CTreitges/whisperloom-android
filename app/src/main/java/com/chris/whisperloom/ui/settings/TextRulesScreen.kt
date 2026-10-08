package com.chris.whisperloom.ui.settings

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
import com.chris.whisperloom.R
import com.chris.whisperloom.ui.components.SectionCard
import com.chris.whisperloom.ui.components.SwitchRow
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.state.LocalAppEnv

/** Text › Regeln ohne KI: feste Fuellwort-Liste (Sheet B3), Gross-Schreibung, Leerzeichen. */
@Composable
fun TextRulesScreen(nav: NavState) {
    val prefs = LocalAppEnv.current.prefs
    var showFillers by rememberSaveable { mutableStateOf(false) }

    TextPageScaffold(R.string.text_card_rules, nav) {
        SectionCard(gap = 4.dp) {
            SwitchRow(
                headline = stringResource(R.string.pref_remove_fillers),
                supporting = stringResource(R.string.text_fillers_sub),
                checked = prefs.removeFillers,
                onCheckedChange = { prefs.removeFillers = it },
            )
            TextButton(onClick = { showFillers = true }, enabled = prefs.removeFillers) {
                Text(stringResource(R.string.text_fillers_edit))
            }
            SwitchRow(
                headline = stringResource(R.string.pref_auto_cap),
                checked = prefs.autoCapitalize,
                onCheckedChange = { prefs.autoCapitalize = it },
            )
            SwitchRow(
                headline = stringResource(R.string.pref_trailing_space),
                checked = prefs.trailingSpace,
                onCheckedChange = { prefs.trailingSpace = it },
            )
            // Die KI entscheidet ueber Fuellwoerter (Schalter unter Diktat) — die Liste pausiert dann.
            if (prefs.smartFillers && anyAiStage(prefs)) {
                Text(
                    stringResource(R.string.text_fillers_paused),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showFillers) FillersSheet { showFillers = false }
}
