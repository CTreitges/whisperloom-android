package com.chris.whisperloom.ui.settings

import android.Manifest
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import com.chris.whisperloom.R
import com.chris.whisperloom.agent.AgentBridge
import com.chris.whisperloom.agent.AgentUrlCheck
import com.chris.whisperloom.agent.VoiceTaskWidget
import com.chris.whisperloom.api.ServerUrlCheck
import com.chris.whisperloom.ui.access.AccessTest
import com.chris.whisperloom.ui.access.PrivacyLine
import com.chris.whisperloom.ui.access.TestAccessRow
import com.chris.whisperloom.ui.access.urlProblemText
import com.chris.whisperloom.ui.components.ApiKeyField
import com.chris.whisperloom.ui.components.DetailScaffold
import com.chris.whisperloom.ui.components.DisclosureKind
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.ScrollColumn
import com.chris.whisperloom.ui.components.SectionCard
import com.chris.whisperloom.ui.components.StatusIcon
import com.chris.whisperloom.ui.components.SwitchRow
import com.chris.whisperloom.ui.components.Tone
import com.chris.whisperloom.ui.components.rememberDisclosureGate
import com.chris.whisperloom.ui.components.rememberPermissionRequest
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.tutorial.TutorialKind

/**
 * E7 — Erweiterte Optionen: Sprachauftrag an einen eigenen Agenten.
 *
 * Bewusst der einzige Ort, an dem das Feature auftaucht: Startbildschirm, Assistent und
 * Home-Status bleiben unangetastet. Wer den Sprachauftrag nicht nutzt, soll ihn nicht bemerken.
 */
@Composable
fun AgentScreen(nav: NavState) {
    val ctx = LocalContext.current
    val prefs = LocalAppEnv.current.prefs
    val snack = rememberSnack()
    val mic = rememberPermissionRequest(Manifest.permission.RECORD_AUDIO)
    // Play-Pflicht: eigener Hinweis VOR dem System-Dialog — der Sprachauftrag ist ein neuer Mikrofon-Einstieg.
    val micGate = rememberDisclosureGate(DisclosureKind.MICROPHONE, onAccept = mic.request)
    val hasMic = VoiceTaskWidget.hasMicPermission(ctx)

    val urlProblem = if (prefs.agentUrl.isBlank()) null else AgentUrlCheck.check(prefs.agentUrl)
    val complete = prefs.agentUrl.isNotBlank() && prefs.agentToken.isNotBlank() &&
        urlProblem?.severity != ServerUrlCheck.Severity.ERROR

    // Das Widget zeigt "aus", solange etwas fehlt — nach jeder Aenderung hier neu zeichnen.
    LaunchedEffect(prefs.agentEnabled, prefs.agentUrl, prefs.agentToken, hasMic) {
        VoiceTaskWidget.refresh(ctx)
    }

    DetailScaffold(title = stringResource(R.string.settings_group_agent), onBack = { nav.pop() }, snack = snack) { padding ->
        ScrollColumn(padding) {
            SectionCard(title = stringResource(R.string.agent_card_task), gap = 4.dp) {
                SwitchRow(
                    headline = stringResource(R.string.agent_enable),
                    supporting = stringResource(R.string.agent_enable_sub),
                    checked = prefs.agentEnabled,
                    onCheckedChange = { prefs.agentEnabled = it },
                )
            }

            SectionCard(title = stringResource(R.string.agent_card_connection)) {
                OutlinedTextField(
                    value = prefs.agentUrl,
                    onValueChange = { prefs.agentUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.agent_url)) },
                    placeholder = { Text("https://bridge.example.de") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    isError = urlProblem?.severity == ServerUrlCheck.Severity.ERROR,
                    supportingText = {
                        Text(if (urlProblem != null) urlProblemText(urlProblem) else stringResource(R.string.agent_url_hint))
                    },
                )
                ApiKeyField(
                    value = prefs.agentToken,
                    onValueChange = { prefs.agentToken = it },
                    label = stringResource(R.string.agent_token),
                )
                TestAccessRow(label = stringResource(R.string.agent_check), enabled = complete) {
                    AccessTest.bridge(AgentBridge(prefs.agentUrl, prefs.agentToken))
                }
                PrivacyLine(stringResource(R.string.agent_privacy))
            }

            if (prefs.agentEnabled && !hasMic) {
                SectionCard(title = stringResource(R.string.agent_card_mic), gap = 4.dp) {
                    LoomRow(
                        headline = stringResource(R.string.agent_mic_missing),
                        supporting = stringResource(R.string.agent_mic_allow),
                        leading = { StatusIcon(Tone.WARNING, R.drawable.ic_mic_off) },
                        onClick = micGate.request,
                    )
                }
            }

            SectionCard(title = stringResource(R.string.agent_card_help), gap = 4.dp) {
                LoomRow(
                    headline = stringResource(R.string.agent_tutorial),
                    supporting = stringResource(R.string.agent_tutorial_sub),
                    leading = { LoomIcon(R.drawable.ic_help, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { nav.push(Screen.Tutorial(kind = TutorialKind.AGENT)) },
                )
                LoomRow(
                    headline = stringResource(R.string.agent_widget_hint),
                    leading = { LoomIcon(R.drawable.ic_touch_app, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
        }
    }
}
