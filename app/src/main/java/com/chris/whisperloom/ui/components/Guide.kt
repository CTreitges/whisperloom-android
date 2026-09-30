package com.chris.whisperloom.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Welches Drawable eine Anleitungs-Illustration zeigt (nur fuer Tests, TalkBack liest es nicht).
 * Bild und Bildtext werden getrennt uebergeben; der Bildtext allein beweist nicht das richtige Bild.
 */
val IllustrationRes = SemanticsPropertyKey<Int>("IllustrationRes")
var SemanticsPropertyReceiver.illustrationRes by IllustrationRes

/**
 * Anleitungs-Kopf: Illustration (ill_*.xml) oben, optional ein kurzer Text darunter. Das Bild
 * traegt seinen Bildtext [imageText] fuer TalkBack. Die Tutorial-Seiten haben ihr eigenes Layout.
 */
@Composable
fun GuideHeader(
    @DrawableRes image: Int,
    @StringRes imageText: Int,
    modifier: Modifier = Modifier,
    text: String? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(
            painterResource(image),
            contentDescription = stringResource(imageText),
            modifier = Modifier.fillMaxWidth().heightIn(max = 168.dp).semantics { illustrationRes = image },
            contentScale = ContentScale.Fit,
        )
        if (text != null) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
