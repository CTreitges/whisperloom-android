package com.chris.whisperloom.ui.settings

import android.Manifest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R
import com.chris.whisperloom.ui.components.DetailScaffold
import com.chris.whisperloom.ui.components.DisclosureKind
import com.chris.whisperloom.ui.components.KeyboardRows
import com.chris.whisperloom.ui.components.OutlinedSection
import com.chris.whisperloom.ui.components.ScrollColumn
import com.chris.whisperloom.ui.components.SectionCard
import com.chris.whisperloom.ui.components.StatusIcon
import com.chris.whisperloom.ui.components.SystemIntents
import com.chris.whisperloom.ui.components.Tone
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.openOrSnack
import com.chris.whisperloom.ui.components.rememberDisclosureGate
import com.chris.whisperloom.ui.components.rememberPermissionRequest
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.home.HomeStatus
import com.chris.whisperloom.ui.home.rememberBubbleControl
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SetupRouter
import com.chris.whisperloom.ui.setup.HowToRow
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.theme.loom

/** E3 — Knopf & Tastatur (UX-Spec §2.6): Knopf, Berechtigungen, Diktier-Tastatur, Kurzanleitung. */
@Composable
fun ButtonKeyboardScreen(nav: NavState) {
    val ctx = LocalContext.current
    val env = LocalAppEnv.current
    val prefs = env.prefs
    val status = env.status
    val snack = rememberSnack()
    val control = rememberBubbleControl(snack) { nav.push(Screen.Setup(SetupRouter.STEP_OVERLAY)) }
    val mic = rememberPermissionRequest(Manifest.permission.RECORD_AUDIO)
    val notif = rememberPermissionRequest(POST_NOTIFICATIONS)
    // Prominent Disclosure vor Mikrofon-Request bzw. Bedienungshilfe-Einstellungen (Play-Pflicht).
    val micGate = rememberDisclosureGate(DisclosureKind.MICROPHONE, onAccept = mic.request)
    val a11yGate = rememberDisclosureGate(
        DisclosureKind.ACCESSIBILITY,
        onAccept = { openOrSnack(ctx, SystemIntents.accessibility(), snack) },
    )
    val positionReset = stringResource(R.string.button_pos_reset_done)

    DetailScaffold(title = stringResource(R.string.button_title), onBack = { nav.pop() }, snack = snack) { padding ->
        ScrollColumn(padding) {
            SectionCard(title = stringResource(R.string.button_card_bubble), gap = 4.dp) {
                val running = control.running
                LoomRow(
                    headline = stringResource(if (running) R.string.home_state_running else R.string.home_state_stopped),
                    leading = { StatusIcon(if (running) Tone.SUCCESS else Tone.NEUTRAL, R.drawable.ic_mic) },
                    trailing = {
                        val loom = MaterialTheme.loom
                        Button(
                            onClick = control.toggle,
                            enabled = control.blocked == HomeStatus.Blocked.NONE && !control.pending,
                            modifier = Modifier.height(48.dp),
                            colors = if (running) {
                                ButtonDefaults.buttonColors(containerColor = loom.recordingContainer, contentColor = loom.recordingText)
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                        ) {
                            Text(stringResource(if (running) R.string.home_hero_btn_stop else R.string.home_hero_btn_start))
                        }
                    },
                )
                LoomRow(
                    headline = stringResource(R.string.button_reset_pos),
                    supporting = stringResource(R.string.button_reset_pos_sub),
                    leading = { LoomIcon(R.drawable.ic_restart_alt, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = {
                        prefs.resetBubblePosition()
                        snack.show(positionReset)
                    },
                )
                LoomRow(
                    headline = stringResource(R.string.button_text_output),
                    supporting = stringResource(if (status.a11yRunning) R.string.button_text_output_a11y else R.string.button_text_output_clip),
                    leading = { StatusIcon(if (status.a11yRunning) Tone.SUCCESS else Tone.WARNING) },
                )
            }

            SectionCard(title = stringResource(R.string.button_card_permissions), gap = 4.dp) {
                PermissionRow(stringResource(R.string.perm_mic), status.micGranted, stringResource(R.string.perm_allow), onAction = micGate.request)
                PermissionRow(stringResource(R.string.perm_overlay), status.canDrawOverlays, stringResource(R.string.perm_open)) {
                    openOrSnack(ctx, SystemIntents.overlay(ctx), snack)
                }
                PermissionRow(
                    stringResource(R.string.perm_a11y), status.a11yRunning, stringResource(R.string.perm_open),
                    supporting = stringResource(R.string.perm_a11y_sub),
                ) { a11yGate.request() }
                if (status.notifNeeded) {
                    PermissionRow(stringResource(R.string.perm_notif), status.notifGranted, stringResource(R.string.perm_allow), onAction = notif.request)
                }
            }

            SectionCard(title = stringResource(R.string.button_card_keyboard), gap = 8.dp) {
                Text(stringResource(R.string.button_keyboard_intro), style = MaterialTheme.typography.bodyMedium)
                KeyboardRows(tryFieldMinLines = 3, snack = snack)
            }

            OutlinedSection(gap = 4.dp) {
                Text(stringResource(R.string.button_card_howto), style = MaterialTheme.typography.titleMedium)
                Column {
                    HowToRow(1, stringResource(R.string.help_dictate_1))
                    HowToRow(2, stringResource(R.string.help_dictate_2))
                    HowToRow(3, stringResource(R.string.help_dictate_3))
                    HowToRow(4, stringResource(R.string.help_dictate_4))
                }
            }
        }
    }
}

/** Berechtigungszeile: Status-Icon links; rechts Haekchen (erledigt) oder Aktions-Button. */
@Composable
private fun PermissionRow(headline: String, done: Boolean, actionLabel: String, supporting: String? = null, onAction: () -> Unit) {
    LoomRow(
        headline = headline,
        supporting = supporting,
        leading = { StatusIcon(if (done) Tone.SUCCESS else Tone.WARNING) },
        stateDescription = stringResource(if (done) R.string.setup_status_done else R.string.setup_status_missing),
        trailing = {
            if (done) {
                LoomIcon(R.drawable.ic_check_circle, null, Modifier.size(24.dp), MaterialTheme.loom.success)
            } else {
                FilledTonalButton(onClick = onAction) { Text(actionLabel) }
            }
        },
    )
}

private const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
