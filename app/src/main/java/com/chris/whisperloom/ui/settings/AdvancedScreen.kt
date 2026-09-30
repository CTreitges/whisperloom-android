package com.chris.whisperloom.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.chris.whisperloom.ProFeature
import com.chris.whisperloom.R
import com.chris.whisperloom.agent.VoiceTaskWidget
import com.chris.whisperloom.ui.components.DetailScaffold
import com.chris.whisperloom.ui.components.GuideHeader
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.ScrollColumn
import com.chris.whisperloom.ui.components.SectionCard
import com.chris.whisperloom.ui.components.SwitchRow
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.WidgetTab
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.WidgetProfilesState
import com.chris.whisperloom.ui.tutorial.TutorialKind

/**
 * E7 — "Erweitert": schaltet die Pro-/Entwickler-Funktionen frei ([ProFeature]: Pro Widgets,
 * Stufe "Prompt").
 *
 * Bewusst der einzige Ort, an dem sie sich einschalten lassen: Startbildschirm, Assistent und
 * Home-Status bleiben unangetastet. Wer sie nicht nutzt, soll sie nicht bemerken. Alles zu den
 * Pro Widgets selbst (Server, Mikrofon, offener Auftrag) steht im Widget-Menue, Tab "Pro Widgets".
 * Einzige Ausnahme: sind Pro Widgets aus, fehlt der Tab — ein wartender Auftrag laesst sich dann
 * hier verwerfen (wie bis 3.7.0).
 */
@Composable
fun AdvancedScreen(nav: NavState) {
    val ctx = LocalContext.current
    val prefs = LocalAppEnv.current.prefs
    val snack = rememberSnack()
    val widgets = remember { WidgetProfilesState(ctx) }

    // Die Widgets zeigen "aus", solange Pro Widgets aus sind — nach jedem Umschalten neu zeichnen.
    // Und den offenen Auftrag neu lesen: er kann entstanden sein, waehrend "Erweitert" offen lag.
    LaunchedEffect(prefs.proWidgetsEnabled) {
        VoiceTaskWidget.refresh(ctx)
        widgets.reload()
    }

    // Ein Auftrag entsteht oder endet, waehrend die App im Hintergrund ist (wie im Widget-Menue).
    LifecycleResumeEffect(widgets) {
        widgets.reload()
        onPauseOrDispose { }
    }

    DetailScaffold(title = stringResource(R.string.settings_group_advanced), onBack = { nav.pop() }, snack = snack) { padding ->
        ScrollColumn(padding) {
            GuideHeader(R.drawable.ill_pro_features, R.string.img_pro_features, text = stringResource(R.string.advanced_intro))

            SectionCard(title = stringResource(R.string.advanced_card_features), gap = 4.dp) {
                ProFeature.entries.forEach { f ->
                    SwitchRow(
                        headline = stringResource(f.title),
                        supporting = stringResource(f.sub),
                        checked = prefs.isEnabled(f),
                        onCheckedChange = { prefs.setEnabled(f, it) },
                    )
                }
            }

            if (!prefs.proWidgetsEnabled && widgets.hasWork) PendingTaskCard(widgets, snack)

            if (prefs.proWidgetsEnabled) {
                SectionCard(gap = 4.dp) {
                    NavRow(R.drawable.ic_layers, stringResource(R.string.advanced_manage_widgets)) {
                        nav.push(Screen.Widgets(WidgetTab.PRO))
                    }
                    NavRow(R.drawable.ic_help, stringResource(R.string.advanced_tutorial)) {
                        nav.push(Screen.Tutorial(kind = TutorialKind.PRO_WIDGETS))
                    }
                }
            }
        }
    }
}

/** Zeile mit Symbol und Pfeil, die woandershin fuehrt. */
@Composable
private fun NavRow(@DrawableRes icon: Int, headline: String, onClick: () -> Unit) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    LoomRow(
        headline = headline,
        leading = { LoomIcon(icon, null, Modifier.size(24.dp), tint) },
        trailing = { LoomIcon(R.drawable.ic_chevron_right, null, Modifier.size(24.dp), tint) },
        onClick = onClick,
    )
}
