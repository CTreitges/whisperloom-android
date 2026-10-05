package com.chris.whisperloom.ui.patchnotes

import android.content.res.AssetManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.BuildConfig
import com.chris.whisperloom.R
import com.chris.whisperloom.ui.components.DetailScaffold
import com.chris.whisperloom.ui.components.HeroShape
import com.chris.whisperloom.ui.components.LinkRow
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.OutlinedSection
import com.chris.whisperloom.ui.components.PrimaryButton
import com.chris.whisperloom.ui.components.ScrollColumn
import com.chris.whisperloom.ui.components.SectionCard
import com.chris.whisperloom.ui.components.SectionHeader
import com.chris.whisperloom.ui.components.SnackController
import com.chris.whisperloom.ui.components.StatusChip
import com.chris.whisperloom.ui.components.StatusIcon
import com.chris.whisperloom.ui.components.Tone
import com.chris.whisperloom.ui.components.openLink
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.settings.GITHUB_URL
import com.chris.whisperloom.ui.theme.loom
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val CHANGELOG_URL = "$GITHUB_URL/blob/main/CHANGELOG.md"

/** Versionen darunter stehen unter "Vor Version 3" (erste App-Generation). */
private const val LEGACY_MAJOR = 3

private val DATE = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)

/**
 * P — Patchnotes (UX-Spec §6.17): die neueste Version als Hero (das Wichtigste aus den Store-Highlights,
 * Details zum Aufklappen), darunter fruehere Versionen als aufklappbare Zeilen, ganz unten der Link aufs
 * vollstaendige CHANGELOG. Inhalt aus den App-Assets ([PatchnotesLoader]); [load] ist fuer Tests austauschbar.
 */
@Composable
fun PatchnotesScreen(nav: NavState, load: (AssetManager) -> List<Release> = PatchnotesLoader::load) {
    val ctx = LocalContext.current
    val snack = rememberSnack()
    // null = laedt noch (kurz, kein Spinner); leer = Assets fehlen oder sind kaputt.
    val releases by produceState<List<Release>?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { load(ctx.assets) }.getOrDefault(emptyList()) }
    }

    DetailScaffold(title = stringResource(R.string.patchnotes_title), onBack = { nav.pop() }, snack = snack) { padding ->
        val list = releases
        when {
            list == null -> Unit
            list.isEmpty() -> Unavailable(padding, snack)
            else -> ReleaseColumn(list, padding, snack)
        }
    }
}

@Composable
private fun ReleaseColumn(releases: List<Release>, padding: PaddingValues, snack: SnackController) {
    val older = releases.drop(1)
    val recent = older.filter { it.major >= LEGACY_MAJOR }
    val legacy = older.filter { it.major < LEGACY_MAJOR }
    // Wie der Hub: kein Seitenpadding, weil SectionHeader eigene 20 dp mitbringt; Karten bekommen es selbst.
    val card = Modifier.padding(horizontal = 20.dp)
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { HeroCard(releases.first(), snack, card.padding(top = 8.dp)) }
        if (recent.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.patchnotes_earlier)) }
            item { ReleaseList(recent, snack, card) }
        }
        if (legacy.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.patchnotes_legacy)) }
            item { ReleaseList(legacy, snack, card, hint = stringResource(R.string.patchnotes_legacy_hint)) }
        }
        item {
            Box(card.padding(top = 8.dp)) {
                LinkRow(
                    headline = stringResource(R.string.patchnotes_github),
                    url = CHANGELOG_URL,
                    snack = snack,
                    supporting = stringResource(R.string.patchnotes_github_sub),
                )
            }
        }
    }
}

private fun formatDate(date: LocalDate): String = date.format(DATE)

/** Neueste Version: Kopf, Status, Tagline, "Das Wichtigste" und die Details zum Aufklappen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(release: Release, snack: SnackController, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val loom = MaterialTheme.loom
    val heroDescription = stringResource(R.string.patchnotes_cd_hero, release.version)
    SectionCard(modifier = modifier, shape = HeroShape, gap = 16.dp) {
        Row(
            Modifier.clearAndSetSemantics {
                heading()
                contentDescription = heroDescription
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(56.dp).background(cs.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                LoomIcon(R.drawable.ic_auto_fix_high, null, Modifier.size(28.dp), cs.onPrimaryContainer)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.patchnotes_kicker), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                // displaySmall statt displayMedium: bei 200 % Schrift bleibt "3.8.1" in einer Zeile.
                Text(release.version, style = MaterialTheme.typography.displaySmall, color = cs.primary)
            }
        }
        // Ein Suffix ("3.9.0-beta") zaehlt nicht: installiert ist die Version davor.
        val installed = release.version == BuildConfig.VERSION_NAME.substringBefore('-')
        if (installed || release.date != null) {
            FlowRow(
                Modifier.semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (installed) {
                    StatusChip(
                        stringResource(R.string.patchnotes_installed), R.drawable.ic_check_circle,
                        loom.successContainer, loom.onSuccessContainer, Modifier.align(Alignment.CenterVertically),
                    )
                }
                release.date?.let {
                    Text(
                        formatDate(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
        release.tagline?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface) }

        if (release.highlights.isEmpty()) {
            // Ohne Highlights gibt es nichts zusammenzufassen: die Details direkt zeigen.
            ReleaseBody(release, snack)
        } else {
            Highlights(release, snack)
        }
    }
}

/** "Das Wichtigste" (Store-Highlights) und darunter der Schalter fuer alle Aenderungen im Detail. */
@Composable
private fun ColumnScope.Highlights(release: Release, snack: SnackController) {
    val cs = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }
    HorizontalDivider(color = cs.outlineVariant)
    Text(
        stringResource(R.string.patchnotes_highlights),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.semantics { heading() },
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        release.highlights.forEachIndexed { i, h -> HighlightItem(i + 1, h) }
    }
    val n = release.changeCount
    val state = stringResource(if (open) R.string.patchnotes_state_expanded else R.string.patchnotes_state_collapsed)
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "expand")
    TextButton(onClick = { open = !open }, modifier = Modifier.semantics { stateDescription = state }) {
        Text(if (open) stringResource(R.string.patchnotes_show_less) else pluralStringResource(R.plurals.patchnotes_show_all, n, n))
        LoomIcon(R.drawable.ic_expand_more, null, Modifier.padding(start = 8.dp).size(18.dp).rotate(rotation))
    }
    AnimatedVisibility(visible = open) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HorizontalDivider(color = cs.outlineVariant)
            ReleaseBody(release, snack)
        }
    }
}

/** Nummerierter Highlight-Punkt; die Nummer ist Deko (TalkBack liest Kicker und Text als ein Element). */
@Composable
private fun HighlightItem(number: Int, highlight: Highlight) {
    Row(
        Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "$number",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(24.dp).clearAndSetSemantics {},
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            highlight.kicker?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = kickerColor(it)) }
            Text(highlight.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun kickerColor(kicker: String): Color = when (kicker.lowercase()) {
    "neu" -> MaterialTheme.colorScheme.primary
    "pro" -> MaterialTheme.colorScheme.tertiary
    "behoben" -> MaterialTheme.loom.success
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Aeltere Versionen als Zeilen mit Trennlinie in einer Karte; [hint] steht oben (nur "Vor Version 3"). */
@Composable
private fun ReleaseList(releases: List<Release>, snack: SnackController, modifier: Modifier, hint: String? = null) {
    SectionCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), gap = 0.dp) {
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }
        releases.forEachIndexed { i, release ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            key(release.version) { ReleaseRow(release, snack) }
        }
    }
}

/** Aufklappbare Version (Muster ProblemEntry in der Hilfe): Ueberschrift fuer TalkBack, Zustand als stateDescription. */
@Composable
private fun ReleaseRow(release: Release, snack: SnackController) {
    var open by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "expand")
    // Aufgeklappt steht der Inhalt darunter — der Einzeiler wuerde ihn (bei 3.0.0 wortgleich) doppeln.
    val teaser = if (open) null else PatchnotesParser.teaser(release)
    val supporting = listOfNotNull(release.date?.let(::formatDate), teaser).joinToString(" · ")
    Column {
        LoomRow(
            headline = stringResource(R.string.patchnotes_version, release.version),
            modifier = Modifier.semantics { heading() },
            supporting = supporting.ifEmpty { null },
            supportingMaxLines = 2,
            trailing = {
                LoomIcon(R.drawable.ic_expand_more, null, Modifier.size(24.dp).rotate(rotation), MaterialTheme.colorScheme.onSurfaceVariant)
            },
            onClick = { open = !open },
            stateDescription = stringResource(if (open) R.string.patchnotes_state_expanded else R.string.patchnotes_state_collapsed),
        )
        AnimatedVisibility(visible = open) {
            ReleaseBody(release, snack, Modifier.padding(top = 4.dp, bottom = 16.dp))
        }
    }
}

/** Assets fehlen oder sind nicht lesbar: ehrlich sagen und auf GitHub verweisen. */
@Composable
private fun Unavailable(padding: PaddingValues, snack: SnackController) {
    val ctx = LocalContext.current
    ScrollColumn(padding) {
        OutlinedSection {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusIcon(Tone.NEUTRAL)
                Text(stringResource(R.string.patchnotes_unavailable_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(R.string.patchnotes_unavailable_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(
                text = stringResource(R.string.patchnotes_unavailable_action),
                onClick = { openLink(ctx, CHANGELOG_URL, snack) },
                leadingIcon = R.drawable.ic_open_in_new,
                tonal = true,
            )
        }
    }
}
