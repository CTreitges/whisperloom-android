package com.chris.whisperloom.ui.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.ui.components.DetailScaffold
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.ScrollColumn
import com.chris.whisperloom.ui.components.SnackController
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.state.PrefsState

/** Geruest einer Einstellungsseite: grosser Titel, Zurueck, scrollende Spalte. */
@Composable
internal fun SettingsPageScaffold(title: Int, nav: NavState, content: @Composable ColumnScope.(SnackController) -> Unit) {
    val snack = rememberSnack()
    DetailScaffold(title = stringResource(title), onBack = { nav.pop() }, snack = snack) { padding ->
        ScrollColumn(padding) { content(snack) }
    }
}

/** Zeile in einer Karte, die eine andere Seite oeffnet: Icon, Seitentitel, aktueller Wert, Pfeil. */
@Composable
internal fun PageLinkRow(icon: Int, headline: String, value: String, onClick: () -> Unit) {
    LoomRow(
        headline = headline,
        supporting = value,
        leading = { LoomIcon(icon, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
        trailing = { LoomIcon(R.drawable.ic_chevron_right, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
        onClick = onClick,
    )
}

/** Irgendeine KI-Stufe an (Diktat oder Sprachnachrichten) — dann wirken KI-Fuellwoerter und die Offline-Regel. */
internal fun anyAiStage(prefs: PrefsState): Boolean =
    prefs.refineMode != RefineMode.OFF || prefs.shareRefineMode != RefineMode.OFF
