package com.chris.whisperloom.ui.access

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R
import com.chris.whisperloom.ui.components.StatusChip
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.theme.loom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FilledTonalButton "Zugang pruefen" + Ergebnis-Chip (AnimatedVisibility, Live-Region).
 * [test] laeuft auf Dispatchers.IO.
 */
@Composable
fun TestAccessRow(label: String, enabled: Boolean = true, test: () -> AccessTest.Outcome) {
    var working by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<AccessTest.Outcome?>(null) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            onClick = {
                working = true
                outcome = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) { test() }
                    outcome = result
                    working = false
                }
            },
            enabled = enabled && !working,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            if (working) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                LoomIcon(R.drawable.ic_verified, null, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
        AnimatedVisibility(visible = outcome != null) {
            outcome?.let { ResultChip(it) }
        }
    }
}

@Composable
private fun ResultChip(outcome: AccessTest.Outcome) {
    when (outcome) {
        is AccessTest.Outcome.Ok -> StatusChip(
            stringResource(R.string.rec_test_ok, AccessTest.seconds(outcome.millis)),
            R.drawable.ic_check_circle,
            MaterialTheme.loom.successContainer,
            MaterialTheme.loom.onSuccessContainer,
            live = true,
        )
        is AccessTest.Outcome.Failed -> StatusChip(
            stringResource(R.string.rec_test_fail, failureText(outcome)),
            R.drawable.ic_error,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            live = true,
        )
    }
}

@Composable
fun failureText(f: AccessTest.Outcome.Failed): String = when (f.kind) {
    AccessTest.Kind.UNAUTHORIZED -> stringResource(if (f.code == 403) R.string.err_403 else R.string.err_401)
    AccessTest.Kind.RATE_LIMIT -> stringResource(R.string.err_429)
    AccessTest.Kind.SERVER -> stringResource(R.string.err_server, f.code)
    AccessTest.Kind.TIMEOUT -> stringResource(R.string.err_timeout)
    AccessTest.Kind.NETWORK -> stringResource(R.string.err_net)
    AccessTest.Kind.UNKNOWN -> stringResource(R.string.err_unknown, f.message)
}
