package com.chris.whisperloom.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R
import com.chris.whisperloom.agent.ProfileIcon
import com.chris.whisperloom.agent.SpeechPause
import com.chris.whisperloom.agent.WidgetIcons
import com.chris.whisperloom.agent.WidgetPhoto
import com.chris.whisperloom.agent.WidgetProfile
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.LoomSheet
import com.chris.whisperloom.ui.components.SwitchRow
import com.chris.whisperloom.ui.state.WidgetProfilesState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ein Profil bearbeiten: Name, Symbol (eingebaut oder aus der Galerie), Auto-Stopp mit
 * Sprechpause, Loeschen. Jede Aenderung wird sofort gespeichert, einen Speichern-Knopf gibt es
 * nicht. Das Standardprofil laesst sich bearbeiten, aber nicht loeschen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetProfileSheet(widgets: WidgetProfilesState, profileId: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val profile = widgets.profile(profileId)
    if (profile == null) {
        // Etwa nach Prozess-Tod mit einem inzwischen geloeschten Profil.
        LaunchedEffect(profileId) { onDismiss() }
        return
    }
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(profileId) { mutableStateOf(profile.name) }
    var nameEdited by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteOnClose by remember { mutableStateOf(false) }

    // Im Sheet selbst, nicht als Snackbar: die laege im Activity-Fenster UNTER dem Sheet.
    var photoFailed by remember { mutableStateOf(false) }

    // Der Name steht sofort im Speicher; neu gezeichnet wird erst, wenn das Tippen kurz ruht.
    LaunchedEffect(name) {
        if (!nameEdited) return@LaunchedEffect
        delay(NAME_REDRAW_MS)
        widgets.redraw()
    }

    // Die Leseerlaubnis des Pickers gilt nur voruebergehend: sofort kopieren.
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = withContext(Dispatchers.IO) { WidgetPhoto.import(ctx, uri, profileId) }
            photoFailed = file == null
            if (file == null) return@launch
            val now = widgets.profile(profileId)
            if (now == null) WidgetPhoto.delete(ctx, file) else widgets.save(now.copy(icon = ProfileIcon.Photo(file)))
        }
    }

    // Geloescht wird erst nach dem Zuklappen: so verschwindet das Sheet nicht mitten in der Animation.
    val close = {
        if (deleteOnClose) widgets.delete(profileId) else if (nameEdited) widgets.redraw()
        onDismiss()
    }

    LoomSheet(title = stringResource(R.string.widget_profile_title), onDismiss = close) { dismiss ->
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = WidgetProfile.clip(it)
                nameEdited = true
                widgets.profile(profileId)?.let { p -> widgets.save(p.copy(name = name), redraw = false) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.widget_profile_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            supportingText = { Text(stringResource(R.string.widget_profile_name_sub, stringResource(R.string.widget_label))) },
        )

        Text(stringResource(R.string.widget_profile_icon), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetIcons.all.forEach { icon ->
                val builtIn = ProfileIcon.BuiltIn(icon.key)
                IconTile(selected = profile.icon == builtIn, label = stringResource(icon.label), onClick = {
                    widgets.save(profile.copy(icon = builtIn))
                }) { LoomIcon(icon.drawable, null, Modifier.size(24.dp)) }
            }
            val photo = rememberPhoto((profile.icon as? ProfileIcon.Photo)?.fileName)
            IconTile(
                selected = profile.icon is ProfileIcon.Photo,
                label = stringResource(R.string.widget_profile_gallery),
                role = Role.Button,
                onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
            ) {
                if (photo != null) Image(photo, null, Modifier.size(48.dp), contentScale = ContentScale.Crop)
                else LoomIcon(R.drawable.ic_add_photo_alternate, null, Modifier.size(24.dp))
            }
        }
        if (photoFailed) {
            Text(
                stringResource(R.string.widget_photo_failed),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (profile.icon is ProfileIcon.Photo) {
            TextButton(onClick = { widgets.save(profile.copy(icon = ProfileIcon.DEFAULT)) }) {
                Text(stringResource(R.string.widget_profile_remove_photo))
            }
        }

        SwitchRow(
            headline = stringResource(R.string.widget_profile_autostop),
            supporting = stringResource(R.string.widget_profile_autostop_sub),
            checked = profile.autoStop,
            onCheckedChange = { widgets.save(profile.copy(autoStop = it)) },
        )
        if (profile.autoStop) {
            Text(stringResource(R.string.widget_profile_pause), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SpeechPause.entries.forEachIndexed { i, pause ->
                    SegmentedButton(
                        selected = profile.pause == pause,
                        onClick = { widgets.save(profile.copy(pause = pause)) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = SpeechPause.entries.size),
                        icon = {},
                        label = { Text(stringResource(pauseLabel(pause)), maxLines = 1) },
                    )
                }
            }
            Text(
                stringResource(pauseHint(profile.pause)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!profile.isDefault) {
                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.widget_profile_delete)) }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = dismiss) { Text(stringResource(R.string.common_done)) }
        }

        if (confirmDelete) {
            val bound = widgets.boundCount(profileId)
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                title = { Text(stringResource(R.string.widget_profile_delete_title, profile.displayName(ctx))) },
                text = {
                    Text(
                        if (bound == 0) stringResource(R.string.widget_profile_delete_unused)
                        else pluralStringResource(
                            R.plurals.widget_profile_delete_confirm, bound, bound, widgets.profiles.first().displayName(ctx),
                        ),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDelete = false
                            deleteOnClose = true
                            dismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.common_delete)) }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } },
            )
        }
    }
}

/** Runde 48-dp-Kachel im Symbolraster; TalkBack liest [label] und den Auswahlzustand. */
@Composable
private fun IconTile(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    role: Role = Role.RadioButton,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (selected) colors.primaryContainer else colors.surfaceContainerHigh)
            .then(if (selected) Modifier.border(2.dp, colors.primary, CircleShape) else Modifier)
            .selectable(selected = selected, role = role, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (selected) colors.onPrimaryContainer else colors.onSurface) {
            content()
        }
    }
}

@StringRes
fun pauseLabel(pause: SpeechPause): Int = when (pause) {
    SpeechPause.SHORT -> R.string.widget_pause_short
    SpeechPause.NORMAL -> R.string.widget_pause_normal
    SpeechPause.LONG -> R.string.widget_pause_long
}

@StringRes
private fun pauseHint(pause: SpeechPause): Int = when (pause) {
    SpeechPause.SHORT -> R.string.widget_pause_short_sub
    SpeechPause.NORMAL -> R.string.widget_pause_normal_sub
    SpeechPause.LONG -> R.string.widget_pause_long_sub
}

private const val NAME_REDRAW_MS = 400L
