package com.chris.whisperloom.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.chris.whisperloom.R

/** Link im Browser oeffnen; ohne Browser Snackbar "Kein Browser gefunden" + "Link kopieren" (Spec §2.8). */
fun openLink(ctx: Context, url: String, snack: SnackController) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        snack.show(ctx.getString(R.string.err_no_browser), ctx.getString(R.string.common_copy_link)) {
            copyToClipboard(ctx, url)
        }
    }
}

fun copyToClipboard(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("WhisperLoom", text))
}

/** Zeile mit Trailing open_in_new, oeffnet [url]. */
@Composable
fun LinkRow(headline: String, url: String, snack: SnackController, supporting: String? = null) {
    val ctx = LocalContext.current
    LoomRow(
        headline = headline,
        supporting = supporting,
        trailing = {
            LoomIcon(
                R.drawable.ic_open_in_new,
                stringResource(R.string.cd_open_link),
                Modifier.size(24.dp),
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = { openLink(ctx, url, snack) },
    )
}
