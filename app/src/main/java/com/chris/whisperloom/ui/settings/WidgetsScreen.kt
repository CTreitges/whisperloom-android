package com.chris.whisperloom.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.chris.whisperloom.R
import com.chris.whisperloom.agent.ProfileIcon
import com.chris.whisperloom.agent.WidgetIcons
import com.chris.whisperloom.agent.WidgetPhoto
import com.chris.whisperloom.agent.WidgetProfile
import com.chris.whisperloom.ui.components.DetailScaffold
import com.chris.whisperloom.ui.components.InfoCard
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.LoomSheet
import com.chris.whisperloom.ui.components.ScrollColumn
import com.chris.whisperloom.ui.components.SectionCard
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.WidgetProfilesState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Untermenue "Widgets": Profile anlegen und bearbeiten ([WidgetProfileSheet]), jedem Widget auf
 * dem Startbildschirm ein Profil zuordnen, kurze Hilfe. Immer im Hub erreichbar; ist der
 * Sprachauftrag nicht eingerichtet, steht oben ein Hinweis mit dem Weg dorthin.
 */
@Composable
fun WidgetsScreen(nav: NavState) {
    val ctx = LocalContext.current
    val prefs = LocalAppEnv.current.prefs
    val snack = rememberSnack()
    val widgets = remember { WidgetProfilesState(ctx) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf<Int?>(null) }
    val newName = stringResource(R.string.widget_profile_default_name, widgets.profiles.size + 1)

    // Widgets kommen auf dem Startbildschirm dazu oder verschwinden, waehrend die App im Hintergrund ist.
    LifecycleResumeEffect(widgets) {
        widgets.reload()
        onPauseOrDispose { }
    }

    DetailScaffold(title = stringResource(R.string.settings_group_widgets), onBack = { nav.pop() }, snack = snack) { padding ->
        ScrollColumn(padding) {
            if (!prefs.agentReady) {
                InfoCard(
                    text = stringResource(R.string.widgets_agent_off),
                    action = {
                        TextButton(onClick = { nav.push(Screen.Agent) }) { Text(stringResource(R.string.settings_group_agent)) }
                    },
                )
            }

            SectionCard(title = stringResource(R.string.widgets_card_profiles), gap = 4.dp) {
                widgets.profiles.forEach { p ->
                    LoomRow(
                        headline = p.displayName(ctx),
                        supporting = modeText(p),
                        leading = { ProfileIconView(p) },
                        onClick = { editing = p.id },
                    )
                }
                LoomRow(
                    headline = stringResource(R.string.widgets_add_profile),
                    leading = { LoomIcon(R.drawable.ic_add, null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary) },
                    onClick = { editing = widgets.create(newName).id },
                )
            }

            if (widgets.placed.isNotEmpty()) {
                SectionCard(title = stringResource(R.string.widgets_card_placed), gap = 4.dp) {
                    widgets.placed.forEachIndexed { i, w ->
                        LoomRow(
                            headline = stringResource(R.string.widgets_placed_row, i + 1, w.profile.displayName(ctx)),
                            supporting = stringResource(R.string.widgets_placed_sub),
                            leading = { ProfileIconView(w.profile) },
                            onClick = { picking = w.widgetId },
                        )
                    }
                }
            }

            SectionCard(title = stringResource(R.string.widgets_card_help), gap = 4.dp) {
                listOf(R.string.agent_widget_hint, R.string.widgets_help_size, R.string.widgets_help_shared).forEach {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    editing?.let { id -> WidgetProfileSheet(widgets, id) { editing = null } }

    // Verschwindet das Widget waehrenddessen vom Startbildschirm, schliesst sich die Auswahl.
    widgets.placed.firstOrNull { it.widgetId == picking }?.let { w ->
        LoomSheet(title = stringResource(R.string.widget_config_title), onDismiss = { picking = null }) { dismiss ->
            ProfilePicker(widgets.profiles, selected = w.profile.id) {
                widgets.bind(w.widgetId, it)
                dismiss()
            }
        }
    }
}

/** "Tippen startet und stoppt" oder "Stoppt nach Sprechpause · Normal". */
@Composable
fun modeText(p: WidgetProfile): String =
    if (p.autoStop) stringResource(R.string.widget_mode_autostop, stringResource(pauseLabel(p.pause)))
    else stringResource(R.string.widget_mode_toggle)

/**
 * Profilliste mit Auswahlknopf; ein Tipp waehlt. Auch fuer die Profilwahl beim Platzieren
 * eines Widgets gedacht.
 */
@Composable
fun ProfilePicker(profiles: List<WidgetProfile>, selected: String, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    Column {
        profiles.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LoomRow(
                headline = p.displayName(ctx),
                supporting = modeText(p),
                leading = { ProfileIconView(p) },
                trailing = { RadioButton(selected = p.id == selected, onClick = null) },
                modifier = Modifier.selectable(selected = p.id == selected, role = Role.RadioButton) { onPick(p.id) },
            )
        }
    }
}

/** Symbol eines Profils im Kreis: eingebautes Symbol eingefaerbt, Foto rund. */
@Composable
fun ProfileIconView(p: WidgetProfile, size: Dp = 40.dp) {
    val photo = (p.icon as? ProfileIcon.Photo)?.fileName
    val bitmap = rememberPhoto(photo)
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap, null, Modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            val icon = WidgetIcons.of((p.icon as? ProfileIcon.BuiltIn)?.key ?: WidgetIcons.DEFAULT_KEY)
            LoomIcon(icon.drawable, null, Modifier.size(size * 0.6f), MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Das gespeicherte Foto, abseits des Main-Threads geladen; null solange es laedt oder fehlt. */
@Composable
fun rememberPhoto(fileName: String?): ImageBitmap? {
    val ctx = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, fileName) {
        value = fileName?.let { withContext(Dispatchers.IO) { WidgetPhoto.load(ctx, it)?.asImageBitmap() } }
    }
    return bitmap
}
