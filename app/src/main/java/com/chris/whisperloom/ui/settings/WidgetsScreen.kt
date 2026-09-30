package com.chris.whisperloom.ui.settings

import android.Manifest
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.chris.whisperloom.agent.Tier
import com.chris.whisperloom.agent.WidgetIcons
import com.chris.whisperloom.agent.WidgetKind
import com.chris.whisperloom.agent.WidgetPhoto
import com.chris.whisperloom.agent.WidgetProfile
import com.chris.whisperloom.ui.components.DetailScaffold
import com.chris.whisperloom.ui.components.DisclosureKind
import com.chris.whisperloom.ui.components.GuideHeader
import com.chris.whisperloom.ui.components.InfoCard
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.LoomSheet
import com.chris.whisperloom.ui.components.ScrollColumn
import com.chris.whisperloom.ui.components.SectionCard
import com.chris.whisperloom.ui.components.SnackController
import com.chris.whisperloom.ui.components.StatusIcon
import com.chris.whisperloom.ui.components.Tone
import com.chris.whisperloom.ui.components.rememberDisclosureGate
import com.chris.whisperloom.ui.components.rememberPermissionRequest
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.WidgetTab
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.WidgetProfilesState
import com.chris.whisperloom.ui.theme.loom
import com.chris.whisperloom.ui.tutorial.TutorialKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

/**
 * Widget-Menue mit den Tabs "Widgets" (normale Widgets — folgen, bis dahin ein Platzhalter) und
 * "Pro Widgets" (Sprach-Command-Widgets anlegen und bearbeiten ([WidgetProfileSheet]), jedem
 * Widget auf dem Startbildschirm ein Profil zuordnen, Mikrofon, offener Auftrag, Hilfe).
 * Immer im Hub erreichbar; sind Pro Widgets aus, gibt es keine Tabs, nur den Normal-Inhalt mit
 * dem Weg nach "Erweitert".
 *
 * [tab] = gewaehlter Tab (null: Pro — der Normal-Tab ist noch Platzhalter). Er steht im
 * Back-Stack statt in rememberSaveable: der Screen-Wechsel (AnimatedContent) hat keinen
 * SaveableStateHolder, ein Weg nach "Erweitert" und zurueck vergaesse ihn sonst.
 * [edit] = Profil-Id, deren Editor sich einmalig oeffnet (Widget-Tipp ohne Server) — auch wenn
 * das Menue schon angezeigt wird.
 */
@Composable
fun WidgetsScreen(nav: NavState, tab: WidgetTab? = null, edit: String? = null) {
    val ctx = LocalContext.current
    val prefs = LocalAppEnv.current.prefs
    val snack = rememberSnack()
    val widgets = remember { WidgetProfilesState(ctx) }
    var editing by rememberSaveable { mutableStateOf(edit) }
    var picking by rememberSaveable { mutableStateOf<Int?>(null) }
    val shown = if (prefs.proWidgetsEnabled) tab ?: WidgetTab.PRO else WidgetTab.NORMAL

    // Der Editor-Wunsch gilt einmal: oeffnen und aus dem Back-Stack nehmen, sonst oeffnete jedes
    // Zurueck von einem anderen Screen (und jede Wiederherstellung) den Editor erneut. Auf [edit]
    // reagieren, nicht nur beim ersten Anzeigen: ein Widget-Tipp, waehrend das Menue schon oben
    // liegt (singleTask, onNewIntent), landet in derselben Composition (gleicher Screen-Key).
    LaunchedEffect(edit) {
        if (edit == null) return@LaunchedEffect
        editing = edit
        if (nav.current is Screen.Widgets) nav.replaceTop(Screen.Widgets(tab))
    }

    // Widgets kommen auf dem Startbildschirm dazu oder verschwinden, waehrend die App im Hintergrund ist.
    LifecycleResumeEffect(widgets) {
        widgets.reload()
        onPauseOrDispose { }
    }

    DetailScaffold(title = stringResource(R.string.settings_group_widgets), onBack = { nav.pop() }, snack = snack) { padding ->
        if (!prefs.proWidgetsEnabled) {
            ScrollColumn(padding) { NormalTab(nav, proOff = true) }
        } else {
            // Die Tabs stehen fest ueber dem scrollenden Inhalt; die grosse App-Bar klappt trotzdem
            // ein (nestedScroll im DetailScaffold). Context7: material3 PrimaryTabRow/Tab.
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                PrimaryTabRow(selectedTabIndex = shown.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                    WidgetTab.entries.forEach { t ->
                        Tab(
                            selected = t == shown,
                            onClick = { if (nav.current is Screen.Widgets) nav.replaceTop(Screen.Widgets(t)) },
                            text = { Text(stringResource(if (t == WidgetTab.PRO) R.string.widgets_tab_pro else R.string.widgets_tab_normal)) },
                        )
                    }
                }
                ScrollColumn(PaddingValues()) {
                    when (shown) {
                        WidgetTab.NORMAL -> NormalTab(nav, proOff = false)
                        WidgetTab.PRO -> ProTab(nav, widgets, snack, onEdit = { editing = it }, onPick = { picking = it })
                    }
                }
            }
        }
    }

    // Je Profil ein frischer Editor: wechselt ein Deep-Link das Profil, darf keine offene
    // Loesch-Rueckfrage des vorigen stehen bleiben.
    editing?.let { id -> key(id) { WidgetProfileSheet(widgets, id) { editing = null } } }

    // Verschwindet das Widget waehrenddessen vom Startbildschirm, schliesst sich die Auswahl.
    widgets.placed.firstOrNull { it.widgetId == picking }?.let { w ->
        LoomSheet(title = stringResource(R.string.widget_config_title), onDismiss = { picking = null }) { dismiss ->
            ProfilePicker(widgets.profiles(Tier.PRO), selected = w.profile.id) {
                widgets.bind(w.widgetId, it)
                dismiss()
            }
        }
    }
}

/** Tab "Widgets": Platzhalter, bis es normale Widgets gibt; ohne Pro Widgets mit dem Weg zum Freischalten. */
@Composable
private fun NormalTab(nav: NavState, proOff: Boolean) {
    GuideHeader(R.drawable.ill_widgets_normal, R.string.img_widgets_normal, text = stringResource(R.string.widgets_normal_soon))
    if (proOff) {
        InfoCard(
            text = stringResource(R.string.widgets_pro_unlock),
            action = {
                TextButton(onClick = { nav.push(Screen.Advanced) }) { Text(stringResource(R.string.settings_group_advanced)) }
            },
        )
    }
}

/**
 * Tab "Pro Widgets": Mikrofon und offener Auftrag (frueher in "Erweitert"), neue Widgets je
 * [WidgetKind] der Stufe Pro, die eigenen Widgets mit ihrem Server, die platzierten, Hilfe.
 */
@Composable
private fun ProTab(
    nav: NavState,
    widgets: WidgetProfilesState,
    snack: SnackController,
    onEdit: (String) -> Unit,
    onPick: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    val env = LocalAppEnv.current
    val mic = rememberPermissionRequest(Manifest.permission.RECORD_AUDIO)
    // Play-Pflicht: eigener Hinweis VOR dem System-Dialog — das Widget ist ein eigener Mikrofon-Einstieg.
    val micGate = rememberDisclosureGate(DisclosureKind.MICROPHONE, onAccept = mic.request)
    // Ueber den Status, nicht per checkSelfPermission: den liest die Activity in onResume neu,
    // und nur so verschwindet die Warnkarte, nachdem der Nutzer das Mikrofon gerade erlaubt hat.
    val hasMic = env.status.micGranted
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    // Ohne Mikrofon zeigen die Widgets "nicht erlaubt" — nach dem Erlauben neu zeichnen.
    LaunchedEffect(hasMic) { widgets.redraw() }

    GuideHeader(R.drawable.ill_pro_widgets, R.string.img_pro_widgets, text = stringResource(R.string.widgets_pro_intro))

    if (!hasMic) {
        SectionCard(title = stringResource(R.string.agent_card_mic), gap = 4.dp) {
            LoomRow(
                headline = stringResource(R.string.agent_mic_missing),
                supporting = stringResource(R.string.agent_mic_allow),
                leading = { StatusIcon(Tone.WARNING, R.drawable.ic_mic_off) },
                onClick = micGate.request,
            )
        }
    }

    // Aus dem Zustand, nicht einmal beim Anzeigen gelesen: Loeschen im Editor nimmt den Auftrag
    // seines Widgets mit, die Karte muss dann mit verschwinden.
    if (widgets.hasWork) PendingTaskCard(widgets, snack)

    SectionCard(title = stringResource(R.string.widgets_card_new_pro), gap = 4.dp) {
        WidgetKind.of(Tier.PRO).forEach { kind ->
            LoomRow(
                headline = stringResource(kind.title),
                supporting = stringResource(kind.description),
                leading = { LoomIcon(R.drawable.ic_add, null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary) },
                onClick = { onEdit(widgets.createNew(kind).id) },
            )
        }
    }

    SectionCard(title = stringResource(R.string.widgets_card_pro), gap = 4.dp) {
        widgets.profiles(Tier.PRO).forEach { p ->
            // Ohne Server kann das Widget nichts senden: Warnung mit Symbol, nicht nur Farbe.
            val missing = !p.serverReady
            LoomRow(
                headline = p.displayName(ctx),
                supporting = if (missing) stringResource(R.string.widgets_server_missing) else serverHost(p.serverUrl) + " · " + modeText(p),
                supportingColor = if (missing) MaterialTheme.loom.warning else dim,
                leading = { ProfileIconView(p) },
                trailing = if (missing) ({ StatusIcon(Tone.WARNING) }) else null,
                onClick = { onEdit(p.id) },
            )
        }
    }

    if (widgets.placed.isNotEmpty()) {
        SectionCard(title = stringResource(R.string.widgets_card_placed), gap = 4.dp) {
            widgets.placed.forEachIndexed { i, w ->
                LoomRow(
                    headline = stringResource(R.string.widgets_placed_row, i + 1, w.profile.displayName(ctx)),
                    supporting = stringResource(R.string.widgets_placed_sub),
                    leading = { ProfileIconView(w.profile) },
                    onClick = { onPick(w.widgetId) },
                )
            }
        }
    }

    SectionCard(title = stringResource(R.string.widgets_card_help), gap = 4.dp) {
        LoomRow(
            headline = stringResource(R.string.agent_tutorial),
            supporting = stringResource(R.string.agent_tutorial_sub),
            leading = { LoomIcon(R.drawable.ic_help, null, Modifier.size(24.dp), dim) },
            onClick = { nav.push(Screen.Tutorial(kind = TutorialKind.PRO_WIDGETS)) },
        )
        listOf(R.string.agent_widget_hint, R.string.widgets_help_size, R.string.widgets_help_shared).forEach {
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = dim)
        }
    }
}

/**
 * Karte "Offener Auftrag": verwirft den wartenden Auftrag, egal von welchem Widget. Im Tab
 * "Pro Widgets" und — sind Pro Widgets aus — in "Erweitert", damit er sich immer verwerfen laesst.
 */
@Composable
fun PendingTaskCard(widgets: WidgetProfilesState, snack: SnackController) {
    val verworfen = stringResource(R.string.agent_discard_done)
    SectionCard(title = stringResource(R.string.agent_card_pending), gap = 4.dp) {
        LoomRow(
            headline = stringResource(R.string.agent_discard),
            supporting = stringResource(R.string.agent_discard_sub),
            leading = { LoomIcon(R.drawable.ic_delete, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = {
                widgets.discardWork()
                snack.show(verworfen)
            },
        )
    }
}

/** "bridge.example.de" aus "https://bridge.example.de" — kurz genug fuer die Unterzeile. */
private fun serverHost(url: String): String = runCatching { URI(url.trim()).host }.getOrNull() ?: url

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
