package com.chris.whisperloom.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.SectionCard
import com.chris.whisperloom.ui.components.SwitchRow
import com.chris.whisperloom.ui.components.levelLabel
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.state.LocalAppEnv

/** Die Stufen-Auswahl fuer geteilte Sprachnachrichten (Tests: von den Radios des Diktats unterscheidbar). */
const val SHARE_REFINE_TAG = "share-refine"

/**
 * Text › Sprachnachrichten (3.8.6): eigene Stufe fuer geteilte Audios (ohne "Prompt") und ein
 * eigener Schalter "Lesbarer glaetten", unabhaengig vom Diktat.
 */
@Composable
fun TextShareScreen(nav: NavState) {
    val prefs = LocalAppEnv.current.prefs
    val polish = prefs.shareRefineMode == RefineMode.POLISH

    TextPageScaffold(R.string.text_hub_share, nav) {
        SectionCard(
            title = stringResource(R.string.text_card_share),
            titleIcon = R.drawable.ic_voicemail,
            gap = 4.dp,
        ) {
            Text(
                stringResource(R.string.text_share_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.selectableGroup().testTag(SHARE_REFINE_TAG)) {
                RefineMode.SETTINGS.forEach { mode ->
                    val selected = prefs.shareRefineMode == mode
                    LoomRow(
                        headline = levelLabel(mode),
                        supporting = stringResource(if (mode == RefineMode.OFF) R.string.text_share_off_sub else levelSubtitle(mode)),
                        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) { prefs.shareRefineMode = mode },
                        trailing = { RadioButton(selected = selected, onClick = null) },
                    )
                }
            }
            SwitchRow(
                headline = stringResource(R.string.pref_polish_readable),
                supporting = stringResource(if (polish) R.string.pref_polish_readable_info else R.string.text_readable_needs_polish),
                checked = prefs.sharePolishReadable,
                onCheckedChange = { prefs.sharePolishReadable = it },
                enabled = polish,
            )
        }
    }
}
