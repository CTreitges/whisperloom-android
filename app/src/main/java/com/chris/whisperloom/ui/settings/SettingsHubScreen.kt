package com.chris.whisperloom.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.BuildConfig
import com.chris.whisperloom.Engine
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.ui.components.DetailScaffold
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.fileSize
import com.chris.whisperloom.ui.components.levelLabel
import com.chris.whisperloom.ui.components.modelLabel
import com.chris.whisperloom.ui.components.offlineModelLabel
import com.chris.whisperloom.ui.components.providerShortName
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.state.LocalAppEnv

/** E — Einstellungen-Hub (UX-Spec §2.3): fuenf Gruppen + Ueber, Supporting = aktueller Wert. */
@Composable
fun SettingsHubScreen(nav: NavState) {
    val env = LocalAppEnv.current
    val prefs = env.prefs
    val status = env.status
    val snack = rememberSnack()
    var showAbout by rememberSaveable { mutableStateOf(false) }

    val stt = prefs.sttAccess()
    val recognition = when (prefs.engine) {
        Engine.ONLINE -> stringResource(R.string.home_val_online, providerShortName(stt.provider), modelLabel(stt))
        Engine.OFFLINE -> "Offline · ${offlineModelLabel(prefs.offlineModel)}"
        null -> stringResource(R.string.setup_chip_open)
    }
    val rules = buildList {
        if (prefs.removeFillers) add(stringResource(R.string.settings_rule_fillers))
        if (prefs.autoCapitalize) add(stringResource(R.string.settings_rule_cap))
        if (prefs.trailingSpace) add(stringResource(R.string.settings_rule_space))
    }
    val text = if (rules.isEmpty()) levelLabel(prefs.refineMode)
    else stringResource(R.string.settings_val_text, levelLabel(prefs.refineMode), rules.joinToString(" · "))
    val button = stringResource(if (status.bubbleRunning) R.string.settings_val_bubble_on else R.string.settings_val_bubble_off) +
        " · " + stringResource(if (status.imeEnabled) R.string.settings_val_kb_on else R.string.settings_val_kb_off)
    // Kein Schalter auf Hub-Ebene (Spec §2.3) — nur der aktuelle Wert als Unterzeile.
    val advanced = buildList {
        if (prefs.agentReady) add(stringResource(R.string.settings_agent_sub))
        if (prefs.promptLevelEnabled) add(stringResource(R.string.settings_prompt_sub))
    }
    val agent = if (advanced.isEmpty()) stringResource(R.string.settings_agent_off) else advanced.joinToString(" · ")
    val n = status.installedModels.size
    val models = if (n > 0) stringResource(R.string.home_val_models, n, fileSize(status.modelsUsedBytes))
    else stringResource(R.string.settings_models_none)

    DetailScaffold(title = stringResource(R.string.settings_title), onBack = { nav.pop() }, snack = snack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { HubRow(R.drawable.ic_graphic_eq, stringResource(R.string.settings_group_recognition), recognition) { nav.push(Screen.Recognition) } }
            item { HubRow(R.drawable.ic_auto_fix_high, stringResource(R.string.settings_group_text), text) { nav.push(Screen.TextSettings) } }
            item { HubRow(R.drawable.ic_touch_app, stringResource(R.string.settings_group_button), button) { nav.push(Screen.ButtonKeyboard) } }
            item { HubRow(R.drawable.ic_download_for_offline, stringResource(R.string.settings_group_models), models) { nav.push(Screen.Models) } }
            item { HubRow(R.drawable.ic_help, stringResource(R.string.settings_group_help), stringResource(R.string.settings_help_sub)) { nav.push(Screen.Help(1)) } }
            item { HubRow(R.drawable.ic_build, stringResource(R.string.settings_group_agent), agent) { nav.push(Screen.Agent) } }
            item {
                HubRow(
                    R.drawable.ic_info, stringResource(R.string.settings_group_about),
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME), divider = false,
                ) { showAbout = true }
            }
        }
    }

    if (showAbout) AboutSheet(snack) { showAbout = false }
}

@Composable
private fun HubRow(icon: Int, headline: String, value: String, divider: Boolean = true, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(headline, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(value, style = MaterialTheme.typography.bodyMedium) },
        leadingContent = {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) { LoomIcon(icon, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurface) }
        },
        trailingContent = { LoomIcon(R.drawable.ic_chevron_right, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { stateDescription = value },
    )
    if (divider) HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
}
