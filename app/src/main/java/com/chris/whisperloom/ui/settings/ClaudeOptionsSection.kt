package com.chris.whisperloom.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R
import com.chris.whisperloom.agent.ClaudeEffort
import com.chris.whisperloom.agent.ClaudeModel
import com.chris.whisperloom.agent.WidgetProfile
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.SwitchRow

/**
 * Block "Claude" im Editor eines Claude-Code-Widgets: Modell, Effort und Ultracode der Session,
 * die das Widget startet. Jede Wahl geht sofort ueber [onChange] in den Speicher, wie die
 * uebrigen Felder des Editors. Die claude-bridge bekommt die Werte je Auftrag als `options`
 * ([WidgetProfile.bridgeOptions]).
 *
 * Effort hat fuenf Werte — als Segmentreihe schnitte "Extra hoch" auf 360 dp ab, deshalb Chips,
 * die umbrechen. TalkBack liest sie als Optionsfelder einer Gruppe, nicht als Kontrollkaestchen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClaudeOptionsSection(profile: WidgetProfile, onChange: (WidgetProfile) -> Unit) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Text(stringResource(R.string.widget_profile_claude), style = MaterialTheme.typography.labelLarge)
    Text(stringResource(R.string.widget_profile_claude_sub), style = MaterialTheme.typography.bodySmall, color = dim)

    Text(stringResource(R.string.widget_profile_claude_model), style = MaterialTheme.typography.labelMedium)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ClaudeModel.entries.forEachIndexed { i, model ->
            SegmentedButton(
                selected = profile.claudeModel == model,
                onClick = { onChange(profile.copy(claudeModel = model)) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = ClaudeModel.entries.size),
                icon = {},
                label = { Text(stringResource(claudeModelLabel(model)), maxLines = 1) },
            )
        }
    }

    Text(stringResource(R.string.widget_profile_claude_effort), style = MaterialTheme.typography.labelMedium)
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ClaudeEffort.entries.forEach { effort ->
            val selected = profile.claudeEffort == effort
            FilterChip(
                selected = selected,
                onClick = { onChange(profile.copy(claudeEffort = effort)) },
                label = { Text(stringResource(claudeEffortLabel(effort)), maxLines = 1) },
                leadingIcon = if (selected) {
                    { LoomIcon(R.drawable.ic_check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
                modifier = Modifier.semantics { role = Role.RadioButton },
            )
        }
    }

    SwitchRow(
        headline = stringResource(R.string.widget_profile_ultracode),
        supporting = stringResource(R.string.widget_profile_ultracode_sub),
        checked = profile.ultracode,
        onCheckedChange = { onChange(profile.copy(ultracode = it)) },
    )
}

@StringRes
private fun claudeModelLabel(model: ClaudeModel): Int = when (model) {
    ClaudeModel.OPUS -> R.string.claude_model_opus
    ClaudeModel.FABLE -> R.string.claude_model_fable
    ClaudeModel.SONNET -> R.string.claude_model_sonnet
    ClaudeModel.HAIKU -> R.string.claude_model_haiku
}

@StringRes
private fun claudeEffortLabel(effort: ClaudeEffort): Int = when (effort) {
    ClaudeEffort.LOW -> R.string.claude_effort_low
    ClaudeEffort.MEDIUM -> R.string.claude_effort_medium
    ClaudeEffort.HIGH -> R.string.claude_effort_high
    ClaudeEffort.XHIGH -> R.string.claude_effort_xhigh
    ClaudeEffort.MAX -> R.string.claude_effort_max
}
