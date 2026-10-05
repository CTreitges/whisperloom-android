package com.chris.whisperloom.ui.patchnotes

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.SnackController
import com.chris.whisperloom.ui.theme.loom

/**
 * Inhalt einer Version (Hero aufgeklappt oder Zeile in der Liste): Intro, dann je sichtbare Kategorie ein
 * [CategoryBlock]. Technik ist ausgeblendet; bleibt nichts uebrig, steht dort ein Hinweis auf GitHub.
 */
@Composable
fun ReleaseBody(release: Release, snack: SnackController, modifier: Modifier = Modifier) {
    val sections = release.visibleSections
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        release.intro?.let {
            Text(rememberInline(it, snack), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        sections.forEach { CategoryBlock(it, snack) }
        if (release.intro == null && sections.isEmpty()) {
            Text(
                stringResource(R.string.patchnotes_only_tech),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Kategorie-Kopf (Kreis + Label + Anzahl, fuer TalkBack eine Ueberschrift "Neu, 1 Punkt") und ihre Punkte. */
@Composable
private fun CategoryBlock(section: Section, snack: SnackController) {
    val style = categoryStyle(section.category)
    val label = style.label ?: section.title
    val n = section.entries.size
    val count = pluralStringResource(R.plurals.patchnotes_entries, n, n)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.clearAndSetSemantics {
                heading()
                contentDescription = "$label, $count"
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(32.dp).background(style.container, CircleShape), contentAlignment = Alignment.Center) {
                LoomIcon(style.icon, null, Modifier.size(18.dp), style.onContainer)
            }
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("$n", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            section.entries.forEach { EntryItem(it, style.accent, snack) }
        }
    }
}

/** Ein Punkt. Nicht verschmolzen, damit Links darin einzeln fokussierbar bleiben. */
@Composable
private fun EntryItem(entry: Entry, accent: Color, snack: SnackController) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // 7 dp + 3 dp = Mitte der ersten Zeile (20 sp Zeilenhoehe bei titleSmall/bodyMedium).
        Box(Modifier.padding(top = 7.dp).size(6.dp).background(accent, CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            entry.lead?.let {
                Text(rememberInline(it, snack), style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
            }
            entry.body?.let {
                Text(
                    rememberInline(it, snack),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (entry.lead != null) cs.onSurfaceVariant else cs.onSurface,
                )
            }
            if (entry.children.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.children.forEach { child ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("–", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics {})
                            Text(
                                rememberInline(child, snack),
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Darstellung einer Kategorie: immer Icon UND Text, nie nur Farbe (Spec §5.6); [label] null = Originaltitel. */
private class CategoryStyle(
    val label: String?,
    @param:DrawableRes val icon: Int,
    val accent: Color,
    val container: Color,
    val onContainer: Color,
)

@Composable
private fun categoryStyle(category: Category): CategoryStyle {
    val cs = MaterialTheme.colorScheme
    val loom = MaterialTheme.loom
    return when (category) {
        Category.ADDED -> CategoryStyle(
            stringResource(R.string.patchnotes_cat_added), R.drawable.ic_add, cs.primary, cs.primaryContainer, cs.onPrimaryContainer,
        )
        Category.CHANGED -> CategoryStyle(
            stringResource(R.string.patchnotes_cat_changed), R.drawable.ic_refresh, cs.secondary, cs.secondaryContainer, cs.onSecondaryContainer,
        )
        Category.FIXED -> CategoryStyle(
            stringResource(R.string.patchnotes_cat_fixed), R.drawable.ic_build, loom.success, loom.successContainer, loom.onSuccessContainer,
        )
        Category.SECURITY -> CategoryStyle(
            stringResource(R.string.patchnotes_cat_security), R.drawable.ic_lock, cs.tertiary, cs.tertiaryContainer, cs.onTertiaryContainer,
        )
        // Kein error-Rot: Entferntes ist kein Fehler.
        Category.REMOVED -> CategoryStyle(
            stringResource(R.string.patchnotes_cat_removed), R.drawable.ic_remove_circle_outline,
            cs.onSurfaceVariant, cs.surfaceContainerHigh, cs.onSurfaceVariant,
        )
        Category.KNOWN -> CategoryStyle(
            stringResource(R.string.patchnotes_cat_known), R.drawable.ic_warning, loom.warning, loom.warningContainer, loom.onWarningContainer,
        )
        // TECH wird nie angezeigt (visibleSections); der Zweig haelt das when nur vollstaendig.
        Category.OTHER, Category.TECH -> CategoryStyle(
            null, R.drawable.ic_info, cs.onSurfaceVariant, cs.surfaceContainerHigh, cs.onSurfaceVariant,
        )
    }
}
