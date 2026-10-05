package com.chris.whisperloom.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.BuildConfig
import com.chris.whisperloom.R
import com.chris.whisperloom.api.ProviderCatalog
import com.chris.whisperloom.ui.access.helpKeyText
import com.chris.whisperloom.ui.components.DetailScaffold
import com.chris.whisperloom.ui.components.ExpandableCard
import com.chris.whisperloom.ui.components.GuideHeader
import com.chris.whisperloom.ui.components.LinkRow
import com.chris.whisperloom.ui.components.OutlinedSection
import com.chris.whisperloom.ui.components.SnackController
import com.chris.whisperloom.ui.components.StepBadge
import com.chris.whisperloom.ui.components.SystemIntents
import com.chris.whisperloom.ui.components.VersionWithPatchnotes
import com.chris.whisperloom.ui.components.LoomIcon
import com.chris.whisperloom.ui.components.LoomRow
import com.chris.whisperloom.ui.components.openLink
import com.chris.whisperloom.ui.components.providerLabel
import com.chris.whisperloom.ui.components.rememberSnack
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SetupRouter
import com.chris.whisperloom.ui.setup.stepName
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.tutorial.TutorialKind

/**
 * E5 — Anleitung & Hilfe (UX-Spec §2.8): acht aufklappbare Abschnitte, [section] (1–8) initial offen.
 * Jeder Abschnitt beginnt mit Illustration und Kurztext ([GuideHeader]).
 */
@Composable
fun HelpScreen(section: Int, nav: NavState) {
    val ctx = LocalContext.current
    val status = LocalAppEnv.current.status
    val snack = rememberSnack()
    // Offene Abschnitte als Bitmaske (rememberSaveable-tauglich).
    var openMask by rememberSaveable { mutableIntStateOf(1 shl section.coerceIn(1, HELP_SECTIONS)) }
    fun isOpen(n: Int) = openMask and (1 shl n) != 0
    fun toggle(n: Int) { openMask = openMask xor (1 shl n) }

    DetailScaffold(title = stringResource(R.string.help_title), onBack = { nav.pop() }, snack = snack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ExpandableCard(stringResource(R.string.help_s1_title), isOpen(1), { toggle(1) }, icon = R.drawable.ic_touch_app) {
                    GuideHeader(R.drawable.ill_tutorial_button, R.string.tutorial_img_button, text = stringResource(R.string.help_s1_intro))
                    HelpLine(R.drawable.ic_touch_app, stringResource(R.string.help_s1_bubble))
                    HelpLine(R.drawable.ic_keyboard, stringResource(R.string.help_s1_keyboard))
                    HelpLine(R.drawable.ic_voicemail, stringResource(R.string.help_s1_share))
                    // Nur anzeigen — tutorialSeen bleibt gesetzt.
                    HelpNavRow(R.drawable.ic_replay, stringResource(R.string.help_s1_tutorial)) { nav.push(Screen.Tutorial()) }
                }
            }
            item {
                ExpandableCard(stringResource(R.string.help_s2_title), isOpen(2), { toggle(2) }, icon = R.drawable.ic_checklist) {
                    GuideHeader(R.drawable.ill_help_setup, R.string.img_help_setup, text = stringResource(R.string.help_s2_intro))
                    val steps = (SetupRouter.STEP_ENGINE..SetupRouter.STEP_KEYBOARD).filter { it != SetupRouter.STEP_NOTIF || status.notifNeeded }
                    steps.forEach { step ->
                        LoomRow(
                            headline = stepName(step),
                            leading = { StepBadge(step) },
                            trailing = {
                                TextButton(onClick = { nav.push(Screen.Setup(step)) }) { Text(stringResource(R.string.common_open)) }
                            },
                        )
                    }
                }
            }
            item {
                ExpandableCard(stringResource(R.string.help_s3_title), isOpen(3), { toggle(3) }, icon = R.drawable.ic_key) {
                    GuideHeader(R.drawable.ill_help_key, R.string.img_help_key, text = stringResource(R.string.help_s3_intro))
                    ProviderCatalog.providers.filter { it.keyUrl.isNotEmpty() }.forEach { p ->
                        LinkRow(headline = providerLabel(p), supporting = helpKeyText(p.id), url = p.keyUrl, snack = snack)
                    }
                    Text(
                        stringResource(R.string.help_prices_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                ExpandableCard(stringResource(R.string.help_s4_title), isOpen(4), { toggle(4) }, icon = R.drawable.ic_dns) {
                    GuideHeader(R.drawable.ill_help_server, R.string.img_help_server, text = stringResource(R.string.help_s4_intro))
                    Text(stringResource(R.string.help_s4_body), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                ExpandableCard(stringResource(R.string.help_s5_title), isOpen(5), { toggle(5) }, icon = R.drawable.ic_offline_bolt) {
                    GuideHeader(R.drawable.ill_help_offline, R.string.img_help_offline, text = stringResource(R.string.help_s5_body))
                }
            }
            item {
                ExpandableCard(stringResource(R.string.help_widgets_title), isOpen(6), { toggle(6) }, icon = R.drawable.ic_layers) {
                    GuideHeader(R.drawable.ill_pro_widgets, R.string.img_pro_widgets, text = stringResource(R.string.help_widgets_intro))
                    HelpLine(R.drawable.ic_build, stringResource(R.string.help_widgets_unlock))
                    HelpLine(R.drawable.ic_add, stringResource(R.string.help_widgets_create))
                    HelpLine(R.drawable.ic_dns, stringResource(R.string.help_widgets_server))
                    HelpLine(R.drawable.ic_home, stringResource(R.string.help_widgets_place))
                    // Weitere Pro-Funktion unter "Erweitert" (3.8.0) — hier wird "Erweitert" erklaert.
                    HelpLine(R.drawable.ic_refresh, stringResource(R.string.help_widgets_models))
                    HelpNavRow(R.drawable.ic_help, stringResource(R.string.advanced_tutorial)) {
                        nav.push(Screen.Tutorial(kind = TutorialKind.PRO_WIDGETS))
                    }
                    HelpNavRow(R.drawable.ic_layers, stringResource(R.string.help_widgets_open)) { nav.push(Screen.Widgets()) }
                }
            }
            item {
                ExpandableCard(stringResource(R.string.help_s6_title), isOpen(7), { toggle(7) }, icon = R.drawable.ic_lock) {
                    GuideHeader(R.drawable.ill_help_privacy, R.string.img_help_privacy, text = stringResource(R.string.help_s6_intro))
                    PrivacyCards()
                    HelpLine(R.drawable.ic_accessibility_new, stringResource(R.string.help_s6_a11y))
                }
            }
            item {
                ExpandableCard(stringResource(R.string.help_s7_title), isOpen(8), { toggle(8) }, icon = R.drawable.ic_build) {
                    GuideHeader(R.drawable.ill_help_trouble, R.string.img_help_trouble, text = stringResource(R.string.help_s7_intro))
                    ProblemEntry(stringResource(R.string.help_p1), stringResource(R.string.help_p1_body)) { nav.push(Screen.Setup(SetupRouter.STEP_OVERLAY)) }
                    ProblemEntry(stringResource(R.string.help_p2), stringResource(R.string.help_p2_body)) { nav.push(Screen.Setup(SetupRouter.STEP_A11Y)) }
                    ProblemEntry(stringResource(R.string.help_p3), stringResource(R.string.help_p3_body)) { nav.push(Screen.Recognition) }
                    ProblemEntry(stringResource(R.string.help_p4), stringResource(R.string.help_p4_body)) {
                        SystemIntents.open(ctx, SystemIntents.appDetails(ctx))
                    }
                    ProblemEntry(stringResource(R.string.help_p5), stringResource(R.string.help_p5_body)) { nav.push(Screen.Models) }
                }
            }
            item { HelpFooter(snack) { nav.push(Screen.Patchnotes) } }
        }
    }
}

/** Anzahl der Abschnitte; [HelpScreen] klemmt `section` auf 1..HELP_SECTIONS. */
private const val HELP_SECTIONS = 8

/** Zeile mit Symbol und Pfeil, die zu einer Anleitung oder einem Screen fuehrt. */
@Composable
private fun HelpNavRow(icon: Int, headline: String, onClick: () -> Unit) {
    LoomRow(
        headline = headline,
        leading = { LoomIcon(icon, null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary) },
        trailing = { LoomIcon(R.drawable.ic_chevron_right, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
        onClick = onClick,
    )
}

@Composable
private fun HelpLine(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LoomIcon(icon, null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** Datenschutz: zwei Karten nebeneinander, auf schmalen Geraeten untereinander. */
@Composable
private fun PrivacyCards() {
    BoxWithConstraints {
        if (maxWidth >= 480.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedSection(Modifier.weight(1f)) { PrivacyText(R.string.help_s6_online) }
                OutlinedSection(Modifier.weight(1f)) { PrivacyText(R.string.help_s6_offline) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedSection { PrivacyText(R.string.help_s6_online) }
                OutlinedSection { PrivacyText(R.string.help_s6_offline) }
            }
        }
    }
}

@Composable
private fun PrivacyText(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.bodyMedium)
}

/** Akkordeon-Eintrag "Wenn etwas nicht klappt": Titel, aufklappbarer Text, Button zum Ziel. */
@Composable
private fun ProblemEntry(title: String, body: String, onAction: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        LoomRow(headline = title, onClick = { open = !open }, trailing = {
            LoomIcon(R.drawable.ic_expand_more, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        })
        AnimatedVisibility(visible = open) {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = onAction) { Text(stringResource(R.string.common_open)) }
            }
        }
    }
}

/** Fusszeile: Version mit (?) zu den Patchnotes, Lizenzen, Quellcode-Link. */
@Composable
private fun HelpFooter(snack: SnackController, onPatchnotes: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        VersionWithPatchnotes(
            stringResource(R.string.home_version, BuildConfig.VERSION_NAME),
            MaterialTheme.typography.bodySmall,
            MaterialTheme.colorScheme.outline,
            onPatchnotes,
        )
        Text(
            stringResource(R.string.about_license),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = { openLink(ctx, GITHUB_URL, snack) }) {
            Text(stringResource(R.string.about_source), style = MaterialTheme.typography.bodySmall)
            LoomIcon(R.drawable.ic_open_in_new, stringResource(R.string.cd_open_link), Modifier.padding(start = 4.dp).size(16.dp))
        }
    }
}
