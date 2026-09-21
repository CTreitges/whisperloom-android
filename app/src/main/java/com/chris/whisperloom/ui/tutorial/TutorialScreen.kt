package com.chris.whisperloom.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chris.whisperloom.R
import com.chris.whisperloom.ui.components.PrimaryButton
import com.chris.whisperloom.ui.share.rememberReduceMotion
import com.chris.whisperloom.ui.state.LocalAppEnv
import kotlinx.coroutines.launch

/** Eine Tutorial-Seite: Illustration (mit Bildtext fuer TalkBack), Titel, Erklaerung. */
private class TutorialPage(
    @param:DrawableRes val image: Int,
    @param:StringRes val imageText: Int,
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
)

/** Einsteiger-Tutorial; Index 2 = Sprachnachrichten abtippen (Screen.Tutorial.PAGE_SHARE). */
private val BASICS_PAGES = listOf(
    TutorialPage(R.drawable.ill_tutorial_button, R.string.tutorial_img_button, R.string.tutorial_p1_title, R.string.tutorial_p1_body),
    TutorialPage(R.drawable.ill_tutorial_keyboard, R.string.tutorial_img_keyboard, R.string.tutorial_p2_title, R.string.tutorial_p2_body),
    TutorialPage(R.drawable.ill_tutorial_share, R.string.tutorial_img_share, R.string.tutorial_p3_title, R.string.tutorial_p3_body),
    TutorialPage(R.drawable.ill_tutorial_result, R.string.tutorial_img_result, R.string.tutorial_p4_title, R.string.tutorial_p4_body),
)

/** Sprachauftrag-Tutorial: Widget hinzufuegen, Server eintragen, aufnehmen, Antwort finden. */
private val AGENT_PAGES = listOf(
    TutorialPage(R.drawable.ill_agent_widget, R.string.tutorial_agent_img_widget, R.string.tutorial_agent_p1_title, R.string.tutorial_agent_p1_body),
    TutorialPage(R.drawable.ill_agent_server, R.string.tutorial_agent_img_server, R.string.tutorial_agent_p2_title, R.string.tutorial_agent_p2_body),
    TutorialPage(R.drawable.ill_agent_record, R.string.tutorial_agent_img_record, R.string.tutorial_agent_p3_title, R.string.tutorial_agent_p3_body),
    TutorialPage(R.drawable.ill_agent_answer, R.string.tutorial_agent_img_answer, R.string.tutorial_agent_p4_title, R.string.tutorial_agent_p4_body),
)

private fun pagesOf(kind: TutorialKind) = if (kind == TutorialKind.AGENT) AGENT_PAGES else BASICS_PAGES

/**
 * T — Tutorial: vier Seiten im HorizontalPager, [kind] waehlt das Heft. Laeuft einmal automatisch nach der Einrichtung
 * (W9) bzw. beim ersten Start eines bereits eingerichteten Bestandsnutzers; spaeter aus Home
 * ("Mehr" -> Seite 3) und Hilfe ("Tutorial erneut ansehen"). "Ueberspringen" und "Los geht's"
 * setzen das Gesehen-Flag DES HEFTES ([TutorialKind.markSeen]) und rufen [onFinish];
 * Zurueck blaettert, auf Seite 1 = ueberspringen.
 */
@Composable
fun TutorialScreen(startPage: Int = 0, kind: TutorialKind = TutorialKind.BASICS, onFinish: () -> Unit) {
    val prefs = LocalAppEnv.current.prefs
    val pages = pagesOf(kind)
    val pager = rememberPagerState(initialPage = startPage.coerceIn(0, pages.lastIndex)) { pages.size }
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val last = pager.currentPage == pages.lastIndex

    // Seitenwechsel mit Standard-Animation, bei Reduce-Motion ohne (Spec §5.4).
    val goTo: (Int) -> Unit = { page ->
        scope.launch { if (reduceMotion) pager.scrollToPage(page) else pager.animateScrollToPage(page) }
    }
    val finish = {
        kind.markSeen(prefs.prefs)
        onFinish()
    }

    BackHandler { if (pager.currentPage > 0) goTo(pager.currentPage - 1) else finish() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            TutorialBottomBar(last = last, onSkip = finish, onNext = { if (last) finish() else goTo(pager.currentPage + 1) })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page -> TutorialPageContent(pages[page]) }
            PageDots(pager)
        }
    }
}

/** Illustration oben (rund 45 % der Hoehe), darunter Titel (Ueberschrift-Semantik) und Text. */
@Composable
private fun TutorialPageContent(page: TutorialPage) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Image(
            painter = painterResource(page.image),
            contentDescription = stringResource(page.imageText),
            modifier = Modifier.fillMaxWidth().weight(0.45f).padding(top = 16.dp, bottom = 8.dp),
            contentScale = ContentScale.Fit,
        )
        Column(
            Modifier.weight(0.55f).verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(page.title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(page.body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Punkt-Indikator: aktive Seite als 20-dp-Pille in primary, andere als 8-dp-Punkte; fuer TalkBack "Seite x von y". */
@Composable
private fun PageDots(pager: PagerState) {
    val text = stringResource(R.string.tutorial_cd_page, pager.currentPage + 1, pager.pageCount)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp).semantics { contentDescription = text },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pager.pageCount) { i ->
            val active = i == pager.currentPage
            Box(
                Modifier
                    .size(width = if (active) 20.dp else 8.dp, height = 8.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

/** Bottom-Bar: "Ueberspringen" (nicht auf der letzten Seite) und "Weiter" bzw. "Los geht's" (56 dp, restliche Breite). */
@Composable
private fun TutorialBottomBar(last: Boolean, onSkip: () -> Unit, onNext: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!last) {
                    TextButton(onClick = onSkip, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.tutorial_skip)) }
                }
                PrimaryButton(
                    text = stringResource(if (last) R.string.tutorial_start else R.string.tutorial_next),
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                    trailingIcon = if (last) null else R.drawable.ic_arrow_forward,
                )
            }
        }
    }
}
