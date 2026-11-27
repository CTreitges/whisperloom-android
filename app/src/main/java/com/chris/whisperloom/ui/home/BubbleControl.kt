package com.chris.whisperloom.ui.home

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.chris.whisperloom.R
import com.chris.whisperloom.overlay.FloatingMicService
import com.chris.whisperloom.ui.components.SnackController
import com.chris.whisperloom.ui.state.LocalAppEnv
import kotlinx.coroutines.delay

/**
 * Start/Beenden des schwebenden Knopfs (Home-Hero und E3, Spec §2.1): PENDING max. 500 ms,
 * danach Status neu lesen. Entzogenes Overlay -> Snackbar mit Aktion [onOverlayLost].
 */
class BubbleControl(
    val running: Boolean,
    val pending: Boolean,
    val blocked: HomeStatus.Blocked,
    val toggle: () -> Unit,
)

@Composable
fun rememberBubbleControl(snack: SnackController, onOverlayLost: () -> Unit): BubbleControl {
    val ctx = LocalContext.current
    val env = LocalAppEnv.current
    val status = env.status
    var pending by remember { mutableStateOf(false) }
    LaunchedEffect(pending) {
        if (pending) {
            delay(PENDING_MS)
            env.refreshStatus()
            pending = false
        }
    }
    val overlayLost = stringResource(R.string.home_snack_overlay_lost)
    val fix = stringResource(R.string.home_snack_fix)
    return BubbleControl(
        running = status.bubbleRunning,
        pending = pending,
        blocked = HomeStatus.blocked(status.micGranted, status.canDrawOverlays, status.a11yRunning),
    ) {
        if (status.bubbleRunning) {
            FloatingMicService.stop(ctx)
        } else {
            if (!Settings.canDrawOverlays(ctx)) {
                snack.show(overlayLost, fix, onOverlayLost)
                env.refreshStatus()
                return@BubbleControl
            }
            FloatingMicService.start(ctx)
        }
        pending = true
    }
}

private const val PENDING_MS = 500L
