package com.chris.whisperloom.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R
import com.chris.whisperloom.overlay.FloatingMicService
import com.chris.whisperloom.ui.components.PrimaryButton
import com.chris.whisperloom.ui.components.SmallTopBar
import com.chris.whisperloom.ui.components.StepStateChip
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.findActivity
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SetupFacts
import com.chris.whisperloom.ui.nav.SetupRouter
import com.chris.whisperloom.ui.state.LocalAppEnv

/**
 * W — Einrichtungs-Assistent (UX-Spec §2.2): Rahmen (App-Bar, Fortschritt), Seiten per
 * AnimatedContent (W1, Schritte 1–7, W9), Bottom-Bar je Seite, Schritt-Uebersicht (W-Ue).
 */
@Composable
fun SetupScreen(step: Int, nav: NavState) {
    val ctx = LocalContext.current
    val env = LocalAppEnv.current
    val prefs = env.prefs
    val facts = SetupFacts.from(prefs, env.status)
    val snack = rememberSnack()
    var showOverview by rememberSaveable { mutableStateOf(false) }

    // Schritt 2 ohne gewaehlten Erkennungsweg (Deep-Link) -> Schritt 1.
    val shown = if (step == SetupRouter.STEP_ACCESS && prefs.engine == null) SetupRouter.STEP_ENGINE else step
    val inSteps = shown in SetupRouter.STEP_ENGINE..SetupRouter.STEP_KEYBOARD
    val prev: Int? = when (shown) {
        Screen.Setup.WELCOME -> null
        Screen.Setup.DONE -> SetupRouter.visibleSteps(facts).last()
        else -> SetupRouter.previous(shown, facts)
    }
    val go: (Int) -> Unit = { nav.replaceTop(Screen.Setup(it)) }
    val close: () -> Unit = { if (nav.canPop) nav.pop() else ctx.findActivity()?.finish() }

    // Zurueck = Schritt zurueck; auf dem ersten sichtbaren Schritt greift der App-Handler (Home) bzw. das System.
    BackHandler(enabled = prev != null) { prev?.let(go) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SmallTopBar(
                title = stringResource(R.string.setup_title),
                titleStyle = MaterialTheme.typography.titleMedium,
                navIcon = if (prev != null) R.drawable.ic_arrow_back else R.drawable.ic_close,
                navContentDescription = stringResource(if (prev != null) R.string.cd_back else R.string.cd_close),
                onNav = { if (prev != null) go(prev) else close() },
            ) {
                if (inSteps) {
                    IconButton(onClick = { showOverview = true }) {
                        LoomIcon(R.drawable.ic_checklist, stringResource(R.string.setup_cd_overview))
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snack.host) },
    ) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
            if (inSteps) ProgressRow(shown, facts)
            AnimatedContent(
                targetState = shown,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState > initialState
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(300)) { dir * it * 3 / 10 } + fadeIn(tween(300)))
                        .togetherWith(slideOutHorizontally(tween(300)) { -dir * it * 3 / 10 } + fadeOut(tween(300)))
                },
                label = "step",
            ) { s ->
                when (s) {
                    Screen.Setup.WELCOME -> WelcomePage(
                        onStart = {
                            prefs.welcomeSeen = true
                            go(SetupRouter.firstOpenStep(facts))
                        },
                    )
                    Screen.Setup.DONE -> DonePage(
                        facts = facts,
                        onGoToStep = go,
                        onFinish = {
                            val startBubble = !prefs.overlaySkipped && env.status.canDrawOverlays
                            if (prefs.tutorialSeen) {
                                if (startBubble) FloatingMicService.start(ctx)
                                nav.replaceAll(Screen.Home)
                            } else {
                                // Nach der Einrichtung einmal das Tutorial; Home liegt darunter, Beenden fuehrt dorthin.
                                // Der Knopf startet erst nach dem Tutorial (WhisperLoomApp), sonst schwebt er darueber.
                                nav.replaceAll(Screen.Home, Screen.Tutorial(startBubbleAfter = startBubble))
                            }
                        },
                    )
                    else -> {
                        val actions = StepActions(snack) { go(SetupRouter.next(s, facts) ?: Screen.Setup.DONE) }
                        StepPage(
                            ui = buildStep(s, facts, actions),
                            showBack = prev != null,
                            onBack = { prev?.let(go) },
                        )
                    }
                }
            }
        }
    }

    if (showOverview) {
        SetupOverviewSheet(
            facts = facts,
            onDismiss = { showOverview = false },
            onGoTo = {
                showOverview = false
                go(it)
            },
        )
    }
}

/** Fortschrittszeile: 6-dp-Balken (erledigt/gesamt) + "Schritt x von y". */
@Composable
private fun ProgressRow(step: Int, facts: SetupFacts) {
    val total = SetupRouter.visibleSteps(facts).size
    val progress by animateFloatAsState(SetupRouter.doneCount(facts).toFloat() / total, tween(300), label = "progress")
    Column(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
        )
        Text(
            stringResource(R.string.setup_progress, SetupRouter.position(step, facts), total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Eine Schritt-Seite: Icon-Kreis, Titel, Erklaerung, Status-Chip, Inhalt; darunter die Bottom-Bar. */
@Composable
private fun StepPage(ui: StepUi, showBack: Boolean, onBack: () -> Unit) {
    // imePadding aussen: die Bottom-Bar sitzt ueber der Tastatur, und ihr navigationBarsPadding
    // greift dann nicht doppelt (die Tastatur-Insets enthalten die Navigationsleiste schon).
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                LoomIcon(ui.icon, null, Modifier.size(36.dp), MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(ui.title, style = MaterialTheme.typography.headlineSmall)
            Text(ui.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Chip morpht offen -> erledigt (Crossfade + Skalierung 0,6 -> 1,0, Spec §5.4).
            AnimatedContent(
                targetState = ui.state,
                transitionSpec = {
                    (fadeIn(tween(250)) + scaleIn(initialScale = 0.6f, animationSpec = tween(250)))
                        .togetherWith(fadeOut(tween(250)))
                },
                label = "chip",
            ) { StepStateChip(it) }
            ui.content(this)
        }
        SetupBottomBar(ui, showBack, onBack)
    }
}

@Composable
private fun SetupBottomBar(ui: StepUi, showBack: Boolean, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (showBack) {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.setup_back)) }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.secondary?.let { TextButton(onClick = it.onClick, enabled = it.enabled) { Text(it.label) } }
                    PrimaryButton(
                        text = if (ui.primary.working) stringResource(R.string.setup_working) else ui.primary.label,
                        onClick = ui.primary.onClick,
                        enabled = ui.primary.enabled,
                        working = ui.primary.working,
                        trailingIcon = ui.primary.trailingIcon,
                        fillWidth = false,
                    )
                }
            }
        }
    }
}
