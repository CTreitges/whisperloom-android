package com.chris.whisperloom.agent

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.ui.components.InfoCard
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.LoomSheet
import com.chris.whisperloom.ui.settings.ProfilePicker
import com.chris.whisperloom.ui.settings.WidgetProfileSheet
import com.chris.whisperloom.ui.state.WidgetProfilesState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme

/**
 * Profilwahl beim Platzieren eines Widgets und beim "Neu konfigurieren" (ab Android 12: Widget
 * lange druecken). Unter Android 12 startet der Launcher sie bei jedem Hinzufuegen.
 *
 * - Das Ergebnis steht zuerst auf RESULT_CANCELED: Zurueck heisst beim Platzieren "kein Widget",
 *   beim Neu-Konfigurieren "nichts aendern".
 * - Sie ist exported (der Launcher startet sie von aussen) und nimmt deshalb nur Ids an, die
 *   wirklich zu [VoiceTaskWidget] gehoeren.
 * - Erstplatzierung mit nur dem Standardprofil: unsichtbar binden und fertig — es gibt nichts zu
 *   waehlen. Sonst das Sheet "Welches Widget-Profil?", beim Neu-Konfigurieren immer, vorgewaehlt
 *   ist das Profil, das die Instanz gerade zeigt. Zur Wahl stehen nur Sprach-Command-Profile.
 * - "Ungebunden" heisst Erstplatzierung: Widgets, die schon vor den Profilen lagen, bindet
 *   [VoiceTaskWidget] nach dem App-Update an das Standardprofil ([WidgetProfileStore.adopt]).
 *
 * Context7 (developer.android.com/guide/topics/appwidgets/configuration): die Activity muss
 * RESULT_OK/RESULT_CANCELED mit EXTRA_APPWIDGET_ID liefern, und das System schickt nach der
 * Konfiguration KEIN APPWIDGET_UPDATE — ohne eigenes Zeichnen bliebe das initialLayout ohne
 * Tippflaeche stehen.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, result())
        if (!isOurs(widgetId)) {
            finish()
            return
        }
        val store = WidgetProfileStore(this)
        if (!store.isBound(widgetId) && store.all().size == 1) {
            pick(WidgetProfile.DEFAULT_ID)
            return
        }
        val current = store.forWidget(widgetId).id
        setContent {
            WhisperLoomTheme {
                ProfileChoice(preselected = current, onPick = ::pick, onCancel = ::finish)
            }
        }
    }

    /** Schutz der exportierten Activity: fremde oder ungueltige Ids fassen wir nicht an. */
    private fun isOurs(id: Int): Boolean =
        id != AppWidgetManager.INVALID_APPWIDGET_ID &&
            AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider ==
            ComponentName(this, VoiceTaskWidget::class.java)

    /** Binden, diese Instanz mit ihrem Profil selbst zeichnen (ohne Server: "Server fehlt"), OK melden. */
    private fun pick(profileId: String) {
        val store = WidgetProfileStore(this)
        store.bind(widgetId, profileId)
        val (state, message) = VoiceTaskWidget.resolve(this, store.forWidget(widgetId))
        VoiceTaskWidgetView.pushTo(this, AppWidgetManager.getInstance(this), intArrayOf(widgetId), state, message = message)
        setResult(RESULT_OK, result())
        finish()
    }

    private fun result(): Intent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}

/**
 * "Welches Widget-Profil?": ein Tipp auf ein Profil bestaetigt. "Neues Widget-Profil" legt es an
 * und oeffnet den Editor; danach ist das neue Profil vorgewaehlt (ein Tipp bestaetigt es).
 * Wegwischen bricht ab. Sind Pro Widgets aus, sagt ein Hinweis, wo sie sich einschalten lassen —
 * platzieren geht trotzdem, das Widget zeigt dann "aus".
 */
@Composable
private fun ProfileChoice(preselected: String, onPick: (String) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val widgets = remember { WidgetProfilesState(ctx) }
    var selected by rememberSaveable { mutableStateOf(preselected) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val proOff = remember { !Prefs(ctx).proWidgetsEnabled }

    val editId = editing
    if (editId == null) {
        LoomSheet(title = stringResource(R.string.widget_config_title), onDismiss = onCancel) {
            if (proOff) InfoCard(stringResource(R.string.widget_config_pro_off))
            ProfilePicker(widgets.profiles.filter { it.kind == WidgetKind.VOICE_COMMAND }, selected = selected, onPick = onPick)
            LoomRow(
                headline = stringResource(R.string.widgets_add_profile),
                leading = { LoomIcon(R.drawable.ic_add, null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary) },
                onClick = { editing = widgets.createNew(WidgetKind.VOICE_COMMAND).id },
            )
        }
    } else {
        WidgetProfileSheet(widgets, editId) {
            editing = null
            // Im Editor wieder geloescht: dann bleibt die bisherige Wahl.
            if (widgets.profile(editId) != null) selected = editId
        }
    }
}
