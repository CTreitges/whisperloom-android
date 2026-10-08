package com.chris.whisperloom.ui.share

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.Formats
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.ui.components.levelLabel
import com.chris.whisperloom.ui.theme.loom
import kotlinx.coroutines.launch

/** Dauer, solange noch keine Datei entpackt ist. */
private const val UNKNOWN_DURATION = "–:––"

/**
 * S — Transkription (Share-Ziel), UX-Spec §2.9. Zeichnet nur [state]; jede Aktion geht
 * als Callback an die Activity bzw. den [ShareController].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    state: ShareUiState,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRetryFile: (Int) -> Unit,
    onRetryAll: () -> Unit,
    onHideFillersChange: (Boolean) -> Unit,
    onOpenSetup: () -> Unit,
    onOpenRefine: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedText = stringResource(R.string.share_copied)
    // Kopieren: Aktion der Activity, Rueckmeldung als Snackbar (nie Toast, Spec §1.3).
    val copy: () -> Unit = {
        onCopy()
        scope.launch { snackbar.showSnackbar(copiedText) }
    }
    val done = state.phase == SharePhase.DONE

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.cd_close),
                        )
                    }
                },
                actions = {
                    if (done) {
                        IconButton(onClick = copy) {
                            Icon(
                                painter = painterResource(R.drawable.ic_content_copy),
                                contentDescription = stringResource(R.string.share_cd_copy),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (done) ActionBar(hasErrors = state.hasErrors, onCopy = copy, onShare = onShare, onRetryAll = onRetryAll)
        },
    ) { padding ->
        when (state.phase) {
            SharePhase.NO_FILE -> NoFileCard(padding, onClose)
            SharePhase.NOT_CONFIGURED -> NotConfiguredCard(padding, onOpenSetup, onClose)
            SharePhase.ALL_FAILED -> AllFailedCard(padding, state.failure, onRetryAll, onClose)
            SharePhase.LOADING, SharePhase.DONE -> TranscriptContent(state, padding, onRetryFile, onHideFillersChange, onOpenRefine)
        }
    }
}

// --- LADEN / FERTIG ------------------------------------------------------------------

@Composable
private fun TranscriptContent(
    state: ShareUiState,
    padding: PaddingValues,
    onRetryFile: (Int) -> Unit,
    onHideFillersChange: (Boolean) -> Unit,
    onOpenRefine: () -> Unit,
) {
    val loading = state.phase == SharePhase.LOADING
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(start = 20.dp, end = 20.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeadCard(state)
        AnimatedVisibility(visible = loading) {
            // Waehrend der Ausblend-Animation ist progress schon null — dann ein leerer Platzhalter.
            ProgressBlock(state.progress ?: ShareProgress(0, state.files.size, 0, 1, ""))
        }
        TranscriptList(state, onRetryFile, Modifier.weight(1f))
        if (state.phase == SharePhase.DONE) {
            RefineNote(state, onOpenRefine)
            // Zeigt jede Datei ihre KI-Fassung, gibt es nichts umzuschalten — die Stufe waehlt
            // man in den Einstellungen, nicht hier.
            if (state.hasUnrefined) FillerToggleBar(state.hideFillers, onHideFillersChange)
        }
    }
}

/**
 * Hinweiszeile zur KI-Stufe fuer geteilte Audios: aktiv (mit Link zur Textverbesserung, 3.9.0),
 * online gescheitert und lokal verbessert, gescheitert (mit Grund) oder bei mehreren Dateien nur
 * teilweise gescheitert — dann mit Anzahl, sonst saehe die KI-Fassung der anderen Dateien wie
 * gescheitert aus. Bei "Aus" nichts.
 */
@Composable
private fun RefineNote(state: ShareUiState, onOpenRefine: () -> Unit) {
    val mode = state.refineMode
    if (mode == RefineMode.OFF) return
    val skipped = state.refineSkipped
    val failures = state.refineFailures
    val style = MaterialTheme.typography.labelMedium
    if (skipped == null && !state.refineLocalFallback) {
        val link = stringResource(R.string.share_refine_link)
        val text = stringResource(R.string.share_refine_note, levelLabel(mode), link)
        val start = text.indexOf(link)
        val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
        // Context7: Compose ui 1.12 – LinkAnnotation.Clickable/addLink: ein Link im Text, TalkBack bietet ihn als Link an.
        val note = buildAnnotatedString {
            append(text)
            addLink(LinkAnnotation.Clickable(REFINE_LINK_TAG, linkStyle) { onOpenRefine() }, start, start + link.length)
        }
        Text(note, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Text(
        when {
            skipped == null -> stringResource(R.string.share_refine_local_fallback, levelLabel(mode))
            failures < state.results.size ->
                stringResource(R.string.share_refine_partly, levelLabel(mode), failures, state.results.size, skipped)
            else -> stringResource(R.string.refine_skipped, skipped)
        },
        style = style,
        color = if (skipped != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Tag des Links "Einstellungen › Textverbesserung" im Hinweis. */
private const val REFINE_LINK_TAG = "refine"

/** Kopf-Karte: Icon-Kreis, Quelle, "Dauer · 1 Datei" bzw. "n Dateien · Dauer gesamt". */
@Composable
private fun HeadCard(state: ShareUiState) {
    val multi = state.files.size > 1
    val unknown = stringResource(R.string.share_unknown_source)
    val source = if (multi) unknown else state.files.firstOrNull()?.name?.ifBlank { unknown } ?: unknown
    val duration = if (state.phase == SharePhase.LOADING) UNKNOWN_DURATION else Formats.duration(state.totalDurationMs)
    val subtitle = if (multi) {
        stringResource(R.string.share_head_multi, state.files.size, duration)
    } else {
        stringResource(R.string.share_head_single, duration)
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
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
                    painter = painterResource(R.drawable.ic_voicemail),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column {
                Text(
                    source,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Determinierter Balken (fileIndex + chunk/chunks)/files und Statuszeile als Live-Region. */
@Composable
private fun ProgressBlock(progress: ShareProgress) {
    val animated by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = tween(durationMillis = 300),
        label = "progress",
    )
    val text = if (progress.isSingle) {
        progress.label
    } else {
        stringResource(
            R.string.share_progress,
            progress.fileIndex + 1,
            progress.files,
            progress.displayChunk,
            progress.chunks,
            progress.label,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
        )
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * Text-Bereich: je Datei ein Abschnitt (Ueberschrift nur bei mehreren Dateien oder Fehlern),
 * Absaetze mit 12 dp Abstand in einem SelectionContainer, Skeleton fuer die laufende Datei,
 * Fehlerkarte je Datei.
 */
@Composable
private fun TranscriptList(state: ShareUiState, onRetryFile: (Int) -> Unit, modifier: Modifier = Modifier) {
    val showHeadings = state.files.size > 1 || state.hasErrors
    val running = if (state.phase == SharePhase.LOADING) state.progress?.fileIndex else null
    val nothing = stringResource(R.string.share_nothing_recognised)
    val retry = stringResource(R.string.share_retry_file)
    val unknown = stringResource(R.string.share_unknown_source)
    SelectionContainer(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.files.forEachIndexed { index, file ->
                if (showHeadings) {
                    item(key = "h$index") { DisableSelection { SectionHeading(file, unknown) } }
                }
                val result = file.result
                when {
                    result != null -> {
                        val paragraphs = ShareText.paragraphs(result, state.hideFillers)
                        if (paragraphs.isEmpty()) {
                            item(key = "e$index") {
                                Text(
                                    nothing,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    fontStyle = FontStyle.Italic,
                                )
                            }
                        } else {
                            items(paragraphs.size, key = { "p$index-$it" }) { ParagraphText(paragraphs[it]) }
                        }
                    }
                    file.error != null -> item(key = "f$index") {
                        DisableSelection {
                            ErrorCard(
                                message = stringResource(R.string.share_one_failed, file.error),
                                retryLabel = retry,
                                onRetry = { onRetryFile(index) },
                                retryEnabled = running == null,
                            )
                        }
                    }
                    index == running -> item(key = "s$index") { DisableSelection { SkeletonLines() } }
                }
            }
        }
    }
}

/** 16 dp Abstand + Divider + titleSmall primary "Quelle · Dauer" (ohne Dauer, solange keine da ist). */
@Composable
private fun SectionHeading(file: ShareFile, unknown: String) {
    val name = file.name.ifBlank { unknown }
    val text = file.result?.let { stringResource(R.string.share_section, name, Formats.duration(it.durationMs)) } ?: name
    Column(Modifier.padding(top = 4.dp)) {
        HorizontalDivider()
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Umschalter-Leiste: ganze Zeile ist das Touch-Ziel, TalkBack liest Label + Zustand. */
@Composable
private fun FillerToggleBar(hideFillers: Boolean, onChange: (Boolean) -> Unit) {
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

/** Aktionsleiste: Kopieren (tonal), Teilen (primaer), "Alles erneut" nur bei Fehlern; traegt den Nav-Inset. */
@Composable
private fun ActionBar(hasErrors: Boolean, onCopy: () -> Unit, onShare: () -> Unit, onRetryAll: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = onCopy, modifier = Modifier.weight(1f).height(56.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_content_copy),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.share_copy))
            }
            Button(onClick = onShare, modifier = Modifier.weight(1f).height(56.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_share),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.share_forward))
            }
            if (hasErrors) {
                IconButton(onClick = onRetryAll, modifier = Modifier.size(56.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = stringResource(R.string.share_retry_all),
                    )
                }
            }
        }
    }
}

// --- Globale Zustaende ------------------------------------------------------------------

@Composable
private fun NoFileCard(padding: PaddingValues, onClose: () -> Unit) {
    StatusCard(
        icon = painterResource(R.drawable.ic_music_off),
        iconTint = MaterialTheme.colorScheme.outline,
        title = stringResource(R.string.share_no_audio),
        body = stringResource(R.string.share_no_audio_body),
        modifier = Modifier.padding(padding),
    ) {
        PrimaryButton(stringResource(R.string.common_close), onClose)
    }
}

@Composable
private fun NotConfiguredCard(padding: PaddingValues, onOpenSetup: () -> Unit, onClose: () -> Unit) {
    StatusCard(
        icon = painterResource(R.drawable.ic_key),
        iconTint = MaterialTheme.loom.warning,
        title = stringResource(R.string.share_not_configured),
        body = stringResource(R.string.share_not_configured_body),
        modifier = Modifier.padding(padding),
    ) {
        PrimaryButton(stringResource(R.string.share_open_setup), onOpenSetup)
        TertiaryButton(stringResource(R.string.common_close), onClose)
    }
}

@Composable
private fun AllFailedCard(padding: PaddingValues, reason: String?, onRetry: () -> Unit, onClose: () -> Unit) {
    StatusCard(
        icon = painterResource(R.drawable.ic_error),
        iconTint = MaterialTheme.colorScheme.error,
        title = stringResource(R.string.share_all_failed),
        body = reason?.let { stringResource(R.string.share_one_failed, it) } ?: "",
        modifier = Modifier.padding(padding),
    ) {
        PrimaryButton(stringResource(R.string.common_retry), onRetry)
        TertiaryButton(stringResource(R.string.common_close), onClose)
    }
}
