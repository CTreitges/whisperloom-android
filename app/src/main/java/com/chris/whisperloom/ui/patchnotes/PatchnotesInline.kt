package com.chris.whisperloom.ui.patchnotes

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import com.chris.whisperloom.ui.components.SnackController
import com.chris.whisperloom.ui.components.openLink

/** Inline-Markdown des CHANGELOG: nur fett, `Code` und [Text](URL) — mehr kommt dort nicht vor. */
sealed interface Span {
    val text: String

    data class Plain(override val text: String) : Span
    data class Bold(override val text: String) : Span
    data class Code(override val text: String) : Span
    data class Link(override val text: String, val url: String) : Span
}

private val INLINE = Regex("""`([^`]+)`|\[([^\]]+)]\(([^)\s]+)\)|\*\*(.+?)\*\*""")

/**
 * Zerlegt [md] von links nach rechts. Code bleibt woertlich (auch `<think>` oder `**` darin), nur
 * http(s) wird ein Link (Anker wie `#310--…` werden Klartext), offene Marker bleiben stehen.
 * [Span.Bold] kann selbst Code oder Links enthalten (`**… `gpt-transcribe`**`): einfach erneut zerlegen.
 */
fun parseInline(md: String): List<Span> {
    val spans = mutableListOf<Span>()
    var pos = 0
    for (m in INLINE.findAll(md)) {
        if (m.range.first > pos) spans += Span.Plain(md.substring(pos, m.range.first))
        val (code, linkText, url, bold) = m.destructured
        spans += when {
            code.isNotEmpty() -> Span.Code(code)
            linkText.isNotEmpty() ->
                if (url.startsWith("https://") || url.startsWith("http://")) Span.Link(linkText, url) else Span.Plain(linkText)
            else -> Span.Bold(bold)
        }
        pos = m.range.last + 1
    }
    if (pos < md.length) spans += Span.Plain(md.substring(pos))
    return spans
}

/** Text ohne Markdown-Zeichen (Teaser in der Versionsliste). */
fun plainText(md: String): String = parseInline(md).joinToString("") { if (it is Span.Bold) plainText(it.text) else it.text }

/** [md] als AnnotatedString: fett, Inline-Code mit Flaeche, Links tippbar (oeffnen den Browser per [openLink]). */
@Composable
fun rememberInline(md: String, snack: SnackController): AnnotatedString {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    return remember(md) {
        buildAnnotatedString { appendSpans(parseInline(md), cs) { url -> openLink(ctx, url, snack) } }
    }
}

private fun AnnotatedString.Builder.appendSpans(spans: List<Span>, cs: ColorScheme, open: (String) -> Unit) {
    spans.forEach { span ->
        when (span) {
            is Span.Plain -> append(span.text)
            is Span.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = cs.onSurface)) {
                appendSpans(parseInline(span.text), cs, open)
            }
            // Schmales geschuetztes Leerzeichen (U+202F) um den Code: die Flaeche klebt nicht am Nachbarwort.
            is Span.Code -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 0.9.em, background = cs.surfaceContainerHighest, color = cs.onSurface),
            ) { append(" ${span.text} ") }
            // Context7: Compose ui 1.12 – LinkAnnotation.Url/withLink (developer.android.com/develop/ui/compose/text/user-interactions):
            // eigener LinkInteractionListener statt UriHandler, damit "kein Browser" als Snackbar endet (openLink).
            is Span.Link -> withLink(
                LinkAnnotation.Url(span.url, TextLinkStyles(SpanStyle(color = cs.primary, textDecoration = TextDecoration.Underline))) {
                    open(span.url)
                },
            ) { append(span.text) }
        }
    }
}
