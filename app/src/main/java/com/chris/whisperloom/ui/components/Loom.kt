package com.chris.whisperloom.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chris.whisperloom.R
import com.chris.whisperloom.ui.theme.loom

/** Karten-Radius laut UX-Spec §1.3 (Hero 24). */
val CardShape = RoundedCornerShape(20.dp)
val HeroShape = RoundedCornerShape(24.dp)

/** Material-Symbol aus res/drawable/ic_*.xml (Spec §3.3); dekorativ = contentDescription null. */
@Composable
fun LoomIcon(
    @DrawableRes id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(painterResource(id), contentDescription, modifier, tint)
}

/** Status-Farbe + Icon (nie nur Farbe, Spec §5.6). */
enum class Tone { SUCCESS, WARNING, ERROR, NEUTRAL }

@Composable
fun StatusIcon(tone: Tone, @DrawableRes neutralIcon: Int = R.drawable.ic_info, size: Dp = 24.dp) {
    val m = Modifier.size(size)
    when (tone) {
        Tone.SUCCESS -> LoomIcon(R.drawable.ic_check_circle, null, m, MaterialTheme.loom.success)
        Tone.WARNING -> LoomIcon(R.drawable.ic_warning, null, m, MaterialTheme.loom.warning)
        Tone.ERROR -> LoomIcon(R.drawable.ic_error, null, m, MaterialTheme.colorScheme.error)
        Tone.NEUTRAL -> LoomIcon(neutralIcon, null, m, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** ElevatedCard(surfaceContainer, Radius 20, Elevation 0, Innenabstand 20) mit optionalem Titel. */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    @DrawableRes titleIcon: Int? = null,
    titleIconTint: Color = MaterialTheme.colorScheme.primary,
    shape: RoundedCornerShape = CardShape,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    gap: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(gap)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (titleIcon != null) LoomIcon(titleIcon, null, Modifier.size(24.dp), titleIconTint)
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
            }
            content()
        }
    }
}

/** OutlinedCard mit 1 dp outlineVariant (Info/Leerzustaende, Spec §1.3). */
@Composable
fun OutlinedSection(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    gap: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(gap)) { content() }
    }
}

/** Farbige Hinweiskarte (Info secondaryContainer, Warnung warningContainer), Icon links. */
@Composable
fun InfoCard(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_info,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    onContainer: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(container, CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LoomIcon(icon, null, Modifier.size(24.dp), onContainer)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = onContainer, modifier = Modifier.weight(1f))
        }
        if (action != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { action() }
    }
}

/**
 * Primaer-Button: volle Breite, 56 dp (Hero 64), voll rund, Leading-/Trailing-Icon 24 dp;
 * [working] ersetzt das Icon durch einen Spinner und sperrt den Button (Spec §2.2).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    working: Boolean = false,
    @DrawableRes leadingIcon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
    height: Dp = 56.dp,
    tonal: Boolean = false,
    colors: ButtonColors? = null,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    fillWidth: Boolean = true,
) {
    val content: @Composable RowScope.() -> Unit = {
        when {
            working -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
            leadingIcon != null -> LoomIcon(leadingIcon, null, Modifier.size(24.dp))
        }
        if (working || leadingIcon != null) Box(Modifier.width(12.dp))
        Text(text, style = textStyle, textAlign = TextAlign.Center)
        if (trailingIcon != null) {
            Box(Modifier.width(8.dp))
            LoomIcon(trailingIcon, null, Modifier.size(24.dp))
        }
    }
    val m = (if (fillWidth) modifier.fillMaxWidth() else modifier).height(height)
    if (tonal) {
        FilledTonalButton(onClick, m, enabled && !working, CircleShape, colors ?: ButtonDefaults.filledTonalButtonColors(), content = content)
    } else {
        Button(onClick, m, enabled && !working, CircleShape, colors ?: ButtonDefaults.buttonColors(), content = content)
    }
}

/** Hero-Button 64 dp mit 16-sp-Label (Home, Spec §2.1). */
val HeroLabelStyle: TextStyle
    @Composable get() = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp)

/**
 * Zeile innerhalb einer Karte (Leading, Headline + Supporting, Trailing), min. 56/72 dp;
 * mit [onClick] klickbar und fuer TalkBack zu einem Element verschmolzen, Status als
 * [stateDescription] (Spec §5.6). [supportingMaxLines] kuerzt die Unterzeile mit "…" (Patchnotes-Teaser).
 */
@Composable
fun LoomRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    stateDescription: String? = null,
    enabled: Boolean = true,
    supportingMaxLines: Int = Int.MAX_VALUE,
) {
    val clickable = if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier
    Row(
        modifier
            .fillMaxWidth()
            .then(clickable)
            .semantics(mergeDescendants = true) { if (stateDescription != null) this.stateDescription = stateDescription }
            .heightIn(min = if (supporting != null) 72.dp else 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(headline, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = supportingColor,
                    maxLines = supportingMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing()
    }
}

/** Nummerierter 24-dp-Kreis (Kurzanleitung, Hilfe-Schritte). */
@Composable
fun StepBadge(number: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(24.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** Aufklappbare Karte (Hilfe-Abschnitte, "Was passiert dabei?"): Kopfzeile + AnimatedVisibility. */
@Composable
fun ExpandableCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "expand")
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .semantics(mergeDescendants = true) {}
                .heightIn(min = 56.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) LoomIcon(icon, null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            LoomIcon(R.drawable.ic_expand_more, null, Modifier.size(24.dp).rotate(rotation), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
        }
    }
}
