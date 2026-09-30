package com.chris.whisperloom.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Die eingebauten Profil-Symbole: gespeichert wird der Schluessel, also muss er stabil bleiben. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetIconsTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun dieSchluesselSindFestgeschrieben() {
        // Ein umbenannter oder entfernter Schluessel setzt gespeicherte Profile lautlos aufs Mikrofon
        // zurueck. Neue Symbole hinten oder an passender Stelle ergaenzen — diese Liste mitziehen.
        assertEquals(
            listOf(
                "mic", "shopping_cart", "home", "work", "lightbulb", "event", "edit_note",
                "directions_car", "favorite", "star", "chat", "voicemail", "graphic_eq", "send",
                "checklist", "schedule", "notifications", "build", "cloud", "language",
                "auto_fix_high", "key",
            ),
            WidgetIcons.all.map { it.key },
        )
    }

    @Test fun dieSchluesselSindEindeutigUndDasMikrofonStehtVorn() {
        val keys = WidgetIcons.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(WidgetIcons.DEFAULT_KEY, keys.first())
        assertEquals(ProfileIcon.BuiltIn(WidgetIcons.DEFAULT_KEY), ProfileIcon.DEFAULT)
    }

    @Test fun jedesSymbolHatEinTalkBackLabel() {
        val labels = WidgetIcons.all.map { ctx.getString(it.label) }
        labels.forEach { assertTrue("leeres Label", it.isNotBlank()) }
        assertEquals("Labels muessen sich unterscheiden: $labels", labels.size, labels.toSet().size)
        assertEquals("Mikrofon", ctx.getString(WidgetIcons.of("mic").label))
        assertEquals("Einkauf", ctx.getString(WidgetIcons.of("shopping_cart").label))
    }

    @Test fun jedesSymbolHatSeinDrawable() {
        WidgetIcons.all.forEach {
            assertEquals("ic_${it.key}", ctx.resources.getResourceEntryName(it.drawable))
            assertNotNull("${it.key} laedt nicht", ctx.getDrawable(it.drawable))
        }
    }

    @Test fun keinSymbolBringtEineEigeneToenungMit() {
        // Das Widget faerbt eingebaute Symbole per setColorFilter (Repo-Regel: kein android:tint).
        WidgetIcons.all.forEach {
            val xml = listOf("src/main/res/drawable/ic_${it.key}.xml", "app/src/main/res/drawable/ic_${it.key}.xml")
                .map(::File).first(File::exists).readText()
            // Als Attribut gesucht: der Kopfkommentar jeder Datei erwaehnt "android:tint" absichtlich.
            assertFalse("ic_${it.key} hat android:tint", xml.contains("android:tint=\""))
            assertTrue("ic_${it.key} ohne Quellenangabe", xml.contains("Material Symbols Rounded"))
        }
    }

    @Test fun einUnbekannterSchluesselErgibtDasMikrofon() {
        assertEquals("mic", WidgetIcons.of("rakete").key)
        assertEquals("mic", WidgetIcons.of("").key)
        assertEquals("star", WidgetIcons.of("star").key)
        assertTrue(WidgetIcons.isKnown("star"))
        assertFalse(WidgetIcons.isKnown("rakete"))
    }
}
