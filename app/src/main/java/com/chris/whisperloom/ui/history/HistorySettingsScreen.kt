package com.chris.whisperloom.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.history.History
import com.chris.whisperloom.ui.components.InfoCard
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.SectionCard
import com.chris.whisperloom.ui.components.SwitchRow
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.settings.SettingsPageScaffold
import com.chris.whisperloom.ui.state.LocalAppEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Innenabstand der Karten, die nur aus Zeilen bestehen (die Zeilen bringen 8 dp mit). */
private val RowCardPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)

/**
 * Verlauf-Einstellungen (Plan §6.6, ueber ⋮ im Verlauf): speichern an/aus (aus loescht alles, E7),
 * Groesse 10–500 (Verkleinern fragt, wie viele der aeltesten gehen), alles loeschen. Geaendert wird
 * nur ueber [History] — die Prefs-Spiegel sind nur zum Lesen.
 */
@Composable
fun HistorySettingsScreen(nav: NavState) {
    val ctx = LocalContext.current
    val prefs = LocalAppEnv.current.prefs
    val scope = rememberCoroutineScope()
    val changes by History.changes.collectAsStateWithLifecycle()
    // null, solange noch nicht gezaehlt: dann fragt auch das Ausschalten.
    val count by produceState<Int?>(null, changes) { value = withContext(Dispatchers.IO) { History.count(ctx) } }
    var confirmOff by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    // Verkleinern: Zielgroesse und wie viele dabei gehen (beides fuer die Rueckfrage).
    var shrinkTo by rememberSaveable { mutableIntStateOf(0) }
    var shrinkBy by rememberSaveable { mutableIntStateOf(0) }
    val io: (() -> Unit) -> Unit = { work -> scope.launch(Dispatchers.IO) { work() } }

    SettingsPageScaffold(R.string.history_settings_title, nav) {
        SectionCard(contentPadding = RowCardPadding) {
            SwitchRow(
                headline = stringResource(R.string.history_save_switch),
                supporting = stringResource(R.string.history_save_switch_sub),
                checked = prefs.historyEnabled,
                onCheckedChange = { on ->
                    if (on || count == 0) io { History.setEnabled(ctx, on) } else confirmOff = true
                },
            )
        }
        SectionCard(title = stringResource(R.string.history_size), gap = 4.dp) {
            Column(Modifier.selectableGroup()) {
                Prefs.HISTORY_SIZES.forEach { size ->
                    val selected = size == prefs.historySize
                    LoomRow(
                        headline = pluralStringResource(R.plurals.history_entries, size, size),
                        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) {
                            if (!selected) {
                                scope.launch {
                                    val excess = withContext(Dispatchers.IO) { History.excess(ctx, size) }
                                    if (excess > 0) {
                                        shrinkBy = excess
                                        shrinkTo = size
                                    } else {
                                        io { History.setSize(ctx, size) }
                                    }
                                }
                            }
                        },
                        trailing = { RadioButton(selected = selected, onClick = null) },
                    )
                }
            }
        }
        SectionCard(contentPadding = RowCardPadding) {
            LoomRow(
                headline = stringResource(R.string.history_clear),
                supporting = count?.let { pluralStringResource(R.plurals.history_entries, it, it) },
                onClick = { confirmClear = true },
                enabled = (count ?: 0) > 0,
            )
        }
        InfoCard(stringResource(R.string.history_settings_hint), icon = R.drawable.ic_privacy_tip)
    }

    if (confirmOff) {
        ConfirmDialog(
            title = stringResource(R.string.history_off_title),
            body = stringResource(R.string.history_off_confirm_body),
            confirm = stringResource(R.string.history_off_confirm),
            onConfirm = {
                confirmOff = false
                io { History.setEnabled(ctx, false) }
            },
            onDismiss = { confirmOff = false },
        )
    }
    if (shrinkTo > 0) {
        ConfirmDialog(
            title = stringResource(R.string.history_shrink_title),
            body = pluralStringResource(R.plurals.history_shrink_body, shrinkBy, shrinkBy),
            confirm = stringResource(R.string.history_shrink_confirm),
            onConfirm = {
                val size = shrinkTo
                shrinkTo = 0
                io { History.setSize(ctx, size) }
            },
            onDismiss = { shrinkTo = 0 },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.history_clear_title),
            body = stringResource(R.string.history_clear_body),
            confirm = stringResource(R.string.history_clear),
            onConfirm = {
                confirmClear = false
                io { History.clear(ctx) }
            },
            onDismiss = { confirmClear = false },
        )
    }
}
