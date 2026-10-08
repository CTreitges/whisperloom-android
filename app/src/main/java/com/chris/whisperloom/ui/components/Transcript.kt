package com.chris.whisperloom.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R

// Bausteine des Sprachnachrichten-Fensters, seit 3.9.0 mit dem Verlauf geteilt (Plan §6.3):
// Kopfkarte, Text auf ruhigem Hintergrund, Hinweiszeile, Fuellwoerter-Leiste, untere Aktionsleiste.

/** Kopfkarte: Icon-Kreis, Titel (hoechstens zwei Zeilen), Unterzeile. */
@Composable
fun TranscriptHeadCard(@DrawableRes icon: Int, title: String, subtitle: String, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Text auf ruhigem Hintergrund: markierbar, Eintraege mit 12 dp Abstand, unten 24 dp Luft.
 * Was nicht markierbar sein soll (Ueberschriften, Karten), steht in DisableSelection.
 */
@Composable
fun TranscriptText(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    SelectionContainer(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Absaetze in der Schrift des Sprachnachrichten-Fensters; ohne Absatz [emptyText] kursiv. Schluessel: [key]-p0, [key]-p1 … */
fun LazyListScope.transcriptParagraphs(key: String, paragraphs: List<String>, emptyText: String) {
    if (paragraphs.isEmpty()) {
        item(key = "$key-e") {
            Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline, fontStyle = FontStyle.Italic)
        }
    } else {
        items(paragraphs.size, key = { "$key-p$it" }) { ParagraphText(paragraphs[it]) }
    }
}

/** Ein Absatz: bodyLarge 16/24 sp, onSurface (Spec §3.2). */
@Composable
fun ParagraphText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/** Hinweiszeile unter dem Text: labelMedium, onSurfaceVariant bzw. error, optional mit Icon davor. */
@Composable
fun TranscriptNote(text: AnnotatedString, error: Boolean = false, @DrawableRes icon: Int? = null) {
    val color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    if (icon == null) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        LoomIcon(icon, null, Modifier.size(18.dp), color)
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun TranscriptNote(text: String, error: Boolean = false, @DrawableRes icon: Int? = null) =
    TranscriptNote(AnnotatedString(text), error, icon)

/** Umschalter-Leiste "Fuellwoerter ausblenden": ganze Zeile ist das Touch-Ziel, TalkBack liest Label + Zustand. */
@Composable
fun FillerToggleBar(hideFillers: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .toggleable(value = hideFillers, role = Role.Switch, onValueChange = onChange),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.share_hide_fillers), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(if (hideFillers) R.string.share_hide_fillers_on else R.string.share_hide_fillers_off),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = hideFillers, onCheckedChange = null)
        }
    }
}

/** Untere Aktionsleiste auf surfaceContainer; traegt den Nav-Inset ([modifier] z. B. imePadding fuer ein Textfeld darueber). */
@Composable
fun TranscriptActionBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * Knopf der Aktionsleiste: halbe Breite, mindestens 56 dp hoch (bei grosser Schrift darf der Text
 * umbrechen), Icon + Text. [primary] gefuellt, sonst tonal.
 */
@Composable
fun RowScope.ActionBarButton(@DrawableRes icon: Int, text: String, onClick: () -> Unit, primary: Boolean = false, enabled: Boolean = true) {
    val modifier = Modifier.weight(1f).heightIn(min = 56.dp)
    val content: @Composable RowScope.() -> Unit = {
        Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(text)
    }
    if (primary) Button(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    else FilledTonalButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
}
