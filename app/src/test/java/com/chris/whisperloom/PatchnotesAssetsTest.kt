package com.chris.whisperloom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.ui.patchnotes.PatchnotesLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Release-Waechter fuer die Patchnotes: Gradle kopiert CHANGELOG.md und die Fastlane-Highlights in die
 * Assets (copy<Variant>PatchnotesAssets). Wer die Version hochzaehlt, ohne beides nachzutragen, bricht hier.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PatchnotesAssetsTest {

    private val assets = ApplicationProvider.getApplicationContext<Context>().assets

    @Test fun changelogEnthaeltAktuelleVersion() {
        val text = assets.open("patchnotes/CHANGELOG.md").bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertTrue("## [${BuildConfig.VERSION_NAME}] fehlt im CHANGELOG", text.contains("## [${BuildConfig.VERSION_NAME}]"))
        assertTrue("UTF-8 intakt", text.contains("glätten"))
    }

    @Test fun highlightsZurAktuellenVersionVorhanden() {
        val first = assets.open("patchnotes/highlights/${BuildConfig.VERSION_CODE}.txt")
            .bufferedReader(Charsets.UTF_8).use { it.readLine() }
        assertEquals("WhisperLoom ${BuildConfig.VERSION_NAME}", first)
    }

    @Test fun loaderVerbindetChangelogUndHighlights() {
        val releases = PatchnotesLoader.load(assets)
        assertEquals(BuildConfig.VERSION_NAME, releases.first().version)
        assertTrue("Highlights der aktuellen Version", releases.first().highlights.isNotEmpty())
        // 6.txt traegt die Tagline von 3.3.0; 10.txt..14.txt sortiert assets.list davor ein.
        assertEquals("Diktieren ohne Dauerhalten.", releases.single { it.version == "3.3.0" }.tagline)
    }
}
