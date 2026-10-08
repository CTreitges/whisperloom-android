package com.chris.whisperloom.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.R
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.SectionCard
import com.chris.whisperloom.ui.components.offlineRuleDetails
import com.chris.whisperloom.ui.components.offlineRuleLabel
import com.chris.whisperloom.ui.models.LocalModelRequiredCard
import com.chris.whisperloom.ui.models.ModelListSection
import com.chris.whisperloom.ui.models.localModelMissing
import com.chris.whisperloom.ui.models.offlineRule
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.state.LocalAppEnv

/**
 * Text › Offline-Erkennung (Spec §4, nur wo offline geht): die Textmodelle zur Auswahl wie in
 * Offline-Modelle (Tipp laedt bzw. waehlt) — fehlt das gewaehlte, wo es gebraucht wird, darueber die
 * Pflichtkarte; passt keins ins Geraet, der Grund — und die Regel "Textverbesserung bei
 * Offline-Erkennung" mit drei Optionen. Ziel des Home-Banners "Offline ohne Textmodell".
 */
@Composable
fun TextOfflineScreen(nav: NavState) {
    val env = LocalAppEnv.current
    val prefs = env.prefs
    // Beide Stufen "Aus": die Regel wirkt dann nicht.
    val noAi = !anyAiStage(prefs)

    TextPageScaffold(R.string.text_card_offline, nav) { snack ->
        SectionCard(gap = 4.dp) {
            if (!env.status.textModelFits) {
                LoomRow(headline = stringResource(R.string.text_local_none), supporting = stringResource(R.string.text_local_needs_ram))
            } else {
                if (localModelMissing(prefs, env.status)) LocalModelRequiredCard(Modifier.padding(vertical = 8.dp), compact = true)
                ModelListSection(snack, showEmptyState = false, text = true, inCard = true)
            }
            Text(
                stringResource(R.string.text_rule_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            Column(Modifier.selectableGroup()) {
                OfflineRefineRule.entries.forEach { rule ->
                    // Passt kein Textmodell ins Geraet, bleibt nur "Ueberspringen" (der Grund steht darueber).
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
            if (noAi) {
                Text(
                    stringResource(R.string.text_smart_needs_level),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
