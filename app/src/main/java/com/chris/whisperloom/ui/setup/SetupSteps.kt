package com.chris.whisperloom.ui.setup

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.Engine
import com.chris.whisperloom.R
import com.chris.whisperloom.ui.access.SttAccessSection
import com.chris.whisperloom.ui.components.CardShape
import com.chris.whisperloom.ui.components.DisclosureKind
import com.chris.whisperloom.ui.components.rememberDisclosureGate
import com.chris.whisperloom.ui.components.InfoCard
import com.chris.whisperloom.ui.components.KeyboardRows
import com.chris.whisperloom.ui.components.OutlinedSection
import com.chris.whisperloom.ui.components.StatusChip
import com.chris.whisperloom.ui.components.StepBadge
import com.chris.whisperloom.ui.components.SystemIntents
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.openOrSnack
import com.chris.whisperloom.ui.components.rememberPermissionRequest
import com.chris.whisperloom.ui.models.ModelListSection
import com.chris.whisperloom.ui.nav.SetupFacts
import com.chris.whisperloom.ui.nav.SetupRouter
import com.chris.whisperloom.ui.nav.StepState
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.theme.loom

/** Baut die Seite zu Schritt [step] (UX-Spec §2.2, Schritte 1–7). */
@Composable
fun buildStep(step: Int, facts: SetupFacts, actions: StepActions): StepUi = when (step) {
    SetupRouter.STEP_ENGINE -> engineStep(facts, actions)
    SetupRouter.STEP_ACCESS -> if (facts.engine == Engine.OFFLINE) modelStep(facts, actions) else accessStep(facts, actions)
    SetupRouter.STEP_MIC -> micStep(facts, actions)
    SetupRouter.STEP_OVERLAY -> overlayStep(facts, actions)
    SetupRouter.STEP_A11Y -> a11yStep(facts, actions)
    SetupRouter.STEP_NOTIF -> notifStep(facts, actions)
    else -> keyboardStep(facts, actions)
}

@Composable
private fun nextAction(actions: StepActions, enabled: Boolean = true) = StepAction(
    label = stringResource(R.string.setup_next),
    enabled = enabled,
    trailingIcon = R.drawable.ic_arrow_forward,
    onClick = actions.next,
)

@Composable
private fun skipAction(actions: StepActions, mark: () -> Unit) = StepAction(stringResource(R.string.setup_skip)) {
    mark()
    actions.next()
}

// --- Schritt 1: Erkennungsweg ------------------------------------------------

@Composable
private fun engineStep(facts: SetupFacts, actions: StepActions): StepUi {
    val env = LocalAppEnv.current
    val prefs = env.prefs
    return StepUi(
        icon = R.drawable.ic_graphic_eq,
        title = stringResource(R.string.setup_s1_title),
        body = stringResource(R.string.setup_s1_body),
        state = SetupRouter.stepState(SetupRouter.STEP_ENGINE, facts),
        primary = nextAction(actions, enabled = facts.engine != null),
    ) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EngineOption(
                selected = prefs.engine == Engine.ONLINE,
                enabled = true,
                icon = R.drawable.ic_cloud,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.setup_s1_online),
                body = stringResource(R.string.setup_s1_online_body),
                badge = stringResource(R.string.setup_s1_recommended),
                unavailable = null,
            ) { prefs.engine = Engine.ONLINE }
            EngineOption(
                selected = prefs.engine == Engine.OFFLINE,
                enabled = env.status.offlineSupported,
                icon = R.drawable.ic_offline_bolt,
                iconTint = MaterialTheme.colorScheme.tertiary,
                title = stringResource(R.string.setup_s1_offline),
                body = stringResource(R.string.setup_s1_offline_body),
                badge = null,
                unavailable = if (env.status.offlineSupported) null else stringResource(R.string.setup_s1_offline_unavailable),
            ) { prefs.engine = Engine.OFFLINE }
        }
    }
}

/** Auswaehlbare Karte mit Radio-Semantik: 2 dp primary bei Auswahl, sonst 1 dp outlineVariant. */
@Composable
private fun EngineOption(
    selected: Boolean,
    enabled: Boolean,
    icon: Int,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    body: String,
    badge: String?,
    unavailable: String?,
    onClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        shape = CardShape,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            Modifier
                .padding(20.dp)
                .heightIn(min = 56.dp)
                .alpha(if (enabled) 1f else 0.38f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            LoomIcon(icon, null, Modifier.size(28.dp), iconTint)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (badge != null) Text(badge, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (unavailable != null) {
                    StatusChip(unavailable, R.drawable.ic_warning, MaterialTheme.loom.warningContainer, MaterialTheme.loom.onWarningContainer)
                }
            }
        }
    }
}

// --- Schritt 2a/2b: Zugang oder Modell ---------------------------------------

@Composable
private fun accessStep(facts: SetupFacts, actions: StepActions): StepUi = StepUi(
    icon = R.drawable.ic_key,
    title = stringResource(R.string.setup_s2a_title),
    body = stringResource(R.string.setup_s2a_body),
    state = SetupRouter.stepState(SetupRouter.STEP_ACCESS, facts),
    primary = nextAction(actions, enabled = SetupRouter.recognitionReady(facts)),
) {
    SttAccessSection(actions.snack)
}

@Composable
private fun modelStep(facts: SetupFacts, actions: StepActions): StepUi = StepUi(
    icon = R.drawable.ic_download,
    title = stringResource(R.string.setup_s2b_title),
    body = stringResource(R.string.setup_s2b_body),
    state = SetupRouter.stepState(SetupRouter.STEP_ACCESS, facts),
    primary = nextAction(actions, enabled = facts.modelInstalled),
) {
    ModelListSection(actions.snack, showEmptyState = false)
}

// --- Schritt 3: Mikrofon ------------------------------------------------------

@Composable
private fun micStep(facts: SetupFacts, actions: StepActions): StepUi {
    val mic = rememberPermissionRequest(Manifest.permission.RECORD_AUDIO)
    // Prominent Disclosure vor dem System-Permission-Dialog (Play-Pflicht, RECORD_AUDIO).
    val micGate = rememberDisclosureGate(DisclosureKind.MICROPHONE, onAccept = mic.request)
    val denied = mic.deniedPermanently && !facts.micGranted
    return StepUi(
        icon = R.drawable.ic_mic,
        title = stringResource(R.string.setup_s3_title),
        body = stringResource(if (denied) R.string.setup_s3_denied else R.string.setup_s3_body),
        state = SetupRouter.stepState(SetupRouter.STEP_MIC, facts),
        primary = when {
            facts.micGranted -> nextAction(actions)
            denied -> StepAction(stringResource(R.string.setup_open_app_settings), onClick = mic.request)
            else -> StepAction(stringResource(R.string.setup_s3_btn), onClick = micGate.request)
        },
    )
}

// --- Schritt 4: Ueber anderen Apps anzeigen ----------------------------------

@Composable
private fun overlayStep(facts: SetupFacts, actions: StepActions): StepUi {
    val ctx = LocalContext.current
    val prefs = LocalAppEnv.current.prefs
    val state = SetupRouter.stepState(SetupRouter.STEP_OVERLAY, facts)
    return StepUi(
        icon = R.drawable.ic_layers,
        title = stringResource(R.string.setup_s4_title),
        body = stringResource(R.string.setup_s4_body),
        state = state,
        primary = if (state == StepState.OPEN) {
            StepAction(stringResource(R.string.setup_s4_btn)) { openOrSnack(ctx, SystemIntents.overlay(ctx), actions.snack) }
        } else {
            nextAction(actions)
        },
    ) {
        MoreInfoCard(listOf(R.string.setup_s4_step_1, R.string.setup_s4_step_2, R.string.setup_s4_step_3))
        if (!facts.overlayGranted) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    prefs.overlaySkipped = true
                    actions.next()
                }) { Text(stringResource(R.string.setup_s4_keyboard_only)) }
                Text(
                    stringResource(R.string.setup_s4_keyboard_only_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Aufklappbare "Was passiert dabei?"-Karte mit nummerierten Zeilen. */
@Composable
private fun MoreInfoCard(lines: List<Int>) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedSection(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp), gap = 0.dp) {
        TextButton(onClick = { open = !open }) { Text(stringResource(R.string.setup_more_info)) }
        AnimatedVisibility(visible = open) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEachIndexed { i, res ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StepBadge(i + 1)
                        Text(stringResource(res), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

// --- Schritt 5: Bedienungshilfe ----------------------------------------------

@Composable
private fun a11yStep(facts: SetupFacts, actions: StepActions): StepUi {
    val ctx = LocalContext.current
    val prefs = LocalAppEnv.current.prefs
    // Prominent Disclosure vor dem Öffnen der Bedienungshilfe-Einstellungen (Play-Pflicht, a11y).
    val a11yGate = rememberDisclosureGate(
        DisclosureKind.ACCESSIBILITY,
        onAccept = { openOrSnack(ctx, SystemIntents.accessibility(), actions.snack) },
    )
    return StepUi(
        icon = R.drawable.ic_accessibility_new,
        title = stringResource(R.string.setup_s5_title),
        body = stringResource(R.string.a11y_description),
        state = SetupRouter.stepState(SetupRouter.STEP_A11Y, facts),
        primary = if (facts.a11yRunning) {
            nextAction(actions)
        } else {
            StepAction(stringResource(R.string.setup_s5_btn)) { a11yGate.request() }
        },
        secondary = if (facts.a11yRunning) null else skipAction(actions) { prefs.a11ySkipped = true },
    ) {
        InfoCard(stringResource(R.string.setup_s5_fallback))
        MoreInfoCard(listOf(R.string.setup_s5_step_1, R.string.setup_s5_step_2, R.string.setup_s5_step_3))
    }
}

// --- Schritt 6: Benachrichtigungen (API >= 33) --------------------------------

@Composable
private fun notifStep(facts: SetupFacts, actions: StepActions): StepUi {
    val prefs = LocalAppEnv.current.prefs
    val notif = rememberPermissionRequest(POST_NOTIFICATIONS)
    return StepUi(
        icon = R.drawable.ic_notifications,
        title = stringResource(R.string.setup_s6_title),
        body = stringResource(R.string.setup_s6_body),
        state = SetupRouter.stepState(SetupRouter.STEP_NOTIF, facts),
        primary = when {
            facts.notifGranted -> nextAction(actions)
            notif.deniedPermanently -> StepAction(stringResource(R.string.setup_open_app_settings), onClick = notif.request)
            else -> StepAction(stringResource(R.string.setup_s6_btn), onClick = notif.request)
        },
        secondary = if (facts.notifGranted) null else skipAction(actions) { prefs.notifSkipped = true },
    )
}

/** Manifest.permission.POST_NOTIFICATIONS gibt es erst in API 33 — als Konstante, minSdk ist 26. */
private const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

// --- Schritt 7: Diktat-Tastatur ----------------------------------------------

@Composable
private fun keyboardStep(facts: SetupFacts, actions: StepActions): StepUi {
    val ctx = LocalContext.current
    val prefs = LocalAppEnv.current.prefs
    val mandatory = SetupRouter.isMandatory(SetupRouter.STEP_KEYBOARD, facts)
    return StepUi(
        icon = R.drawable.ic_keyboard,
        title = stringResource(R.string.setup_s7_title),
        body = stringResource(R.string.setup_s7_body),
        state = SetupRouter.stepState(SetupRouter.STEP_KEYBOARD, facts),
        primary = if (facts.imeEnabled) {
            nextAction(actions)
        } else {
            StepAction(stringResource(R.string.setup_s7_enable_btn)) { openOrSnack(ctx, SystemIntents.inputMethods(), actions.snack) }
        },
        secondary = if (facts.imeEnabled || mandatory) null else skipAction(actions) { prefs.keyboardSkipped = true },
    ) {
        KeyboardRows(tryFieldMinLines = 2, snack = actions.snack)
    }
}
